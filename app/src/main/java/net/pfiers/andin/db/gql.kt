package net.pfiers.andin.db

import android.content.SharedPreferences
import com.andin_api.type.CustomType
import com.apollographql.apollo.ApolloClient
import net.pfiers.andin.PREF_API_HOSTNAME
import net.pfiers.andin.b64WkbCustomTypeAdapter
import net.pfiers.andin.okHttpClient
import net.pfiers.andin.uuidCustomTypeAdapter
import okhttp3.HttpUrl


private const val DEFAULT_HOSTNAME = "home.ubipo.net"

fun urlFromPrefs(preferences: SharedPreferences): HttpUrl {
    val urlBuilder = HttpUrl.Builder()
        .port(41533)
        .addPathSegment("graphql")
        .scheme("http")
    val prefHost = preferences.getString(PREF_API_HOSTNAME, null)

    val host = if (!prefHost.isNullOrBlank()) prefHost else DEFAULT_HOSTNAME
    try {
        urlBuilder.host(host)
    } catch (ex: IllegalArgumentException) {
//        throw Exception("Bad hostname", "Bad hostname (\"$host\"), fell back to default (\"$DEFAULT_HOSTNAME\")")
        urlBuilder.host(DEFAULT_HOSTNAME)
    }

    return urlBuilder.build()
}

fun createApolloClient(url: HttpUrl): ApolloClient {
    return ApolloClient.builder()
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
}
