package com.netk.app

import android.annotation.SuppressLint
import android.content.Context
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
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import org.json.JSONStringer

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var errorView: View
    private var accountSelectionInProgress = false

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        accountSelectionInProgress = false
        Log.d(TAG, "Google Sign-In result received (resultCode=${result.resultCode})")

        if (result.resultCode != RESULT_OK) {
            Log.d(TAG, "Google Sign-In cancelled")
            return@registerForActivityResult
        }

        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                .getResult(ApiException::class.java)
            Log.d(TAG, "Google Sign-In account received (email=${account.email ?: "unavailable"})")
            val idToken = account.idToken
            Log.d(TAG, "Google Sign-In idToken present=${!idToken.isNullOrBlank()}")
            if (idToken.isNullOrBlank()) {
                Log.e(TAG, "Google Sign-In returned no ID token")
            } else {
                sendGoogleIdTokenToWebView(idToken)
            }
        } catch (error: ApiException) {
            Log.e(TAG, "Google Sign-In failed (statusCode=${error.statusCode})", error)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            addJavascriptInterface(GoogleAccountBridge(), GOOGLE_BRIDGE_NAME)
            webViewClient = SiteWebViewClient()
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
        Log.d(TAG, "Launching Google Sign-In")
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        val signInIntent = GoogleSignIn.getClient(this, options).signInIntent
        try {
            googleSignInLauncher.launch(signInIntent)
        } catch (error: RuntimeException) {
            accountSelectionInProgress = false
            Log.e(TAG, "Unable to launch Google Sign-In", error)
        }
    }

    private fun sendGoogleIdTokenToWebView(idToken: String) {
        webView.post {
            if (!isTrustedSite(webView.url)) return@post
            // JSONStringer performs the required JavaScript string escaping; never interpolate raw tokens.
            val encodedToken = JSONStringer().value(idToken).toString()
            webView.evaluateJavascript(
                """
                    (() => {
                      console.log('Firebase Auth: token Android reçu');
                      if (typeof window.firebaseLoginWithToken !== 'function') {
                        console.error('Firebase Auth: firebaseLoginWithToken indisponible');
                        return;
                      }
                      Promise.resolve(window.firebaseLoginWithToken($encodedToken))
                        .then(result => {
                          const user = result && result.user;
                          if (!user) {
                            throw new Error('Firebase Auth returned no current user');
                          }
                          window.AndroidGoogleSignIn.onFirebaseAuthResult(
                            user.uid || null,
                            user.email || null,
                            null
                          );
                        })
                        .catch(error => {
                          const message = error instanceof Error ? error.message : String(error);
                          console.error('Firebase Auth: échec de connexion', error);
                          window.AndroidGoogleSignIn.onFirebaseAuthResult(null, null, message);
                        });
                    })();
                """.trimIndent(),
                null,
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
        webView.loadUrl(SITE_URL)
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
        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)
            if (isTrustedSite(url)) view.evaluateJavascript(GOOGLE_BUTTON_BRIDGE_SCRIPT, null)
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

    private inner class GoogleAccountBridge {
        @JavascriptInterface
        fun openAccountChooser() {
            webView.post { showGoogleAccountChooser() }
        }

        @JavascriptInterface
        fun onFirebaseAuthResult(uid: String?, email: String?, error: String?) {
            webView.post {
                if (!isTrustedSite(webView.url)) return@post
                if (!error.isNullOrBlank()) {
                    Log.e(TAG, "Firebase Auth failed: $error")
                    return@post
                }
                if (uid.isNullOrBlank()) {
                    Log.e(TAG, "Firebase Auth completed without a currentUser")
                    return@post
                }
                Log.d(
                    TAG,
                    "Firebase Auth succeeded; currentUser uid=$uid email=${email ?: "unavailable"}",
                )
            }
        }
    }

    private fun isTrustedSite(url: String?): Boolean {
        val uri = url?.let(Uri::parse) ?: return false
        return uri.host == SITE_HOST && uri.scheme == "https"
    }

    private companion object {
        const val SITE_URL = "https://kanto0316.github.io/Album"
        const val SITE_HOST = "kanto0316.github.io"
        const val GOOGLE_BRIDGE_NAME = "AndroidGoogleSignIn"
        const val TAG = "FirebaseAuth"

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
                window.AndroidGoogleSignIn.openAccountChooser();
              }, true);
            })();
        """.trimIndent()
    }
}
