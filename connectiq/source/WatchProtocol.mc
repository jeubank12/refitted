import Toybox.Lang;

// Positional wire format shared with the phone's :garmin module
// (data/.../device/WatchProtocol.kt). Envelope: [protocolVersion, messageType, payload].
module WatchProtocol {
    const PROTOCOL_VERSION = 1;

    const TYPE_PLAN = 1;
    const TYPE_ACK = 2;
    const TYPE_END = 3;
    const TYPE_NAK = 4;
    const TYPE_HELLO = 16;
    const TYPE_SET_DONE = 17;
    const TYPE_BUFFER = 18;
    const TYPE_SESSION_ENDED = 19;

    const FLAG_TO_FAILURE = 0x1;
    const FLAG_REPS_SEQUENCED = 0x2;
    const FLAG_HAS_TIME_LIMIT = 0x4;

    // One flattened exercise as decoded from a PLAN message. Its index in Plan.exercises is its
    // identity - the watch never stores "$day.$step" strings.
    class PlanExercise {
        var name as String;
        var sets as Number; // -1 == open/challenge set
        var reps as Number;
        var restSeconds as Number;
        var weightCenti as Number;
        var isToFailure as Boolean;
        var repsSequence as Array<Number>;
        var timeLimitMillis as Number?;

        function initialize(
            name as String, sets as Number, reps as Number, restSeconds as Number,
            weightCenti as Number, isToFailure as Boolean, repsSequence as Array<Number>,
            timeLimitMillis as Number?
        ) {
            self.name = name;
            self.sets = sets;
            self.reps = reps;
            self.restSeconds = restSeconds;
            self.weightCenti = weightCenti;
            self.isToFailure = isToFailure;
            self.repsSequence = repsSequence;
            self.timeLimitMillis = timeLimitMillis;
        }
    }

    class Plan {
        var workout as String;
        var day as String;
        var exercises as Array<PlanExercise>;

        function initialize(workout as String, day as String, exercises as Array<PlanExercise>) {
            self.workout = workout;
            self.day = day;
            self.exercises = exercises;
        }
    }

    // Returns null when the envelope isn't a PLAN, or when its protocolVersion is newer than we
    // understand - refuse rather than misparse a format we don't recognize.
    function decodePlan(message as Array) as Plan? {
        var version = message[0] as Number;
        if (version > PROTOCOL_VERSION) {
            return null;
        }
        if ((message[1] as Number) != TYPE_PLAN) {
            return null;
        }

        var payload = message[2] as Array;
        var rawExercises = payload[2] as Array;
        var exercises = new Array<PlanExercise>[rawExercises.size()];
        for (var i = 0; i < rawExercises.size(); i += 1) {
            exercises[i] = decodeExercise(rawExercises[i] as Array);
        }
        return new Plan(payload[0] as String, payload[1] as String, exercises);
    }

    function decodeExercise(raw as Array) as PlanExercise {
        var flags = raw[5] as Number;

        var index = 6;
        var repsSequence = [] as Array<Number>;
        if ((flags & FLAG_REPS_SEQUENCED) != 0) {
            repsSequence = raw[index] as Array<Number>;
            index += 1;
        }
        var timeLimitMillis = null;
        if ((flags & FLAG_HAS_TIME_LIMIT) != 0) {
            timeLimitMillis = raw[index] as Number;
            index += 1;
        }

        return new PlanExercise(
            raw[0] as String,
            raw[1] as Number,
            raw[2] as Number,
            raw[3] as Number,
            raw[4] as Number,
            (flags & FLAG_TO_FAILURE) != 0,
            repsSequence,
            timeLimitMillis
        );
    }

    // [protocolVersion, TYPE_END, []] - sent when the user ends the workout from the phone
    // (Phase 3). Kept here now so the envelope shape has exactly one source of truth.
    function encodeEnd() as Array {
        return [PROTOCOL_VERSION, TYPE_END, []];
    }

    // Mirrors data/.../device/WatchProtocol.kt's encodeSetDone exactly - both ends ship this
    // shape together. seq bounds the offline-buffer ack window (Phase 4); elapsedMs is what the
    // phone turns into the record's timestamp, since the watch never sends a wall-clock time.
    function encodeSetDone(
        seq as Number, exerciseIndex as Number, setNumber as Number,
        reps as Number, weightCenti as Number, elapsedMs as Number
    ) as Array {
        return [
            PROTOCOL_VERSION,
            TYPE_SET_DONE,
            [seq, exerciseIndex, setNumber, reps, weightCenti, elapsedMs]
        ];
    }
}
