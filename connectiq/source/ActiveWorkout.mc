import Toybox.ActivityRecording;
import Toybox.Communications;
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.System;
import Toybox.WatchUi;

// The in-workout screen. Distinct from ExerciseListMenu (the pre-workout overview) because its
// buttons need distinct meaning from a generic Menu2 "select" - BehaviorDelegate/Menu2InputDelegate
// collapse everything into onSelect/onBack, so this extends WatchUi.InputDelegate directly and
// switches on KeyEvent.getKey().
//
// On-device (Forerunner 945/265s), confirmed by testing rather than assumed: this hardware exposes
// only START/STOP and a single BACK/LAP button (no distinct 5th key) to a third-party watch app -
// KEY_START is never actually delivered here (the physical Start/Enter button sends KEY_ENTER
// instead, matching the SDK's Attention sample checking both), and the physical BACK/LAP button
// always sends KEY_ESC, never KEY_LAP. So the mapping is START/STOP (KEY_ENTER) opens the
// exit-confirm Save/Discard menu, and BACK/LAP (KEY_ESC) completes the current set. There is no
// pause/resume in v1 - with only two distinguishable buttons reaching the app, there's no third
// one to spare for it; the session simply records start-to-finish.
class ActiveWorkoutView extends WatchUi.View {

    var plan as WatchProtocol.Plan;
    var currentIndex as Number;
    var completedSets as Array<Number>;
    // What the user actually confirmed last time for this exercise, per index - null until a set
    // is confirmed. Prefilling the adjust Picker from this instead of the plan's suggestion means
    // an adjustment you make on set 1 carries forward to set 2, rather than resetting every time.
    var lastReps as Array<Number?>;
    var lastWeightCenti as Array<Number?>;

    function initialize(plan as WatchProtocol.Plan, startIndex as Number) {
        View.initialize();
        self.plan = plan;
        self.currentIndex = startIndex;
        completedSets = new Array<Number>[plan.exercises.size()];
        lastReps = new Array<Number?>[plan.exercises.size()];
        lastWeightCenti = new Array<Number?>[plan.exercises.size()];
        for (var i = 0; i < completedSets.size(); i += 1) {
            completedSets[i] = 0;
            lastReps[i] = null;
            lastWeightCenti[i] = null;
        }
    }

    function onLayout(dc as Dc) as Void {
    }

    function onShow() as Void {
    }

    function onUpdate(dc as Dc) as Void {
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_BLACK);
        dc.clear();

        var exercise = plan.exercises[currentIndex];
        var centerX = dc.getWidth() / 2;
        var y = dc.getHeight() / 4;
        var lineHeight = Graphics.getFontHeight(Graphics.FONT_MEDIUM);

        dc.drawText(centerX, y, Graphics.FONT_MEDIUM, exercise.name, Graphics.TEXT_JUSTIFY_CENTER);
        y += lineHeight * 1.5;
        dc.drawText(centerX, y, Graphics.FONT_SMALL, targetText(exercise), Graphics.TEXT_JUSTIFY_CENTER);
        y += lineHeight;
        dc.drawText(
            centerX, y, Graphics.FONT_SMALL,
            (currentIndex + 1).toString() + "/" + plan.exercises.size().toString(),
            Graphics.TEXT_JUSTIFY_CENTER
        );
    }

    function onHide() as Void {
    }

    private function targetText(exercise as WatchProtocol.PlanExercise) as String {
        var setsPart = exercise.sets == -1 ? "AMRAP" : exercise.sets.toString();
        var repsPart = exercise.reps < 0 ? "MAX" : exercise.reps.toString();
        var progress = exercise.sets == -1
            ? completedSets[currentIndex].toString() + " done"
            : completedSets[currentIndex].toString() + "/" + setsPart;
        return setsPart + "x" + repsPart + " - " + progress;
    }

}

class ActiveWorkoutDelegate extends WatchUi.InputDelegate {

