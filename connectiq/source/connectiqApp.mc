import Toybox.Application;
import Toybox.Communications;
import Toybox.Lang;
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

    // Dispatched for every message the phone's :garmin module sends. Phase 1 only handles PLAN -
    // ACK/END and the watch -> phone direction (SET_DONE) land in Phase 2/3.
    function onPhoneMessage(msg as Communications.PhoneAppMessage) as Void {
        var plan = WatchProtocol.decodePlan(msg.data as Array);
        if (plan != null) {
            WatchUi.switchToView(
                new ExerciseListMenu(plan),
                new ExerciseListMenuDelegate(),
                WatchUi.SLIDE_IMMEDIATE
            );
        }
    }

}

function getApp() as connectiqApp {
    return Application.getApp() as connectiqApp;
}