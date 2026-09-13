import Toybox.Lang;
import Toybox.WatchUi;

class connectiqMenuDelegate extends WatchUi.MenuInputDelegate {

    function initialize() {
        MenuInputDelegate.initialize();
    }

    function onMenuItem(item as Symbol) as Void {
        if (item == :item_1) {
            WatchUi.pushView(new DiagnosticsView(), new DiagnosticsDelegate(), WatchUi.SLIDE_LEFT);
        }
    }

}