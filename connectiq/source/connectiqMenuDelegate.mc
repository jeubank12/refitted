import Toybox.Lang;
import Toybox.WatchUi;

class connectiqMenuDelegate extends WatchUi.MenuInputDelegate {

    function initialize() {
        MenuInputDelegate.initialize();
    }

    function onMenuItem(item as Symbol) as Void {
        if (item == :item_1) {
            var view = new DiagnosticsView();
            WatchUi.pushView(view, new DiagnosticsDelegate(view), WatchUi.SLIDE_LEFT);
        }
    }

}