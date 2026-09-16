package com.netk.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.JavascriptInterface
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var errorView: View
    private var accountSelectionInProgress = false
    private var isWebPageLoaded = false
    private var isFirebaseWebBridgeReady = false
    private var pendingGoogleIdToken: String? = null
    private var pendingFirebaseDiagnostic: FirebaseDiagnostic? = null
    private var pendingDownload: PendingDownload? = null

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val download = pendingDownload
        pendingDownload = null
        if (granted && download != null) {
            enqueueDownload(download)
        } else if (!granted) {
            Toast.makeText(this, R.string.download_permission_denied, Toast.LENGTH_LONG).show()
        }
    }

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        accountSelectionInProgress = false
        Log.d(TAG, "[BRIDGE_CHECK] activity_result_received")
        Log.d(TAG, "[GOOGLE_CALLBACK] activity_result_received")
        when (result.resultCode) {
            RESULT_OK -> Log.d(TAG, "[GOOGLE_CALLBACK] resultCode=RESULT_OK")
            RESULT_CANCELED -> Log.d(TAG, "[GOOGLE_CALLBACK] resultCode=RESULT_CANCELED")
            else -> Log.d(TAG, "[GOOGLE_CALLBACK] resultCode=OTHER(${result.resultCode})")
        }
        Log.d(TAG, "[GOOGLE_CALLBACK] intent_received=${result.data != null}")

        if (result.resultCode != RESULT_OK) {
            val resultLabel = if (result.resultCode == RESULT_CANCELED) {
                "RESULT_CANCELED"
            } else {
                "OTHER(${result.resultCode})"
            }
            publishFirebaseDiagnostic(
                FirebaseDiagnostic.disconnected(
                    event = "GOOGLE_SIGN_IN_CANCELLED",
                    error = "Google Sign-In result: $resultLabel",
                ),
            )
            return@registerForActivityResult
        }

        try {
            Log.d(TAG, "[GOOGLE_CALLBACK] extracting_account")
            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                .getResult(ApiException::class.java)
            updateAuthDebug("GOOGLE_ACCOUNT_RECEIVED", "Compte Google reçu")
            Log.d(TAG, "[GOOGLE_RESULT] account_received")
            val idToken = account.idToken
            Log.d(TAG, "[GOOGLE_RESULT] idToken_present=${!idToken.isNullOrBlank()}")
            if (idToken.isNullOrBlank()) {
                Log.e(TAG, "Google Sign-In returned no ID token")
                publishFirebaseDiagnostic(
                    FirebaseDiagnostic.disconnected(
                        event = "GOOGLE_SIGN_IN_ERROR",
                        error = "Google Sign-In returned no ID token",
                    ),
                )
            } else {
                signInToFirebase(idToken)
            }
        } catch (error: ApiException) {
            Log.e(
                TAG,
                "[GOOGLE_CALLBACK] ApiException code=${error.statusCode} message=${error.message.orEmpty()}",
                error,
            )
            publishFirebaseDiagnostic(
                FirebaseDiagnostic.disconnected(
                    event = "GOOGLE_SIGN_IN_ERROR",
                    error = "ApiException code=${error.statusCode}: ${error.message.orEmpty()}",
                ),
            )
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            Log.d(TAG, "[BRIDGE_CHECK] webview_created")
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            addJavascriptInterface(AndroidAuthBridge(), GOOGLE_BRIDGE_NAME)
            Log.d(TAG, "[BRIDGE_CHECK] javascript_interface_added")
            webViewClient = SiteWebViewClient()
            setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                handleDownload(url, userAgent, contentDisposition, mimeType)
            }
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
            setOnApplyWindowInsetsListener { view, insets ->
                applySystemBarInsets(view, insets)
                insets
            }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                webView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f,
                ),
            )
        }
        root.addView(content, matchParentLayoutParams())
        errorView = createErrorView()
        root.addView(errorView, matchParentLayoutParams())
        setContentView(root)
        root.requestApplyInsets()

        if (savedInstanceState == null) {
            loadSite()
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    private fun showGoogleAccountChooser() {
        if (accountSelectionInProgress || !isTrustedSite(webView.url)) return
        accountSelectionInProgress = true
        Log.d(TAG, "[ANDROID_AUTH] start_google_signin")
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        val signInIntent = GoogleSignIn.getClient(this, options).signInIntent
        try {
            Log.d(TAG, "[BRIDGE_CHECK] google_launcher_called")
            Log.d(TAG, "[GOOGLE_CALLBACK] launcher_launch_called")
            googleSignInLauncher.launch(signInIntent)
        } catch (error: RuntimeException) {
            accountSelectionInProgress = false
            Log.e(TAG, "Unable to launch Google Sign-In", error)
            publishFirebaseDiagnostic(
                FirebaseDiagnostic.disconnected(
                    event = "GOOGLE_SIGN_IN_ERROR",
                    error = error.message ?: "Unable to launch Google Sign-In",
                ),
            )
        }
    }

    private fun handleDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
    ) {
        val uri = Uri.parse(url)
        if (uri.scheme != "http" && uri.scheme != "https") {
            Log.e(TAG, "Download rejected: unsupported URL scheme ${uri.scheme.orEmpty()}")
            Toast.makeText(this, R.string.download_unsupported, Toast.LENGTH_LONG).show()
            return
        }

        val download = PendingDownload(url, userAgent, contentDisposition, mimeType)
        if (
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingDownload = download
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            enqueueDownload(download)
        }
    }

    private fun enqueueDownload(download: PendingDownload) {
        val fileName = URLUtil.guessFileName(
            download.url,
            download.contentDisposition,
            download.mimeType,
        )
        val request = DownloadManager.Request(Uri.parse(download.url)).apply {
            setTitle(fileName)
            setDescription(getString(R.string.download_in_progress))
            setMimeType(download.mimeType)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName)
            download.userAgent?.takeIf(String::isNotBlank)?.let { addRequestHeader("User-Agent", it) }
            CookieManager.getInstance().getCookie(download.url)
                ?.takeIf(String::isNotBlank)
                ?.let { addRequestHeader("Cookie", it) }
        }

        try {
            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show()
        } catch (error: RuntimeException) {
            Log.e(TAG, "Unable to enqueue download", error)
            Toast.makeText(this, R.string.download_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun signInToFirebase(idToken: String) {
        val auth = try {
            FirebaseAuth.getInstance()
        } catch (error: IllegalStateException) {
            Log.e(TAG, "[FIREBASE_RESULT] Firebase is not configured", error)
            publishFirebaseDiagnostic(
                FirebaseDiagnostic.disconnected("FIREBASE_CONFIGURATION_ERROR", error.message.orEmpty()),
            )
            return
        }
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
            .addOnCompleteListener(this) { task ->
                val user = if (task.isSuccessful) auth.currentUser else null
                if (user == null) {
                    Log.e(TAG, "[FIREBASE_RESULT] sign_in_error", task.exception)
                    publishFirebaseDiagnostic(
                        FirebaseDiagnostic.disconnected(
                            "FIREBASE_ERROR",
                            task.exception?.message ?: "Firebase returned no currentUser",
                        ),
                    )
                    return@addOnCompleteListener
                }

                Log.d(TAG, "[FIREBASE_RESULT] uid_received")
                Log.d(TAG, "[FIREBASE_RESULT] email_received")
                publishFirebaseDiagnostic(
                    FirebaseDiagnostic(
                        status = "CONNECTÉ",
                        uid = user.uid,
                        email = user.email.orEmpty(),
                        displayName = user.displayName.orEmpty(),
                        event = "FIREBASE_SUCCESS",
                        error = "",
                    ),
                )
                queueGoogleIdTokenForWeb(idToken)
            }
    }

    private fun queueGoogleIdTokenForWeb(idToken: String) {
        pendingGoogleIdToken = idToken
        if (isFirebaseWebBridgeReady) deliverPendingGoogleIdToken() else checkWebBridgeReady()
    }

    private fun checkWebBridgeReady(attempt: Int = 0) {
        if (!isWebPageLoaded || !isTrustedSite(webView.url)) return
        Log.d(TAG, "[BRIDGE_CHECK] evaluate_javascript_called")
        webView.evaluateJavascript(
            "typeof window.firebaseLoginWithToken === 'function'",
        ) { result ->
            isFirebaseWebBridgeReady = result == "true"
            if (isFirebaseWebBridgeReady) {
                Log.d(TAG, "[WEBVIEW_BRIDGE] bridge_ready")
                deliverPendingGoogleIdToken()
            } else if (attempt < WEB_BRIDGE_MAX_ATTEMPTS) {
                webView.postDelayed({ checkWebBridgeReady(attempt + 1) }, WEB_BRIDGE_RETRY_MS)
            } else {
                Log.d(TAG, "[WEBVIEW_BRIDGE] bridge_not_ready")
            }
        }
    }

    private fun deliverPendingGoogleIdToken() {
        val idToken = pendingGoogleIdToken ?: return
        webView.post {
            if (!isWebPageLoaded || !isFirebaseWebBridgeReady || !isTrustedSite(webView.url)) {
                return@post
            }
            // JSONObject.quote performs the required JavaScript string escaping; never interpolate raw tokens.
            val encodedToken = JSONObject.quote(idToken)
            pendingGoogleIdToken = null
            Log.d(TAG, "[BRIDGE_CHECK] evaluate_javascript_called")
            webView.evaluateJavascript(
                """
                    (() => {
                      if (typeof window.firebaseLoginWithToken !== 'function') {
                        window.AndroidAuth.onJavascriptCallback(false, 'firebaseLoginWithToken unavailable');
                        return;
                      }
                      Promise.resolve(window.firebaseLoginWithToken($encodedToken))
                        .then(() => window.AndroidAuth.onJavascriptCallback(true, null))
                        .catch(error => {
                          const message = error instanceof Error ? error.message : String(error);
                          window.AndroidAuth.onJavascriptCallback(false, message);
                        });
                    })();
                """.trimIndent(),
                { Log.d(TAG, "[WEBVIEW_BRIDGE] token_sent") },
            )
        }
    }

    private fun matchParentLayoutParams() = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
    )

    @Suppress("DEPRECATION")
    private fun applySystemBarInsets(view: View, insets: WindowInsets) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val systemBars = insets.getInsets(WindowInsets.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
        } else {
            view.setPadding(
                insets.systemWindowInsetLeft,
                insets.systemWindowInsetTop,
                insets.systemWindowInsetRight,
                insets.systemWindowInsetBottom,
            )
        }
    }

    private fun createErrorView(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(48, 48, 48, 48)
        setBackgroundColor(Color.WHITE)
        visibility = View.GONE

        addView(TextView(context).apply {
            text = getString(R.string.site_unavailable)
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.DKGRAY)
        })
        addView(Button(context).apply {
            text = getString(R.string.retry)
            setOnClickListener { loadSite() }
        })
    }

    private fun loadSite() {
        if (!hasInternetConnection()) {
            showError()
            return
        }
        errorView.visibility = View.GONE
        isWebPageLoaded = false
        isFirebaseWebBridgeReady = false
        webView.loadUrl(SITE_URL)
    }

    private fun publishFirebaseDiagnostic(diagnostic: FirebaseDiagnostic) {
        pendingFirebaseDiagnostic = diagnostic
        Log.d(TAG, "[AUTH_BRIDGE] status=${diagnostic.status}")
        Log.d(TAG, "[AUTH_BRIDGE] uid=${diagnostic.uid.ifBlank { "unavailable" }}")
        Log.d(TAG, "[AUTH_BRIDGE] email_present=${diagnostic.email.isNotBlank()}")
        Log.d(TAG, "[AUTH_BRIDGE] event=${diagnostic.event}")
        deliverPendingFirebaseDiagnostic()
    }

    private fun updateAuthDebug(event: String, message: String) {
        publishFirebaseDiagnostic(
            FirebaseDiagnostic.disconnected(
                event = event,
                error = message,
            ),
        )
    }

    private fun deliverPendingFirebaseDiagnostic() {
        val diagnostic = pendingFirebaseDiagnostic ?: return
        if (!isWebPageLoaded || !isTrustedSite(webView.url)) return

        val status = JSONObject.quote(diagnostic.status)
        val message = JSONObject.quote(
            listOf(diagnostic.event, diagnostic.error)
                .filter(String::isNotBlank)
                .joinToString(": "),
        )
        Log.d(TAG, "[BRIDGE_CHECK] diagnostic_send_start")
        Log.d(TAG, "[BRIDGE_CHECK] evaluate_javascript_called")
        webView.evaluateJavascript(
            """
                (() => {
                  try {
                    if (typeof window.updateAuthDebug !== 'function') return false;
                    window.updateAuthDebug($status, $message);
                    return true;
                  } catch (error) {
                    return false;
                  }
                })();
            """.trimIndent(),
        ) { result ->
            if (result == "true") {
                Log.d(TAG, "[BRIDGE_CHECK] diagnostic_send_success")
                if (pendingFirebaseDiagnostic === diagnostic) pendingFirebaseDiagnostic = null
            } else {
                Log.e(TAG, "[BRIDGE_CHECK] diagnostic_send_failed")
                Log.e(TAG, "[BRIDGE_CHECK] javascript_callback_failed")
            }
        }
    }

    private fun hasInternetConnection(): Boolean {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun showError() {
        errorView.visibility = View.VISIBLE
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Android, retained for compatibility with older supported versions")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView.stopLoading()
        webView.webViewClient = WebViewClient()
        webView.destroy()
        super.onDestroy()
    }

    private inner class SiteWebViewClient : WebViewClient() {
        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
            isWebPageLoaded = false
            isFirebaseWebBridgeReady = false
            super.onPageStarted(view, url, favicon)
        }

        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)
            isWebPageLoaded = isTrustedSite(url)
            if (isWebPageLoaded) {
                Log.d(TAG, "[BRIDGE_CHECK] evaluate_javascript_called")
                view.evaluateJavascript(GOOGLE_BUTTON_BRIDGE_SCRIPT, null)
                checkWebBridgeReady()
                deliverPendingFirebaseDiagnostic()
            }
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            if (request.isForMainFrame) showError()
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse,
        ) {
            if (request.isForMainFrame) showError()
        }
    }

    private inner class AndroidAuthBridge {
        @JavascriptInterface
        fun startGoogleSignIn() {
            webView.post { showGoogleAccountChooser() }
        }

        @JavascriptInterface
        fun onJavascriptCallback(success: Boolean, error: String?) {
            webView.post {
                if (!isTrustedSite(webView.url)) return@post
                if (success) Log.d(TAG, "[WEBVIEW_BRIDGE] javascript_callback_success")
                else Log.e(TAG, "[WEBVIEW_BRIDGE] javascript_callback_error: ${error.orEmpty()}")
            }
        }
    }

    private data class FirebaseDiagnostic(
        val status: String,
        val uid: String,
        val email: String,
        val displayName: String,
        val event: String,
        val error: String,
    ) {
        fun toJson(): String = JSONObject()
            .put("status", status)
            .put("uid", uid)
            .put("email", email)
            .put("displayName", displayName)
            .put("event", event)
            .put("error", error)
            .toString()

        companion object {
            fun disconnected(event: String, error: String = "") = FirebaseDiagnostic(
                status = "NON CONNECTÉ",
                uid = "",
                email = "",
                displayName = "",
                event = event,
                error = error,
            )
        }
    }

    private data class PendingDownload(
        val url: String,
        val userAgent: String?,
        val contentDisposition: String?,
        val mimeType: String?,
    )

    private fun isTrustedSite(url: String?): Boolean {
        val uri = url?.let(Uri::parse) ?: return false
        return uri.host == SITE_HOST && uri.scheme == "https"
    }

    private companion object {
        const val SITE_URL = "https://kanto0316.github.io/Album"
        const val SITE_HOST = "kanto0316.github.io"
        const val GOOGLE_BRIDGE_NAME = "AndroidAuth"
        const val TAG = "FirebaseAuth"
        const val WEB_BRIDGE_MAX_ATTEMPTS = 20
        const val WEB_BRIDGE_RETRY_MS = 250L

        val GOOGLE_BUTTON_BRIDGE_SCRIPT = """
            (() => {
              if (window.__androidGoogleAccountBridgeInstalled) return;
              window.__androidGoogleAccountBridgeInstalled = true;

              document.addEventListener('click', event => {
                const target = event.target instanceof Element
                  ? event.target.closest('button, a, [role="button"]')
                  : null;
                if (!target) return;
                const description = [
                  target.id,
                  target.className,
                  target.getAttribute('href'),
                  target.getAttribute('aria-label'),
                  target.getAttribute('data-provider'),
                  target.textContent
                ].filter(Boolean).join(' ').toLowerCase();
                if (!description.includes('google')) return;

                event.preventDefault();
                event.stopImmediatePropagation();
                window.AndroidAuth.startGoogleSignIn();
              }, true);
            })();
        """.trimIndent()
    }
}
