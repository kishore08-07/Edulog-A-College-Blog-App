package com.example.edulog

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.edulog.databinding.ActivityBlogListBinding
import com.example.edulog.databinding.ItemBlogBinding
import com.example.edulog.models.Blog
import com.firebase.ui.firestore.FirestoreRecyclerAdapter
import com.firebase.ui.firestore.FirestoreRecyclerOptions
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class BlogListActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBlogListBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var blogAdapter: BlogAdapter? = null
    private var trendingAdapter: BlogAdapter? = null
    private var currentCategory: String = "All"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBlogListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Edulog"

        setupCategoryChips()
        setupRecyclerViews()
        setupFab()
    }

    private fun setupCategoryChips() {
        // When a category chip is selected, update the blog list
        binding.categoryChipGroup.setOnCheckedChangeListener { group, checkedId ->
            if (checkedId == View.NO_ID) {
                // If no chip is selected, show all blogs
                filterByCategory("All")
            } else {
                val selectedChip = group.findViewById<Chip>(checkedId)
                val category = selectedChip?.text?.toString() ?: "All"
                filterByCategory(category)
            }
        }

        // Set "All" chip as checked by default
        binding.categoryChipGroup.check(binding.categoryChipGroup.getChildAt(0).id)
    }

    private fun filterByCategory(category: String) {
        currentCategory = category
        Log.d("BlogList", "Filtering by category: $currentCategory")

        // Create the base query
        val query = when (category) {
            "All" -> {
                db.collection("blogs")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
            }
            else -> {
                val categoryValue = category.lowercase()
                Log.d("BlogList", "Using category value for query: $categoryValue")
                db.collection("blogs")
                    .whereEqualTo("category", categoryValue)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
            }
        }

        // Create adapter options
        val options = FirestoreRecyclerOptions.Builder<Blog>()
            .setQuery(query, Blog::class.java)
            .build()

        // Update adapter
        blogAdapter?.stopListening()
        blogAdapter = BlogAdapter(options, false)
        binding.recyclerView.adapter = blogAdapter
        blogAdapter?.startListening()
    }

    private fun updateBlogList() {
        filterByCategory(currentCategory)
    }

    private fun setupRecyclerViews() {
        // Setup trending blogs RecyclerView with horizontal layout
        binding.trendingRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        setupTrendingAdapter()

        // Setup main blogs RecyclerView
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        updateBlogList()
    }

    private fun setupTrendingAdapter() {
        // Query for trending blogs - most recent 5 blogs regardless of category
        val trendingQuery = db.collection("blogs")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(5)

        val options = FirestoreRecyclerOptions.Builder<Blog>()
            .setQuery(trendingQuery, Blog::class.java)
            .build()

        trendingAdapter = BlogAdapter(options, true) // true indicates horizontal layout
        binding.trendingRecyclerView.adapter = trendingAdapter
    }

    private fun setupFab() {
        binding.fab.setOnClickListener {
            startActivity(Intent(this, CreateBlogActivity::class.java))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_blog_list, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_profile -> {
                startActivity(Intent(this, UserProfileActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onStart() {
        super.onStart()
        blogAdapter?.startListening()
        trendingAdapter?.startListening()
    }

    override fun onStop() {
        super.onStop()
        blogAdapter?.stopListening()
        trendingAdapter?.stopListening()
    }

    private fun showDeleteConfirmation(blog: Blog) {
        AlertDialog.Builder(this)
            .setTitle("Delete Blog")
            .setMessage("Are you sure you want to delete this blog?")
            .setPositiveButton("Delete") { _, _ ->
                deleteBlog(blog)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteBlog(blog: Blog) {
        db.collection("blogs").document(blog.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Blog deleted successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error deleting blog: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    inner class BlogAdapter(options: FirestoreRecyclerOptions<Blog>, private val isHorizontal: Boolean) :
        FirestoreRecyclerAdapter<Blog, BlogViewHolder>(options) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BlogViewHolder {
            val binding = ItemBlogBinding.inflate(layoutInflater, parent, false)
            
            // If horizontal layout, adjust the item width
            if (isHorizontal) {
                val displayMetrics = resources.displayMetrics
                val screenWidth = displayMetrics.widthPixels
                binding.root.layoutParams.width = (screenWidth * 0.8).toInt() // 80% of screen width
            }
            
            return BlogViewHolder(binding)
        }

        override fun onBindViewHolder(holder: BlogViewHolder, position: Int, model: Blog) {
            // Log each blog for debugging
            Log.d("BlogAdapter", "Binding blog: ${model.title}, AuthorID: ${model.authorId}, Current user: ${auth.currentUser?.uid}")
            holder.bind(model)
        }
    }

    inner class BlogViewHolder(private val binding: ItemBlogBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(blog: Blog) {
            binding.titleText.text = blog.title
            binding.authorText.text = blog.authorName
            binding.authorDetailsText.text = "${blog.authorRole} • ${blog.authorDepartment}"
            binding.categoryText.text = Blog.getCategoryDisplayName(blog.category)
            binding.previewText.text = if (blog.content.length > 100) {
                "${blog.content.substring(0, 100)}..."
            } else {
                blog.content
            }

            // Set category text color based on category
            val categoryColor = when (blog.category.lowercase()) {
                "technical" -> android.graphics.Color.parseColor("#2196F3") // Blue
                "research" -> android.graphics.Color.parseColor("#4CAF50") // Green
                "interview" -> android.graphics.Color.parseColor("#FF9800") // Orange
                else -> android.graphics.Color.GRAY
            }
            binding.categoryText.setBackgroundColor(categoryColor)

            // Remove category text click listener as we only want top buttons to work
            binding.categoryText.setOnClickListener(null)

            // Show edit/delete buttons only for the author
            val isAuthor = auth.currentUser?.uid == blog.authorId
            binding.editButton.visibility = if (isAuthor) View.VISIBLE else View.GONE
            binding.deleteButton.visibility = if (isAuthor) View.VISIBLE else View.GONE

            // Set click listeners
            itemView.setOnClickListener {
                val intent = Intent(this@BlogListActivity, ViewBlogActivity::class.java)
                intent.putExtra("blog_id", blog.id)
                startActivity(intent)
            }

            binding.editButton.setOnClickListener {
                val intent = Intent(this@BlogListActivity, CreateBlogActivity::class.java)
                intent.putExtra("blog_id", blog.id)
                startActivity(intent)
            }

            binding.deleteButton.setOnClickListener {
                showDeleteConfirmation(blog)
            }
        }
    }
} 