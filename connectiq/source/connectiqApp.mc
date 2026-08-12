import Toybox.ActivityRecording;
import Toybox.Application;
import Toybox.Communications;
import Toybox.Lang;
import Toybox.System;
import Toybox.WatchUi;

class connectiqApp extends Application.AppBase {

    // Tracked here (rather than only on whichever screen is on top) so onStop's crash-safety net
    // always has a handle to a still-recording session, regardless of which view pushed it.
    private var activeSession as ActivityRecording.Session?;

    function initialize() {
        AppBase.initialize();
        activeSession = null;
    }

    // onStart() is called on application start up
    function onStart(state as Dictionary?) as Void {
        Communications.registerForPhoneAppMessages(method(:onPhoneMessage));
    }

    // onStop() is called when your application is exiting. A normal exit already goes through
    // ExitConfirmMenu (which calls clearActiveSession), so this only fires for an abnormal
    // teardown - default to saving rather than silently losing the FIT file.
    function onStop(state as Dictionary?) as Void {
        if (activeSession != null && activeSession.isRecording()) {
            activeSession.stop();
            activeSession.save();
        }
    }

    function setActiveSession(session as ActivityRecording.Session) as Void {
        activeSession = session;
    }

    function clearActiveSession() as Void {
        activeSession = null;
    }

    // Return the initial view of your application here
    function getInitialView() as [Views] or [Views, InputDelegates] {
        return [ new connectiqView(), new connectiqDelegate() ];
    }

    // Dispatched for every message the phone's :garmin module sends. Phase 1 only handled PLAN -
    // ACK/NAK and the watch -> phone direction (SET_DONE) are Phase 2; END is Phase 3. No
    // ActivityRecording session is created here - that happens once the user selects an exercise
    // to actually start the workout (ExerciseListMenuDelegate.onSelect).
    function onPhoneMessage(msg as Communications.PhoneAppMessage) as Void {
        var plan = WatchProtocol.decodePlan(msg.data as Array);
        if (plan != null) {
            var menu = new ExerciseListMenu(plan);
            WatchUi.switchToView(
                menu,
                new ExerciseListMenuDelegate(plan),
                WatchUi.SLIDE_IMMEDIATE
            );
        }
    }

}

function getApp() as connectiqApp {
    return Application.getApp() as connectiqApp;
}