package com.example.edulog

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.example.edulog.databinding.ActivitySignupBinding

class SignupActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySignupBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    companion object {
        val DEPARTMENTS = arrayOf(
            "MCA", "MSc.SS", "MSc.DS", "MSc.TCS", "MSc.CS", "MSc.FDM",
            "BE.CSE", "BE.EEE", "BE.ECE", "BE.MECH", "BE.CIVIL", "BE.PROD",
            "BSc.AS", "BSc.CSD"
        )
        val ROLES = arrayOf("Student", "Professor")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setupSpinners()
        setupClickListeners()
    }

    private fun setupSpinners() {
        // Setup role spinner
        val roleAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, ROLES)
        (binding.roleSpinner as? AutoCompleteTextView)?.setAdapter(roleAdapter)

        // Setup department spinner
        val departmentAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, DEPARTMENTS)
        (binding.departmentSpinner as? AutoCompleteTextView)?.setAdapter(departmentAdapter)
    }

    private fun setupClickListeners() {
        binding.signupButton.setOnClickListener {
            if (validateInputs()) {
                createAccount()
            }
        }

        binding.loginLink.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun validateInputs(): Boolean {
        val name = binding.nameEditText.text.toString().trim()
        val email = binding.emailEditText.text.toString().trim()
        val password = binding.passwordEditText.text.toString()
        val role = binding.roleSpinner.text.toString()
        val department = binding.departmentSpinner.text.toString()

        if (name.isEmpty()) {
            binding.nameEditText.error = "Name is required"
            return false
        }

        if (email.isEmpty()) {
            binding.emailEditText.error = "Email is required"
            return false
        }

        if (!email.endsWith("@psgtech.ac.in")) {
            binding.emailEditText.error = "Must use PSG Tech email"
            return false
        }

        if (role.isEmpty()) {
            binding.roleSpinner.error = "Please select your role"
            return false
        }

        if (department.isEmpty()) {
            binding.departmentSpinner.error = "Please select your department"
            return false
        }

        if (password.length < 6) {
            binding.passwordEditText.error = "Password must be at least 6 characters"
            return false
        }

        return true
    }

    private fun createAccount() {
        val email = binding.emailEditText.text.toString().trim()
        val password = binding.passwordEditText.text.toString()

        binding.signupButton.isEnabled = false
        binding.signupButton.text = "Creating Account..."

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        // Update display name
                        val profileUpdates = UserProfileChangeRequest.Builder()
                            .setDisplayName(binding.nameEditText.text.toString().trim())
                            .build()

                        user.updateProfile(profileUpdates)
                            .addOnCompleteListener { profileTask ->
                                if (profileTask.isSuccessful) {
                                    // Save additional user data to Firestore
                                    saveUserData(user.uid)
                                } else {
                                    handleError(profileTask.exception?.message)
                                }
                            }

                        // Send email verification
                        user.sendEmailVerification()
                            .addOnCompleteListener { emailTask ->
                                if (emailTask.isSuccessful) {
                                    Toast.makeText(this, "Verification email sent", Toast.LENGTH_LONG).show()
                                }
                            }
                    }
                } else {
                    handleError(task.exception?.message)
                }
            }
    }

    private fun saveUserData(userId: String) {
        val userData = hashMapOf(
            "name" to binding.nameEditText.text.toString().trim(),
            "email" to binding.emailEditText.text.toString().trim(),
            "role" to binding.roleSpinner.text.toString(),
            "department" to binding.departmentSpinner.text.toString(),
            "createdAt" to System.currentTimeMillis()
        )

        db.collection("users").document(userId)
            .set(userData)
            .addOnSuccessListener {
                Toast.makeText(this, "Account created successfully!", Toast.LENGTH_SHORT).show()
                // Navigate to email verification activity
                startActivity(Intent(this, EmailVerificationActivity::class.java))
                finishAffinity()
            }
            .addOnFailureListener { e ->
                handleError(e.message)
            }
    }

    private fun handleError(message: String?) {
        binding.signupButton.isEnabled = true
        binding.signupButton.text = "Sign Up"
        Toast.makeText(this, "Error: ${message ?: "Unknown error"}", Toast.LENGTH_LONG).show()
    }
} 