import Toybox.Activity;
import Toybox.ActivityRecording;
import Toybox.Communications;
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
    // workout" rather than needing a separate affordance. Picking an exercise here means you're
    // about to do it now, not browsing - the session starts in WORK (lap timer running) for that
    // exercise rather than the view's own default REST/ready state.
    function onSelect(item as WatchUi.MenuItem) as Void {
        var index = item.getId() as Number;
        var session = ActivityRecording.createSession({
            :name => plan.workout,
            :sport => Activity.SPORT_TRAINING,
            :subSport => Activity.SUB_SPORT_STRENGTH_TRAINING
        });
        session.start();
        getApp().setActiveSession(session);

        var sessionStartMs = System.getTimer();
        var view = new ActiveWorkoutView(plan, index, sessionStartMs);
        view.enterWork();
        WatchUi.pushView(
            view,
            new ActiveWorkoutDelegate(view, plan, session, sessionStartMs),
            WatchUi.SLIDE_LEFT
        );
    }

    // This is the app's base view (connectiqApp.onPhoneMessage got here via switchToView), so the
    // default Menu2InputDelegate.onBack (pop the active view) exits the whole app - backing out
    // before ever selecting an exercise, with no ActivityRecording session ever created. Without
    // this override that silent exit left the phone stuck showing WatchState.Active (the checkmark
    // icon), since only ExitConfirmMenu's Save/Discard path used to send SESSION_ENDED - there was
    // no session to end, but the phone still needs telling so it can accept a new plan.
    function onBack() as Void {
        Communications.transmit(WatchProtocol.encodeSessionEnded(0), {}, new SessionEndedTransmitListener());
        WatchUi.popView(WatchUi.SLIDE_IMMEDIATE);
    }

}
