package com.voiceassistant

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.voiceassistant.databinding.ActivityHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        binding.btnClearHistory.setOnClickListener {
            Prefs.clearHistory(this)
            loadHistory()
            Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show()
        }

        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        loadHistory()
    }

    private fun loadHistory() {
        val items = Prefs.getHistory(this)
        if (items.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.rvHistory.visibility = View.GONE
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.rvHistory.visibility = View.VISIBLE
            binding.rvHistory.adapter = HistoryAdapter(items)
        }
    }

    inner class HistoryAdapter(
        private val items: List<Triple<Long, String, String>>
    ) : RecyclerView.Adapter<HistoryAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvCommand: TextView = v.findViewById(R.id.tvCommand)
            val tvReply: TextView = v.findViewById(R.id.tvReply)
            val tvTime: TextView = v.findViewById(R.id.tvTime)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false)
        )

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val (ts, cmd, reply) = items[position]
            holder.tvCommand.text = "🎤 $cmd"
            holder.tvReply.text = reply
            val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
            holder.tvTime.text = sdf.format(Date(ts))
        }
    }
}
