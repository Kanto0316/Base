package com.netk.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var errorView: View
    private var accountSelectionInProgress = false

    @Deprecated("Deprecated in Android, retained for the Google account chooser result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != ACCOUNT_CHOOSER_REQUEST) return

        accountSelectionInProgress = false
        if (resultCode != RESULT_OK) {
            returnGoogleAccount(null, GOOGLE_ACCOUNT_CANCELLED)
            return
        }

        val account = try {
            GoogleSignIn.getSignedInAccountFromIntent(data)
                .getResult(ApiException::class.java)
        } catch (_: ApiException) {
            null
        }

        if (account?.email.isNullOrBlank()) {
            returnGoogleAccount(null, GOOGLE_ACCOUNT_ERROR)
        } else {
            returnGoogleAccount(
                JSONObject().apply {
                    put("id", account.id)
                    put("email", account.email)
                    put("displayName", account.displayName)
                    put("photoUrl", account.photoUrl?.toString())
                },
                null,
            )
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
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        val pickerIntent = GoogleSignIn.getClient(this, options).signInIntent
        startActivityForResult(pickerIntent, ACCOUNT_CHOOSER_REQUEST)
    }

    private fun returnGoogleAccount(account: JSONObject?, error: String?) {
        if (!isTrustedSite(webView.url)) return
        val result = JSONObject().apply {
            put("account", account ?: JSONObject.NULL)
            put("error", error ?: JSONObject.NULL)
        }
        webView.post {
            webView.evaluateJavascript(
                "window.__receiveAndroidGoogleAccount(${result});",
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
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (request.url.host == GOOGLE_ACCOUNTS_HOST) {
                view.post { showGoogleAccountChooser() }
                return true
            }
            return false
        }

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
    }

    private fun isTrustedSite(url: String?): Boolean {
        val uri = url?.let(Uri::parse) ?: return false
        return uri.host == SITE_HOST && (uri.scheme == "http" || uri.scheme == "https")
    }

    private companion object {
        const val SITE_URL = "http://kanto0316.github.io/Album"
        const val SITE_HOST = "kanto0316.github.io"
        const val GOOGLE_ACCOUNTS_HOST = "accounts.google.com"
        const val ACCOUNT_CHOOSER_REQUEST = 1002
        const val GOOGLE_BRIDGE_NAME = "AndroidGoogleSignIn"
        const val GOOGLE_ACCOUNT_CANCELLED = "cancelled"
        const val GOOGLE_ACCOUNT_ERROR = "account_unavailable"

        val GOOGLE_BUTTON_BRIDGE_SCRIPT = """
            (() => {
              if (window.__androidGoogleAccountBridgeInstalled) return;
              window.__androidGoogleAccountBridgeInstalled = true;

              window.__receiveAndroidGoogleAccount = result => {
                window.dispatchEvent(new CustomEvent('android-google-account-result', {
                  detail: result
                }));
                if (typeof window.onAndroidGoogleAccountResult === 'function') {
                  window.onAndroidGoogleAccountResult(result);
                }
              };

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
