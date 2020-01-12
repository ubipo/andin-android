package net.pieterfiers.andin.view.fragments


import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.ui.NavigationUI
import net.pieterfiers.andin.MapViewModel

import net.pieterfiers.andin.R
import net.pieterfiers.andin.databinding.FragmentPreferencesBinding

class PreferencesFragment : Fragment() {
    private lateinit var viewModel: MapViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = activity?.run {
            ViewModelProviders.of(this)[MapViewModel::class.java]
        } ?: throw Exception("Invalid Activity")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = FragmentPreferencesBinding.inflate(inflater)

        val toolbar = binding.myToolbar
        val activity = requireActivity() as AppCompatActivity
        activity.setSupportActionBar(toolbar)
        NavigationUI.setupActionBarWithNavController(activity, viewModel.navController)

        return binding.root
    }


    companion object {
        @JvmStatic
        fun newInstance(param1: String, param2: String) = PreferencesFragment()
    }
}
