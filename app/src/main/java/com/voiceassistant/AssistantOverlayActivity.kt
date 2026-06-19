package com.voiceassistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.voiceassistant.databinding.ActivityOverlayBinding
import java.util.Locale

/**
 * Triggered by long-pressing the Home button (via ASSIST intent).
 * Stays open and hands-free: listens -> responds -> re-listens, forever,
 * until the user taps outside or presses back.
 */
class AssistantOverlayActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityOverlayBinding
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var tts: TextToSpeech
    private var ttsReady = false
    private var isListening = false
    private var pendingFollowUp: String = ""
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOverlayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tts = TextToSpeech(this, this)
        binding.tvOverlayName.text = Prefs.getAssistantName(this)

        binding.btnMicOverlay.setOnClickListener {
            if (!isListening) startListening()
        }

        // Fix #3: tapping outside dismisses; tapping the card itself does NOT close
        binding.root.setOnClickListener { finish() }
        binding.overlayCard.setOnClickListener { /* consume click, keep open */ }

        handler.postDelayed({ startListening() }, 500)
    }

    override fun onResume() {
        super.onResume()
        // Fix #3: when user returns to this overlay after an app opened (e.g. WhatsApp,
        // Alarm, YouTube), resume listening automatically instead of staying frozen
        if (!isListening) {
            handler.postDelayed({ if (!isListening) startListening() }, 400)
        }
    }

    override fun onPause() {
        super.onPause()
        // Don't destroy recognizer here — we want it to survive brief app switches
        // (e.g. opening Alarm clock and coming back). Just stop active listening.
        cancelListening()
    }

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Microphone permission needed", Toast.LENGTH_SHORT).show()
            return
        }
        if (isListening) return
        isListening = true

        binding.tvOverlayStatus.text = "Listening"
        binding.tvOverlaySpoken.text = if (pendingFollowUp.isNotBlank()) "Waiting for your answer..." else ""
        binding.overlayWaveform.setActive(true)
        val pulse = AnimationUtils.loadAnimation(this, R.anim.pulse)
        binding.micRippleOverlay.startAnimation(pulse)

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) { binding.tvOverlayStatus.text = "Listening" }
            override fun onBeginningOfSpeech() { binding.tvOverlayStatus.text = "Hearing you" }
            override fun onRmsChanged(rmsdB: Float) {
                binding.overlayWaveform.updateAmplitude((rmsdB / 10f).coerceIn(0f, 1f))
            }
            override fun onPartialResults(partial: Bundle?) {
                val t = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!t.isNullOrBlank()) binding.tvOverlaySpoken.text = t
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                binding.overlayWaveform.setActive(false)
                binding.micRippleOverlay.clearAnimation()
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (spoken.isNullOrBlank()) {
                    handleNoMatch()
                } else {
                    handleCommand(spoken)
                }
            }
            override fun onError(error: Int) {
                isListening = false
                binding.overlayWaveform.setActive(false)
                binding.micRippleOverlay.clearAnimation()
                handleNoMatch()
            }
            override fun onEndOfSpeech() {
                binding.tvOverlayStatus.text = "Processing"
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
        }
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            isListening = false
        }
    }

    private fun cancelListening() {
        isListening = false
        try { speechRecognizer?.stopListening() } catch (_: Exception) {}
        try { speechRecognizer?.cancel() } catch (_: Exception) {}
        binding.overlayWaveform.setActive(false)
        binding.micRippleOverlay.clearAnimation()
    }

    // Fix #5: say "please say again" ONCE, then re-listen only after that
    // utterance finishes — prevents the listen/fail/listen rapid loop
    private fun handleNoMatch() {
        binding.tvOverlayStatus.text = "Ready"
        binding.tvOverlaySpoken.text = "Please say again"
        speakThenListen("Please say again", null)
    }

    private fun handleCommand(spoken: String) {
        val input = if (pendingFollowUp.isNotBlank())
            "__wa_select__:$pendingFollowUp|||$spoken"
        else spoken

        binding.tvOverlaySpoken.text = "\"$spoken\""
        binding.tvOverlayStatus.text = "Processing"

        val result = CommandProcessor.process(this, input)
        pendingFollowUp = if (result.needsFollowUp) result.followUpPrompt else ""

        binding.tvOverlayResponse.text = result.reply.replace(Regex("[^\\p{L}\\p{N}\\p{P}\\p{Z}\\n]"), "").trim()
        Prefs.saveHistoryItem(this, spoken, result.reply)

        speakThenListen(result.reply, result.action)
    }

    // Fix #3: never call finish() here — overlay stays open.
    // Fix #5: speak fully, THEN run the action, THEN re-listen — one clean cycle.
    private fun speakThenListen(text: String, action: (() -> Unit)?) {
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
                    handler.postDelayed({
                        if (!isListening) startListening()
                    }, 800)
                }
            }
            override fun onError(utteranceId: String?) {
                handler.post {
                    action?.invoke()
                    handler.postDelayed({ if (!isListening) startListening() }, 800)
                }
            }
        })

        tts.setLanguage(Locale.ENGLISH)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "overlay_reply_${System.currentTimeMillis()}")
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) tts.setLanguage(Locale.ENGLISH)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        speechRecognizer?.destroy()
        tts.shutdown()
    }
}
