import Toybox.ActivityRecording;
import Toybox.Application;
import Toybox.Communications;
import Toybox.Lang;
import Toybox.System;
import Toybox.Timer;
import Toybox.WatchUi;

// Bumped only if a future HELLO consumer on the phone needs to distinguish watch app builds -
// GarminWatchService currently just logs it.
const WATCH_APP_VERSION = 1;

// The phone has no passive way to ask "is the watch app open" (see garmin/CLAUDE.md) - this
// heartbeat is what lets it find out. Sent once on start and then repeated so the phone can age
// out a stale "open" reading if the watch app is closed or the watch goes out of range.
const HELLO_INTERVAL_MS = 10000;

class connectiqApp extends Application.AppBase {

    // Tracked here (rather than only on whichever screen is on top) so onStop's crash-safety net
    // always has a handle to a still-recording session, regardless of which view pushed it.
    private var activeSession as ActivityRecording.Session?;
    private var helloTimer as Timer.Timer?;

    function initialize() {
        AppBase.initialize();
        activeSession = null;
    }

    // onStart() is called on application start up
    function onStart(state as Dictionary?) as Void {
        Communications.registerForPhoneAppMessages(method(:onPhoneMessage));
        // Covers "app was killed and relaunched mid-workout" - anything still in Storage from a
        // prior run gets one retry attempt as soon as the phone link is registered.
        PendingSetBuffer.flush();

        sendHello();
        helloTimer = new Timer.Timer();
        helloTimer.start(method(:sendHello), HELLO_INTERVAL_MS, true);
    }

    // onStop() is called when your application is exiting. A normal exit already goes through
    // ExitConfirmMenu (which calls clearActiveSession), so this only fires for an abnormal
    // teardown - default to saving rather than silently losing the FIT file.
    function onStop(state as Dictionary?) as Void {
        if (helloTimer != null) {
            helloTimer.stop();
            helloTimer = null;
        }
        if (activeSession != null && activeSession.isRecording()) {
            activeSession.stop();
            activeSession.save();
        }
    }

    function sendHello() as Void {
        Communications.transmit(
            WatchProtocol.encodeHello(WATCH_APP_VERSION, WatchProtocol.PROTOCOL_VERSION),
            {},
            new HelloTransmitListener()
        );
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

    // Dispatched for every message the phone's :garmin module sends. No ActivityRecording session
    // is created here - that happens once the user selects an exercise to actually start the
    // workout (ExerciseListMenuDelegate.onSelect).
    function onPhoneMessage(msg as Communications.PhoneAppMessage) as Void {
        var data = msg.data as Array;
        var plan = WatchProtocol.decodePlan(data);
        if (plan != null) {
            var menu = new ExerciseListMenu(plan);
            WatchUi.switchToView(
                menu,
                new ExerciseListMenuDelegate(plan),
                WatchUi.SLIDE_IMMEDIATE
            );
            return;
        }

        var highestSeqPersisted = WatchProtocol.decodeAck(data);
        if (highestSeqPersisted != null) {
            PendingSetBuffer.trim(highestSeqPersisted);
        }
    }

}

function getApp() as connectiqApp {
    return Application.getApp() as connectiqApp;
}

// transmit() takes a ConnectionListener instance, not a Method reference (see
// PendingSetBuffer.mc's PendingBufferTransmitListener for the same pattern). A dropped HELLO just
// means the phone's next heartbeat window catches up - nothing to retry here.
class HelloTransmitListener extends Communications.ConnectionListener {

    function initialize() {
        Communications.ConnectionListener.initialize();
    }

    function onComplete() as Void {
    }

    function onError() as Void {
    }

}