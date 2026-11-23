package com.hemanth.friday

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.app.Activity
import java.net.HttpURLConnection
import java.net.URL
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import org.json.JSONObject

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var status: TextView
    private lateinit var reply: TextView
    private lateinit var backendHint: TextView
    private lateinit var tts: TextToSpeech
    private val executor = Executors.newSingleThreadExecutor()

    // The HTTPS backend URL is saved on-device. Never store provider API keys in this APK.
    private val prefs by lazy { getSharedPreferences("friday_settings", MODE_PRIVATE) }
    private var backendUrl: String = ""
    private var backendToken: String = ""

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startListening()
        } else if (requestCode == 100) {
            say("Microphone permission is needed for voice input.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.statusText)
        reply = findViewById(R.id.replyText)
        backendHint = findViewById(R.id.backendHint)
        tts = TextToSpeech(this, this)
        backendUrl = prefs.getString("backend_url", "") ?: ""
        backendToken = prefs.getString("backend_token", "") ?: ""
        val backendUrlInput = findViewById<EditText>(R.id.backendUrlInput)
        val backendTokenInput = findViewById<EditText>(R.id.backendTokenInput)
        backendUrlInput.setText(backendUrl.removeSuffix("/chat"))
        backendTokenInput.setText(backendToken)
        findViewById<Button>(R.id.saveBackendButton).setOnClickListener {
            val entered = backendUrlInput.text.toString().trim().trimEnd('/')
            val token = backendTokenInput.text.toString().trim()
            if (entered.isNotBlank() && !entered.startsWith("https://")) {
                say("Please enter a secure HTTPS backend address.")
            } else if (entered.isNotBlank() && token.isBlank()) {
                say("Enter the private FRIDAY access token from your server settings.")
            } else {
                backendUrl = if (entered.isBlank()) "" else if (entered.endsWith("/chat")) entered else "$entered/chat"
                backendToken = token
                prefs.edit().putString("backend_url", backendUrl).putString("backend_token", backendToken).apply()
                updateBackendHint()
                say(if (backendUrl.isBlank()) "Online service disconnected." else "FRIDAY online connection saved securely on this phone.")
            }
        }

        findViewById<Button>(R.id.talkButton).setOnClickListener {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startListening()
            } else {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 100)
            }
        }

        findViewById<Button>(R.id.sendButton).setOnClickListener {
            val input = findViewById<EditText>(R.id.messageInput)
            val text = input.text.toString().trim()
            if (text.isNotEmpty()) {
                input.text.clear()
                handleMessage(text)
            }
        }

        findViewById<Button>(R.id.timeButton).setOnClickListener {
            handleMessage("what time is it")
        }

        findViewById<Button>(R.id.searchButton).setOnClickListener {
            askOnlineOrExplain("Search the web for the latest technology news and summarize the most useful current findings with sources.")
        }

        updateBackendHint()

        requestNotificationPermissionIfNeeded()
    }

    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) {
            tts.language = Locale("en", "IN")
        }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            say("Speech recognition is not available on this phone.")
            return
        }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            if (Build.VERSION.SDK_INT >= 34) {
                putExtra("android.speech.extra.ENABLE_LANGUAGE_DETECTION", true)
                putExtra("android.speech.extra.LANGUAGE_DETECTION_ALLOWED_LANGUAGES",
                    arrayOf("te-IN", "en-IN"))
            }
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to FRIDAY")
        }
        status.text = "LISTENING..."
        recognizer.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { status.text = "THINKING..." }
            override fun onError(error: Int) {
                status.text = "READY"
                recognizer.destroy()
                say("I couldn't hear that clearly. Please try again.")
            }
            override fun onResults(results: Bundle?) {
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                recognizer.destroy()
                status.text = "PROCESSING..."
                if (!spoken.isNullOrBlank()) handleMessage(spoken)
                else say("Please say that again.")
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer.startListening(intent)
    }

    private fun handleMessage(raw: String) {
        val message = raw.trim()
        val lower = message.lowercase(Locale.ROOT)

        when {
            lower.contains("time") || lower.contains("samayam") || lower.contains("సమయం") -> {
                val now = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
                say("The time is $now")
            }
            lower.contains("date") || lower.contains("today's date") || lower == "today" || lower.contains("తేదీ") -> {
                val today = DateFormat.getDateInstance(DateFormat.FULL).format(Date())
                say("Today is $today")
            }
            lower.contains("battery") -> {
                val battery = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val level = battery?.getIntExtra("level", -1) ?: -1
                val scale = battery?.getIntExtra("scale", -1) ?: -1
                val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
                say(if (percent >= 0) "Your phone battery is $percent percent." else "I cannot read the battery level.")
            }
            lower.startsWith("remind me in ") || lower.startsWith("remind me after ") -> {
                scheduleRelativeReminder(message)
            }
            lower.contains("open youtube") -> openApp("com.google.android.youtube", "YouTube")
            lower.contains("open settings") -> {
                startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                say("Opening settings.")
            }
            lower.startsWith("search ") || lower.startsWith("google ") ||
                lower.contains("latest news") || lower.contains("live score") ||
                lower.contains("weather") || lower.contains("today's") ||
                lower.contains("today ") || lower.contains("latest") -> {
                askOnlineOrExplain(message)
            }
            lower.contains("stop speaking") || lower == "stop" -> {
                tts.stop()
                status.text = "READY"
            }
            else -> askOnlineOrExplain(message)
        }
    }

    private fun scheduleRelativeReminder(message: String) {
        val regex = Regex("""(?i)remind me (?:in|after)\s+(\d+)\s+(second|seconds|minute|minutes|hour|hours)\s*(.*)""")
        val match = regex.find(message)
        if (match == null) {
            say("Try saying: remind me in 10 minutes to study.")
            return
        }
        val amount = match.groupValues[1].toLongOrNull() ?: 0L
        val unit = match.groupValues[2].lowercase(Locale.ROOT)
        val task = match.groupValues[3].ifBlank { "Your reminder" }
        val multiplier = when {
            unit.startsWith("second") -> 1_000L
            unit.startsWith("hour") -> 3_600_000L
            else -> 60_000L
        }
        val delay = (amount * multiplier).coerceAtMost(7L * 24 * 60 * 60 * 1000)
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val reminderIntent = Intent(this, ReminderReceiver::class.java).putExtra("task", task)
        val pending = PendingIntent.getBroadcast(
            this, (System.currentTimeMillis() % Int.MAX_VALUE).toInt(), reminderIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = System.currentTimeMillis() + delay
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
            say("Reminder set for $task in $amount $unit.")
        } catch (e: Exception) {
            say("I couldn't schedule that reminder. Please check alarm permissions.")
        }
    }

    private fun updateBackendHint() {
        backendHint.text = if (backendUrl.isBlank()) {
            "Online search + AI is not connected. Deploy the secure backend, then paste its HTTPS address above."
        } else {
            "Online search + AI backend saved. FRIDAY can research and speak answers when connected."
        }
    }

    private fun askOnlineOrExplain(message: String) {
        if (backendUrl.isBlank()) {
            say("Online search is not connected yet. Add your deployed FRIDAY HTTPS backend address in the connection box below.")
            return
        }
        status.text = "SEARCHING THE WEB..."
        executor.execute {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(backendUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20000
                    readTimeout = 65000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("X-FRIDAY-TOKEN", backendToken)
                }
                val body = JSONObject().put("message", message).put("language", "te-en").toString()
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val responseCode = connection.responseCode
                val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
                val responseText = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
                val json = JSONObject(responseText)
                if (responseCode !in 200..299) {
                    val detail = json.optString("detail", "Online service error ($responseCode).")
                    runOnUiThread { say(detail) }
                } else {
                    val response = json.optString("reply", "I received an empty response.")
                    val sources = json.optJSONArray("sources")
                    val sourceLines = mutableListOf<String>()
                    if (sources != null) {
                        for (i in 0 until sources.length()) {
                            val source = sources.optString(i)
                            if (source.startsWith("https://")) sourceLines.add(source)
                        }
                    }
                    val displayText = if (sourceLines.isNotEmpty()) response + "\n\nSources:\n" + sourceLines.take(3).joinToString("\n") else response
                    runOnUiThread { say(displayText, response) }
                }
            } catch (e: Exception) {
                runOnUiThread { say("I couldn't reach FRIDAY online. Check your internet connection and HTTPS backend address.") }
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun openSearch(query: String) {
        val q = if (query.isBlank()) "technology news" else query
        val browser = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com/search?q=" + android.net.Uri.encode(q)))
        try {
            startActivity(browser)
            say("Opening web search for $q.")
        } catch (_: Exception) {
            say("No browser is available on this phone.")
        }
    }

    private fun openApp(packageName: String, appName: String) {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        if (launch != null) {
            startActivity(launch)
            say("Opening $appName.")
        } else if (appName == "YouTube") {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com")))
                say("Opening YouTube in your browser.")
            } catch (_: Exception) {
                say("I couldn't open YouTube. Please check your browser.")
            }
        } else {
            say("$appName is not installed.")
        }
    }

    private fun say(text: String, spokenText: String = text) {
        reply.text = text
        status.text = "FRIDAY • READY"
        if (::tts.isInitialized) {
            val hasTelugu = spokenText.any { it.code in 0x0C00..0x0C7F }
            tts.language = if (hasTelugu) Locale("te", "IN") else Locale("en", "IN")
            tts.speak(spokenText, TextToSpeech.QUEUE_FLUSH, null, "friday_reply")
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        executor.shutdown()
        super.onDestroy()
    }
}
