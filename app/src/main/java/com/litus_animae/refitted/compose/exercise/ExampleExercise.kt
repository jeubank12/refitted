package com.litus_animae.refitted.compose.exercise

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.litus_animae.refitted.models.Exercise
import com.litus_animae.refitted.models.ExerciseSet
import com.litus_animae.refitted.models.RoomExerciseSet
import kotlinx.coroutines.flow.MutableStateFlow

val exampleExercise = Exercise(
    workout = "Refitted Exercise",
    id = "1.1",
    description = "These are the instructions for how to do the exercise. " +
            "There could be a lot of information present here. Begin by starting the exercise, " +
            "slowly performing the exercise to fully gain the benfits of the exercise. " +
            "As you perform the exercise, consciously think about the motion you are performing. " +
            "New line \n \n Start the exercise over again once you have finished it. " +
            "Continue repeating these exercises for all the reps in all the sets. " +
            "blah blah blah filling up the space until its so overflowed that its going to " +
            "burst so that we can see what will happen. Even more stuff to fit into the " +
            "space come on just overflow already why don't you. now we are getting there, " +
            "yeah come on just a little further wow there is just so much text in this box " +
            "that it fills up so much space it's impossible to believe. \n\nDid you know if there" +
            "is too much text that you might not be able to see it? I don't know if that is true" +
            " but if it is then the developer should really do something about it so that all sizes" +
            " of screens can use this app"
)

val exampleExerciseSet =
    ExerciseSet(
        RoomExerciseSet(
            workout = "Refitted Exercise",
            day = "1",
            step = "1",
            name = "A_Do Things",
            note = "These are instructions that are specific to this particular set. " +
                    "They are usually fairly short",
            reps = -1,
            sets = 3,
            isToFailure = true,
            rest = 60,
            repsUnit = "",
            repsRange = 0
        ), MutableStateFlow(exampleExercise)
    )

class ExampleExerciseProvider : PreviewParameterProvider<ExerciseSet> {
    override val values = sequenceOf(exampleExerciseSet)
}