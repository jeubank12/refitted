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
        var lines = wrapText(promptText, font, maxWidth, dc);

        var lineHeight = Graphics.getFontHeight(font);
        var y = (dc.getHeight() - lineHeight * lines.size()) / 2;

        for (var i = 0; i < lines.size(); i += 1) {
            dc.drawText(dc.getWidth() / 2, y, font, lines[i], Graphics.TEXT_JUSTIFY_CENTER);
            y += lineHeight;
        }
    }

    function onHide() as Void {
    }

    private function wrapText(text as String, font as FontDefinition, maxWidth as Number, dc as Dc) as Array<String> {
        var words = splitWords(text);
        var lines = [] as Array<String>;
        var current = "";
        for (var i = 0; i < words.size(); i += 1) {
            var word = words[i];
            var candidate = current.equals("") ? word : current + " " + word;
            if (dc.getTextWidthInPixels(candidate, font) > maxWidth && !current.equals("")) {
                lines.add(current);
                current = word;
            } else {
                current = candidate;
            }
        }
        if (!current.equals("")) {
            lines.add(current);
        }
        return lines;
    }

    // Toybox.Lang.String has no split() - scan for spaces manually instead.
    private function splitWords(text as String) as Array<String> {
        var words = [] as Array<String>;
        var remaining = text;
        while (true) {
            var spaceIndex = remaining.find(" ");
            if (spaceIndex == null) {
                if (remaining.length() > 0) {
                    words.add(remaining);
                }
                return words;
            }
            var word = remaining.substring(0, spaceIndex) as String;
            if (word.length() > 0) {
                words.add(word);
            }
            remaining = remaining.substring(spaceIndex + 1, remaining.length()) as String;
        }
        return words;
    }

}
