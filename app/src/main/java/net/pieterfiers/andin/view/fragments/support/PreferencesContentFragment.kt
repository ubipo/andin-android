package net.pieterfiers.andin.view.fragments.support

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Button
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import com.google.common.net.HostSpecifier
import net.pieterfiers.andin.R
import java.text.ParseException


class PreferencesContentFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        val p = findPreference<EditTextPreference>("api_hostname") ?: throw RuntimeException("api_hostname pref missing")
        p.setOnBindEditTextListener {editText ->
            editText.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    val okBtn = editText.rootView.findViewById<Button>(android.R.id.button1)
                    if (s.isNullOrBlank()) {
                        okBtn.isEnabled = true
                        return
                    }

                    var err: String? = null
                    try {
                        @Suppress("UnstableApiUsage")
                        HostSpecifier.from(s.toString())
                    } catch (e: ParseException) {
                        err = e.message
                    }
                    editText.error = err
                    okBtn.isEnabled = err == null
                }

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }
    }
}
