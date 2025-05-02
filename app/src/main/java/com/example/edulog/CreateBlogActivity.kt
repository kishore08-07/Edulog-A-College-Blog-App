package com.example.edulog

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.edulog.databinding.ActivityCreateBlogBinding
import com.example.edulog.models.Blog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class CreateBlogActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCreateBlogBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var editingBlogId: String? = null
    private var userRole: String = ""
    private var userDepartment: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateBlogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

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
                publishBlog()
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
            .addOnCanceledListener {
                // Handle timeout or cancellation
                binding.publishButton.isEnabled = true
                binding.publishButton.text = if (editingBlogId != null) "Update Blog" else "Publish Blog"
                Toast.makeText(this, "Operation timed out. Please try again.", Toast.LENGTH_LONG).show()
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
} 