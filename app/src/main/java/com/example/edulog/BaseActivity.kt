package com.example.edulog

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

/**
 * BaseActivity that provides common functionality for all activities
 * This helps maintain consistent behavior across the app
 */
open class BaseActivity : AppCompatActivity() {
    
    private val TAG = "BaseActivity"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "${javaClass.simpleName} onCreate")
    }
    
    /**
     * Register a back press handler with custom behavior
     */
    protected fun registerBackPressHandler(handler: () -> Unit) {
        try {
            Log.d(TAG, "${javaClass.simpleName} registering back press handler")
            val callback = object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    try {
                        Log.d(TAG, "${javaClass.simpleName} handling back press")
                        handler()
                    } catch (e: Exception) {
                        Log.e(TAG, "${javaClass.simpleName} Error in back press handler: ${e.message}", e)
                        Toast.makeText(this@BaseActivity, "Error handling back press", Toast.LENGTH_SHORT).show()
                        // Fallback to default behavior
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
            onBackPressedDispatcher.addCallback(this, callback)
            Log.d(TAG, "${javaClass.simpleName} back press handler registered")
        } catch (e: Exception) {
            Log.e(TAG, "${javaClass.simpleName} Error registering back press handler: ${e.message}", e)
            Toast.makeText(this, "Error setting up back navigation", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Ensures proper handling of Up navigation in the action bar
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            // Handle the back button in the action bar
            try {
                Log.d(TAG, "${javaClass.simpleName} action bar back button pressed")
                onBackPressedDispatcher.onBackPressed()
                return true
            } catch (e: Exception) {
                Log.e(TAG, "${javaClass.simpleName} Error handling action bar back: ${e.message}", e)
                Toast.makeText(this, "Error navigating back", Toast.LENGTH_SHORT).show()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
} 