    private var view as ActiveWorkoutView;
    private var plan as WatchProtocol.Plan;
    private var session as ActivityRecording.Session;
    private var sessionStartMs as Number;
    private var seq as Number;

    function initialize(view as ActiveWorkoutView, plan as WatchProtocol.Plan, session as ActivityRecording.Session, sessionStartMs as Number) {
        InputDelegate.initialize();
        self.view = view;
        self.plan = plan;
        self.session = session;
        self.sessionStartMs = sessionStartMs;
        seq = 0;
    }

    function onKey(keyEvent as WatchUi.KeyEvent) as Boolean {
        var key = keyEvent.getKey();
        // The physical Start/Enter button - confirmed on-device to arrive as KEY_ENTER, not
        // KEY_START (the SDK's own Attention sample checks both for the same reason). Opens the
        // Save/Discard exit-confirm menu.
        if (key == WatchUi.KEY_ENTER || key == WatchUi.KEY_START) {
            WatchUi.pushView(
                new ExitConfirmMenu(), new ExitConfirmMenuDelegate(session, sessionStartMs), WatchUi.SLIDE_UP
            );
            return true;
        }
        // The physical BACK/LAP button - confirmed on-device to arrive as KEY_ESC, not KEY_LAP.
        // Completes the currently paged-to exercise's set.
        if (key == WatchUi.KEY_ESC || key == WatchUi.KEY_LAP) {
            onCompleteSet();
            return true;
        }
        if (key == WatchUi.KEY_UP) {
            if (view.currentIndex > 0) {
                view.currentIndex -= 1;
                WatchUi.requestUpdate();
            }
            return true;
        }
        if (key == WatchUi.KEY_DOWN) {
            if (view.currentIndex < plan.exercises.size() - 1) {
                view.currentIndex += 1;
                WatchUi.requestUpdate();
            }
            return true;
        }
        return false;
    }

    // Opens the reps/weight adjust screen for the currently paged-to exercise. Nothing is
    // transmitted or marked complete until the checkmark is accepted - cancel leaves everything as
    // it was. Prefills from what the user last confirmed for this exercise, falling back to the
    // plan's suggestion only for that exercise's first set.
    private function onCompleteSet() as Void {
        var index = view.currentIndex;
        var exercise = plan.exercises[index];
        var completedSoFar = view.completedSets[index];

        var lastReps = view.lastReps[index];
        var initialReps = lastReps != null
            ? lastReps
            : (exercise.repsSequence.size() > completedSoFar ? exercise.repsSequence[completedSoFar] : exercise.reps);

        var lastWeightCenti = view.lastWeightCenti[index];
        var initialWeightCenti = lastWeightCenti != null ? lastWeightCenti : exercise.weightCenti;

        SetAdjustPicker.show(initialReps, initialWeightCenti, method(:onSetConfirmed));
    }

    function onSetConfirmed(reps as Number, weightCenti as Number) as Void {
        var index = view.currentIndex;
        view.completedSets[index] = view.completedSets[index] + 1;
        view.lastReps[index] = reps;
        view.lastWeightCenti[index] = weightCenti;
        seq += 1;

        var elapsedMs = System.getTimer() - sessionStartMs;
        Communications.transmit(
            WatchProtocol.encodeSetDone(seq, index, view.completedSets[index], reps, weightCenti, elapsedMs),
            {},
            new SetDoneTransmitListener()
        );
        session.addLap();

        WatchUi.requestUpdate();
    }

}

// transmit() takes a ConnectionListener instance, not a Method reference. SET_DONE has no
// ack/retry in Phase 2 - offline buffering and honouring ACK to trim a resend queue is Phase 4 -
// so a failed transmit here is simply lost until the user re-marks the set; nothing to recover in
// these callbacks yet.
class SetDoneTransmitListener extends Communications.ConnectionListener {

    function initialize() {
        Communications.ConnectionListener.initialize();
    }

    function onComplete() as Void {
    }

    function onError() as Void {
    }

}
