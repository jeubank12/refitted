package com.litus_animae.refitted

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.litus_animae.refitted.databinding.ActivityExerciseDetailViewBinding
import com.litus_animae.refitted.fragments.ButtonsFragment
import com.litus_animae.refitted.fragments.CommonButtonsFragment
import com.litus_animae.refitted.fragments.ConfigureButtonsDialogFragment
import com.litus_animae.refitted.fragments.ConfigureButtonsDialogFragment.ButtonLayout
import com.litus_animae.refitted.fragments.ConfigureButtonsDialogFragment.OnButtonConfigurationChangeListener
import com.litus_animae.refitted.fragments.ExerciseHistoryDialogFragment
import com.litus_animae.refitted.models.ExerciseSet
import com.litus_animae.refitted.models.ExerciseViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.util.*

@AndroidEntryPoint
class ExerciseDetailViewActivity : AppCompatActivity(), OnButtonConfigurationChangeListener {
    private var model: ExerciseViewModel? = null
    private var workout: String? = null
    private var day = 0
    private var lowerFragment: ButtonsFragment? = null
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.alternate_menu, menu)
        val switchToAlternateButton = menu.findItem(R.id.switch_to_alternate_menu_item)
        model!!.exercise.observe(this, Observer { exerciseSet: ExerciseSet -> switchToAlternateButton.isVisible = exerciseSet.hasAlternate() })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.switch_to_alternate_menu_item -> {
                Log.d(TAG, "onOptionsItemSelected: handle 'switch to alternate'")
                model!!.swapToAlternate()
                true
            }
            R.id.config_button_layout -> {
                Log.d(TAG, "onOptionsItemSelected: handle 'enable 2.5'")
                val configureButtonsDialogFragment = ConfigureButtonsDialogFragment.newInstance(lowerFragment!!.currentLayout)
                configureButtonsDialogFragment.show(supportFragmentManager, null)
                true
            }
            R.id.show_history -> {
                val history: DialogFragment = ExerciseHistoryDialogFragment()
                history.show(supportFragmentManager, null)
                true
            }
            else -> false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewmodel: ExerciseViewModel by viewModels()
        model = viewmodel
        val binding: ActivityExerciseDetailViewBinding = DataBindingUtil.setContentView(this, R.layout.activity_exercise_detail_view)
        binding.lifecycleOwner = this
        binding.locale = Locale.getDefault()
        val intent = intent
        day = intent.getIntExtra("day", 1)
        workout = intent.getStringExtra("workout")
        title = getString(R.string.app_name) + getString(R.string.colon) + " " +
                workout + " " + getString(R.string.day) + " " + day
        model!!.loadExercises(day.toString(), workout)
        binding.viewmodel = model
        binding.exerciseDetailSwipeLayout.setOnRefreshListener { model!!.loadExercises(day.toString(), workout) }
        model!!.isLoadingBool.observe(this, Observer { isLoading: Boolean ->
            binding.exerciseDetailSwipeLayout.isRefreshing = isLoading
        })
        lowerFragment = CommonButtonsFragment.newInstance()
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.buttons_fragment_frame, lowerFragment!!)
        transaction.commit()
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("RefittedMainPrefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString("lastActivity", javaClass.name)
        editor.putInt("day", day)
        editor.putString("workout", workout)
        editor.apply()
    }

    override fun onButtonConfigurationChange(layout: EnumSet<ButtonLayout>) {
        lowerFragment!!.onButtonConfigurationChange(layout)
    }

    companion object {
        private const val TAG = "ExerciseDetailViewActivity"
    }
}