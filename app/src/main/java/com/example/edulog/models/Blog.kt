package com.example.edulog.models

data class Blog(
    val id: String = "", // Document ID from Firestore
    val title: String = "",
    val content: String = "",
    val authorId: String = "", // Firebase Auth UID
    val authorName: String = "",
    val authorRole: String = "", // "Student" or "Professor"
    val authorDepartment: String = "", // Department code
    val category: String = "", // "technical", "research", or "interview"
    val timestamp: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis(),
    val likes: Int = 0,
    val likedBy: List<String> = listOf() // List of user IDs who liked this blog
) {
    companion object {
        const val CATEGORY_TECHNICAL = "technical"
        const val CATEGORY_RESEARCH = "research"
        const val CATEGORY_INTERVIEW = "interview"
        
        // Get display name for category
        fun getCategoryDisplayName(category: String): String {
            return when (category.lowercase()) {
                CATEGORY_TECHNICAL -> "Technical"
                CATEGORY_RESEARCH -> "Research"
                CATEGORY_INTERVIEW -> "Interview"
                else -> "Other"
            }
        }
    }
} 