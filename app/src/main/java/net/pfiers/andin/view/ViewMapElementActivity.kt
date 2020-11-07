package net.pfiers.andin.view

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.andin_api.type.CustomType
import com.apollographql.apollo.ApolloClient
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import net.pfiers.andin.*
import net.pfiers.andin.databinding.ActivityViewMapElementBinding
import net.pfiers.andin.db.AndinDb
import net.pfiers.andin.db.getOrDownload
import net.pfiers.andin.view.fragments.support.SlippymapFragment
import okhttp3.HttpUrl
import java.util.*


class ViewMapElementActivity : AppCompatActivity() {
    lateinit var binding: ActivityViewMapElementBinding
    lateinit var viewModel: MapViewModel
    lateinit var view: View

    override fun onCreateOptionsMenu(menu: Menu?): Boolean { // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.view_map_element, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.open_in_app) {
            Log.v("AAA", "Open in app")
            return false
        } else {
            return super.onOptionsItemSelected(item)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        Log.v("AAA","Intent: ${intent.action}")
        if (intent.action == Intent.ACTION_VIEW) {
            val data = intent.data
            if (data != null) {
                if (data.pathSegments.getOrNull(0) == "room") {
                    val uuidStr = data.pathSegments.getOrNull(1)
                    val uuid = try {
                        UUID.fromString(uuidStr)
                    } catch (e: IllegalArgumentException) {
                        val snack = Snackbar.make(view, "Unknown room format \"$uuidStr\"", Snackbar.LENGTH_LONG)
                        snack.show()
                        return
                    }
                    getOrDownload(uuid, viewModel, { room ->
                        viewModel.desiredLevel.set(room.levelRange.from)
                        viewModel.selectedMapElement.set(room)
                    }, { e ->
                        dialog("Error getting info for shared room", e.message)
                    })
                }
            }
        }

        this.intent = null // Prevents triggering handleIntent() again next time
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onBackPressed() {
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProviders.of(this)[MapViewModel::class.java]

        viewModel.dao = AndinDb.getInstance(application).dao

        val d = viewModel.dao

        val all = d.getAll()
        viewModel.favorites = all

        if (!viewModel.apolloClientInitialized) {
            val defaultHost = getString(R.string.default_andin_host)

            val sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(this /* Activity context */)
            val urlBuilder = HttpUrl.Builder()
                .port(41533)
                .addPathSegment("graphql")
                .scheme("http")
            val prefHost = sharedPreferences.getString("api_hostname",null)
            val host = if (!prefHost.isNullOrBlank()) prefHost else defaultHost
            try {
                urlBuilder.host(host)
            } catch (ex: IllegalArgumentException) {
                dialog("Bad hostname", "Bad hostname (\"$host\"), fell back to default (\"$defaultHost\")")
                urlBuilder.host(getString(R.string.default_andin_host))
            }
            val url = urlBuilder.build()
            Log.v("AAA", "Url: $url")
            // 10.0.2.2
            // 192.168.1.22
            // 192.168.1.26
            val apolloClient = ApolloClient.builder()
                .serverUrl(url)
                .okHttpClient(okHttpClient)
                .addCustomTypeAdapter(
                    CustomType.UUID,
                    uuidCustomTypeAdapter
                )
                .addCustomTypeAdapter(
                    CustomType.B64WKB,
                    b64WkbCustomTypeAdapter
                )
                //            .addCustomTypeAdapter(CustomType.WKT, wktCustomTypeAdapter)
                .build()
            viewModel.apolloClient = apolloClient
        }

        binding = ActivityViewMapElementBinding.inflate(layoutInflater)
        view = binding.root

        val toolbar = binding.toolbar
        setSupportActionBar(toolbar)

        val slippymapFragment = supportFragmentManager.findFragmentById(R.id.slippymapFragment) as SlippymapFragment
        slippymapFragment.highlightFavorites.set(true)

        setContentView(binding.root)

        handleIntent(intent)
    }

    fun dialog(title: String, message: String?) {
        val builder = AlertDialog.Builder(this@ViewMapElementActivity)
        builder.setTitle(title)
        builder.setMessage(message)
        lifecycleScope.launch {
            val dialog: AlertDialog = builder.create()
            dialog.show()
        }
    }
}
