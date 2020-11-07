package net.pfiers.andin.gdx

import android.util.Log
import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalShadowLight
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.input.GestureDetector
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.GdxRuntimeException
import kotlinx.coroutines.sync.Mutex
import net.pfiers.andin.MapViewModel
import net.pfiers.andin.domain.*
import net.pfiers.andin.domain.geometry.TAU
import net.pfiers.andin.domain.geometry.positiveAngle
import net.pfiers.andin.domain.geometry.smallestAngleTo
import net.pfiers.andin.model.geospatial.GEO
import net.pfiers.andin.model.geospatial.distanceGeo
import net.pfiers.andin.model.map.CompleteBuilding
import org.locationtech.jts.geom.Coordinate
import kotlin.math.*


class SphericalCoordinate(val rho: Double, theta: Double, phi: Double) {
    val theta: Double = positiveAngle(theta)
    val phi: Double = positiveAngle(phi)

    val vector3: Vector3 by lazy { Vector3(
        (rho * cos(theta) * sin(phi)).toFloat(),
        (rho * cos(phi)).toFloat(),
        (rho * sin(theta) * sin(phi)).toFloat()
    ) }

    operator fun plus(other: SphericalCoordinate) = SphericalCoordinate(
        rho + other.rho,
        theta + other.theta,
        phi + other.phi
    )
}

data class IntVector2(val x: Int, val y: Int) {
    operator fun minus(other: IntVector2) = IntVector2(
        x - other.x,
        y - other.y
    )

    val abs by lazy { IntVector2(
        x.absoluteValue,
        y.absoluteValue
    ) }
}

val Vector3.spherical get() = SphericalCoordinate(
    len().toDouble(),
    atan2(y.toDouble(), x.toDouble()),
    acos(z.toDouble() / len().toDouble())
)


class BucketGame(val viewModel: MapViewModel) : ApplicationAdapter(), InputProcessor {
    private var cameraCoordinate = Vector3(10f, 10f, 10f).spherical
    private var cameraCenter = Vector2(0f, 0f)
    private lateinit var camera: PerspectiveCamera
    private lateinit var environment: Environment
    private lateinit var shadowLight: DirectionalShadowLight
    private lateinit var batch: ModelBatch
    private lateinit var wallBoxModels: MutableList<Model>
    private lateinit var wallBoxInstances: MutableList<ModelInstance>
    private lateinit var mesh: Mesh
    private lateinit var shaderProgram: ShaderProgram

    val VERT_SHADER = """attribute vec2 a_position;
attribute vec4 a_color;
uniform mat4 u_projTrans;
varying vec4 vColor;
void main() {
	vColor = a_color;
	gl_Position =  u_projTrans * vec4(a_position.xy, 0.0, 1.0);
}"""

    val FRAG_SHADER = """#ifdef GL_ES
precision mediump float;
#endif
varying vec4 vColor;
void main() {
	gl_FragColor = vColor;
}"""

    protected fun createMeshShader(): ShaderProgram {
        ShaderProgram.pedantic = false
        val shader = ShaderProgram(VERT_SHADER, FRAG_SHADER)
        val log: String = shader.log
        if (!shader.isCompiled) throw GdxRuntimeException(log)
        if (log.isNotEmpty()) println("Shader Log: $log")
        return shader
    }

//    private fun wallMesh(wall: List) {
//
//    }

