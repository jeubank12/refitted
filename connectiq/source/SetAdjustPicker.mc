import Toybox.Graphics;
import Toybox.Lang;
import Toybox.WatchUi;

// A minimal, reusable PickerFactory over a contiguous integer range - the SDK ships no built-in
// NumberFactory (that's illustrative pseudocode in the Picker docs, not a real class; the SDK's own
// Picker sample defines its own), and WatchUi.NumberPicker is deprecated, so this is the supported
// replacement. divisor/formatString let the same class serve plain reps (divisor 1, "%d") and
// centi-weight values (divisor 100, "%.1f") without a Method callback per instance.
class IntegerPickerFactory extends WatchUi.PickerFactory {

    private var minValue as Number;
    private var step as Number;
    private var count as Number;
    private var divisor as Number;
    private var formatString as String;

    // Not part of PickerFactory - read by the caller to seed Picker's :defaults option.
    var initialIndex as Number;

    function initialize(
        minValue as Number, maxValue as Number, step as Number, initialValue as Number,
        divisor as Number, formatString as String
    ) {
        PickerFactory.initialize();
        self.minValue = minValue;
        self.step = step;
        self.count = ((maxValue - minValue) / step) + 1;
        self.divisor = divisor;
        self.formatString = formatString;
        self.initialIndex = (initialValue - minValue) / step;
    }

    function getDrawable(item as Number, isSelected as Boolean) as WatchUi.Drawable or Null {
        var value = valueForItem(item);
        var text = divisor == 1 ? value.format(formatString) : (value / divisor.toFloat()).format(formatString);
        return new WatchUi.Text({
            :text => text,
            :color => Graphics.COLOR_WHITE,
            :font => Graphics.FONT_MEDIUM,
            :locX => WatchUi.LAYOUT_HALIGN_CENTER,
            :locY => WatchUi.LAYOUT_VALIGN_CENTER
        });
    }

    function getSize() as Number {
        return count;
    }

    function getValue(item as Number) as Object or Null {
        return valueForItem(item);
    }

    private function valueForItem(item as Number) as Number {
        return minValue + (item * step);
    }

}

// A fixed single-value "field" - not user-adjustable (UP/DOWN have nothing else to land on), it
// exists purely so the Picker has a third stop after reps and weight. Without it, pressing select
// on the weight field (the last pattern entry) would fire onAccept immediately - landing on this
// field first, with its own explicit "confirm" label, is what makes save a deliberate final step
// rather than something that happens as a side effect of finishing the weight field.
class ConfirmPickerFactory extends WatchUi.PickerFactory {

    function initialize() {
        PickerFactory.initialize();
    }

    function getDrawable(item as Number, isSelected as Boolean) as WatchUi.Drawable or Null {
        return new WatchUi.Text({
            :text => "SAVE",
            :color => Graphics.COLOR_GREEN,
            :font => Graphics.FONT_MEDIUM,
            :locX => WatchUi.LAYOUT_HALIGN_CENTER,
            :locY => WatchUi.LAYOUT_VALIGN_CENTER
        });
    }

    function getSize() as Number {
        return 1;
    }

    function getValue(item as Number) as Object or Null {
        return 0;
    }

}

// Adjusts reps and weight for one set as three fields on a single Picker screen: reps, weight, and
// a fixed SAVE confirm field (Picker's :pattern takes an array of PickerFactory entries and
// PickerDelegate.onAccept gets one value back per entry - no need for chained Picker screens, and
// the trailing confirm field is what makes reaching onAccept a deliberate final step instead of an
// automatic side effect of finishing the weight field). Values are centi-weight ints, matching the
// wire format's weightCenti field, so no float ever crosses into WatchProtocol.encodeSetDone.
class SetAdjustPickerDelegate extends WatchUi.PickerDelegate {

    private var onDone as Method(reps as Number, weightCenti as Number) as Void;
    private var onCancelled as Method() as Void;

    function initialize(
        onDone as Method(reps as Number, weightCenti as Number) as Void,
        onCancelled as Method() as Void
    ) {
        PickerDelegate.initialize();
        self.onDone = onDone;
        self.onCancelled = onCancelled;
    }

    // values[2] is the fixed ConfirmPickerFactory's dummy value - only values[0]/values[1] (reps,
    // weightCenti) matter.
    function onAccept(values as Array) as Boolean {
        WatchUi.popView(WatchUi.SLIDE_DOWN);
        onDone.invoke(values[0] as Number, values[1] as Number);
        return true;
    }

    function onCancel() as Boolean {
        WatchUi.popView(WatchUi.SLIDE_DOWN);
        onCancelled.invoke();
        return true;
    }

}

module SetAdjustPicker {

    const REPS_MIN = 0;
    const REPS_MAX = 100;
    const REPS_STEP = 1;

    // weightCenti units - 2.5kg/lb steps expressed as centi-units, 0 to 500 (kg/lb) covers the
    // practical range for on-watch adjustment without an unwieldy number of picker positions.
    const WEIGHT_MIN_CENTI = 0;
    const WEIGHT_MAX_CENTI = 50000;
    const WEIGHT_STEP_CENTI = 250;

    // Pushes the combined reps/weight Picker. onDone fires only on confirm, with the values the
    // user landed on (which may equal the prefilled suggestion if they changed nothing); onCancelled
    // fires if the screen is backed out of instead.
    function show(
        initialReps as Number, initialWeightCenti as Number,
        onDone as Method(reps as Number, weightCenti as Number) as Void,
        onCancelled as Method() as Void
    ) as Void {
        var repsFactory = new IntegerPickerFactory(REPS_MIN, REPS_MAX, REPS_STEP, initialReps, 1, "%d");
        var weightFactory = new IntegerPickerFactory(
            WEIGHT_MIN_CENTI, WEIGHT_MAX_CENTI, WEIGHT_STEP_CENTI, initialWeightCenti, 100, "%.1f"
        );
        var confirmFactory = new ConfirmPickerFactory();

        var picker = new WatchUi.Picker({
            // "Select" within a Picker and the physical START/STOP button are the same input on
            // this hardware (KEY_ENTER) - spelling that out here since it's not obvious, and this
            // is the one place a set actually gets saved.
            :title => new WatchUi.Text({
                :text => "START to save",
                :color => Graphics.COLOR_WHITE,
                :font => Graphics.FONT_XTINY,
                :locX => WatchUi.LAYOUT_HALIGN_CENTER,
                :locY => WatchUi.LAYOUT_VALIGN_CENTER
            }),
            :pattern => [repsFactory, weightFactory, confirmFactory],
            :defaults => [repsFactory.initialIndex, weightFactory.initialIndex, 0]
        });

        WatchUi.pushView(picker, new SetAdjustPickerDelegate(onDone, onCancelled), WatchUi.SLIDE_UP);
    }

}
