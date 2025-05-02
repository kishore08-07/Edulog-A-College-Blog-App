package com.example.edulog

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.example.edulog.databinding.ActivityEmailVerificationBinding

class EmailVerificationActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEmailVerificationBinding
    private lateinit var auth: FirebaseAuth
    private var countdown: CountDownTimer? = null
    private var email: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmailVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        email = intent.getStringExtra("email")

        binding.emailText.text = email
        startVerificationCheck()

        binding.verifyButton.setOnClickListener {
            checkEmailVerification()
        }

        binding.resendEmailButton.setOnClickListener {
            resendVerificationEmail()
        }

        startResendTimer()
    }

    private fun startVerificationCheck() {
        // Check every 5 seconds if email is verified
        countdown = object : CountDownTimer(300000, 5000) { // 5 minutes total, check every 5 seconds
            override fun onTick(millisUntilFinished: Long) {
                checkEmailVerification()
            }

            override fun onFinish() {
                binding.verifyButton.isEnabled = true
                binding.statusText.text = "Verification link expired. Please resend."
            }
        }.start()
    }

    private fun checkEmailVerification() {
        val user = auth.currentUser
        user?.reload()?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                if (user.isEmailVerified) {
                    // Email verified, proceed to main activity
                    countdown?.cancel()
                    startActivity(Intent(this, MainActivity::class.java))
                    finishAffinity()
                }
            }
        }
    }

    private fun startResendTimer() {
        binding.resendEmailButton.isEnabled = false
        object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                binding.resendEmailButton.text = "Resend email in ${millisUntilFinished / 1000}s"
            }

            override fun onFinish() {
                binding.resendEmailButton.isEnabled = true
                binding.resendEmailButton.text = "Resend verification email"
            }
        }.start()
    }

    private fun resendVerificationEmail() {
        val user = auth.currentUser
        user?.sendEmailVerification()
            ?.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Verification email sent", Toast.LENGTH_SHORT).show()
                    startResendTimer()
                } else {
                    Toast.makeText(this, "Failed to send email: ${task.exception?.message}",
                        Toast.LENGTH_SHORT).show()
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        countdown?.cancel()
    }
} 