    override fun create() {
        pointerOnScreen = MutableList(Gdx.input.maxPointers) { false }

        Gdx.input.inputProcessor = this

        camera = PerspectiveCamera(
            67F, Gdx.graphics.width.toFloat(),
            Gdx.graphics.height.toFloat()
        )
        camera.position.set(cameraCoordinate.vector3)
        camera.lookAt(0F, 0F, 0F)
        camera.near = 1f
        camera.far = 300f
        camera.update()

        environment = Environment()
        environment.set(ColorAttribute(ColorAttribute.AmbientLight, 1.0f, 1f, 1f, 1f))
//        environment.add(DirectionalShadowLight(1024, 1024, 60f, 60f, .1f, 50f).also {
//            shadowLight = it
//        }.set(1f, 1f, 1f, -35f, 40.0f, -35f))
//        environment.add(PointLight().setPosition(-10f, -10f, 10f))
//        environment.shadowMap = shadowLight

        val building = viewModel.mapData.elements.values.filterIsInstance<CompleteBuilding>().first()
        val envelope = building.geometry.envelopeInternal
        val bL = Coordinate(envelope.minX, envelope.minY)
        val bR = Coordinate(envelope.maxX, envelope.minY)
        val tL = Coordinate(envelope.minX, envelope.maxY)
        val tR = Coordinate(envelope.maxX, envelope.maxY)
        val geoWidthBottom = bL.distanceGeo(GEO, bR)
        val geoWidthTop = tL.distanceGeo(GEO, tR)
        val geoHeightLeft = bL.distanceGeo(GEO, tL)
        val geoHeightRight = bR.distanceGeo(GEO, tR)

        val xRatio = 7.0 / max(geoWidthBottom, geoWidthTop)
        val yRatio = 7.0 / max(geoHeightLeft, geoHeightRight)

        val project = { coordinate: Coordinate ->
            Vector2(
                (((coordinate.x - envelope.minX) / (envelope.maxX - envelope.minX)) * 14.0).toFloat() - 7f,
                (((coordinate.y - envelope.minY) / (envelope.maxY - envelope.minY)) * 14.0).toFloat() - 7f
            )
        }
//
//        val meshBuilder = MeshBuilder()
        val modelBuilder = ModelBuilder()
//
        val boxModel = { w: Float, h: Float, d: Float, x: Float, y: Float, z: Float, r: Float, c: Color ->
            val model = modelBuilder.createBox(
                w, h, d,
                Material(ColorAttribute.createDiffuse(c)),
                (Usage.Position or Usage.Normal).toLong()
            )
            val instance = ModelInstance(model)
            instance.transform.rotate(Vector3(0F, 1F, 0F), r)
            instance.transform.translate(w / 2f, h / 2f, d / 2f)
            instance.transform.translate(x, y, z)
            Pair(model, instance)
        }

        val wallModel = { w: Float, h: Float, d: Float, x: Float, y: Float, z: Float, r: Float, c: Color ->
            val model = modelBuilder.createBox(
                w, h, d,
                Material(ColorAttribute.createDiffuse(c)),
                (Usage.Position or Usage.Normal).toLong()
            )
            val instance = ModelInstance(model)
            instance.transform.translate(x, y, z)
            instance.transform.rotateRad(Vector3.Y, r)
            instance.transform.translate(w / 2f, h / 2f, 0f)
            Pair(model, instance)
        }

        wallBoxInstances = ArrayList()
        wallBoxModels = ArrayList()

        val modelBox = modelBuilder.createBox(
            1f, 1f, 1f,
            Material(ColorAttribute.createDiffuse(Color.GREEN)),
            (Usage.Position or Usage.Normal).toLong()
        )
        val instanceBox = ModelInstance(modelBox)
        val (modelX, instanceX) = boxModel(2f, 1f, 1f, -2f, 0f, 0f, 0f, Color.RED)
        val (modelY, instanceY) = boxModel(1f, 2f, 1f, 0f, -2f, 0f, 0f, Color.GREEN)
        val (modelZ, instanceZ) = boxModel(1f, 1f, 2f, 0f, 0f, -2f, 0f, Color.BLUE)

        val (modelXY, instanceXY) = boxModel(1f, 1f, 1f, -1f, -1f, 0f, 0f, Color.YELLOW)
        val (modelYZ, instanceYZ) = boxModel(1f, 1f, 1f, 0f, -1f, -1f, 0f, Color.CYAN)
        val (modelZX, instanceZX) = boxModel(1f, 1f, 1f, -1f, 0f, -1f, 0f, Color.MAGENTA)

        val (modelXYZ, instanceXYZ) = boxModel(1f, 1f, 1f, 0f, 0f, 0f, 0f, Color.PURPLE)

        val (modelWall, instanceWall) = wallModel(5f, 2f, 1f, 0f, 0f, 0f, 90f, Color.ORANGE)

//        wallBoxModels.add(modelX)
//        wallBoxInstances.add(instanceX)
//        wallBoxModels.add(modelY)
//        wallBoxInstances.add(instanceY)
//        wallBoxModels.add(modelZ)
//        wallBoxInstances.add(instanceZ)
//
//        wallBoxModels.add(modelXY)
//        wallBoxInstances.add(instanceXY)
//        wallBoxModels.add(modelYZ)
//        wallBoxInstances.add(instanceYZ)
//        wallBoxModels.add(modelZX)
//        wallBoxInstances.add(instanceZX)

        wallBoxModels.add(modelXYZ)
        wallBoxInstances.add(instanceXYZ)
//
//        wallBoxModels.add(modelWall)
//        wallBoxInstances.add(instanceWall)
//        wallBoxModels.add(modelBox)
//        wallBoxInstances.add(instanceBox)

//        val c: Float = Color.RED.toFloatBits()
//
//        shaderProgram = createMeshShader()
//        mesh = Mesh(
//            true, 6, 0,
//            VertexAttribute(Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE),
//            VertexAttribute(Usage.ColorPacked, 4, ShaderProgram.COLOR_ATTRIBUTE)
//        )
//
//        val vertices = listOf(
//            4, 0, 20, c,
//            4, 0, 0, c,
//            0, 0, 0, c,
//            0, 0, 0, c,
//            0, 0, 0, c,
//            4, 0, 20, c
//        ).map { it.toFloat() }.toFloatArray()
//        mesh.setVertices(vertices)




        val wallBetween = { p1: Vector2, p2: Vector2, h: Float, d: Float, c: Color ->
            val coordProj = p2.cpy().sub(p1)
            val len = coordProj.len()
            val angle = -coordProj.angleRad()
            Log.v(
                "RENDER",
                "Wall between Prev: $p1 , curr: $p2, proj: $coordProj, len: $len, angle: $angle, color: $c"
            )
            wallModel(len, 1f, 0.1f, p1.x, 0f, p1.y, angle, c)
        }


        val geomCoordsWalls = { geomCoords: List<Coordinate> ->
            val vectors = geomCoords.map { project(it) } .toList()

            val colors = listOf(
                Pair("blue", Color.BLUE),
                Pair("red", Color.RED),
                Pair("green", Color.GREEN),
                Pair("orange", Color.ORANGE),
                Pair("cyan", Color.CYAN)
            )
            var colorI = 0
            var prev = vectors.first()
            println("Prev: $prev")
            for (coord in vectors.subList(1, vectors.size)) {
                val co = colors[colorI++ % colors.size]
                Log.v("RENDER", "Color: ${co.first}")
                val (model, instance) = wallBetween(prev, coord, 1f, 0.1f, co.second)
                wallBoxModels.add(model)
                wallBoxInstances.add(instance)
                prev = coord
            }
        }
//
//        Log.v("AAA", "Room: ${room.name}")

        val rooms = building.indoorElements.rooms.filter { it.levelRange.contains(2.0) }
        rooms.forEach {
            geomCoordsWalls(it.geometry.exteriorRing.coordinates.toList())
        }

//        val geomCoords = room.geometry.exteriorRing.coordinates
//        val geomCoords = listOf(Coordinate(4.7152892, 50.8788975),
//                Coordinate(4.7152309, 50.8790626),
//        Coordinate(4.7147075, 50.878989))
//        println(geomCoords[0].azimuthGeo(GEO, geomCoords[1]))
//        println(geomCoords[1].azimuthGeo(GEO, geomCoords[2]))

//        val coords = listOf(
//            Vector2.Zero,
//            Vector2(0f, 5f),
//            Vector2(5f, 5f),
////            Vector2(5f, 0f),
//            Vector2(10f, 10f),
//            Vector2(10f, 5f),
//            Vector2(10f, 0f),
//            Vector2.Zero
//        )



//        val (m1, i1) = boxModel(5f, 1f, 0.1f, 0f, 5f, 0f, Color.GREEN)
//        wallBoxInstances.add(i1)
//        wallBoxModels.add(m1)
//        val (m2, i2) = boxModel(5f, 1f, 0.1f, 0f, 0f, 90f, Color.RED)
//        wallBoxInstances.add(i2)
//        wallBoxModels.add(m2)
//        val (m3, i3) = boxModel(5f, 1f, 0.1f, 0f, 0f, 180f, Color.BLUE)
//        wallBoxInstances.add(i3)
//        wallBoxModels.add(m3)
//        val (m4, i4) = boxModel(5f, 1f, 0.1f, 0f, 0f, 270f, Color.MAGENTA)
//        wallBoxInstances.add(i4)
//        wallBoxModels.add(m4)

//        val model = modelBuilder.createBox(5f, 1f, 0.1F,
//            Material(ColorAttribute.createDiffuse(Color.GREEN)),
//            (Usage.Position or Usage.Normal).toLong()
//        )
//        val model2 = modelBuilder.createBox(2.5f, 2f, 0.1F,
//            Material(ColorAttribute.createDiffuse(Color.RED)),
//            (Usage.Position or Usage.Normal).toLong()
//        )
//        val instance = ModelInstance(model)
//        instance.transform.translate(5f/2f, 1f/2f, 0.1f/2f)
//        val instance2 = ModelInstance(model2)
//        instance2.transform.rotate(Vector3(0F, 1F, 0F), 270f)
//        instance2.transform.translate(Vector3(2.5F / 2f, 2/2f, 0.1F/2f))
//        wallBoxModels.add(model)
//        wallBoxInstances.add(instance)
//        wallBoxInstances.add(instance2)

//        Log.v("AAA", ">> Height: ${coord3.y - coord2.y}")

//        wallBoxInstances = building.indoorElements.rooms.flatMap {
//            it.geometry.exteriorRing.coordinates
//            ModelInstance(modelBuilder.createXYZCoordinates()
//
//
//        }

        batch = ModelBatch()
    }

