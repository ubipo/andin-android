package net.pieterfiers.andin

import android.content.SearchRecentSuggestionsProvider

class MainSearchSuggestionProvider : SearchRecentSuggestionsProvider() {
    init {
        setupSuggestions(AUTHORITY, MODE)
    }

    companion object {
        const val AUTHORITY = "net.pieterfiers.andin.MainSearchSuggestionProvider"
        const val MODE: Int = DATABASE_MODE_QUERIES
    }
}
