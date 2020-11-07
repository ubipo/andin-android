package net.pfiers.andin.view.fragments.support

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.badlogic.gdx.backends.android.AndroidFragmentApplication
import net.pfiers.andin.R
import net.pfiers.andin.view.fragments.DrieDeeFragment


class DrieDeeMapFragment : FragmentActivity(), AndroidFragmentApplication.Callbacks {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let {
        }

        val libgdxFragment = DrieDeeFragment()

        supportFragmentManager.beginTransaction().add(R.id.drie_dee, libgdxFragment).commit()
    }

    companion object {
        @JvmStatic
        fun newInstance() =
            DrieDeeMapFragment().apply {
//                savedInstanceState = Bundle().apply {
//                }
            }
    }

    override fun exit() {

    }
}