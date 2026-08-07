import Toybox.Communications;
import Toybox.Lang;
import Toybox.System;
import Toybox.WatchUi;

// Renders a received WatchProtocol.Plan as a scrollable list, one MenuItem per exercise in the
// order the phone sent them - supersets and alternates arrive already flattened to one entry
// each, so no grouping logic is needed here.
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

    // Called by ExerciseListMenuDelegate after a SET_DONE transmit, so the watch's own display
    // agrees with what it just told the phone.
    function updateProgress(index as Number, completedSets as Number) as Void {
        var item = getItem(index);
        if (item == null) {
            return;
        }
        item.setSubLabel(subtitleFor(plan.exercises[index], completedSets));
        updateItem(item, index);
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

    private var menu as ExerciseListMenu;
    private var plan as WatchProtocol.Plan;
    private var sessionStartMs as Number;
    private var completedSets as Array<Number>;
    private var seq as Number;

    function initialize(menuArg as ExerciseListMenu, planArg as WatchProtocol.Plan, sessionStartMsArg as Number) {
        Menu2InputDelegate.initialize();
        menu = menuArg;
        plan = planArg;
        sessionStartMs = sessionStartMsArg;
        seq = 0;
        completedSets = new Array<Number>[plan.exercises.size()];
        for (var i = 0; i < completedSets.size(); i += 1) {
            completedSets[i] = 0;
        }
    }

    // Marks one set of the selected exercise complete and relays it to the phone. v1 sends the
    // plan's suggested reps/weight as-is - no on-watch adjustment (Open Question 3 in
    // PLAN-garmin.md defers that past v1). System.getTimer() (ms since boot) minus the reference
    // captured when PLAN arrived is elapsedMs - the watch never sends a wall-clock timestamp, the
    // phone (data/.../device/WatchSession.kt) is the sole time authority.
    function onSelect(item as WatchUi.MenuItem) as Void {
        var index = item.getId() as Number;
        var exercise = plan.exercises[index];
        completedSets[index] = completedSets[index] + 1;
        seq += 1;

        var elapsedMs = System.getTimer() - sessionStartMs;
        Communications.transmit(
            WatchProtocol.encodeSetDone(
                seq, index, completedSets[index], exercise.reps, exercise.weightCenti, elapsedMs
            ),
            {},
            new SetDoneTransmitListener()
        );

        menu.updateProgress(index, completedSets[index]);
    }

}

// transmit() takes a ConnectionListener instance, not a Method reference. SET_DONE has no
// ack/retry in Phase 2 - offline buffering and honouring ACK to trim a resend queue is Phase 4 -
// so a failed transmit here is simply lost until the user re-selects the exercise; nothing to
// recover in these callbacks yet.
class SetDoneTransmitListener extends Communications.ConnectionListener {

    function initialize() {
        Communications.ConnectionListener.initialize();
    }

    function onComplete() as Void {
    }

    function onError() as Void {
    }

}
