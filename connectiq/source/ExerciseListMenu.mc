import Toybox.Lang;
import Toybox.WatchUi;

// Renders a received WatchProtocol.Plan as a scrollable list, one MenuItem per exercise in the
// order the phone sent them - supersets and alternates arrive already flattened to one entry
// each, so no grouping logic is needed here.
class ExerciseListMenu extends WatchUi.Menu2 {

    function initialize(plan as WatchProtocol.Plan) {
        Menu2.initialize({:title => plan.workout + " - Day " + plan.day});
        var exercises = plan.exercises;
        for (var i = 0; i < exercises.size(); i += 1) {
            var exercise = exercises[i];
            addItem(new WatchUi.MenuItem(exercise.name, subtitleFor(exercise), i, {}));
        }
    }

    // Mirrors RepsDisplay.kt on the phone: reps < 0 means "as many as possible", not a literal
    // rep count. sets == -1 is the separate open/challenge-set sentinel - the two are independent.
    private function subtitleFor(exercise as WatchProtocol.PlanExercise) as String {
        var setsPart = exercise.sets == -1 ? "AMRAP" : exercise.sets.toString();
        var repsPart = exercise.reps < 0 ? "MAX" : exercise.reps.toString();
        return setsPart + "x" + repsPart;
    }

}

class ExerciseListMenuDelegate extends WatchUi.Menu2InputDelegate {

    function initialize() {
        Menu2InputDelegate.initialize();
    }

    // Selecting an exercise is a no-op in Phase 1 (one-way plan push only) - completion tracking
    // (SET_DONE) is Phase 2.
    function onSelect(item as WatchUi.MenuItem) as Void {
    }

}
