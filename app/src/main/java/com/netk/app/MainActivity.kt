package com.netk.app

import android.Manifest
import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var errorView: View
    private lateinit var accountsStatusView: TextView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
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
            addView(createAccountsView())
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

        loadGoogleAccounts()

        if (savedInstanceState == null) {
            loadSite()
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    private fun createAccountsView(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val horizontalPadding = dpToPx(16)
        val verticalPadding = dpToPx(8)
        setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
        setBackgroundColor(Color.WHITE)

        addView(TextView(context).apply {
            text = getString(R.string.google_accounts_title)
            textSize = 14f
            setTextColor(Color.DKGRAY)
        })
        accountsStatusView = TextView(context).apply {
            text = getString(R.string.google_accounts_permission_required)
            textSize = 13f
            setTextColor(Color.GRAY)
            setPadding(0, dpToPx(4), 0, 0)
            setOnClickListener { requestAccountsPermission() }
        }
        addView(accountsStatusView)
    }

    private fun loadGoogleAccounts() {
        if (checkSelfPermission(Manifest.permission.GET_ACCOUNTS) != PackageManager.PERMISSION_GRANTED) {
            requestAccountsPermission()
            return
        }

        val googleAccounts = try {
            AccountManager.get(this).getAccountsByType(GOOGLE_ACCOUNT_TYPE)
        } catch (_: SecurityException) {
            emptyArray()
        }
        accountsStatusView.text = if (googleAccounts.isEmpty()) {
            getString(R.string.google_accounts_empty)
        } else {
            googleAccounts.joinToString(separator = "\n") { it.name }
        }
    }

    private fun requestAccountsPermission() {
        if (checkSelfPermission(Manifest.permission.GET_ACCOUNTS) == PackageManager.PERMISSION_GRANTED) {
            loadGoogleAccounts()
        } else {
            requestPermissions(arrayOf(Manifest.permission.GET_ACCOUNTS), ACCOUNTS_PERMISSION_REQUEST)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != ACCOUNTS_PERMISSION_REQUEST) return

        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            loadGoogleAccounts()
        } else {
            accountsStatusView.text = getString(R.string.google_accounts_permission_denied)
        }
    }

    private fun matchParentLayoutParams() = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
    )

    private fun dpToPx(value: Int): Int = (value * resources.displayMetrics.density).toInt()

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
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false

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

    private companion object {
        const val SITE_URL = "http://kanto0316.github.io/Album"
        const val GOOGLE_ACCOUNT_TYPE = "com.google"
        const val ACCOUNTS_PERMISSION_REQUEST = 1001
    }
}
