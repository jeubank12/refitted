import Toybox.Graphics;
import Toybox.Lang;
import Toybox.System;
import Toybox.WatchUi;

// Pairing/HELLO diagnostics, reachable from the idle screen's MainMenu. Exists because the phone
// side has no way to tell "the watch has never said HELLO" from "it said HELLO once and then
// stopped" without this - see garmin/CLAUDE.md's HELLO gotcha. Every line is drawn manually and
// routed through TextWrap, same as connectiqView/ActiveWorkout - a static <label> would clip on
// the round bezel the moment any of these strings runs long (a device name, an error count).
class DiagnosticsView extends WatchUi.View {

    function initialize() {
        View.initialize();
    }

    function onLayout(dc as Dc) as Void {
    }

    function onShow() as Void {
    }

    function onUpdate(dc as Dc) as Void {
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_BLACK);
        dc.clear();

        var app = getApp();
        var font = Graphics.FONT_XTINY;
        var lineHeight = Graphics.getFontHeight(font);
        var centerX = dc.getWidth() / 2;
        var maxWidth = (dc.getWidth() * 0.75).toNumber();

        var rows = [
            "Refitted v" + WATCH_APP_VERSION.toString() + " / proto v" + WatchProtocol.PROTOCOL_VERSION.toString(),
            "Hello: " + app.getHelloAttempts().toString() + " sent",
            "OK " + app.getHelloSuccesses().toString() + " / Err " + app.getHelloErrors().toString(),
            lastAttemptText(app),
            lastResultText(app)
        ] as Array<String>;

        var lines = [] as Array<String>;
        for (var i = 0; i < rows.size(); i += 1) {
            var wrapped = TextWrap.wrapText(rows[i], font, maxWidth, dc);
            for (var j = 0; j < wrapped.size(); j += 1) {
                lines.add(wrapped[j]);
            }
        }

        var y = (dc.getHeight() - lineHeight * lines.size()) / 2;
        for (var i = 0; i < lines.size(); i += 1) {
            dc.drawText(centerX, y, font, lines[i], Graphics.TEXT_JUSTIFY_CENTER);
            y += lineHeight;
        }

        dc.drawText(centerX, dc.getHeight() - lineHeight, font, "SELECT: resend now", Graphics.TEXT_JUSTIFY_CENTER);
    }

    private function lastAttemptText(app as connectiqApp) as String {
        var lastAttemptMs = app.getLastHelloAttemptMs();
        if (lastAttemptMs == null) {
            return "Last attempt: none";
        }
        return "Last attempt: " + secondsAgoText(lastAttemptMs);
    }

    private function lastResultText(app as connectiqApp) as String {
        var result = app.getLastHelloResult();
        if (result == null) {
            return "Last result: none yet";
        }
        var resultMs = app.getLastHelloResultMs();
        var label = (result == :success) ? "ok" : "error";
        return "Last result: " + label + " (" + secondsAgoText(resultMs) + ")";
    }

    private function secondsAgoText(sinceMs as Number?) as String {
        if (sinceMs == null) {
            return "n/a";
        }
        var elapsedSec = (System.getTimer() - sinceMs) / 1000;
        return elapsedSec.toString() + "s ago";
    }

    function onHide() as Void {
    }

}

class DiagnosticsDelegate extends WatchUi.BehaviorDelegate {

    function initialize() {
        BehaviorDelegate.initialize();
    }

    // Forces a HELLO attempt right now instead of waiting up to HELLO_INTERVAL_MS for the next
    // timer tick - the "force resend" affordance. onBack is left at BehaviorDelegate's default
    // (pop back to MainMenu), same as every other pushed screen that doesn't need to intercept it.
    function onSelect() as Boolean {
        getApp().sendHello();
        return true;
    }

}
