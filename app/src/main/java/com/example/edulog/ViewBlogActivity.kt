package com.example.edulog

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.edulog.databinding.ActivityViewBlogBinding
import com.example.edulog.models.Blog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class ViewBlogActivity : AppCompatActivity() {
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

            // Show edit/delete menu only for the author
            invalidateOptionsMenu()
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
            android.R.id.home -> {
                finish()
                true
            }
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