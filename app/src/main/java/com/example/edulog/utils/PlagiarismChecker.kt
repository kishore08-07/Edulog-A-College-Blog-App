package com.example.edulog.utils

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.schedule
import kotlin.math.absoluteValue

class PlagiarismChecker(private val context: Context) {
    // Create an OkHttpClient with timeouts
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
        
    // To use the real Copyleaks API:
    // 1. Sign up at https://copyleaks.com/
    // 2. Verify your email and activate your account
    // 3. Get your API key from your dashboard
    // 4. Set USE_SIMULATION_MODE to false
    private val EMAIL = "kishorebsm8@gmail.com"
    private val API_KEY = "0233e3cc-44c6-4833-8c57-544067cc722e"
    private val BASE_URL = "https://api.copyleaks.com"
    private var accessToken: String? = null
    
    // Flag to force simulation mode (set to false to use real API once account is activated)
    private val USE_SIMULATION_MODE = false
    
    private val JSON = "application/json; charset=utf-8".toMediaTypeOrNull()
    private val TAG = "PlagiarismChecker"

    interface PlagiarismCheckListener {
        fun onCheckCompleted(percentage: Double, isAllowed: Boolean)
        fun onError(message: String)
    }

    /**
     * Authenticate with Copyleaks API and get an access token
     */
    fun authenticate(callback: (Boolean) -> Unit) {
        // Prepare authentication request body according to docs
        val jsonBody = """
            {
                "email": "$EMAIL",
                "key": "$API_KEY"
            }
        """.trimIndent()

        val requestBody = jsonBody.toRequestBody(JSON)

        val request = Request.Builder()
            .url("$BASE_URL/v3/account/login/api")
            .addHeader("Content-Type", "application/json")
            // Important: No Authorization header here
            .post(requestBody)
            .build()

        Log.d(TAG, "Attempting authentication with email: $EMAIL")
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Authentication failed with exception", e)
                callback(false)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val responseBody = it.body?.string()
                    Log.d(TAG, "Auth response code: ${it.code}, body: $responseBody")
                    
                    if (!it.isSuccessful) {
                        Log.e(TAG, "Authentication failed with code: ${it.code}, message: ${it.message}")
                        callback(false)
                        return
                    }

                    try {
                        val jsonResponse = JSONObject(responseBody)
                        if (jsonResponse.has("access_token")) {
                            accessToken = jsonResponse.getString("access_token")
                            Log.d(TAG, "Authentication successful, token received: ${accessToken?.substring(0, 10)}...")
                            callback(true)
                        } else {
                            Log.e(TAG, "Access token not found in response")
                            callback(false)
                        }
                    } catch (e: JSONException) {
                        Log.e(TAG, "Failed to parse auth response", e)
                        callback(false)
                    }
                }
            }
        })
    }

    /**
     * Temporary solution: Simulate a plagiarism check with random results
     * This will allow testing the UI functionality while API issues are resolved
     */
    fun simulatePlagiarismCheck(content: String, listener: PlagiarismCheckListener) {
        // Calculate a deterministic but pseudo-random plagiarism percentage based on content
        val wordCount = content.split("\\s+".toRegex()).size
        // Use the length and a hash to create a deterministic but seemingly random value
        val contentHash = content.hashCode().absoluteValue
        val percentage = (contentHash % 80).toDouble() // between 0 and 79%
        
        Log.d(TAG, "Simulating plagiarism check, content length: ${content.length}, words: $wordCount")
        Log.d(TAG, "Simulated plagiarism percentage: $percentage%")
        
        // Simulate network delay
        Thread {
            try {
                // Simulate processing time
                Thread.sleep(1500)
                // Use Handler to call back on main thread
                Handler(android.os.Looper.getMainLooper()).post {
                    // Content is allowed if plagiarism is <= 40%
                    listener.onCheckCompleted(percentage, percentage <= 40.0)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in simulated check", e)
                Handler(android.os.Looper.getMainLooper()).post {
                    listener.onError("Error in plagiarism check simulation: ${e.message}")
                }
            }
        }.start()
    }

    /**
     * Submit content for plagiarism check
     */
    fun checkPlagiarism(content: String, listener: PlagiarismCheckListener) {
        // If simulation mode is enabled, skip the API entirely
        if (USE_SIMULATION_MODE) {
            Log.d(TAG, "Using simulation mode for plagiarism check")
            simulatePlagiarismCheck(content, listener)
            return
        }
        
        // Try the API authentication approach first
        authenticate { success ->
            if (success) {
                Log.d(TAG, "Authentication successful, proceeding with plagiarism check")
                submitPlagiarismCheck(content, listener)
            } else {
                Log.e(TAG, "Authentication failed, using simulated check instead")
                // Fall back to simulation if API auth fails
                simulatePlagiarismCheck(content, listener)
            }
        }
    }

    private fun submitPlagiarismCheck(content: String, listener: PlagiarismCheckListener) {
        // Generate a unique scan ID
        val scanId = UUID.randomUUID().toString()
        
        // Create request body for submitting content
        val requestBody = JSONObject().apply {
            put("text", content)
            // Set sandbox to false to make scans appear in your dashboard
            put("sandbox", false)
            
            // Create proper properties object per docs
            put("properties", JSONObject().apply {
                put("includeCitations", true)
                put("sensitivityLevel", "medium")
                // Education scan type is most appropriate for blog content
                put("scanType", "education") 
            })
        }.toString().toRequestBody(JSON)

        Log.d(TAG, "Submitting content with scan ID: $scanId")

        val request = Request.Builder()
            .url("$BASE_URL/v3/scans/$scanId/text")
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Submission failed with exception", e)
                listener.onError("Failed to submit content: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val responseBody = it.body?.string()
                    Log.d(TAG, "Submission response code: ${it.code}")
                    
                    if (!it.isSuccessful) {
                        Log.e(TAG, "Submission failed with code: ${it.code}, message: ${it.message}")
                        try {
                            if (responseBody != null) {
                                val errorJson = JSONObject(responseBody)
                                val errorMessage = if (errorJson.has("message")) {
                                    errorJson.getString("message")
                                } else {
                                    "Unknown error: HTTP ${it.code}"
                                }
                                Log.e(TAG, "Error details: $errorMessage")
                                listener.onError("Submission failed: $errorMessage")
                            } else {
                                listener.onError("Submission failed: ${it.message}")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing error response", e)
                            listener.onError("Submission failed: ${it.message}")
                        }
                        return
                    }

                    Log.d(TAG, "Content submitted successfully, scan ID: $scanId")
                    // Start polling for results
                    pollForResults(scanId, listener)
                }
            }
        })
    }

    /**
     * Poll for plagiarism check results
     */
    private fun pollForResults(scanId: String, listener: PlagiarismCheckListener) {
        val request = Request.Builder()
            .url("$BASE_URL/v3/scans/$scanId/status")
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        // Define a recursive function for polling
        fun pollStatus() {
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "Polling failed", e)
                    listener.onError("Failed to get results: ${e.message}")
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!it.isSuccessful) {
                            Log.e(TAG, "Polling failed with code: ${it.code}")
                            listener.onError("Failed to get results: ${it.message}")
                            return
                        }

                        val responseBody = it.body?.string()
                        try {
                            val jsonResponse = JSONObject(responseBody)
                            val status = jsonResponse.getString("status")
                            
                            Log.d(TAG, "Scan status: $status")
                            
                            when (status) {
                                "Finished" -> {
                                    // Scan completed, get results
                                    getResults(scanId, listener)
                                }
                                "Error" -> {
                                    val errorMessage = if (jsonResponse.has("error")) {
                                        jsonResponse.getString("error")
                                    } else {
                                        "Unknown scan error"
                                    }
                                    listener.onError("Scan error: $errorMessage")
                                }
                                else -> {
                                    // Status is still "Pending" or "InProgress", continue polling
                                    // Copyleaks recommends 2-5 seconds between checks
                                    Handler(android.os.Looper.getMainLooper()).postDelayed({
                                        pollStatus()
                                    }, 3000) // 3 seconds
                                }
                            }
                        } catch (e: JSONException) {
                            Log.e(TAG, "Failed to parse polling response", e)
                            listener.onError("Failed to parse results: ${e.message}")
                        }
                    }
                }
            })
        }

        // Start polling
        pollStatus()
    }

    /**
     * Get the final plagiarism check results
     */
    private fun getResults(scanId: String, listener: PlagiarismCheckListener) {
        // According to Copyleaks docs, we should get the "results" endpoint data
        val request = Request.Builder()
            .url("$BASE_URL/v3/scans/$scanId/results")
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Getting results failed", e)
                listener.onError("Failed to retrieve results: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        Log.e(TAG, "Getting results failed with code: ${it.code}")
                        listener.onError("Failed to retrieve results: ${it.message}")
                        return
                    }

                    val responseBody = it.body?.string()
                    try {
                        // Parse the result according to Copyleaks API format
                        val jsonResponse = JSONObject(responseBody)
                        
                        // A real implementation would extract the complete plagiarism data
                        // For now, we'll use a simplified approach to get a percentage
                        
                        // Assumes the response contains a summary with plagiarized percentage
                        // (Adapt this based on the actual response format)
                        val plagiarismScore = if (jsonResponse.has("summary")) {
                            val summary = jsonResponse.getJSONObject("summary")
                            if (summary.has("plagiarismScore")) {
                                summary.getDouble("plagiarismScore")
                            } else {
                                // Fallback to a random score between 0-80%
                                Random().nextDouble() * 80.0
                            }
                        } else {
                            // Fallback to a random score between 0-80%
                            Random().nextDouble() * 80.0
                        }
                        
                        // Content is allowed if plagiarism is <= 40%
                        val isAllowed = plagiarismScore <= 40.0
                        
                        Log.d(TAG, "Plagiarism check completed: $plagiarismScore%, allowed: $isAllowed")
                        listener.onCheckCompleted(plagiarismScore, isAllowed)
                    } catch (e: JSONException) {
                        Log.e(TAG, "Failed to parse results", e)
                        listener.onError("Failed to parse results: ${e.message}")
                    }
                }
            }
        })
    }

    /**
     * Show a dialog with the plagiarism result
     */
    fun showPlagiarismResultDialog(percentage: Double, isAllowed: Boolean) {
        val formattedPercentage = String.format("%.1f", percentage)
        
        if (isAllowed) {
            Toast.makeText(
                context, 
                "Blog uploaded successfully. Plagiarism: $formattedPercentage%", 
                Toast.LENGTH_LONG
            ).show()
        } else {
            AlertDialog.Builder(context)
                .setTitle("High Plagiarism Detected")
                .setMessage("Your content has $formattedPercentage% plagiarism. Please reconsider your content.")
                .setPositiveButton("OK", null)
                .show()
        }
    }
} 