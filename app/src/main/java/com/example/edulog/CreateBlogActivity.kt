package com.example.edulog

import android.app.ProgressDialog
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.edulog.databinding.ActivityCreateBlogBinding
import com.example.edulog.models.Blog
import com.example.edulog.utils.PlagiarismChecker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class CreateBlogActivity : BaseActivity() {
    private lateinit var binding: ActivityCreateBlogBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var plagiarismChecker: PlagiarismChecker
    private var progressDialog: ProgressDialog? = null
    private var editingBlogId: String? = null
    private var userRole: String = ""
    private var userDepartment: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateBlogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        plagiarismChecker = PlagiarismChecker(this)

        // Check if user is logged in
        if (auth.currentUser == null) {
            finish()
            return
        }

        loadUserData()
        setupSpinners()
        setupButtons()
        
        // Check if we're editing an existing blog
        editingBlogId = intent.getStringExtra("blog_id")
        if (editingBlogId != null) {
            loadBlogData()
            binding.publishButton.text = "Update Blog"
        }

        // Setup toolbar with back button
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        
        // Handle back gestures with the helper method from BaseActivity
        registerBackPressHandler {
            handleBackNavigation()
        }
    }

    private fun loadUserData() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    userRole = document.getString("role") ?: ""
                    userDepartment = document.getString("department") ?: ""
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading user data: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun setupSpinners() {
        // Setup category spinner
        val categories = arrayOf(
            Blog.getCategoryDisplayName(Blog.CATEGORY_TECHNICAL),
            Blog.getCategoryDisplayName(Blog.CATEGORY_RESEARCH),
            Blog.getCategoryDisplayName(Blog.CATEGORY_INTERVIEW)
        )
        val categoryAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categories)
        (binding.categorySpinner as? AutoCompleteTextView)?.setAdapter(categoryAdapter)
    }

    private fun setupButtons() {
        binding.publishButton.setOnClickListener {
            if (validateInputs()) {
                // If we're editing, skip plagiarism check
                if (editingBlogId != null) {
                    publishBlog()
                } else {
                    checkPlagiarismAndPublish()
                }
            }
        }
    }

    private fun validateInputs(): Boolean {
        val title = binding.titleEditText.text.toString().trim()
        val content = binding.contentEditText.text.toString().trim()
        val category = binding.categorySpinner.text.toString()

        if (title.isEmpty()) {
            binding.titleEditText.error = "Title is required"
            return false
        }

        if (content.isEmpty()) {
            binding.contentEditText.error = "Content is required"
            return false
        }

        if (category.isEmpty()) {
            binding.categorySpinner.error = "Please select a category"
            return false
        }

        return true
    }

    private fun checkPlagiarismAndPublish() {
        val content = binding.contentEditText.text.toString().trim()
        
        // Disable button to prevent multiple submissions
        binding.publishButton.isEnabled = false
        
        // Show progress dialog
        progressDialog = ProgressDialog(this).apply {
            setMessage("Checking for plagiarism...")
            setCancelable(false)
            show()
        }
        
        // Start plagiarism check
        plagiarismChecker.checkPlagiarism(content, object : PlagiarismChecker.PlagiarismCheckListener {
            override fun onCheckCompleted(percentage: Double, isAllowed: Boolean) {
                runOnUiThread {
                    progressDialog?.dismiss()
                    
                    val formattedPercentage = String.format("%.1f", percentage)
                    
                    if (isAllowed) {
                        // Less than or equal to 40% plagiarism, proceed with publishing
                        Toast.makeText(
                            this@CreateBlogActivity,
                            "Plagiarism check passed: $formattedPercentage%",
                            Toast.LENGTH_SHORT
                        ).show()
                        publishBlog()
                    } else {
                        // More than 40% plagiarism, show warning dialog
                        AlertDialog.Builder(this@CreateBlogActivity)
                            .setTitle("High Plagiarism Detected")
                            .setMessage("Your content has $formattedPercentage% plagiarism. Please reconsider your content.")
                            .setPositiveButton("Edit Content") { _, _ ->
                                // User will edit content
                                binding.publishButton.isEnabled = true
                            }
                            .setCancelable(false)
                            .show()
                    }
                }
            }

            override fun onError(message: String) {
                runOnUiThread {
                    progressDialog?.dismiss()
                    
                    // Re-enable the button
                    binding.publishButton.isEnabled = true
                    
                    // Show error dialog with more information about API issues
                    AlertDialog.Builder(this@CreateBlogActivity)
                        .setTitle("Plagiarism Check Error")
                        .setMessage("$message\n\nTo fix this issue:\n1. Check your Copyleaks account status\n2. Verify your API key is activated\n3. Try again later\n\nDo you want to publish without plagiarism checking?")
                        .setPositiveButton("Publish Anyway") { _, _ -> publishBlog() }
                        .setNegativeButton("Cancel", null)
                        .setCancelable(false)
                        .show()
                }
            }
        })
    }

    private fun publishBlog() {
        val currentUser = auth.currentUser ?: return

        // Disable the button and show progress
        binding.publishButton.isEnabled = false
        binding.publishButton.text = if (editingBlogId != null) "Updating..." else "Publishing..."

        // Create blog document
        val blogRef = if (editingBlogId != null) {
            db.collection("blogs").document(editingBlogId!!)
        } else {
            db.collection("blogs").document()
        }

        // Get the category and convert to lowercase for storing
        val displayCategory = binding.categorySpinner.text.toString()
        val categoryValue = when (displayCategory) {
            Blog.getCategoryDisplayName(Blog.CATEGORY_TECHNICAL) -> Blog.CATEGORY_TECHNICAL
            Blog.getCategoryDisplayName(Blog.CATEGORY_RESEARCH) -> Blog.CATEGORY_RESEARCH
            Blog.getCategoryDisplayName(Blog.CATEGORY_INTERVIEW) -> Blog.CATEGORY_INTERVIEW
            else -> displayCategory.lowercase()
        }

        // Create blog object
        val blog = Blog(
            id = blogRef.id,
            title = binding.titleEditText.text.toString().trim(),
            content = binding.contentEditText.text.toString().trim(),
            authorId = currentUser.uid,
            authorName = currentUser.displayName ?: currentUser.email?.substringBefore("@") ?: "Anonymous",
            authorRole = userRole,
            authorDepartment = userDepartment,
            category = categoryValue,
            timestamp = if (editingBlogId == null) System.currentTimeMillis() else 0,
            lastModified = System.currentTimeMillis()
        )

        // Set the document with merge option to prevent overwriting existing fields
        blogRef.set(blog, SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(
                    this,
                    if (editingBlogId != null) "Blog updated successfully!" else "Blog published successfully!",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
            .addOnFailureListener { e ->
                // Re-enable the button and show error
                binding.publishButton.isEnabled = true
                binding.publishButton.text = if (editingBlogId != null) "Update Blog" else "Publish Blog"
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun loadBlogData() {
        binding.publishButton.text = "Update Blog"
        
        db.collection("blogs").document(editingBlogId!!)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val blog = document.toObject(Blog::class.java)
                    blog?.let {
                        binding.titleEditText.setText(it.title)
                        binding.contentEditText.setText(it.content)
                        binding.categorySpinner.setText(it.category, false)
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading blog: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }
    
    private fun handleBackNavigation() {
        try {
            // Check if there's unsaved content before allowing back navigation
            val title = binding.titleEditText.text.toString().trim()
            val content = binding.contentEditText.text.toString().trim()
            
            if (title.isNotEmpty() || content.isNotEmpty()) {
                // There is content that might be lost
                AlertDialog.Builder(this)
                    .setTitle("Discard Changes")
                    .setMessage("You have unsaved changes. Are you sure you want to leave?")
                    .setPositiveButton("Discard") { _, _ ->
                        finish()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                finish()
            }
        } catch (e: Exception) {
            Log.e("CreateBlog", "Error in handleBackNavigation: ${e.message}", e)
            // Fall back to simple finish if there's an error
            finish()
        }
    }
} 