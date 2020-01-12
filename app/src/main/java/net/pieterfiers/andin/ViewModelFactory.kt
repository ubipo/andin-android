package net.pieterfiers.andin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

interface ViewModelArgs

class ViewModelFactory<V, A>(
    private val viewModelClass: Class<V>,
    private val dataRepo: DataRepo,
    private val otherArgs: A? = null
) : ViewModelProvider.Factory
        where V : ViewModel, A : ViewModelArgs {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel?> create(modelClass: Class<T>): T =
        if (otherArgs == null) {
            viewModelClass
                .getConstructor(dataRepo.javaClass)
                .newInstance(dataRepo)
        } else {
            viewModelClass
                .getConstructor(dataRepo.javaClass, otherArgs.javaClass)
                .newInstance(dataRepo, otherArgs)
        } as T
}