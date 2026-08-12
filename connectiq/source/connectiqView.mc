import Toybox.Graphics;
import Toybox.Lang;
import Toybox.WatchUi;

// The idle screen shown before a PLAN has arrived. Drawn directly rather than via layout.xml - a
// static <label> has no word-wrap, so any prompt longer than a couple of words gets clipped by
// the round bezel on devices like the Forerunner.
class connectiqView extends WatchUi.View {

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

        var font = Graphics.FONT_MEDIUM;
        var promptText = WatchUi.loadResource(Rez.Strings.prompt) as String;
        var maxWidth = (dc.getWidth() * 0.75).toNumber();
        var lines = TextWrap.wrapText(promptText, font, maxWidth, dc);

        var lineHeight = Graphics.getFontHeight(font);
        var y = (dc.getHeight() - lineHeight * lines.size()) / 2;

        for (var i = 0; i < lines.size(); i += 1) {
            dc.drawText(dc.getWidth() / 2, y, font, lines[i], Graphics.TEXT_JUSTIFY_CENTER);
            y += lineHeight;
        }
    }

    function onHide() as Void {
    }

}
