import Toybox.Activity;
import Toybox.ActivityRecording;
import Toybox.Lang;
import Toybox.System;
import Toybox.WatchUi;

// Renders a received WatchProtocol.Plan as a scrollable list, one MenuItem per exercise in the
// order the phone sent them - supersets and alternates arrive already flattened to one entry
// each, so no grouping logic is needed here. Pre-workout overview only - once a session starts
// (onSelect below) the live screen is ActiveWorkoutView, not this Menu2.
class ExerciseListMenu extends WatchUi.Menu2 {

    private var plan as WatchProtocol.Plan;

    function initialize(planArg as WatchProtocol.Plan) {
        Menu2.initialize({:title => planArg.workout + " - Day " + planArg.day});
        plan = planArg;
        var exercises = plan.exercises;
        for (var i = 0; i < exercises.size(); i += 1) {
            var exercise = exercises[i];
            addItem(new WatchUi.MenuItem(exercise.name, subtitleFor(exercise, 0), i, {}));
        }
    }

    // Mirrors RepsDisplay.kt on the phone: reps < 0 means "as many as possible", not a literal
    // rep count. sets == -1 is the separate open/challenge-set sentinel - the two are independent.
    private function subtitleFor(exercise as WatchProtocol.PlanExercise, completedSets as Number) as String {
        var setsPart = exercise.sets == -1 ? "AMRAP" : exercise.sets.toString();
        var repsPart = exercise.reps < 0 ? "MAX" : exercise.reps.toString();
        var progress = exercise.sets == -1 ? completedSets.toString() + " done" : completedSets.toString() + "/" + setsPart;
        return setsPart + "x" + repsPart + " - " + progress;
    }

}

class ExerciseListMenuDelegate extends WatchUi.Menu2InputDelegate {

    private var plan as WatchProtocol.Plan;

    function initialize(planArg as WatchProtocol.Plan) {
        Menu2InputDelegate.initialize();
        plan = planArg;
    }

    // Selecting an exercise fires on KEY_ENTER, which is the physical Start/Enter button on
    // 5-button devices (confirmed against BehaviorDelegate's docs) - so this doubles as "start the
    // workout" rather than needing a separate affordance. No onBack override: at this stage no
    // session exists yet, so the default Menu2InputDelegate pop (nothing to confirm) is correct.
    function onSelect(item as WatchUi.MenuItem) as Void {
        var index = item.getId() as Number;
        var session = ActivityRecording.createSession({
            :name => plan.workout,
            :sport => Activity.SPORT_TRAINING,
            :subSport => Activity.SUB_SPORT_STRENGTH_TRAINING
        });
        session.start();
        getApp().setActiveSession(session);

        var view = new ActiveWorkoutView(plan, index);
        WatchUi.pushView(
            view,
            new ActiveWorkoutDelegate(view, plan, session, System.getTimer()),
            WatchUi.SLIDE_LEFT
        );
    }

}
