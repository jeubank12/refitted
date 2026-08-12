package com.litus_animae.refitted.garmin

import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import com.google.common.truth.Truth.assertThat
import com.litus_animae.refitted.data.device.SetRecordSink
import com.litus_animae.refitted.data.device.WatchExercise
import com.litus_animae.refitted.data.device.WatchPlan
import com.litus_animae.refitted.data.device.WatchProtocol
import com.litus_animae.refitted.data.device.WatchState
import com.litus_animae.refitted.data.models.SetRecord
import com.litus_animae.refitted.util.LogUtil
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

// TYPE_BUFFER's wire value - WatchProtocol.kt keeps its type constants private, so a test that
// needs to hand-build a raw BUFFER envelope (there is no encodeBuffer on the phone side; only the
// watch ever sends one) has to mirror the value rather than import it.
private const val TYPE_BUFFER = 18
private const val TYPE_HELLO = 16

class GarminWatchServiceTest {

  private val connection = mockk<GarminConnection>(relaxed = true)
  private val connectIQ = mockk<ConnectIQ>(relaxed = true)
  private val setRecordSink = mockk<SetRecordSink>(relaxed = true)
  private val log = mockk<LogUtil>(relaxed = true)

  private val device = IQDevice(1L, "Forerunner")
  private val watchApp = IQApp("7fb7b276-65e1-47df-a7d2-0d31553e0b4d")
  private val plan = WatchPlan(
    workout = "Push Day",
    day = "1",
    exercises = listOf(
      WatchExercise(
        name = "Bench Press", sets = 3, reps = 10, restSeconds = 90,
        suggestedWeight = 135.0, isToFailure = false, repsSequence = emptyList(), timeLimitMillis = null
      )
    ),
    ids = listOf("push_bench-press")
  )

  private lateinit var service: GarminWatchService
  private val appEventListener = slot<ConnectIQ.IQApplicationEventListener>()

  // GarminWatchService persists/ACKs a watch message on its own IO-dispatched CoroutineScope, not
  // the caller's - a real production concern (a singleton can't borrow a ViewModel's scope), but it
  // means a test can't just await a suspend call to know handling finished. Every sendMessage
  // (including setUp's own PLAN send) routes through this hook so a test can await the specific
  // send it cares about instead.
  private var onSendMessage: (List<Any>) -> Unit = {}

  @BeforeEach
  fun setUp() = runTest {
    every { connection.connectIQ } returns connectIQ
    every { connection.whenReady(any()) } answers { firstArg<() -> Unit>().invoke() }
    every { connectIQ.knownDevices } returns listOf(device)
    every { connectIQ.registerForDeviceEvents(device, any()) } just Runs
    every { connectIQ.registerForAppEvents(device, any(), capture(appEventListener)) } just Runs
    every { connectIQ.sendMessage(any(), any(), any(), any()) } answers {
      @Suppress("UNCHECKED_CAST")
      onSendMessage(thirdArg<Any>() as List<Any>)
      arg<ConnectIQ.IQSendMessageListener>(3).onMessageStatus(device, watchApp, ConnectIQ.IQMessageStatus.SUCCESS)
    }

    service = GarminWatchService(connection, setRecordSink, log)
    service.refresh()
    service.startSession(plan)
  }

  // A watch's Communications.transmit() arrives wrapped in an extra List layer (see garmin/CLAUDE.md) -
  // mirror that here so onMessageReceived unwraps it the same way it does on-device.
  private fun deliver(envelope: List<Any>) {
    appEventListener.captured.onMessageReceived(device, watchApp, listOf(envelope), ConnectIQ.IQMessageStatus.SUCCESS)
  }

  private fun rawSetDone(setDone: WatchProtocol.SetDone): List<Any> =
    listOf(setDone.seq, setDone.exerciseIndex, setDone.setNumber, setDone.reps, setDone.weightCenti, setDone.elapsedMs)

  // Waits for an ACK carrying exactly this seq to be sent, off the service's own scope.
  private fun awaitAck(highestSeqPersisted: Int, action: () -> Unit) {
    val latch = CountDownLatch(1)
    onSendMessage = { payload -> if (payload == WatchProtocol.encodeAck(highestSeqPersisted)) latch.countDown() }
    action()
    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue()
  }

