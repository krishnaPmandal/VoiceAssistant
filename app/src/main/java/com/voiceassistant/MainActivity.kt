package com.voiceassistant

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.voiceassistant.databinding.ActivityMainBinding
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var tts: TextToSpeech
    private var isListening = false
    private var ttsReady = false
    private var pendingFollowUp: String = ""
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tts = TextToSpeech(this, this)
        setupUI()
        applyTheme()
        checkPermissions()
        binding.tvAssistantName.text = Prefs.getAssistantName(this)
    }

    private fun setupUI() {
        binding.btnMic.setOnClickListener { toggleListening() }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        binding.chipAlarm.setOnClickListener { processQuick("set alarm for 7 AM") }
        binding.chipMusic.setOnClickListener { processQuick("play music") }
        binding.chipWhatsApp.setOnClickListener { processQuick("open WhatsApp") }
        binding.chipSearch.setOnClickListener { processQuick("search on Google") }
    }

    private fun applyTheme() {
        when (Prefs.getTheme(this)) {
            "pink" -> binding.rootLayout.setBackgroundColor(Color.parseColor("#0F0A14"))
            "blue" -> binding.rootLayout.setBackgroundColor(Color.parseColor("#0A0A1F"))
            else   -> binding.rootLayout.setBackgroundColor(Color.parseColor("#0A0A14"))
        }
    }

    override fun onResume() {
        super.onResume()
        binding.tvAssistantName.text = Prefs.getAssistantName(this)
        applyTheme()
        // Fix #5: resume listening whenever app comes back to foreground
        // (e.g. after returning from an opened app)
        if (!isListening) {
            handler.postDelayed({ if (!isListening) startListening() }, 500)
        }
    }

    override fun onPause() {
        super.onPause()
        // stop mic while app isn't visible to save battery / avoid conflicts
        stopListening()
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        }
    }

    private fun toggleListening() {
        if (isListening) stopListening() else startListening()
    }

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Microphone permission needed", Toast.LENGTH_SHORT).show()
            return
        }
        if (isListening) return

        isListening = true
        binding.btnMic.setBackgroundResource(R.drawable.mic_bg_active)
        binding.tvStatus.text = "Listening"
        binding.tvCommandText.text = if (pendingFollowUp.isNotBlank()) "Waiting for your choice..." else "Speak now"
        binding.waveformView.setActive(true)
        val pulseAnim = AnimationUtils.loadAnimation(this, R.anim.pulse)
        binding.micRipple.startAnimation(pulseAnim)

        if (::speechRecognizer.isInitialized) {
            try { speechRecognizer.destroy() } catch (_: Exception) {}
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) { binding.tvStatus.text = "Listening" }
            override fun onBeginningOfSpeech() { binding.tvStatus.text = "Hearing you" }
            override fun onRmsChanged(rmsdB: Float) {
                binding.waveformView.updateAmplitude((rmsdB / 10f).coerceIn(0f, 1f))
            }
            override fun onPartialResults(partial: Bundle?) {
                val t = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!t.isNullOrBlank()) binding.tvCommandText.text = "\"$t\""
            }
            override fun onResults(results: Bundle?) {
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                isListening = false
                binding.btnMic.setBackgroundResource(R.drawable.mic_bg_idle)
                binding.micRipple.clearAnimation()
                binding.waveformView.setActive(false)
                if (spoken.isNullOrBlank()) {
                    handleNoMatch()
                } else {
                    onCommandReceived(spoken)
                }
            }
            override fun onError(error: Int) {
                isListening = false
                binding.btnMic.setBackgroundResource(R.drawable.mic_bg_idle)
                binding.micRipple.clearAnimation()
                binding.waveformView.setActive(false)
                // Fix #4: on any failure, say "please say again" and re-listen automatically
                handleNoMatch()
            }
            override fun onEndOfSpeech() {
                binding.tvStatus.text = "Processing"
            }
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEvent(t: Int, p: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        try {
            speechRecognizer.startListening(intent)
        } catch (e: Exception) {
            isListening = false
        }
    }

    private fun stopListening() {
        isListening = false
        if (::speechRecognizer.isInitialized) {
            try { speechRecognizer.stopListening() } catch (_: Exception) {}
            try { speechRecognizer.cancel() } catch (_: Exception) {}
        }
        binding.btnMic.setBackgroundResource(R.drawable.mic_bg_idle)
        binding.micRipple.clearAnimation()
        binding.waveformView.setActive(false)
    }

    // Fix #4: "please say again" when nothing heard / error
    private fun handleNoMatch() {
        binding.tvStatus.text = "Ready"
        binding.tvCommandText.text = "Please say again"
        speakThenListen("Please say again", null, false)
    }

    private fun onCommandReceived(spoken: String) {
        val input = if (pendingFollowUp.isNotBlank())
            "__wa_select__:$pendingFollowUp|||$spoken"
        else spoken

        binding.tvCommandText.text = "\"$spoken\""
        binding.tvStatus.text = "Processing"

        val result = CommandProcessor.process(this, input)
        pendingFollowUp = if (result.needsFollowUp) result.followUpPrompt else ""

        showResponse(result.reply)
        speakThenListen(result.reply, result.action, result.needsFollowUp)

        Prefs.saveHistoryItem(this, spoken, result.reply)
    }

    private fun processQuick(cmd: String) {
        binding.tvCommandText.text = "\"$cmd\""
        binding.tvStatus.text = "Processing"
        val result = CommandProcessor.process(this, cmd)
        pendingFollowUp = if (result.needsFollowUp) result.followUpPrompt else ""
        showResponse(result.reply)
        speakThenListen(result.reply, result.action, result.needsFollowUp)
        Prefs.saveHistoryItem(this, cmd, result.reply)
    }

    // Fix #2 + #3 + #5: speak, run action, ALWAYS auto-restart mic after (hands-free loop)
    private fun speakThenListen(text: String, action: (() -> Unit)?, needsFollowUp: Boolean) {
        if (!ttsReady) {
            action?.invoke()
            handler.postDelayed({ if (!isListening) startListening() }, 1000)
            return
        }

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                handler.post {
                    action?.invoke()
                    // Fix #5: always re-show listening state after task completes,
                    // whether or not an external app was opened
                    handler.postDelayed({
                        if (!isListening) startListening()
                    }, 700)
                }
            }
            override fun onError(utteranceId: String?) {
                handler.post {
                    action?.invoke()
                    handler.postDelayed({ if (!isListening) startListening() }, 700)
                }
            }
        })

        tts.setLanguage(Locale.ENGLISH)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "va_reply")
    }

    private fun showResponse(text: String) {
        val clean = text.replace(Regex("[^\\p{L}\\p{N}\\p{P}\\p{Z}\\n]"), "").trim()
        binding.responseCard.visibility = View.VISIBLE
        binding.tvResponse.text = clean
        ObjectAnimator.ofFloat(binding.responseCard, "alpha", 0f, 1f).apply { duration = 300 }.start()
    }

    override fun onInit(status: Int) {
        ttsReady = (status == TextToSpeech.SUCCESS)
        if (ttsReady) tts.setLanguage(Locale.ENGLISH)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        if (::speechRecognizer.isInitialized) speechRecognizer.destroy()
        tts.shutdown()
    }
}
