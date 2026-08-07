import Toybox.Application;
import Toybox.Communications;
import Toybox.Lang;
import Toybox.System;
import Toybox.WatchUi;

class connectiqApp extends Application.AppBase {

    function initialize() {
        AppBase.initialize();
    }

    // onStart() is called on application start up
    function onStart(state as Dictionary?) as Void {
        Communications.registerForPhoneAppMessages(method(:onPhoneMessage));
    }

    // onStop() is called when your application is exiting
    function onStop(state as Dictionary?) as Void {
    }

    // Return the initial view of your application here
    function getInitialView() as [Views] or [Views, InputDelegates] {
        return [ new connectiqView(), new connectiqDelegate() ];
    }

    // Dispatched for every message the phone's :garmin module sends. Phase 1 only handled PLAN -
    // ACK/NAK and the watch -> phone direction (SET_DONE) are Phase 2; END is Phase 3.
    function onPhoneMessage(msg as Communications.PhoneAppMessage) as Void {
        var plan = WatchProtocol.decodePlan(msg.data as Array);
        if (plan != null) {
            // System.getTimer() (ms since boot) rather than a wall-clock read - the watch never
            // sends absolute time, only elapsed ms since this reference, so the phone (which
            // stamps its own Instant.now() at the same moment) stays the sole time authority.
            var menu = new ExerciseListMenu(plan);
            WatchUi.switchToView(
                menu,
                new ExerciseListMenuDelegate(menu, plan, System.getTimer()),
                WatchUi.SLIDE_IMMEDIATE
            );
        }
    }

}

function getApp() as connectiqApp {
    return Application.getApp() as connectiqApp;
}