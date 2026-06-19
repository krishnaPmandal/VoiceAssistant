package com.voiceassistant

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.voiceassistant.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Load saved values
        binding.etAssistantName.setText(Prefs.getAssistantName(this))
        when (Prefs.getTheme(this)) {
            "pink" -> binding.rbPink.isChecked = true
            "blue" -> binding.rbBlue.isChecked = true
            else   -> binding.rbGlassmorphism.isChecked = true
        }

        binding.btnBack.setOnClickListener { finish() }

        binding.btnSave.setOnClickListener {
            val name = binding.etAssistantName.text?.toString()?.trim()
            if (!name.isNullOrBlank()) {
                Prefs.setAssistantName(this, name)
            }
            val theme = when {
                binding.rbPink.isChecked -> "pink"
                binding.rbBlue.isChecked -> "blue"
                else -> "glass"
            }
            Prefs.setTheme(this, theme)
            Toast.makeText(this, "Settings saved! ✅", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
