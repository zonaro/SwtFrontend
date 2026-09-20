package br.com.redclaw.swt.views

import android.content.Intent
import android.os.Bundle

/**
 * Entry point kept for backward compatibility with existing intent filters
 * and shortcuts. Immediately redirects to [LibraryActivity] which is the
 * real launcher screen.
 *
 * Extends [ScaledAppCompatActivity] so the accent overlay is applied even
 * if something briefly creates a MainActivity before the redirect fires.
 */
class MainActivity : ScaledAppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, LibraryActivity::class.java))
        finish()
    }
}