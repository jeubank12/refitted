package com.litus_animae.refitted

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.litus_animae.refitted.databinding.CalendarRowLayoutBinding
import java.lang.ref.WeakReference
import java.util.*
import kotlin.math.ceil

class CalendarAdapter(private val daysCount: Int, private val planSwitch: WeakReference<Switch>) : RecyclerView.Adapter<CalendarAdapter.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding: CalendarRowLayoutBinding = DataBindingUtil.inflate(inflater,
                R.layout.calendar_row_layout, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.binding.daysVisible = daysCount - position * 7
        holder.binding.minDay = position * 7
        holder.binding.locale = Locale.getDefault()
    }

    override fun getItemCount(): Int {
        return ceil(daysCount / 7.0).toInt()
    }

    inner class ViewHolder(val binding: CalendarRowLayoutBinding) : RecyclerView.ViewHolder(binding.root), View.OnClickListener {
        override fun onClick(v: View) {
            val position = adapterPosition * 7
            val intent = Intent(v.context, ExerciseDetailViewActivity::class.java)
            val mPlanSwitch = planSwitch.get()
            if (mPlanSwitch != null && mPlanSwitch.isChecked) {
                intent.putExtra("workout", "Inferno Size")
            } else {
                intent.putExtra("workout", "AX1")
            }
            when (v.id) {
                R.id.day1button -> intent.putExtra("day", position + 1)
                R.id.day2button -> intent.putExtra("day", position + 2)
                R.id.day3button -> intent.putExtra("day", position + 3)
                R.id.day4button -> intent.putExtra("day", position + 4)
                R.id.day5button -> intent.putExtra("day", position + 5)
                R.id.day6button -> intent.putExtra("day", position + 6)
                R.id.day7button -> intent.putExtra("day", position + 7)
                else -> return
            }
            v.context.startActivity(intent)
        }

        init {
            binding.day1button.setOnClickListener(this)
            binding.day2button.setOnClickListener(this)
            binding.day3button.setOnClickListener(this)
            binding.day4button.setOnClickListener(this)
            binding.day5button.setOnClickListener(this)
            binding.day6button.setOnClickListener(this)
            binding.day7button.setOnClickListener(this)
        }
    }

}