  @Nested
  @DisplayName("SET_DONE from the watch")
  inner class SetDoneTests {

    @Test
    fun `persists the record and ACKs the seq back`() {
      val records = slot<List<SetRecord>>()
      coEvery { setRecordSink.store(capture(records)) } returns Unit

      awaitAck(1) { deliver(WatchProtocol.encodeSetDone(WatchProtocol.SetDone(1, 0, 1, 10, 13500, 5_000))) }

      assertThat(records.captured).hasSize(1)
      assertThat(records.captured[0].reps).isEqualTo(10)
      assertThat(records.captured[0].targetSet).isEqualTo("push_bench-press")
    }

    @Test
    fun `an exerciseIndex outside the session's plan is dropped without crashing or ACKing`() {
      deliver(WatchProtocol.encodeSetDone(WatchProtocol.SetDone(1, 5, 1, 10, 13500, 5_000)))

      coVerify(exactly = 0) { setRecordSink.store(any()) }
    }
  }

  @Nested
  @DisplayName("BUFFER replay from the watch")
  inner class BufferTests {

    @Test
    fun `persists every entry and ACKs the highest seq`() {
      val records = slot<List<SetRecord>>()
      coEvery { setRecordSink.store(capture(records)) } returns Unit

      val entries = listOf(
        WatchProtocol.SetDone(1, 0, 1, 10, 13500, 1_000),
        WatchProtocol.SetDone(2, 0, 2, 9, 13500, 2_000)
      )
      awaitAck(2) {
        deliver(listOf(WatchProtocol.PROTOCOL_VERSION, TYPE_BUFFER, listOf(entries.map(::rawSetDone))))
      }

      assertThat(records.captured).hasSize(2)
    }

    @Test
    fun `an all-unresolvable buffer is dropped without ACKing`() {
      val entries = listOf(WatchProtocol.SetDone(1, 5, 1, 10, 13500, 1_000))
      deliver(listOf(WatchProtocol.PROTOCOL_VERSION, TYPE_BUFFER, listOf(entries.map(::rawSetDone))))

      coVerify(exactly = 0) { setRecordSink.store(any()) }
    }
  }

  @Nested
  @DisplayName("HELLO from the watch")
  inner class HelloTests {

    // A fresh service (not the shared fixture, which is already past startSession/Active) so the
    // Idle -> appOpen transition is actually observable.
    @Test
    fun `flips Idle to appOpen once a HELLO arrives`() = runTest {
      val freshAppEventListener = slot<ConnectIQ.IQApplicationEventListener>()
      every { connectIQ.registerForAppEvents(device, any(), capture(freshAppEventListener)) } just Runs

      val freshService = GarminWatchService(connection, setRecordSink, log)
      freshService.refresh()

      assertThat((freshService.state.value as WatchState.Idle).appOpen).isFalse()

      // No phone-side encodeHello exists - only the watch ever sends one (see TYPE_BUFFER's
      // comment above) - so hand-build the raw envelope the same way.
      val helloEnvelope = listOf(WatchProtocol.PROTOCOL_VERSION, TYPE_HELLO, listOf(1, WatchProtocol.PROTOCOL_VERSION))
      freshAppEventListener.captured.onMessageReceived(
        device,
        watchApp,
        listOf(helloEnvelope),
        ConnectIQ.IQMessageStatus.SUCCESS
      )

      assertThat((freshService.state.value as WatchState.Idle).appOpen).isTrue()
    }
  }

  @Nested
  @DisplayName("replaying an already-persisted seq")
  inner class DedupTests {

    @Test
    fun `re-delivering the same seq still ACKs it - RoomDao's IGNORE conflict strategy is the actual dedup`() {
      coEvery { setRecordSink.store(any()) } returns Unit

      val setDone = WatchProtocol.SetDone(1, 0, 1, 10, 13500, 5_000)
      awaitAck(1) { deliver(WatchProtocol.encodeSetDone(setDone)) }
      awaitAck(1) { deliver(WatchProtocol.encodeSetDone(setDone)) }

      // GarminWatchService itself does not dedup - it relies on the phone's DAO-level
      // OnConflictStrategy.IGNORE (room/.../ExerciseDao.kt) for that, since elapsedMs makes the
      // resulting SetRecord.completed byte-identical on replay.
      coVerify(exactly = 2) { setRecordSink.store(any()) }
    }
  }
}
