package com.example.edulog

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException

class LoginActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var emailEditText: TextInputEditText
    private lateinit var passwordEditText: TextInputEditText
    private lateinit var loginButton: Button
    private lateinit var signupLink: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        // Initialize views
        emailEditText = findViewById(R.id.emailEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        loginButton = findViewById(R.id.loginButton)
        signupLink = findViewById(R.id.signupLink)

        // Set click listeners
        loginButton.setOnClickListener {
            loginUser()
        }

        signupLink.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
            finish()
        }
    }

    private fun loginUser() {
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()

        // Validate inputs
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (!email.endsWith("@psgtech.ac.in")) {
            Toast.makeText(this, "Please use your PSG Tech email", Toast.LENGTH_SHORT).show()
            return
        }

        // Show loading state
        loginButton.isEnabled = false
        loginButton.text = "Logging in..."

        // Attempt login
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        if (user.isEmailVerified) {
                            // Login successful and email is verified
                            Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()
                            // Navigate to blog list activity
                            startActivity(Intent(this, BlogListActivity::class.java))
                            finishAffinity()
                        } else {
                            // Email not verified
                            Toast.makeText(this, "Please verify your email first", Toast.LENGTH_LONG).show()
                            // Sign out the user since email is not verified
                            auth.signOut()
                        }
                    }
                } else {
                    // Handle specific login errors
                    val errorMessage = when (task.exception) {
                        is FirebaseAuthInvalidUserException -> "No account found with this email"
                        is FirebaseAuthInvalidCredentialsException -> "Invalid password"
                        else -> "Login failed: ${task.exception?.message}"
                    }
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                }
                // Reset button state
                loginButton.isEnabled = true
                loginButton.text = "Login"
            }
    }
} 