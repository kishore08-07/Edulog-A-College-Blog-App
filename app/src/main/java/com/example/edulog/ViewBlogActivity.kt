package com.example.edulog

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.example.edulog.databinding.ActivityViewBlogBinding
import com.example.edulog.models.Blog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class ViewBlogActivity : BaseActivity() {
    private lateinit var binding: ActivityViewBlogBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var blogId: String? = null
    private var blog: Blog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViewBlogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        blogId = intent.getStringExtra("blog_id")
        if (blogId == null) {
            finish()
            return
        }

        loadBlog()
        setupBackNavigation()
    }

    private fun setupBackNavigation() {
        // Setup back press handler using helper method from BaseActivity
        registerBackPressHandler {
            try {
                Log.d("ViewBlog", "Back navigation triggered, finishing activity")
                finish()
            } catch (e: Exception) {
                Log.e("ViewBlog", "Error finishing activity: ${e.message}", e)
                // Try alternative method to finish
                finishAndRemoveTask()
            }
        }
    }

    private fun loadBlog() {
        db.collection("blogs").document(blogId!!)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    blog = document.toObject(Blog::class.java)
                    displayBlog()
                } else {
                    Toast.makeText(this, "Blog not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading blog: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun displayBlog() {
        blog?.let {
            binding.titleText.text = it.title
            binding.authorText.text = "${it.authorName} • ${it.authorRole} • ${it.authorDepartment}"
            binding.categoryText.text = Blog.getCategoryDisplayName(it.category)
            binding.contentText.text = it.content
            
            val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            binding.dateText.text = "Posted on ${dateFormat.format(Date(it.timestamp))}"
            
            if (it.lastModified > it.timestamp) {
                binding.lastModifiedText.text = "Last modified on ${dateFormat.format(Date(it.lastModified))}"
                binding.lastModifiedText.visibility = android.view.View.VISIBLE
            }

            // Setup like button
            val currentUserId = auth.currentUser?.uid
            val isLiked = currentUserId in it.likedBy
            binding.likeButton.setImageResource(
                if (isLiked) R.drawable.ic_like_filled else R.drawable.ic_like
            )
            binding.likeCount.text = it.likes.toString()

            // Like button click listener
            binding.likeContainer.setOnClickListener { _ ->
                if (auth.currentUser != null) {
                    toggleLike()
                } else {
                    Toast.makeText(this, "Please login to like blogs", Toast.LENGTH_SHORT).show()
                }
            }

            // Show edit/delete menu only for the author
            invalidateOptionsMenu()
        }
    }

    private fun toggleLike() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "Please login to like blogs", Toast.LENGTH_SHORT).show()
            return
        }

        if (blogId == null) {
            Toast.makeText(this, "Error: Blog not found", Toast.LENGTH_SHORT).show()
            return
        }

        val blogRef = db.collection("blogs").document(blogId!!)

        // Show loading state
        binding.likeContainer.isEnabled = false

        db.runTransaction { transaction ->
            val snapshot = transaction.get(blogRef)
            if (!snapshot.exists()) {
                throw Exception("Blog not found")
            }

            val currentLikedBy = snapshot.get("likedBy") as? List<String> ?: listOf()
            
            if (userId in currentLikedBy) {
                // Unlike: Remove user from likedBy array
                val updatedLikedBy = currentLikedBy.filter { it != userId }
                transaction.update(blogRef, 
                    mapOf(
                        "likedBy" to updatedLikedBy,
                        "likes" to updatedLikedBy.size
                    )
                )
            } else {
                // Like: Add user to likedBy array
                val updatedLikedBy = currentLikedBy + userId
                transaction.update(blogRef, 
                    mapOf(
                        "likedBy" to updatedLikedBy,
                        "likes" to updatedLikedBy.size
                    )
                )
            }
        }.addOnSuccessListener {
            Log.d("ViewBlog", "Like toggled successfully for blog $blogId")
            binding.likeContainer.isEnabled = true
            loadBlog() // Reload blog to update UI
        }.addOnFailureListener { e ->
            Log.e("ViewBlog", "Error toggling like for blog $blogId: ${e.message}", e)
            binding.likeContainer.isEnabled = true
            Toast.makeText(this, "Error updating like. Please try again.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        blog?.let {
            if (auth.currentUser?.uid == it.authorId) {
                menuInflater.inflate(R.menu.menu_view_blog, menu)
            }
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_edit -> {
                val intent = Intent(this, CreateBlogActivity::class.java)
                intent.putExtra("blog_id", blogId)
                startActivity(intent)
                true
            }
            R.id.action_delete -> {
                deleteBlog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun deleteBlog() {
        db.collection("blogs").document(blogId!!)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Blog deleted successfully", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error deleting blog: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
} 