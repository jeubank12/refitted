import Toybox.Application.Storage;
import Toybox.Communications;
import Toybox.Lang;

// Storage-backed queue of confirmed sets that haven't been ACK'd by the phone yet. Deliberately
// not owned by ActiveWorkoutDelegate - Storage survives the view being torn down or the whole app
// being killed and relaunched mid-workout, which a merely in-memory buffer would not.
//
// Each entry mirrors WatchProtocol.encodeSetDone's payload shape:
// [seq, exerciseIndex, setNumber, reps, weightCenti, elapsedMs].
module PendingSetBuffer {
    const STORAGE_KEY = "pendingSetDone";

    function append(
        seq as Number, exerciseIndex as Number, setNumber as Number,
        reps as Number, weightCenti as Number, elapsedMs as Number
    ) as Void {
        var entries = load();
        entries.add([seq, exerciseIndex, setNumber, reps, weightCenti, elapsedMs]);
        save(entries);
    }

    // Drops every entry at or below an ACK'd seq.
    function trim(highestSeqPersisted as Number) as Void {
        var entries = load();
        var kept = [] as Array<Array>;
        for (var i = 0; i < entries.size(); i += 1) {
            var entry = entries[i] as Array;
            if ((entry[0] as Number) > highestSeqPersisted) {
                kept.add(entry);
            }
        }
        save(kept);
    }

    // Sends whatever is still pending: a lone entry goes as a plain SET_DONE (today's normal-case
    // shape), more than one goes as a single batched BUFFER - no retry/backoff bookkeeping here,
    // just "try now"; ActiveWorkoutView's 1Hz timer and connectiqApp.onStart are what call this
    // again later if the phone never ACKs.
    function flush() as Void {
        var entries = load();
        if (entries.size() == 0) {
            return;
        }
        if (entries.size() == 1) {
            var entry = entries[0] as Array;
            Communications.transmit(
                WatchProtocol.encodeSetDone(
                    entry[0] as Number, entry[1] as Number, entry[2] as Number,
                    entry[3] as Number, entry[4] as Number, entry[5] as Number
                ),
                {},
                new PendingBufferTransmitListener()
            );
        } else {
            Communications.transmit(WatchProtocol.encodeBuffer(entries), {}, new PendingBufferTransmitListener());
        }
    }

    // Module-level functions can't be marked private (monkeyc rejects it, same as module-level
    // const - see connectiq/CLAUDE.md) - these just aren't part of the module's intended surface.
    function load() as Array<Array> {
        var stored = Storage.getValue(STORAGE_KEY);
        return stored != null ? (stored as Array<Array>) : ([] as Array<Array>);
    }

    function save(entries as Array<Array>) as Void {
        Storage.setValue(STORAGE_KEY, entries);
    }
}

// transmit() takes a ConnectionListener instance, not a Method reference (see SetDoneTransmitListener's
// prior art, now folded into this one buffer-wide listener). No retry scheduling here on purpose -
// a failed send just leaves the entry in Storage for the next flush() trigger to pick up.
class PendingBufferTransmitListener extends Communications.ConnectionListener {

    function initialize() {
        Communications.ConnectionListener.initialize();
    }

    function onComplete() as Void {
    }

    function onError() as Void {
    }

}
