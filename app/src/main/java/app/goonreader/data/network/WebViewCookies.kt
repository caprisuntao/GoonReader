package app.goonreader.data.network

import okhttp3.Cookie
import okhttp3.HttpUrl

/**
 * Turns a `CookieManager.getCookie` header back into OkHttp cookies.
 *
 * The header carries only `name=value`; scope and lifetime are gone. Host-only on the requested
 * host, path `/`, and a session lifetime is right for the one cookie this exists for:
 * Cloudflare's `cf_clearance`, which is bound to this host, this IP and this User-Agent. A
 * `Set-Cookie` from the real response replaces these later and brings the correct scope with it.
 */
fun parseCookieHeader(header: String?, url: HttpUrl): List<Cookie> {
    if (header.isNullOrBlank()) return emptyList()
    return header.split(';').mapNotNull { pair ->
        val name = pair.substringBefore('=', "").trim()
        val value = pair.substringAfter('=', "").trim()
        if (name.isEmpty()) return@mapNotNull null
        runCatching {
            Cookie.Builder()
                .name(name)
                .value(value)
                .hostOnlyDomain(url.host)
                .path("/")
                .build()
        }.getOrNull()
    }
}
