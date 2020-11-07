package net.pfiers.andin.view.fragments.support

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import net.pfiers.andin.MapViewModel
import net.pfiers.andin.R
import net.pfiers.andin.databinding.FragmentNavInfoBsBinding
import net.pfiers.andin.view.fragments.SearchResultsFragment


class NavInfoBsFragment : BottomSheetDialogFragment() {
    lateinit var viewModel: MapViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
        }
        viewModel = activity?.run {
            ViewModelProvider(this)[MapViewModel::class.java]
        } ?: throw Exception("Invalid Activity")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = FragmentNavInfoBsBinding.inflate(inflater)
        binding.lifecycleOwner = this

        binding.vm = viewModel

        binding.departureSearchButton.setOnClickListener {
            val args = Bundle()
            args.putBoolean(SearchResultsFragment.ARG_SEARCH_DEPARTURE, true)
            viewModel.navController.navigate(R.id.navSearchResultsFragment, args)
        }

        binding.departureClearButton.setOnClickListener {
            viewModel.departure.set(null)
        }

        binding.destinationSearchButton.setOnClickListener {
            val args = Bundle()
            args.putBoolean(SearchResultsFragment.ARG_SEARCH_DESTINATION, true)
            viewModel.navController.navigate(R.id.navSearchResultsFragment, args)
        }

        binding.destinationClearButton.setOnClickListener {
            viewModel.destination.set(null)
        }

        return binding.root
    }

    companion object {
        @JvmStatic
        fun newInstance() =
            NavInfoBsFragment().apply {
                arguments = Bundle().apply {
                }
            }
    }
}