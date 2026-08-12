import Toybox.ActivityRecording;
import Toybox.Attention;
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.System;
import Toybox.Timer;
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
// exit-confirm Save/Discard menu, and BACK/LAP (KEY_ESC) drives the REST/WORK state machine below.
// There is no pause/resume in v1 - with only two distinguishable buttons reaching the app, there's
// no third one to spare for it; the session simply records start-to-finish.
//
// REST/WORK is one state shared by the whole screen, not per-exercise: REST is a white-background
// countdown that keeps running (and remains pageable) no matter which exercise you're looking at,
// since it tracks recovery from whichever set you last confirmed, not the exercise on screen. WORK
// is a black-background count-up lap timer for the set in progress, and disables paging - there's
// only one exercise you can be "in the middle of a set" for.
class ActiveWorkoutView extends WatchUi.View {

    enum {
        REST,
        WORK
    }

    var plan as WatchProtocol.Plan;
    var currentIndex as Number;
    var completedSets as Array<Number>;
    // What the user actually confirmed last time for this exercise, per index - null until a set
    // is confirmed. Prefilling the adjust Picker from this instead of the plan's suggestion means
    // an adjustment you make on set 1 carries forward to set 2, rather than resetting every time.
    var lastReps as Array<Number?>;
    var lastWeightCenti as Array<Number?>;

    var state as Number;
    // REST: seconds left in the rest period - goes negative (overtime) once it reaches 0 rather
    // than clamping, since the countdown never auto-advances to WORK on its own.
    // WORK: seconds spent on the current set so far.
    var elapsedSec as Number;
    private var sessionStartMs as Number;
    private var timer as Timer.Timer?;
    // True for exactly the one onUpdate() call that draws the tick REST's countdown crossed zero -
    // a one-second visual "look at your wrist" cue paired with the vibrate, not a sustained blink.
    private var flashZeroCrossing as Boolean;
    // Retries PendingSetBuffer.flush() every 5th tick rather than every tick - cheap
    // retry-with-backoff without a second Timer.
    private var ticksSinceFlush as Number;

    function initialize(plan as WatchProtocol.Plan, startIndex as Number, sessionStartMs as Number) {
        View.initialize();
        self.plan = plan;
        self.currentIndex = startIndex;
        self.sessionStartMs = sessionStartMs;
        completedSets = new Array<Number>[plan.exercises.size()];
        lastReps = new Array<Number?>[plan.exercises.size()];
        lastWeightCenti = new Array<Number?>[plan.exercises.size()];
        for (var i = 0; i < completedSets.size(); i += 1) {
            completedSets[i] = 0;
            lastReps[i] = null;
            lastWeightCenti[i] = null;
        }
        // Starts in REST at 0 rather than a separate "ready" state - lets you page around and
        // look at exercises before starting, and "0, press LAP when ready" is a degenerate case
        // of REST, not a third state to build. No flash/vibrate here since it starts at 0 rather
        // than counting down to it.
        state = REST;
        elapsedSec = 0;
        flashZeroCrossing = false;
        ticksSinceFlush = 0;
    }

    function onLayout(dc as Dc) as Void {
    }

    // Started once, on first show, and deliberately never stopped in onHide - the optimistic REST
    // countdown (started the moment the adjust picker opens, see ActiveWorkoutDelegate.onCompleteSet)
    // needs to keep ticking while SetAdjustPicker/ExitConfirmMenu cover this view, not just while
    // it's the visible top of the stack. WatchUi.requestUpdate() from a covered view is harmless -
    // it just redraws whatever is actually on top - so there's nothing to guard here.
    function onShow() as Void {
        if (timer == null) {
            timer = new Timer.Timer();
            timer.start(method(:onTick), 1000, true);
        }
    }

    function onHide() as Void {
    }

