package net.pieterfiers.andin.view.fragments.support

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import net.pieterfiers.andin.R

class PreferencesContentFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }
}