    override fun render() {
//        Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)
//        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        Gdx.gl20.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)
        Gdx.gl20.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        Gdx.gl20.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl20.glDepthMask(true)
        Gdx.gl20.glClearColor(1f, 1f, 1f, 1f);

        camera.position.set(cameraCoordinate.vector3.cpy().add(cameraCenter.vector3))
//        camera.view.set(cameraCoordinate.vector3, Input.Orientation)
//        camera.direction.setFromSpherical(cameraCoordinate.theta.toFloat(), cameraCoordinate.phi.toFloat())
        camera.lookAt(cameraCenter.vector3)
        camera.up.set(Vector3.Y)
        camera.update()

//        shaderProgram.bind()
//
//        shaderProgram.setUniformMatrix("u_projTrans", camera.combined);

//        mesh.render(shaderProgram, GL20.GL_TRIANGLES, 0, 6);

        batch.begin(camera)
        wallBoxInstances.forEach { batch.render(it, environment) }
        batch.end()
    }

    override fun dispose() {
        batch.dispose()
        wallBoxModels.forEach { it.dispose() }
    }

    override fun keyDown(keycode: Int): Boolean {
        TODO("Not yet implemented")
    }

    override fun keyUp(keycode: Int): Boolean {
        TODO("Not yet implemented")
    }

