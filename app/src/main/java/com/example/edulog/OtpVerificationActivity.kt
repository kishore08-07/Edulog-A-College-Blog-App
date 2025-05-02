package com.example.edulog

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.*
import com.google.firebase.firestore.FirebaseFirestore
import com.example.edulog.databinding.ActivityOtpVerificationBinding
import java.util.concurrent.TimeUnit
import com.google.firebase.FirebaseException

class OtpVerificationActivity : AppCompatActivity() {
    private lateinit var binding: ActivityOtpVerificationBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var verificationId: String? = null
    private var phoneNumber: String? = null
    private var name: String? = null
    private var password: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    private var countdown: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOtpVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Get data from intent
        verificationId = intent.getStringExtra("verificationId")
        phoneNumber = intent.getStringExtra("phoneNumber")
        name = intent.getStringExtra("name")
        password = intent.getStringExtra("password")

        binding.phoneNumberText.text = phoneNumber

        binding.verifyButton.setOnClickListener {
            val code = binding.otpEditText.text.toString().trim()
            if (code.length == 6) {
                verifyPhoneNumberWithCode(code)
            } else {
                binding.otpEditText.error = "Please enter valid OTP"
            }
        }

        binding.resendOtpText.setOnClickListener {
            if (resendToken != null) {
                resendVerificationCode()
            }
        }

        startResendTimer()
    }

    private fun startResendTimer() {
        binding.resendOtpText.isEnabled = false
        countdown = object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                binding.resendOtpText.text = "Resend OTP in ${millisUntilFinished / 1000} seconds"
            }

            override fun onFinish() {
                binding.resendOtpText.isEnabled = true
                binding.resendOtpText.text = "Resend OTP"
            }
        }.start()
    }

    private fun verifyPhoneNumberWithCode(code: String) {
        val credential = PhoneAuthProvider.getCredential(verificationId!!, code)
        signInWithPhoneAuthCredential(credential)
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    createUserProfile()
                } else {
                    if (task.exception is FirebaseAuthInvalidCredentialsException) {
                        binding.otpEditText.error = "Invalid code."
                    }
                    Toast.makeText(this, "Verification failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun createUserProfile() {
        val user = hashMapOf(
            "name" to name,
            "phone" to phoneNumber,
            "createdAt" to System.currentTimeMillis()
        )

        db.collection("users")
            .document(auth.currentUser?.uid ?: "")
            .set(user)
            .addOnSuccessListener {
                // Navigate to main activity
                startActivity(Intent(this, MainActivity::class.java))
                finishAffinity()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error creating profile: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun resendVerificationCode() {
        phoneNumber?.let { phone ->
            val options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(phone)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                        signInWithPhoneAuthCredential(credential)
                    }

                    override fun onVerificationFailed(e: FirebaseException) {
                        Toast.makeText(this@OtpVerificationActivity, "Verification failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }

                    override fun onCodeSent(newVerificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                        verificationId = newVerificationId
                        resendToken = token
                        Toast.makeText(this@OtpVerificationActivity, "OTP sent successfully!", Toast.LENGTH_SHORT).show()
                        startResendTimer()
                    }
                })
                .setForceResendingToken(resendToken!!)
                .build()
            PhoneAuthProvider.verifyPhoneNumber(options)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countdown?.cancel()
    }
} 