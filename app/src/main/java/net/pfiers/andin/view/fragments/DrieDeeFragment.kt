package net.pfiers.andin.view.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import com.badlogic.gdx.backends.android.AndroidFragmentApplication
import net.pfiers.andin.MapViewModel
import net.pfiers.andin.gdx.BucketGame
import java.lang.IllegalArgumentException


class DrieDeeFragment : AndroidFragmentApplication(), AndroidFragmentApplication.Callbacks {
    private lateinit var viewModel: MapViewModel
    private lateinit var game: BucketGame

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        viewModel = activity?.run {
            ViewModelProvider(this)[MapViewModel::class.java]
        } ?: throw Exception("Invalid Activity")

        game = BucketGame(viewModel)
        // return the GLSurfaceView on which libgdx is drawing game stuff
        return initializeForView(game);
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            game.dispose()
        } catch (e: IllegalArgumentException) {
            // nop
        }
    }
}
