package app.goonreader.di

import android.content.Context
import app.goonreader.BuildConfig
import app.goonreader.data.network.Ao3Client
import app.goonreader.data.network.MemoryCookieJar
import app.goonreader.data.network.OkHttpTransport
import app.goonreader.data.network.RateLimiter
import app.goonreader.data.network.WebViewTransport
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // Cloudflare fronts AO3 and challenges anything that does not look like a browser, so this
    // is a browser UA with the app token appended (PRD 7.3 wants the app identified; the token
    // keeps that true while the prefix is what the bot filter reads).
    // The Advanced setting in PRD 9.8 will let users override the suffix.
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/128.0.0.0 Mobile Safari/537.36 GoonReader/${BuildConfig.VERSION_NAME}"

    @Provides
    @Singleton
    fun provideCookieJar(): CookieJar = MemoryCookieJar()

    @Provides
    @Singleton
    fun provideOkHttpClient(cookieJar: CookieJar): OkHttpClient =
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            // HTTP/1.1 matches what a mobile Chrome uses on first contact; Cloudflare's
            // fingerprinting looks at this too.
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor.Level.BASIC
                    } else {
                        HttpLoggingInterceptor.Level.NONE
                    }
                },
            )
            .build()

    @Provides
    @Singleton
    fun provideRateLimiter(): RateLimiter = RateLimiter()

    @Provides
    @Singleton
    fun provideBrowserFallback(@ApplicationContext context: Context, cookieJar: CookieJar): WebViewTransport =
        WebViewTransport(context, USER_AGENT, cookieJar)

    @Provides
    @Singleton
    fun provideAo3Client(
        rateLimiter: RateLimiter,
        client: OkHttpClient,
        browserFallback: WebViewTransport,
    ): Ao3Client = Ao3Client(
        rateLimiter = rateLimiter,
        transport = OkHttpTransport(client, USER_AGENT),
        browserFallback = browserFallback,
    )
}