    function onTick() as Void {
        if (state == REST) {
            var wasPositive = elapsedSec > 0;
            elapsedSec -= 1;
            flashZeroCrossing = wasPositive && elapsedSec <= 0;
            if (flashZeroCrossing) {
                Attention.vibrate([new Attention.VibeProfile(50, 500)]);
            }
        } else {
            elapsedSec += 1;
            flashZeroCrossing = false;
        }

        ticksSinceFlush += 1;
        if (ticksSinceFlush >= 5) {
            ticksSinceFlush = 0;
            PendingSetBuffer.flush();
        }

        WatchUi.requestUpdate();
    }

    // REST -> WORK: a fresh set is starting. WORK -> REST: a set was just confirmed - restSeconds
    // resets the countdown for the exercise that set belongs to.
    function enterWork() as Void {
        state = WORK;
        elapsedSec = 0;
    }

    function enterRest(restSeconds as Number) as Void {
        state = REST;
        elapsedSec = restSeconds;
    }

    // Reverts an optimistic REST that enterRest started when the adjust picker opened - the picker
    // was backed out of, so no set happened and the countdown that assumed one would is discarded.
    // Resumes WORK's lap timer from where it was interrupted, not from 0.
    function resumeWork(elapsedSec as Number) as Void {
        state = WORK;
        self.elapsedSec = elapsedSec;
    }

