package com.example.edulog

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.edulog.databinding.ActivityDeleteAccountBinding

class DeleteAccountActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDeleteAccountBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeleteAccountBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Check if user is logged in
        if (auth.currentUser == null) {
            finish()
            return
        }

        binding.deleteAccountButton.setOnClickListener {
            showDeleteConfirmation()
        }
    }

    private fun showDeleteConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Delete Account")
            .setMessage("Are you sure you want to delete your account? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteAccount()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteAccount() {
        val user = auth.currentUser ?: return
        val userId = user.uid

        // First, delete user data from Firestore
        db.collection("users").document(userId)
            .delete()
            .addOnSuccessListener {
                // After Firestore data is deleted, delete the auth account
                deleteAuthAccount()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error deleting user data: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun deleteAuthAccount() {
        val user = auth.currentUser ?: return
        
        user.delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Account deleted successfully", Toast.LENGTH_SHORT).show()
                // Navigate to login screen
                startActivity(Intent(this, LoginActivity::class.java))
                finishAffinity()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error deleting account: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
} 