    override fun keyTyped(character: Char): Boolean {
        TODO("Not yet implemented")
    }

    lateinit private var pointerOnScreen: MutableList<Boolean>
//    private var lastPointerLocations = arrayOfNulls<IntVector2?>(3).toMutableList()

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        pointerOnScreen[pointer] = true
//        lastPointerLocations[pointer] = IntVector2(screenX, screenY)
        return true
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        pointerOnScreen[pointer] = false
//            lastPointerLocations.fill(null)
        return true
    }

    private val draggedMutex = Mutex()
    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        if (!draggedMutex.tryLock()) {
            Log.v("RENDER", "Touch dragged was busy")
            return false
        }

        val pointersNums = pointerOnScreen.withIndex().filter { it.value }.map { it.index }
        val d = Vector2(
            Gdx.input.getDeltaX(1).toFloat(),
            Gdx.input.getDeltaY(1).toFloat()
        )
        if (pointersNums.size == 2) {
            // Two finger: pan or zoom
            val deltas = pointersNums.map { deltaPointer -> Vector2(
                Gdx.input.getDeltaX(deltaPointer).toFloat(),
                Gdx.input.getDeltaY(deltaPointer).toFloat()
            ) }
            val delta1 = deltas[0]
            val delta2 = deltas[1]
            val angleD1 = delta1.angleRad().toDouble()
            val angleD2 = delta2.angleRad().toDouble()
            val deltaAngle = angleD1 smallestAngleTo angleD2
            val pinching = deltaAngle > PI / 2
            if (pinching) {
                // Two finger, moved perpendicular: zoom
                val delta2Rotated = delta2.cpy().rotateRad(deltaAngle.toFloat())
                val totalMovement = delta1.cpy().add(delta2Rotated)
                val vector1 = Vector2(
                    Gdx.input.getX(pointersNums[0]).toFloat(),
                    Gdx.input.getY(pointersNums[0]).toFloat()
                )
                val vector2 = Vector2(
                    Gdx.input.getX(pointersNums[1]).toFloat(),
                    Gdx.input.getY(pointersNums[1]).toFloat()
                )
                val vector1Sub2 = vector1.cpy().sub(vector2)
                val vector1Sub2Angle = vector1Sub2.angleRad().toDouble()
                val vector1Sub2AngleD1Diff = vector1Sub2Angle smallestAngleTo angleD1
                val zoomOutward = vector1Sub2AngleD1Diff < (PI / 2)
                val pinchLen = (totalMovement.len().toDouble()) * (if (zoomOutward) -1 else 1)

                val currentZoom = cameraCoordinate.rho
                val currentZoomNormalized = 1 - ((currentZoom - 5.0) / (30.0 - 5.0))

                val zoomRateBelowPoint3 = 1.0
                val zoomRateAbovePoint3 = 0.8952381 + 0.7111111*currentZoomNormalized - 1.206349*currentZoomNormalized.sqrd
                val zoomRate = if (currentZoomNormalized < 0.3) zoomRateBelowPoint3 else zoomRateAbovePoint3

                val zoomAmount = (pinchLen / 15) * zoomRate

                Log.v("RENDER", "two finger total: $totalMovement, len: ${pinchLen.round2}, rate: ${zoomRate.round2}, amount: ${zoomAmount.round2}, current: ${currentZoomNormalized.round2}")
                cameraCoordinate = SphericalCoordinate(
                    (cameraCoordinate.rho + zoomAmount).coerceIn(5.0..30.0),
                    cameraCoordinate.theta,
                    cameraCoordinate.phi
                )
            } else {
//                Log.v("RENDER", "Pan")
                // Two finger, moved parallel: pan
                val deltasAverage = deltas.average
                val movementVector = deltasAverage.mul(0.005f).rotateRad(
                    (cameraCoordinate.theta + PI / 2).toFloat()
                )
                cameraCenter.add(movementVector)
            }
        } else {
            // One finger: rotate
            val x = Gdx.input.deltaX.toDouble() / 240.0
            val y = Gdx.input.deltaY.toDouble() / 300.0
            val c = cameraCoordinate
            cameraCoordinate = SphericalCoordinate(
                c.rho,
                c.theta + x,
                (c.phi - y).coerceIn((PI * (1.0 / 5.0))..(PI * (2.25 / 5.0)))
            )
        }
        draggedMutex.unlock()
        return true
    }

    override fun mouseMoved(screenX: Int, screenY: Int): Boolean {
        TODO("Not yet implemented")
    }

    override fun scrolled(amount: Int): Boolean {
        TODO("Not yet implemented")
    }
}
