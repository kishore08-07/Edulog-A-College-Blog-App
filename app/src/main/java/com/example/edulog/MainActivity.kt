package com.example.edulog

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        auth = FirebaseAuth.getInstance()

        // Check if user is logged in
        if (auth.currentUser == null) {
            // Not logged in, go to login screen
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // User is logged in, go to blog list
        startActivity(Intent(this, BlogListActivity::class.java))
        finish()
    }
}