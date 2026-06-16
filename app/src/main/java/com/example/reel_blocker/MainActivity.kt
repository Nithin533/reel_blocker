package com.example.reel_blocker

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import com.example.reel_blocker.ui.theme.Reel_blockerTheme

class MainActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences
    private var countDownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("blocker_prefs", MODE_PRIVATE)

        // If opened from a shared link (WhatsApp/Telegram etc.)
        if (intent?.action == Intent.ACTION_VIEW && intent?.data != null) {
            prefs.edit().putLong("allow_until", System.currentTimeMillis() + 5 * 60 * 1000).apply()
            // Open the actual link in YouTube/Instagram
            val newIntent = Intent(Intent.ACTION_VIEW, intent?.data).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(newIntent)
            finish()
            return
        }

        enableEdgeToEdge()
        setContent {
            Reel_blockerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->

                    var timerLabel by remember { mutableStateOf("Allow for 5 mins (for shared links)") }
                    var timerActive by remember { mutableStateOf(false) }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🚫 Reel Blocker", fontSize = 24.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Blocks Reels on Instagram\nand Shorts on YouTube",
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        // Go to Accessibility Settings
                        Button(onClick = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }) {
                            Text("Enable Accessibility Service")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Temporary whitelist button for shared links
                        Button(
                            onClick = {
                                if (!timerActive) {
                                    // Allow Shorts/Reels for 5 minutes
                                    prefs.edit()
                                        .putLong("allow_until", System.currentTimeMillis() + 5 * 60 * 1000)
                                        .apply()
                                    timerActive = true

                                    countDownTimer?.cancel()
                                    countDownTimer = object : CountDownTimer(5 * 60 * 1000L, 1000) {
                                        override fun onTick(millisUntilFinished: Long) {
                                            val secs = (millisUntilFinished / 1000).toInt()
                                            val m = secs / 60
                                            val s = secs % 60
                                            timerLabel = "Allowed — ${m}m ${s}s remaining"
                                        }
                                        override fun onFinish() {
                                            prefs.edit().putLong("allow_until", 0).apply()
                                            timerLabel = "Allow for 5 mins (for shared links)"
                                            timerActive = false
                                        }
                                    }.start()
                                } else {
                                    // Cancel early
                                    countDownTimer?.cancel()
                                    prefs.edit().putLong("allow_until", 0).apply()
                                    timerLabel = "Allow for 5 mins (for shared links)"
                                    timerActive = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (timerActive) Color(0xFF388E3C) else Color(0xFF1976D2)
                            )
                        ) {
                            Text(timerLabel, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}