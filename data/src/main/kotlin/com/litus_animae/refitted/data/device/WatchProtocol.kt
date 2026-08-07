package com.litus_animae.refitted.data.device

import kotlin.math.roundToInt

/**
 * Positional wire format shared with the Connect IQ watch app (`connectiq/`). Both ends ship this
 * format together, so there is no need to self-describe it with dictionary keys — arrays only.
 * Envelope shape: [protocolVersion, messageType, payload].
 */
object WatchProtocol {
  const val PROTOCOL_VERSION = 1

  private const val TYPE_PLAN = 1
  private const val TYPE_ACK = 2
  private const val TYPE_END = 3
  private const val TYPE_NAK = 4
  private const val TYPE_HELLO = 16
  private const val TYPE_SET_DONE = 17
  private const val TYPE_BUFFER = 18
  private const val TYPE_SESSION_ENDED = 19

  private const val FLAG_TO_FAILURE = 1 shl 0
  private const val FLAG_REPS_SEQUENCED = 1 shl 1
  private const val FLAG_HAS_TIME_LIMIT = 1 shl 2

  sealed interface Envelope

  data class Plan(val workoutName: String, val dayLabel: String, val exercises: List<WatchExercise>) : Envelope
  data class Ack(val highestSeqPersisted: Int) : Envelope
  data object End : Envelope
  data class Nak(val reason: String) : Envelope
  data class Hello(val watchAppVersion: Int, val maxProtocolVersion: Int) : Envelope
  data class SetDone(
    val seq: Int,
    val exerciseIndex: Int,
    val setNumber: Int,
    val reps: Int,
    val weightCenti: Int,
    val elapsedMs: Long
  ) : Envelope

  data class Buffer(val entries: List<SetDone>) : Envelope
  data class SessionEnded(val elapsedMs: Long) : Envelope

  /** Received a message stamped with a protocol version newer than we understand. */
  data class UnsupportedVersion(val receivedVersion: Int) : Envelope

  fun encodePlan(workoutName: String, dayLabel: String, exercises: List<WatchExercise>): List<Any> =
    envelope(TYPE_PLAN, listOf(workoutName, dayLabel, exercises.map(::encodeExercise)))

  fun encodeAck(highestSeqPersisted: Int): List<Any> =
    envelope(TYPE_ACK, listOf(highestSeqPersisted))

  fun encodeEnd(): List<Any> = envelope(TYPE_END, emptyList<Any>())

  fun encodeNak(reason: String): List<Any> = envelope(TYPE_NAK, listOf(reason))

  fun encodeSetDone(setDone: SetDone): List<Any> = envelope(
    TYPE_SET_DONE,
    listOf(
      setDone.seq,
      setDone.exerciseIndex,
      setDone.setNumber,
      setDone.reps,
      setDone.weightCenti,
      setDone.elapsedMs
    )
  )

  private fun envelope(type: Int, payload: List<Any>): List<Any> = listOf(PROTOCOL_VERSION, type, payload)

  private fun encodeExercise(exercise: WatchExercise): List<Any> {
    var flags = 0
    if (exercise.isToFailure) flags = flags or FLAG_TO_FAILURE
    if (exercise.repsSequence.isNotEmpty()) flags = flags or FLAG_REPS_SEQUENCED
    if (exercise.timeLimitMillis != null) flags = flags or FLAG_HAS_TIME_LIMIT

    val weightCenti = (exercise.suggestedWeight * 100).roundToInt()
    val entry = mutableListOf<Any>(
      exercise.name, exercise.sets, exercise.reps, exercise.restSeconds, weightCenti, flags
    )
    if (exercise.repsSequence.isNotEmpty()) entry.add(exercise.repsSequence)
    if (exercise.timeLimitMillis != null) entry.add(exercise.timeLimitMillis)
    return entry
  }

  private fun decodeExercise(raw: List<*>): WatchExercise {
    val flags = (raw[5] as Number).toInt()
    var index = 6
    val repsSequence = if (flags and FLAG_REPS_SEQUENCED != 0) {
      (raw[index] as List<*>).map { (it as Number).toInt() }.also { index++ }
    } else {
      emptyList()
    }
    val timeLimitMillis = if (flags and FLAG_HAS_TIME_LIMIT != 0) {
      (raw[index] as Number).toInt()
    } else {
      null
    }
    return WatchExercise(
      name = raw[0] as String,
      sets = (raw[1] as Number).toInt(),
      reps = (raw[2] as Number).toInt(),
      restSeconds = (raw[3] as Number).toInt(),
      suggestedWeight = (raw[4] as Number).toInt() / 100.0,
      isToFailure = flags and FLAG_TO_FAILURE != 0,
      repsSequence = repsSequence,
      timeLimitMillis = timeLimitMillis
    )
  }

  private fun decodeSetDone(payload: List<*>): SetDone = SetDone(
    seq = (payload[0] as Number).toInt(),
    exerciseIndex = (payload[1] as Number).toInt(),
    setNumber = (payload[2] as Number).toInt(),
    reps = (payload[3] as Number).toInt(),
    weightCenti = (payload[4] as Number).toInt(),
    elapsedMs = (payload[5] as Number).toLong()
  )

  /**
   * Decodes a received envelope. A [UnsupportedVersion] result means refuse the session — never
   * attempt to interpret [payload] under a newer format than [PROTOCOL_VERSION] understands.
   */
  fun decode(message: List<*>): Envelope {
    val version = (message[0] as Number).toInt()
    if (version > PROTOCOL_VERSION) return UnsupportedVersion(version)

    val type = (message[1] as Number).toInt()
    val payload = message[2] as List<*>
    return when (type) {
      TYPE_PLAN -> Plan(
        workoutName = payload[0] as String,
        dayLabel = payload[1] as String,
        exercises = (payload[2] as List<*>).map { decodeExercise(it as List<*>) }
      )

      TYPE_ACK -> Ack((payload[0] as Number).toInt())
      TYPE_END -> End
      TYPE_NAK -> Nak(payload[0] as String)
      TYPE_HELLO -> Hello((payload[0] as Number).toInt(), (payload[1] as Number).toInt())
      TYPE_SET_DONE -> decodeSetDone(payload)
      TYPE_BUFFER -> Buffer((payload[0] as List<*>).map { decodeSetDone(it as List<*>) })
      TYPE_SESSION_ENDED -> SessionEnded((payload[0] as Number).toLong())
      else -> Nak("unknown message type $type")
    }
  }
}
