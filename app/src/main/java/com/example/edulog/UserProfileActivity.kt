package com.example.edulog

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.edulog.databinding.ActivityUserProfileBinding
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class UserProfileActivity : BaseActivity() {
    private lateinit var binding: ActivityUserProfileBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var isEditing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Check if user is logged in
        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Profile"

        loadUserData()
        setupClickListeners()
        setupBackNavigation()
    }

    private fun loadUserData() {
        val userId = auth.currentUser?.uid ?: return
        
        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    binding.nameEditText.setText(document.getString("name"))
                    binding.emailText.text = document.getString("email")
                    binding.roleText.text = document.getString("role")
                    binding.departmentText.text = document.getString("department")
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading profile: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupClickListeners() {
        binding.editButton.setOnClickListener {
            if (isEditing) {
                // Save changes
                saveChanges()
            } else {
                // Enter edit mode
                startEditing()
            }
        }

        binding.changePasswordButton.setOnClickListener {
            showChangePasswordDialog()
        }
        
        binding.logoutButton.setOnClickListener {
            showLogoutConfirmation()
        }
        
        binding.deleteAccountButton.setOnClickListener {
            showDeleteAccountConfirmation()
        }
    }

    private fun startEditing() {
        isEditing = true
        binding.editButton.text = "Save"
        binding.nameEditText.isEnabled = true
        binding.nameEditText.requestFocus()
    }

    private fun saveChanges() {
        val userId = auth.currentUser?.uid ?: return
        val newName = binding.nameEditText.text.toString().trim()

        if (newName.isEmpty()) {
            binding.nameEditText.error = "Name cannot be empty"
            return
        }

        // Update Firestore
        db.collection("users").document(userId)
            .update("name", newName)
            .addOnSuccessListener {
                // Update Auth Profile
                auth.currentUser?.updateProfile(
                    com.google.firebase.auth.UserProfileChangeRequest.Builder()
                        .setDisplayName(newName)
                        .build()
                )

                Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                isEditing = false
                binding.editButton.text = "Edit"
                binding.nameEditText.isEnabled = false
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error updating profile: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showChangePasswordDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_change_password, null)
        val currentPasswordInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.currentPasswordInput)
        val newPasswordInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.newPasswordInput)
        val confirmPasswordInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.confirmPasswordInput)

        AlertDialog.Builder(this)
            .setTitle("Change Password")
            .setView(dialogView)
            .setPositiveButton("Change") { dialog, _ ->
                val currentPassword = currentPasswordInput.text.toString()
                val newPassword = newPasswordInput.text.toString()
                val confirmPassword = confirmPasswordInput.text.toString()

                if (newPassword != confirmPassword) {
                    Toast.makeText(this, "New passwords don't match", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (newPassword.length < 6) {
                    Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                changePassword(currentPassword, newPassword)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun changePassword(currentPassword: String, newPassword: String) {
        val user = auth.currentUser ?: return
        val email = user.email ?: return

        // Re-authenticate user
        val credential = EmailAuthProvider.getCredential(email, currentPassword)
        user.reauthenticate(credential)
            .addOnSuccessListener {
                // Update password
                user.updatePassword(newPassword)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Password updated successfully", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error updating password: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Current password is incorrect", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun showLogoutConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                logout()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun logout() {
        auth.signOut()
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
        
        // Navigate to login screen and clear the back stack
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finishAffinity()
    }
    
    private fun showDeleteAccountConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Delete Account")
            .setMessage("Warning: This will permanently delete your account and all your data. This action cannot be undone. Are you sure?")
            .setPositiveButton("Delete") { _, _ ->
                showPasswordConfirmationForDelete()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showPasswordConfirmationForDelete() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_confirm_password, null)
        val passwordInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.passwordInput)
        
        AlertDialog.Builder(this)
            .setTitle("Confirm Password")
            .setMessage("Please enter your password to confirm account deletion")
            .setView(dialogView)
            .setPositiveButton("Confirm") { _, _ ->
                val password = passwordInput.text.toString()
                if (password.isNotEmpty()) {
                    deleteUserAccount(password)
                } else {
                    Toast.makeText(this, "Password is required", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteUserAccount(password: String) {
        val user = auth.currentUser ?: return
        val email = user.email ?: return
        val userId = user.uid

        // Re-authenticate before deletion
        val credential = EmailAuthProvider.getCredential(email, password)
        user.reauthenticate(credential)
            .addOnSuccessListener {
                // First delete Firestore data
                db.collection("users").document(userId)
                    .delete()
                    .addOnSuccessListener {
                        // After Firestore data is deleted, delete the auth account
                        user.delete()
                            .addOnSuccessListener {
                                Toast.makeText(this, "Account deleted successfully", Toast.LENGTH_SHORT).show()
                                // Navigate to login
                                val intent = Intent(this, LoginActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                                finishAffinity()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Error deleting account: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error deleting user data: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupBackNavigation() {
        // Setup back press handler using helper method from BaseActivity
        registerBackPressHandler {
            try {
                Log.d("UserProfile", "Back navigation triggered, finishing activity")
                finish()
            } catch (e: Exception) {
                Log.e("UserProfile", "Error finishing activity: ${e.message}", e)
                // Try alternative method to finish
                finishAndRemoveTask()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return super.onSupportNavigateUp()
    }
} 