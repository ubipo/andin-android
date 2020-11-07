package net.pfiers.andin.model

import androidx.databinding.Observable

fun onPropertyChangedCallback(callback: (sender: Observable?, propertyId: Int) -> Unit): Observable.OnPropertyChangedCallback {
    return object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
            callback(sender, propertyId)
        }
    }
}

fun onPropertyChangedCallback(callback: () -> Unit): Observable.OnPropertyChangedCallback {
    return object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
            callback()
        }
    }
}
