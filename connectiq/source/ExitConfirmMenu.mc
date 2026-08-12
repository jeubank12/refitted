import Toybox.ActivityRecording;
import Toybox.Communications;
import Toybox.Lang;
import Toybox.System;
import Toybox.WatchUi;

// No Connect IQ SDK class provides the native slide-from-right save/discard prompt - it's part of
// Garmin's own closed-source apps, not SDK-exposed (Toybox.ActivityRecording.Session has only bare
// save()/discard(), no dialog helper). That native look is documented as Garmin's standard Menu2
// pattern (developer.garmin.com/connect-iq/user-experience-guidelines/menus), so this reuses Menu2
// - the same component already driving the pre-workout overview - rather than introducing a second
// UI idiom.
class ExitConfirmMenu extends WatchUi.Menu2 {

    function initialize() {
        Menu2.initialize({:title => WatchUi.loadResource(Rez.Strings.exit_confirm_title) as String});
        addItem(new WatchUi.MenuItem(
            WatchUi.loadResource(Rez.Strings.exit_confirm_save) as String, null, :save, {}
        ));
        addItem(new WatchUi.MenuItem(
            WatchUi.loadResource(Rez.Strings.exit_confirm_discard) as String, null, :discard, {}
        ));
    }

}

class ExitConfirmMenuDelegate extends WatchUi.Menu2InputDelegate {

    private var session as ActivityRecording.Session;
    private var sessionStartMs as Number;

    function initialize(session as ActivityRecording.Session, sessionStartMs as Number) {
        Menu2InputDelegate.initialize();
        self.session = session;
        self.sessionStartMs = sessionStartMs;
    }

    // Completed-set history already reached the phone in real time via SET_DONE - Discard only
    // affects the watch's own FIT/activity artifact, never the phone's record of finished sets.
    function onSelect(item as WatchUi.MenuItem) as Void {
        session.stop();
        if (item.getId() == :save) {
            session.save();
        } else {
            session.discard();
        }
        getApp().clearActiveSession();

        var elapsedMs = System.getTimer() - sessionStartMs;
        Communications.transmit(WatchProtocol.encodeSessionEnded(elapsedMs), {}, new SessionEndedTransmitListener());

        WatchUi.popView(WatchUi.SLIDE_DOWN); // dismiss this menu
        WatchUi.popView(WatchUi.SLIDE_DOWN); // dismiss the active-workout screen - actual exit
    }

}

class SessionEndedTransmitListener extends Communications.ConnectionListener {

    function initialize() {
        Communications.ConnectionListener.initialize();
    }

    function onComplete() as Void {
    }

    function onError() as Void {
    }

}
