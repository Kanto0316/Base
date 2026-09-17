package com.netk.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast

/** Opens an exported file only after the user taps its download notification. */
class OpenExportActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = intent?.data
        val mimeType = intent?.type
        if (!isValidExport(uri, mimeType)) {
            Log.w(TAG, "Invalid export URI or MIME type")
            Toast.makeText(this, R.string.export_open_invalid, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val exportUri = requireNotNull(uri)
        val exportMimeType = requireNotNull(mimeType)

        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(exportUri, exportMimeType)
            clipData = ClipData.newRawUri(EXPORT_CLIP_LABEL, exportUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            // Do not preflight this with resolveActivity(): Android package visibility can
            // hide handlers. Starting ACTION_VIEW lets the system select or offer a chooser.
            startActivity(viewIntent)
        } catch (error: ActivityNotFoundException) {
            Log.w(TAG, "No application can open the exported file", error)
            Toast.makeText(this, R.string.export_open_no_application, Toast.LENGTH_LONG).show()
        } catch (error: SecurityException) {
            Log.e(TAG, "Unable to grant access to the exported file", error)
            Toast.makeText(this, R.string.export_open_security_error, Toast.LENGTH_LONG).show()
        } finally {
            finish()
        }
    }

    private fun isValidExport(uri: Uri?, mimeType: String?): Boolean =
        uri?.scheme == "content" &&
            !mimeType.isNullOrBlank() &&
            MIME_TYPE_PATTERN.matches(mimeType)

    private companion object {
        const val TAG = "OpenExportActivity"
        const val EXPORT_CLIP_LABEL = "export"
        val MIME_TYPE_PATTERN = Regex("^[^/\\s]+/[^/\\s]+$")
    }
}
