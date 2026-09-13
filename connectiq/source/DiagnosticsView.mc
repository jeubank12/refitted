import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Math;
import Toybox.System;
import Toybox.WatchUi;

// Pairing/HELLO diagnostics, reachable from the idle screen's MainMenu. Exists because the phone
// side has no way to tell "the watch has never said HELLO" from "it said HELLO once and then
// stopped" without this - see garmin/CLAUDE.md's HELLO gotcha. Every line is drawn manually and
// routed through TextWrap, same as connectiqView/ActiveWorkout - a static <label> would clip on
// the round bezel the moment any of these strings runs long (a device name, an error count).
class DiagnosticsView extends WatchUi.View {

    // In lines (not pixels) - stays valid across onUpdate calls regardless of font/layout, and a
    // button press is naturally "one more line", not a pixel amount nobody without the current dc
    // could reason about.
    private var mScrollOffset as Number = 0;
    private var mMaxScroll as Number = 0;

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

        var footerNormal = "SELECT: resend now";
        var footerScroll = "SELECT resend, UP/DN scroll";

        // A round display's usable width shrinks to 0 at the very top/bottom pixel rows - it's a
        // circle, not a rectangle - so text anchored near either edge gets clipped by the bezel
        // even though it's within dc's rectangular bounds. Inset both edges by however much height
        // the widest row on this screen (a content line or the footer) needs before the chord at
        // that row is wide enough to hold it, so nothing this view ever draws can land in the
        // clipped zone. Rectangular devices don't need this - screenShape only reports round here
        // because that's this app's actual target hardware (see connectiq/CLAUDE.md).
        var verticalMargin = 0;
        if (System.getDeviceSettings().screenShape == System.SCREEN_SHAPE_ROUND) {
            var footerScrollWidth = dc.getTextWidthInPixels(footerScroll, font);
            var safeWidth = (footerScrollWidth > maxWidth) ? footerScrollWidth : maxWidth;
            var radius = dc.getHeight() / 2.0;
            var halfWidth = safeWidth / 2.0;
            verticalMargin = (halfWidth < radius)
                ? (radius - Math.sqrt(radius * radius - halfWidth * halfWidth)).toNumber()
                : radius.toNumber();
        }

        // Reserve the bottom line for the footer so scrolled content can never be drawn under it,
        // then clip the content region so a line that's only partially scrolled into view gets cut
        // instead of bleeding into the footer.
        var contentTop = verticalMargin;
        var contentBottom = dc.getHeight() - verticalMargin - lineHeight;
        var visibleLines = (contentBottom - contentTop) / lineHeight;
        mMaxScroll = lines.size() - visibleLines;
        if (mMaxScroll < 0) {
            mMaxScroll = 0;
        }
        if (mScrollOffset > mMaxScroll) {
            mScrollOffset = mMaxScroll;
        }

        var totalHeight = lineHeight * lines.size();
        var y = (totalHeight <= (contentBottom - contentTop))
            ? contentTop + ((contentBottom - contentTop) - totalHeight) / 2
            : contentTop - (mScrollOffset * lineHeight);

        dc.setClip(0, contentTop, dc.getWidth(), contentBottom - contentTop);
        for (var i = 0; i < lines.size(); i += 1) {
            dc.drawText(centerX, y, font, lines[i], Graphics.TEXT_JUSTIFY_CENTER);
            y += lineHeight;
        }
        dc.clearClip();

        var footer = (mMaxScroll > 0) ? footerScroll : footerNormal;
        dc.drawText(centerX, contentBottom, font, footer, Graphics.TEXT_JUSTIFY_CENTER);
    }

    // Called by DiagnosticsDelegate's onNextPage/onPreviousPage - direction is +1 or -1 lines.
    function scroll(direction as Number) as Void {
        if (mMaxScroll <= 0) {
            return;
        }
        mScrollOffset += direction;
        if (mScrollOffset < 0) {
            mScrollOffset = 0;
        }
        if (mScrollOffset > mMaxScroll) {
            mScrollOffset = mMaxScroll;
        }
        WatchUi.requestUpdate();
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

    private var mView as DiagnosticsView;

    function initialize(view as DiagnosticsView) {
        BehaviorDelegate.initialize();
        mView = view;
    }

    // Forces a HELLO attempt right now instead of waiting up to HELLO_INTERVAL_MS for the next
    // timer tick - the "force resend" affordance. onBack is left at BehaviorDelegate's default
    // (pop back to MainMenu), same as every other pushed screen that doesn't need to intercept it.
    function onSelect() as Boolean {
        getApp().sendHello();
        return true;
    }

    // up/down map to previousPage/nextPage on this app's target devices - see the button-mapping
    // gotcha in connectiq/CLAUDE.md.
    function onNextPage() as Boolean {
        mView.scroll(1);
        return true;
    }

    function onPreviousPage() as Boolean {
        mView.scroll(-1);
        return true;
    }

}
