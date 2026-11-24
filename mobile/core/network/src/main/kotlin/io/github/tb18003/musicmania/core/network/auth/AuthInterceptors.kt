/*
 * MusicMania - open-source Spotify listening statistics for Android.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.tb18003.musicmania.core.network.auth

import okhttp3.Authenticator
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject

private const val AUTHORIZATION = "Authorization"
private const val BEARER = "Bearer "

internal fun Request.withBearer(token: String): Request = newBuilder().header(AUTHORIZATION, BEARER + token).build()

/**
 * Attaches the bearer token — but only to the Spotify API host. Defence in depth:
 * a misconfigured client or redirect can never leak the token to a third party
 * (e.g. the image CDN).
 */
class AuthInterceptor @Inject constructor(
    private val coordinator: TokenRefreshCoordinator,
    private val allowedHost: AllowedApiHost,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!allowedHost.matches(request.url)) return chain.proceed(request)
        val token = coordinator.validAccessToken() ?: return chain.proceed(request)
        return chain.proceed(request.withBearer(token))
    }
}

/** Reactive refresh on 401, retried at most once per request. */
class TokenAuthenticator @Inject constructor(
    private val coordinator: TokenRefreshCoordinator,
    private val allowedHost: AllowedApiHost,
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        val request = response.request
        if (!allowedHost.matches(request.url) || response.priorResponse != null) return null
        val failedToken = request.header(AUTHORIZATION)?.removePrefix(BEARER)
        val newToken = coordinator.refreshAfterUnauthorized(failedToken) ?: return null
        return request.withBearer(newToken)
    }
}

class AllowedApiHost(private val host: String) {
    fun matches(url: HttpUrl): Boolean = url.host.equals(host, ignoreCase = true)
}
