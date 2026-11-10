package com.vitalstream.android.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.vitalstream.android.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.openid.appauth.*
import net.openid.appauth.connectivity.ConnectionBuilder
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AuthManager(context: Context) {
    private val vault = SessionVault(context)
    private val saved = vault.read()?.let { runCatching { JSONObject(it) }.getOrNull() }
    private var state = saved?.optString("auth")?.let { runCatching { AuthState.jsonDeserialize(it) }.getOrNull() } ?: AuthState()
    private val mutableOwner = MutableStateFlow(saved?.optString("owner")?.takeIf { it.isNotBlank() && state.isAuthorized })
    val owner = mutableOwner.asStateFlow()
    private val lock = Mutex()
    private val connections = ConnectionBuilder { uri ->
        require(uri.scheme == "https" || (BuildConfig.DEBUG && uri.scheme == "http" && uri.host == "10.0.2.2"))
        (URL(uri.toString()).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 10_000; instanceFollowRedirects = false
        }
    }
    private val service = AuthorizationService(context, AppAuthConfiguration.Builder()
        .setConnectionBuilder(connections).setSkipIssuerHttpsCheck(BuildConfig.DEBUG).build())

    suspend fun loginIntent(): Intent {
        // JOSEPH-AUTH: verify the browser callback + refresh flow against local Keycloak.
        // The host-side smoke harness currently receives HTTP 400 on its login POST.
        // Compare localhost vs 10.0.2.2 cookie/issuer handling; keep PKCE/state/nonce checks.
        // See YOUR_TASKS.md for restart, logout and cross-account acceptance tests.
        val configuration = suspendCancellableCoroutine<AuthorizationServiceConfiguration> { continuation ->
            AuthorizationServiceConfiguration.fetchFromUrl(
                Uri.parse("${BuildConfig.OIDC_ISSUER}/.well-known/openid-configuration"),
                { config, error ->
                    if (continuation.isActive) {
                        if (config != null) continuation.resume(config)
                        else continuation.resumeWithException(error ?: IllegalStateException("Discovery failed"))
                    }
                }, connections)
        }
        // AppAuth generates state, nonce and a PKCE verifier/challenge. No client secret in APKs.
        val request = AuthorizationRequest.Builder(configuration, "vitalstream-android",
            ResponseTypeValues.CODE, Uri.parse("com.vitalstream.android:/oauth2redirect"))
            .setScope("openid profile").setPrompt("login").build()
        return service.getAuthorizationRequestIntent(request)
    }
    suspend fun finishLogin(data: Intent): Unit = lock.withLock {
        val response = AuthorizationResponse.fromIntent(data)
        val error = AuthorizationException.fromIntent(data)
        require(response != null && error == null) { "Sign-in was cancelled or rejected" }
        val newState = AuthState(response, null)
        val tokens = suspendCancellableCoroutine<TokenResponse> { continuation ->
            service.performTokenRequest(response.createTokenExchangeRequest()) { result, failure ->
                if (continuation.isActive) {
                    if (result != null) continuation.resume(result)
                    else continuation.resumeWithException(failure ?: IllegalStateException("Token exchange failed"))
                }
            }
        }
        newState.update(tokens, null)
        vault.clear()
        state = newState
        mutableOwner.value = null
    }
    suspend fun establishOwner(id: String) = lock.withLock {
        require(id.isNotBlank() && state.isAuthorized)
        mutableOwner.value = id
        persist()
    }
    suspend fun token(expectedOwner: String?): String = lock.withLock {
        check(owner.value == expectedOwner && state.isAuthorized) { "Please sign in again" }
        val token = suspendCancellableCoroutine<String> { continuation ->
            state.performActionWithFreshTokens(service) { access, _, error ->
                if (continuation.isActive) {
                    if (access != null) continuation.resume(access)
                    else continuation.resumeWithException(error ?: IllegalStateException("Session expired"))
                }
            }
        }
        if (owner.value != null) persist()
        token
    }
    private fun persist() = vault.write(JSONObject().put("owner", owner.value).put("auth", state.jsonSerializeString()).toString())
    suspend fun signOut() = lock.withLock { state = AuthState(); mutableOwner.value = null; vault.clear() }
}
