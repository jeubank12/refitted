import Toybox.Graphics;
import Toybox.Lang;

// Shared word-wrap helper - a static <label> layout has no word-wrap, and neither drawText nor the
// SDK offers one, so any screen drawing user-controlled or variable-length text (workout prompts,
// exercise names) needs this. Originally written for connectiqView's idle prompt; ActiveWorkout's
// exercise name needs the identical treatment.
module TextWrap {

    function wrapText(text as String, font as FontDefinition, maxWidth as Number, dc as Dc) as Array<String> {
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
    function splitWords(text as String) as Array<String> {
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
