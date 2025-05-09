package com.example.edulog

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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

class BlogListActivity : BaseActivity() {
    private lateinit var binding: ActivityBlogListBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var blogAdapter: BlogAdapter? = null
    private var trendingAdapter: BlogAdapter? = null
    private var currentCategory: String = "All"
    
    // Variables for double-tap back to exit
    private var doubleBackToExitPressedOnce = false
    private val doubleBackToExitInterval = 2000 // 2 seconds

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
        setupBackNavigation()
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
        try {
            currentCategory = category
            Log.d("BlogList", "Filtering by category: $currentCategory")
            
            // Initially show loading state
            binding.emptyStateLayout.visibility = View.VISIBLE
            binding.emptyStateText.text = "Loading blogs..."
            binding.recyclerView.visibility = View.GONE
            
            // Stop the current adapter to prevent inconsistencies
            blogAdapter?.stopListening()

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

            // Create new adapter
            blogAdapter = BlogAdapter(options, false)
            binding.recyclerView.adapter = blogAdapter
            blogAdapter?.startListening()
        } catch (e: Exception) {
            Log.e("BlogList", "Error filtering by category: ${e.message}", e)
            // Show error to user
            binding.emptyStateLayout.visibility = View.VISIBLE
            binding.emptyStateText.text = "Error loading blogs"
            binding.recyclerView.visibility = View.GONE
            Toast.makeText(this, "Error loading blogs. Try again.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateBlogList() {
        try {
            Log.d("BlogList", "Updating blog list with category: $currentCategory")
            filterByCategory(currentCategory)
        } catch (e: Exception) {
            Log.e("BlogList", "Error updating blog list: ${e.message}", e)
        }
    }

    private fun setupRecyclerViews() {
        try {
            Log.d("BlogList", "Setting up RecyclerViews")
            
            // First, stop any existing adapters to prevent memory leaks
            blogAdapter?.stopListening()
            trendingAdapter?.stopListening()
            
            // Clear adapters
            binding.recyclerView.adapter = null
            binding.trendingRecyclerView.adapter = null
            
            // Setup trending blogs RecyclerView with horizontal layout
            binding.trendingRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            setupTrendingAdapter()

            // Setup main blogs RecyclerView
            binding.recyclerView.layoutManager = LinearLayoutManager(this)
            updateBlogList()
            
            Log.d("BlogList", "RecyclerViews setup complete")
        } catch (e: Exception) {
            Log.e("BlogList", "Error setting up RecyclerViews: ${e.message}", e)
            Toast.makeText(this, "Error loading blogs. Please restart the app.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupTrendingAdapter() {
        try {
            // Stop existing adapter if it exists
            trendingAdapter?.stopListening()
            
            // Query for trending blogs - most recent 5 blogs regardless of category
            val trendingQuery = db.collection("blogs")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(5)

            val options = FirestoreRecyclerOptions.Builder<Blog>()
                .setQuery(trendingQuery, Blog::class.java)
                .build()

            // Create new adapter
            trendingAdapter = BlogAdapter(options, true) // true indicates horizontal layout
            binding.trendingRecyclerView.adapter = trendingAdapter
            trendingAdapter?.startListening()
            Log.d("BlogList", "Trending adapter setup complete")
        } catch (e: Exception) {
            Log.e("BlogList", "Error setting up trending adapter: ${e.message}", e)
        }
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
        when (item.itemId) {
            R.id.action_profile -> {
                startActivity(Intent(this, UserProfileActivity::class.java))
                return true
            }
            android.R.id.home -> {
                // This is redundant since we're overriding in BaseActivity,
                // but included for completeness
                onBackPressedDispatcher.onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onStart() {
        super.onStart()
        try {
            // Safely start adapters
            if (blogAdapter?.itemCount == 0) {
                updateBlogList() // Refresh data if empty
            } else {
                blogAdapter?.startListening()
            }
            
            if (trendingAdapter?.itemCount == 0) {
                setupTrendingAdapter() // Refresh trending if empty
            } else {
                trendingAdapter?.startListening()
            }
            Log.d("BlogList", "Adapters started listening")
        } catch (e: Exception) {
            Log.e("BlogList", "Error starting adapters: ${e.message}", e)
            // Recreate adapters if there was an error
            setupRecyclerViews()
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            // Safely stop listening to prevent leaks
            blogAdapter?.stopListening()
            trendingAdapter?.stopListening()
            Log.d("BlogList", "Adapters stopped listening")
        } catch (e: Exception) {
            Log.e("BlogList", "Error stopping adapters: ${e.message}", e)
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Preemptively stop adapters when activity goes to background
        // This helps prevent inconsistency errors when returning
        try {
            blogAdapter?.stopListening()
            trendingAdapter?.stopListening()
            Log.d("BlogList", "Adapters stopped listening in onPause")
        } catch (e: Exception) {
            Log.e("BlogList", "Error stopping adapters in onPause: ${e.message}", e)
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Restart adapters when activity comes to foreground
        try {
            setupRecyclerViews() // Recreate adapters to ensure clean state
            Log.d("BlogList", "RecyclerViews setup in onResume")
        } catch (e: Exception) {
            Log.e("BlogList", "Error setting up RecyclerViews in onResume: ${e.message}", e)
        }
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
            try {
                // Log each blog for debugging
                Log.d("BlogAdapter", "Binding blog at position $position: ${model.title}, AuthorID: ${model.authorId}")
                holder.bind(model)
            } catch (e: Exception) {
                Log.e("BlogAdapter", "Error binding view holder at position $position: ${e.message}", e)
                // Attempt minimal binding to prevent UI issues
                try {
                    holder.binding.titleText.text = "Error loading blog"
                    holder.binding.previewText.text = "There was a problem loading this blog item."
                } catch (e2: Exception) {
                    Log.e("BlogAdapter", "Critical error in fallback binding: ${e2.message}", e2)
                }
            }
        }
        
        override fun onViewRecycled(holder: BlogViewHolder) {
            super.onViewRecycled(holder)
            try {
                // Clean up any resources that might cause leaks
                Log.d("BlogAdapter", "Recycling view holder at position ${holder.adapterPosition}")
            } catch (e: Exception) {
                Log.e("BlogAdapter", "Error recycling view holder: ${e.message}", e)
            }
        }
        
        override fun onDataChanged() {
            super.onDataChanged()
            try {
                // Check if we have any items
                if (itemCount == 0 && !isHorizontal) {
                    // No blogs found for this category, show empty state
                    binding.emptyStateLayout.visibility = View.VISIBLE
                    binding.recyclerView.visibility = View.GONE
                    
                    // Set appropriate message based on current category
                    if (currentCategory == "All") {
                        binding.emptyStateText.text = "No blogs found. Be the first to post!"
                    } else {
                        binding.emptyStateText.text = "No blogs found in the '${currentCategory}' category."
                    }
                } else if (!isHorizontal) {
                    // We have blogs, hide empty state
                    binding.emptyStateLayout.visibility = View.GONE
                    binding.recyclerView.visibility = View.VISIBLE
                }
                
                // Handle trending RecyclerView separately
                if (itemCount == 0 && isHorizontal) {
                    binding.trendingRecyclerView.visibility = View.GONE
                    binding.trendingLabel.visibility = View.GONE
                } else if (isHorizontal) {
                    binding.trendingRecyclerView.visibility = View.VISIBLE
                    binding.trendingLabel.visibility = View.VISIBLE
                }
                
                Log.d("BlogAdapter", "Data changed: ${itemCount} items available")
            } catch (e: Exception) {
                Log.e("BlogAdapter", "Error in onDataChanged: ${e.message}", e)
            }
        }
    }

    inner class BlogViewHolder(val binding: ItemBlogBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(blog: Blog) {
            try {
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
            } catch (e: Exception) {
                Log.e("BlogViewHolder", "Error binding blog data: ${e.message}", e)
                // Set minimal content to prevent UI issues
                binding.titleText.text = "Error"
                binding.previewText.text = "There was a problem loading this blog."
            }
        }
    }

    private fun setupBackNavigation() {
        // Set up the back press callback with more robust handling
        registerBackPressHandler {
            try {
                if (doubleBackToExitPressedOnce) {
                    // If second back press within time window, finish the activity
                    Log.d("BlogList", "Double back press detected, finishing activity")
                    finishAndRemoveTask() // More robust way to finish
                    return@registerBackPressHandler
                }

                // First back press - show toast and set flag
                doubleBackToExitPressedOnce = true
                Toast.makeText(this@BlogListActivity, "Press back again to exit", Toast.LENGTH_SHORT).show()
                Log.d("BlogList", "First back press detected, waiting for second")

                // Reset flag after delay
                Handler(Looper.getMainLooper()).postDelayed({
                    doubleBackToExitPressedOnce = false
                    Log.d("BlogList", "Double back press timer expired")
                }, doubleBackToExitInterval.toLong())
            } catch (e: Exception) {
                Log.e("BlogList", "Error in back navigation: ${e.message}", e)
                // As a last resort, just try to finish normally
                finish()
            }
        }
    }
} 