package com.netk.app

import android.annotation.SuppressLint
import android.accounts.AccountManager
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
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.common.AccountPicker
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var errorView: View
    private var accountSelectionInProgress = false

    private val googleAccountPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        accountSelectionInProgress = false
        when (result.resultCode) {
            RESULT_OK -> handleSelectedGoogleAccount(result.data)
            RESULT_CANCELED -> returnGoogleAccount(null, GOOGLE_ACCOUNT_CANCELLED)
            else -> returnGoogleAccount(null, GOOGLE_ACCOUNT_ERROR)
        }
    }

    private fun handleSelectedGoogleAccount(data: Intent?) {
        val email = data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        val accountType = data?.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE)

        if (email.isNullOrBlank()) {
            returnGoogleAccount(null, GOOGLE_ACCOUNT_ERROR)
        } else {
            returnGoogleAccount(
                JSONObject().apply {
                    put("email", email)
                    put("type", accountType ?: GOOGLE_ACCOUNT_TYPE)
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
        val pickerIntent = AccountPicker.newChooseAccountIntent(
            AccountPicker.AccountChooserOptions.Builder()
                .setAllowableAccountsTypes(listOf(GOOGLE_ACCOUNT_TYPE))
                .setAlwaysShowAccountPicker(true)
                .build(),
        )
        try {
            googleAccountPicker.launch(pickerIntent)
        } catch (_: RuntimeException) {
            accountSelectionInProgress = false
            returnGoogleAccount(null, GOOGLE_ACCOUNT_ERROR)
        }
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
        const val GOOGLE_BRIDGE_NAME = "AndroidGoogleSignIn"
        const val GOOGLE_ACCOUNT_TYPE = "com.google"
        const val GOOGLE_ACCOUNT_CANCELLED = "cancelled"
        const val GOOGLE_ACCOUNT_ERROR = "account_unavailable"

        val GOOGLE_BUTTON_BRIDGE_SCRIPT = """
            (() => {
              if (window.__androidGoogleAccountBridgeInstalled) return;
              window.__androidGoogleAccountBridgeInstalled = true;

              window.__receiveAndroidGoogleAccount = result => {
                const googleButton = Array.from(
                  document.querySelectorAll('button, a, [role="button"]')
                ).find(element => [
                  element.id,
                  element.className,
                  element.getAttribute('href'),
                  element.getAttribute('aria-label'),
                  element.getAttribute('data-provider'),
                  element.textContent
                ].filter(Boolean).join(' ').toLowerCase().includes('google'));

                if (result.account && googleButton) {
                  let accountLabel = document.getElementById('android-google-account');
                  if (!accountLabel) {
                    accountLabel = document.createElement('div');
                    accountLabel.id = 'android-google-account';
                    accountLabel.setAttribute('role', 'status');
                    googleButton.insertAdjacentElement('afterend', accountLabel);
                  }
                  accountLabel.textContent = result.account.email;
                }
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