    function onUpdate(dc as Dc) as Void {
        var showWorkColors = (state == WORK) || flashZeroCrossing;
        if (showWorkColors) {
            dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_BLACK);
        } else {
            dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_WHITE);
        }
        dc.clear();

        var exercise = plan.exercises[currentIndex];
        var centerX = dc.getWidth() / 2;
        var nameFont = Graphics.FONT_MEDIUM;
        var lineHeight = Graphics.getFontHeight(nameFont);
        var maxWidth = (dc.getWidth() * 0.75).toNumber();
        var nameLines = TextWrap.wrapText(exercise.name, nameFont, maxWidth, dc);

        // A single-line name starts at height/4 (the original baseline). Each extra wrapped line
        // shifts the whole block up by one line height instead of growing downward, so the fixed
        // elements below (target/elapsed/progress) and the bottom-anchored total-elapsed clock
        // never overlap regardless of how long the exercise name is.
        var y = dc.getHeight() / 4 - (nameLines.size() - 1) * lineHeight;
        if (y < 4) {
            y = 4;
        }

        for (var i = 0; i < nameLines.size(); i += 1) {
            dc.drawText(centerX, y, nameFont, nameLines[i], Graphics.TEXT_JUSTIFY_CENTER);
            y += lineHeight;
        }
        y += lineHeight * 0.5;
        dc.drawText(centerX, y, Graphics.FONT_SMALL, targetText(exercise), Graphics.TEXT_JUSTIFY_CENTER);
        y += lineHeight;
        dc.drawText(centerX, y, Graphics.FONT_SMALL, elapsedText(), Graphics.TEXT_JUSTIFY_CENTER);
        y += lineHeight;
        dc.drawText(
            centerX, y, Graphics.FONT_SMALL,
            (currentIndex + 1).toString() + "/" + plan.exercises.size().toString(),
            Graphics.TEXT_JUSTIFY_CENTER
        );

        var totalElapsedSec = (System.getTimer() - sessionStartMs) / 1000;
        dc.drawText(centerX, dc.getHeight() - lineHeight, Graphics.FONT_XTINY, formatClock(totalElapsedSec), Graphics.TEXT_JUSTIFY_CENTER);
    }

    private function elapsedText() as String {
        if (state == WORK) {
            return formatClock(elapsedSec);
        }
        return elapsedSec >= 0 ? formatClock(elapsedSec) : ("+" + formatClock(-elapsedSec));
    }

    private function formatClock(totalSeconds as Number) as String {
        var minutes = totalSeconds / 60;
        var seconds = totalSeconds % 60;
        return minutes.toString() + ":" + (seconds < 10 ? "0" : "") + seconds.toString();
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
    // view.elapsedSec (WORK's lap timer) at the moment the adjust picker opened - what
    // onSetCancelled resumes WORK from if the picker is backed out of instead of saved.
    private var pendingWorkElapsedSec as Number;

    function initialize(view as ActiveWorkoutView, plan as WatchProtocol.Plan, session as ActivityRecording.Session, sessionStartMs as Number) {
        InputDelegate.initialize();
        self.view = view;
        self.plan = plan;
        self.session = session;
        self.sessionStartMs = sessionStartMs;
        seq = 0;
        pendingWorkElapsedSec = 0;
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
        // Drives the REST/WORK state machine: REST -> WORK starts the current set's lap timer;
        // WORK -> (adjust picker) -> REST opens the confirm screen for the set in progress.
        if (key == WatchUi.KEY_ESC || key == WatchUi.KEY_LAP) {
            if (view.state == ActiveWorkoutView.REST) {
                // sets == -1 is the open/challenge set (AMRAP) - no cap, always allowed. Otherwise
                // once completedSets reaches the planned count there's nothing left to log for this
                // exercise, so REST -> WORK is refused rather than starting an extra set.
                var exercise = plan.exercises[view.currentIndex];
                var setsRemaining = exercise.sets == -1 || view.completedSets[view.currentIndex] < exercise.sets;
                if (setsRemaining) {
                    view.enterWork();
                    WatchUi.requestUpdate();
                }
            } else {
                onCompleteSet();
            }
            return true;
        }
        // Paging only makes sense at REST - WORK means you're mid-set on one exercise.
        if (key == WatchUi.KEY_UP) {
            if (view.state == ActiveWorkoutView.REST && view.currentIndex > 0) {
                view.currentIndex -= 1;
                WatchUi.requestUpdate();
            }
            return true;
        }
        if (key == WatchUi.KEY_DOWN) {
            if (view.state == ActiveWorkoutView.REST && view.currentIndex < plan.exercises.size() - 1) {
                view.currentIndex += 1;
                WatchUi.requestUpdate();
            }
            return true;
        }
        return false;
    }

    // Opens the reps/weight adjust screen for the currently paged-to exercise. Nothing is
    // transmitted or marked complete until the checkmark is accepted - cancel resumes WORK via
    // onSetCancelled. Prefills from what the user last confirmed for this exercise, falling back
    // to the plan's suggestion only for that exercise's first set.
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

        // Optimistic: REST starts counting down the moment this screen opens, not after the set is
        // confirmed - the time spent picking reps/weight is itself recovery time, so it should
        // count toward rest rather than being wasted before the timer even starts. Discarded by
        // onSetCancelled if the picker is backed out of instead of saved.
        pendingWorkElapsedSec = view.elapsedSec;
        view.enterRest(exercise.restSeconds);

        SetAdjustPicker.show(initialReps, initialWeightCenti, method(:onSetConfirmed), method(:onSetCancelled));
    }

    function onSetConfirmed(reps as Number, weightCenti as Number) as Void {
        var index = view.currentIndex;
        view.completedSets[index] = view.completedSets[index] + 1;
        view.lastReps[index] = reps;
        view.lastWeightCenti[index] = weightCenti;
        seq += 1;

        var elapsedMs = System.getTimer() - sessionStartMs;
        // Storage-backed first, so the completion survives an app kill even if the transmit below
        // never gets an ACK - PendingSetBuffer.flush() is what retries it later.
        PendingSetBuffer.append(seq, index, view.completedSets[index], reps, weightCenti, elapsedMs);
        PendingSetBuffer.flush();
        session.addLap();

        // REST is already counting down - it started optimistically in onCompleteSet when this
        // screen opened. Leave it as-is rather than resetting to a fresh restSeconds, so the time
        // spent adjusting genuinely counts toward recovery.
        WatchUi.requestUpdate();
    }

    // The adjust screen was backed out of rather than saved - no set happened, so the optimistic
    // REST onCompleteSet started when it opened was premature. Resume WORK's lap timer from where
    // it was interrupted, not from 0.
    function onSetCancelled() as Void {
        view.resumeWork(pendingWorkElapsedSec);
        WatchUi.requestUpdate();
    }

}
