package com.litus_animae.refitted.compose

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.litus_animae.refitted.models.Exercise
import com.litus_animae.refitted.models.ExerciseSet
import com.litus_animae.refitted.models.RoomExerciseSet
import kotlinx.coroutines.flow.MutableStateFlow

class ExampleExerciseProvider : PreviewParameterProvider<ExerciseSet> {
    val exercise = Exercise(
        workout = "AX1",
        id = "1.1",
        description = "Attach a rope to a high pulley or drape a band over a pullup bar and grab an end in each hand with your elbows bent and held close to your chest to start. Begin by bringing your hands down and across your body, extending the elbows straight to fully contract the triceps. As you bring the rope down, consciously contract your core as well to help with the rotation of the body and increase your trunk stability at the same time. Return the hands to the starting position to repeat the same movement over the opposite leg on the next rep. Continue repeating this alternating pattern for the prescribed number of reps per set. blah blah blah filling up the space until its so overflowed that its going to burst so that we can see what will happen. Even more stuff to fit into the space come on just overflow already why don't you. now we are getting there, yeah come on just a little further wow there is just so much text in this box that it fills up so much space its impossible to believe"
    )
    override val values = sequenceOf(
        ExerciseSet(
            RoomExerciseSet(
                workout = "AX1",
                day = "1",
                step = "1",
                name = "A_Do Things",
                note = "Alternate reps (10-12 in each direction) for each completed set. If using a resistance band, step further away to increase tension on band and difficulty of exercise",
                reps = -1,
                sets = 3,
                isToFailure = true,
                rest = 60,
                repsUnit = "",
                repsRange = 0
            ), MutableStateFlow(exercise)
        )
    )
}