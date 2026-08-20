package com.litus_animae.refitted.ui.compose.exercise

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.litus_animae.refitted.data.effort.EffortConfig
import com.litus_animae.refitted.data.effort.EffortModel
import com.litus_animae.refitted.data.effort.ScoredSet
import com.litus_animae.refitted.data.effort.TrendPoint
import com.litus_animae.refitted.data.effort.toEffortSet
import com.litus_animae.refitted.data.models.SetRecord
import com.litus_animae.refitted.identity.ConfigProvider
import com.litus_animae.refitted.ui.R
import com.litus_animae.refitted.ui.compose.LocalFeatures
import com.litus_animae.refitted.ui.compose.charts.BubbleChart
import com.litus_animae.refitted.ui.compose.charts.BubbleChartExploded
import com.litus_animae.refitted.ui.compose.charts.BubbleData
import com.litus_animae.refitted.ui.compose.charts.EffortChart
import com.litus_animae.refitted.ui.compose.charts.EffortLegend
import com.litus_animae.refitted.ui.compose.charts.EffortPoint
import com.litus_animae.refitted.ui.compose.charts.LineChart
import com.litus_animae.refitted.ui.compose.charts.buildTrendRuns
import com.litus_animae.refitted.ui.compose.state.SetHistory
import com.litus_animae.refitted.ui.compose.util.LoadingView
import com.litus_animae.refitted.ui.compose.util.ExtendedTheme
import com.litus_animae.refitted.ui.compose.util.appBarColors
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

private const val GapThresholdDays = 21L
private const val MaxXLabels = 4

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun SetRecordList(
  modifier: Modifier = Modifier,
  history: SetHistory,
  showBackButton: Boolean = false,
  onBack: () -> Unit = {},
  /** Whether a display cutout's actual bounds overlap this pane - see exercise/Main.kt, which
   * measures both panes' real bounds against `rememberDisplayCutoutBoundingRects()` rather than
   * assuming a cutout affects whichever pane owns a given screen edge. */
  affectedByCutout: Boolean = true,
  onUpdateRecord: (exercise: String, completed: Instant, weight: Double, reps: Int) -> Unit = { _, _, _, _ -> },
  onDeleteRecord: (exercise: String, completed: Instant) -> Unit = { _, _ -> }
) {
  val records = history.paged.collectAsLazyPagingItems()
  val zone = remember { ZoneId.systemDefault() }

  // Owned here, not per-SessionRow, since only one set can be under edit at a time across the
  // whole drawer. Delete is a separate step from the edit dialog's own Delete button (see
  // EditSetRecordDialog) - never fires directly from the edit form.
  var editingRecord by remember { mutableStateOf<SetRecord?>(null) }
  var confirmingDelete by remember { mutableStateOf<SetRecord?>(null) }

  // The oldest loaded session may be an arbitrary slice of Paging's page boundary rather
  // than the exercise's actual set count for that day - holding it back until pagination
  // is exhausted keeps its row summary (set count, top weight) and its bubble/PR status
  // from flickering, and keeps a truncated session from understating its capacity into the
  // effort fit.
  val appendDone = records.loadState.append.endOfPaginationReached
  val retained = remember(records.itemSnapshotList, appendDone) {
    val items = records.itemSnapshotList.items
    if (appendDone || items.isEmpty()) {
      items
    } else {
      val oldestDay = items.minOf { it.completed.atZone(zone).toLocalDate() }
      items.filterNot { it.completed.atZone(zone).toLocalDate() == oldestDay }
    }
  }

  // Swiping the exercise pager points [history] at a brand-new paging flow, which restarts
  // collectAsLazyPagingItems() from a Loading refresh - holding the previous exercise's
  // retained rows until the new source's first page actually lands keeps the chart/list from
  // blanking to a spinner on every swipe.
  val isRefreshing = records.loadState.refresh is LoadState.Loading
  val shownRetained = remember { mutableStateOf(retained) }
  SideEffect {
    if (!isRefreshing) shownRetained.value = retained
  }
  val displayRetained = if (isRefreshing) shownRetained.value else retained

  val sessions = remember(displayRetained) {
    displayRetained.groupBy { it.completed.atZone(zone).toLocalDate() }
      .map { (day, sets) -> SessionGroup(day, sets.sortedBy { it.completed }) }
  }
  val series = remember(displayRetained) { EffortModel.score(displayRetained.map { it.toEffortSet() }) }
  val sessionIndexByDay = remember(displayRetained) {
    displayRetained.map { it.completed.atZone(zone).toLocalDate() }.distinct().sorted()
      .withIndex().associate { (i, day) -> day to i }
  }
  val bestBySessionIndex = remember(series) {
    series.sets.groupBy { it.sessionIndex }.mapValues { (_, scored) -> scored.maxBy { it.capacity } }
  }
  val bestCapacityOverall = remember(bestBySessionIndex) {
    bestBySessionIndex.values.maxOfOrNull { it.capacity }
  }

  var expandedOverrides by rememberSaveable { mutableStateOf<Map<Long, Boolean>>(emptyMap()) }
  fun isExpanded(day: LocalDate) =
    expandedOverrides[day.toEpochDay()] ?: (day == sessions.firstOrNull()?.day)

  val barColors = appBarColors()
  val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
  // Width-compact portrait phones have plenty of height and should keep the default bar - only
  // shrink it when height is actually the scarce dimension, independent of isLandscape (which is
  // about the chart/list split above, not this).
  val compactHeight = !currentWindowAdaptiveInfoV2().windowSizeClass.isHeightAtLeastBreakpoint(480)
  Scaffold(
    modifier = modifier,
    // Only the horizontal cutout inset - the session LazyColumn below already applies its own
    // navigationBars-bottom contentPadding, so including navigationBars here too would double
    // it up. Gated by affectedByCutout - a cutout over ExerciseMainPane's pane instead shouldn't
    // cost this pane any width for it.
    contentWindowInsets = if (affectedByCutout) {
      WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
    } else {
      WindowInsets(0, 0, 0, 0)
    },
    topBar = {
      TopAppBar(
        title = {
          // TODO localize
          Text("Set History")
        },
        // Matches ExerciseMainPane's top bar height when both are short on height - a portrait
        // phone is width-compact too but has height to spare, so keeps the default bar.
        expandedHeight = if (compactHeight) 48.dp else TopAppBarDefaults.TopAppBarExpandedHeight,
        windowInsets = TopAppBarDefaults.windowInsets.union(
          if (affectedByCutout) {
            WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
          } else {
            WindowInsets(0, 0, 0, 0)
          }
        ),
        colors = barColors,
        navigationIcon = {
          if (showBackButton) {
            IconButton(onBack) {
              Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                // TODO localize
                "back to exercise"
              )
            }
          }
        },
        actions = {
          Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            IconButton({ records.refresh() }) {
              Icon(
                Icons.Default.Refresh,
                // TODO localize
                "refresh"
              )
            }
            if (isRefreshing) {
              CircularProgressIndicator(Modifier.size(40.dp), strokeWidth = 2.dp)
            }
          }
        }
      )
    }
  ) { contentPadding ->
    // A single spinner spanning the whole pane rather than one wedged into the list - the
    // chart pane doesn't render at all yet during the initial load, so a list-only spinner left
    // it looking broken beside an empty chart. Only for the genuine first load though - once
    // shownRetained holds a previous exercise's rows, fall through and keep showing those
    // instead of blanking to a spinner on every pager swipe.
    if (isRefreshing && shownRetained.value.isEmpty()) {
      Box(
        Modifier.fillMaxSize().padding(contentPadding),
        contentAlignment = Alignment.Center
      ) {
        LoadingView()
      }
      return@Scaffold
    }

    // A permanently-mounted supporting pane (unlike a dismissable drawer) needs its own
    // explanation for the zero-records case rather than an empty chart beside an empty list -
    // on medium+ width this pane sits on screen the whole time a brand-new exercise is worked.
    if (displayRetained.isEmpty() && appendDone) {
      Box(
        Modifier.fillMaxSize().padding(contentPadding),
        contentAlignment = Alignment.Center
      ) {
        Column(
          Modifier.padding(32.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(
            Icons.Default.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.size(48.dp)
          )
          Text(
            // TODO localize
            "No sets logged yet",
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center
          )
          Text(
            // TODO localize
            "Complete a set to start building your history here",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
          )
        }
      }
      return@Scaffold
    }

    // AdaptiveExercisePanes applies one ratio to both axes, but the list's good portrait
    // width (2/3 of a narrow screen) and its good landscape width (which only needs to stay
    // roughly that same absolute width, not 2/3 of a much wider screen) aren't the same
    // fraction - a landscape screen is wide enough that giving the chart a bigger share still
    // leaves the list at a comparable width to portrait's, rather than needlessly wide.
    val chartSplitRatio = if (isLandscape) {
      0.6f
    } else {
      1f / 3f
    }

    // Chart left / list right in landscape, chart top / list bottom in portrait - reuses the
    // same reflow PagerExercise.kt uses for its instructions/set-detail split.
    AdaptiveExercisePanes(
      Modifier
        .fillMaxSize()
        .padding(contentPadding),
      splitRatio = chartSplitRatio,
      first = {
        // Matches SessionRow's own horizontal inset (10.dp) so the chart pane doesn't sit
        // flush against the drawer edge or the header the way the list's Card rows don't
        // either - the elevated Card had no margin of its own in any orientation, most
        // visible in landscape where it touched both the header and the pane's bottom edge.
        // windowInsetsPadding matches the list pane's own navigationBars-bottom protection below.
        Box(
          Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
            .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
          if (LocalFeatures.current.flags[ConfigProvider.Companion.Feature.RECORD_CHART_TYPE] == "effort") {
            if (bestBySessionIndex.isNotEmpty()) {
              EffortHistoryCard(
                Modifier.fillMaxSize(),
                bestBySessionIndex = bestBySessionIndex,
                trend = series.trend,
                zone = zone
              )
            }
          } else if (displayRetained.isNotEmpty()) {
            val items = remember(displayRetained) {
              displayRetained.reversed()
            }
            if (LocalFeatures.current.flags[ConfigProvider.Companion.Feature.RECORD_CHART_TYPE] == "bubble-exploded") {
              val data = remember(items) {
                items.map { BubbleData(it.completed, it.weight.toFloat(), it.reps) }
              }

              BubbleChartExploded(Modifier.fillMaxSize(), data = data, inverseRelationship = true)
            } else if (LocalFeatures.current.flags[ConfigProvider.Companion.Feature.RECORD_CHART_TYPE] == "bubble") {
              val data = remember(items) {
                items.map { BubbleData(it.completed, it.weight.toFloat(), it.reps) }
              }

              BubbleChart(Modifier.fillMaxSize(), data = data, inverseRelationship = true)
            } else {
              val data = remember(items) {
                items.map { it.completed to it.weight.toFloat() }
              }
              LineChart(Modifier.fillMaxSize(), data = data)
            }
          }
        }
      },
      second = {
        LazyColumn(
          Modifier.fillMaxSize(),
          contentPadding = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom).asPaddingValues()
        ) {
          items(sessions, key = { it.day.toEpochDay() }) { session ->
            val scored = bestBySessionIndex[sessionIndexByDay[session.day]]
            SessionRow(
              session = session,
              isPR = scored != null && scored.capacity == bestCapacityOverall,
              expanded = isExpanded(session.day),
              onToggle = {
                expandedOverrides = expandedOverrides +
                  (session.day.toEpochDay() to !isExpanded(session.day))
              },
              onEditSet = { editingRecord = it }
            )
          }
          if (records.loadState.append is LoadState.Loading) {
            item {
              Row(Modifier.fillMaxWidth()) {
                LoadingView()
              }
            }
          }
        }
      }
    )
  }

  editingRecord?.let { record ->
    EditSetRecordDialog(
      record = record,
      onDismissRequest = { editingRecord = null },
      onSave = { weight, reps ->
        onUpdateRecord(record.exercise, record.completed, weight, reps)
        editingRecord = null
      },
      onDelete = {
        confirmingDelete = record
        editingRecord = null
      }
    )
  }

  confirmingDelete?.let { record ->
    DeleteSetRecordConfirmDialog(
      onDismissRequest = { confirmingDelete = null },
      onConfirm = {
        onDeleteRecord(record.exercise, record.completed)
        confirmingDelete = null
      }
    )
  }
}

private data class SessionGroup(val day: LocalDate, val sets: List<SetRecord>) {
  val topWeight = sets.maxOf { it.weight }
  val volume = sets.sumOf { it.reps * it.weight }
}

@Composable
private fun SessionRow(
  session: SessionGroup,
  isPR: Boolean,
  expanded: Boolean,
  onToggle: () -> Unit,
  onEditSet: (SetRecord) -> Unit
) {
  val dayFormat = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }
  val timeFormat = remember {
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())
  }

  Card(
    Modifier
      .fillMaxWidth()
      .padding(horizontal = 10.dp, vertical = 4.dp),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Column {
      Row(
        Modifier
          .fillMaxWidth()
          .clickable(onClick = onToggle)
          .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Column(Modifier.weight(1f)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Text(dayFormat.format(session.day), style = MaterialTheme.typography.titleSmall)
            if (isPR) {
              Text(
                // TODO localize
                "PR",
                style = MaterialTheme.typography.labelSmall,
                color = ExtendedTheme.colors.goodAttention.onColor,
                modifier = Modifier
                  .background(ExtendedTheme.colors.goodAttention.color, RoundedCornerShape(4.dp))
                  .padding(horizontal = 4.dp, vertical = 1.dp)
              )
            }
          }
          Text(
            // TODO localize
            "${session.sets.size} sets · ${String.format("%.0f", session.volume)} lbs volume",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
          )
        }
        Text(String.format("%.1f", session.topWeight), style = MaterialTheme.typography.labelLarge)
        Icon(
          if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
          contentDescription = null
        )
      }
      if (expanded) {
        session.sets.forEachIndexed { index, set ->
          HorizontalDivider()
          Row(
            Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(timeFormat.format(set.completed), style = MaterialTheme.typography.labelSmall)
            Text(set.reps.toString(), style = MaterialTheme.typography.bodyMedium)
            Text(String.format("%.1f", set.weight), style = MaterialTheme.typography.bodyMedium)
            IconButton(
              onClick = { onEditSet(set) },
              modifier = Modifier.size(32.dp)
            ) {
              Icon(
                Icons.Default.Edit,
                // TODO localize
                "edit set",
                modifier = Modifier.size(18.dp)
              )
            }
          }
        }
      }
    }
  }
}

/**
 * The effort chart, titled and cared, plotting one bubble per session (its best set) against
 * uniform session slots rather than calendar time - real histories jump months between
 * sessions, and a calendar x-axis would leave most of the plot empty. [EffortModel.score]
 * still fits on real day-offset regardless of this display choice.
 */
/**
 * The coarsest tick format that keeps thinned labels legible at the loaded span - avoids the
 * month/year pattern rounding a handful of days apart down to identical text, while still
 * collapsing a multi-year history to something coarser than a day-of-month number nobody
 * needs at that scale.
 */
private fun axisFormatterFor(spanDays: Long): DateTimeFormatter = when {
  spanDays < 120 -> DateTimeFormatter.ofPattern("MMM d")
  spanDays < 730 -> DateTimeFormatter.ofPattern("MMM ''yy")
  else -> DateTimeFormatter.ofPattern("yyyy")
}

@Composable
private fun EffortHistoryCard(
  modifier: Modifier = Modifier,
  bestBySessionIndex: Map<Int, ScoredSet>,
  trend: List<TrendPoint>,
  zone: ZoneId
) {
  val sortedEntries = remember(bestBySessionIndex) {
    bestBySessionIndex.entries.sortedBy { it.key }
  }

  val points = remember(sortedEntries) {
    sortedEntries.map { (sessionIndex, scored) ->
      EffortPoint(
        sessionIndex.toFloat(),
        scored.source.weight.toFloat(),
        EffortModel.repSize(scored.source.reps),
        scored.zone,
        z = scored.z
      )
    }
  }
  val expectedWeightBySession = remember(trend) {
    trend.associateBy({ it.sessionIndex }, { it.expectedWeight })
  }
  val trendRuns = remember(sortedEntries, expectedWeightBySession) {
    buildTrendRuns(sortedEntries.map { it.key.toFloat() to it.key }, expectedWeightBySession)
  }
  // The funnel background: the weight range a session's fitted expectedCapacity implies at a
  // heavy (4-rep) and a light (15-rep) target, connected with straight lines for now.
  val bandTopBySession = remember(trend) {
    trend.associateBy({ it.sessionIndex }, { EffortModel.weightForReps(it.expectedCapacity, 4) })
  }
  val bandBottomBySession = remember(trend) {
    trend.associateBy({ it.sessionIndex }, { EffortModel.weightForReps(it.expectedCapacity, 15) })
  }
  val bandTopRuns = remember(sortedEntries, bandTopBySession) {
    buildTrendRuns(sortedEntries.map { it.key.toFloat() to it.key }, bandTopBySession)
  }
  val bandBottomRuns = remember(sortedEntries, bandBottomBySession) {
    buildTrendRuns(sortedEntries.map { it.key.toFloat() to it.key }, bandBottomBySession)
  }
  val gapMarks = remember(sortedEntries) {
    sortedEntries.zipWithNext().mapNotNull { (a, b) ->
      if (b.value.dayOffset - a.value.dayOffset > GapThresholdDays) a.key + 0.5f else null
    }
  }
  val xLabels = remember(sortedEntries, zone) {
    if (sortedEntries.isEmpty()) {
      emptyList()
    } else {
      val spanDays = sortedEntries.last().value.dayOffset - sortedEntries.first().value.dayOffset
      val formatter = axisFormatterFor(spanDays)
      val step = (sortedEntries.size / MaxXLabels).coerceAtLeast(1)
      sortedEntries.filterIndexed { index, _ -> index % step == 0 }
        .map { (sessionIndex, scored) ->
          sessionIndex.toFloat() to formatter.format(scored.source.completed.atZone(zone))
        }
        // The whole point of picking a granularity is distinct ticks - a residual collision
        // (e.g. sessions packed tightly at extreme thinning) drops the later duplicate rather
        // than showing two ticks with the same rounded text.
        .distinctBy { it.second }
    }
  }
  val yLabels = remember(points) {
    if (points.isEmpty()) {
      emptyList()
    } else {
      val minWeight = points.minOf { it.weight }
      val maxWeight = points.maxOf { it.weight }
      listOf(minWeight, (minWeight + maxWeight) / 2f, maxWeight)
        .distinct()
        .map { it to it.roundToInt().toString() }
    }
  }

  Card(modifier, elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
    Column(Modifier.padding(12.dp)) {
      Text(stringResource(R.string.effort_label), style = MaterialTheme.typography.titleSmall)
      Text(
        stringResource(R.string.effort_chart_subtitle),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
      )
      EffortChart(
        Modifier
          .fillMaxWidth()
          .weight(1f),
        points = points,
        trend = trendRuns,
        bandTop = bandTopRuns,
        bandBottom = bandBottomRuns,
        // Same source map as trendRuns (both keyed off expectedWeightBySession), and the same
        // null pattern as bandTopBySession/bandBottomBySession (all three come from trend), so
        // it lines up run-for-run with bandTop/bandBottom without needing its own build.
        bandMid = trendRuns,
        xLabels = xLabels,
        yLabels = yLabels,
        gapMarks = gapMarks
      )
      if (trend.isEmpty()) {
        // No SESSION-sourced trend point exists yet - say so explicitly instead of a legend
        // decoding colors that haven't appeared. The drawer only ever calls plain
        // EffortModel.score(), so it never sees the strip's bootstrap trend either; the wait
        // is real, not just a rendering gap.
        val remaining = (EffortConfig.Default.minPriorSessions + 1 - bestBySessionIndex.size)
          .coerceAtLeast(1)
        Text(
          pluralStringResource(R.plurals.sessions_until_trend, remaining, remaining),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
        )
      } else {
        EffortLegend(
          Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
        )
      }
    }
  }
}

@Preview
@Composable
private fun PreviewSetRecordList() {
  // Spans a multi-year gap so the drawer preview exercises grouping, the PR badge, and the
  // chart's gap marks together - same fixture shape as the drafted redesign this was based on.
  val records = listOf(
    SetRecord(20.0, 8, "X", "Y", Instant.parse("2021-11-16T06:44:50Z"), "Z"),
    SetRecord(20.0, 8, "X", "Y", Instant.parse("2021-11-16T06:46:29Z"), "Z"),
    SetRecord(20.0, 8, "X", "Y", Instant.parse("2021-11-16T06:49:20Z"), "Z"),
    SetRecord(15.0, 11, "X", "Y", Instant.parse("2023-01-17T06:29:26Z"), "Z"),
    SetRecord(15.0, 10, "X", "Y", Instant.parse("2023-01-17T06:31:18Z"), "Z"),
    SetRecord(15.0, 10, "X", "Y", Instant.parse("2023-01-17T06:33:05Z"), "Z"),
    SetRecord(35.0, 10, "X", "Y", Instant.parse("2023-06-05T06:32:29Z"), "Z"),
    SetRecord(35.0, 11, "X", "Y", Instant.parse("2023-06-05T06:34:27Z"), "Z"),
    SetRecord(35.0, 11, "X", "Y", Instant.parse("2023-06-05T06:36:36Z"), "Z"),
    SetRecord(40.0, 11, "X", "Y", Instant.parse("2023-06-13T06:24:07Z"), "Z"),
    SetRecord(40.0, 10, "X", "Y", Instant.parse("2023-06-13T06:25:59Z"), "Z"),
    SetRecord(40.0, 10, "X", "Y", Instant.parse("2023-06-13T06:27:55Z"), "Z")
  )
  val data = PagingData.from(
    records.reversed(),
    sourceLoadStates = LoadStates(
      LoadState.NotLoading(true),
      LoadState.NotLoading(true),
      LoadState.NotLoading(true)
    )
  )

  SetRecordList(
    Modifier
      .background(Color.White)
      .height(500.dp)
      .width(360.dp),
    SetHistory(flowOf(data))
  )
}

@Preview
@Composable
private fun PreviewSetRecordListEffortChart() {
  CompositionLocalProvider(
    LocalFeatures provides ConfigProvider.Companion.RemoteConfig(
      mapOf(ConfigProvider.Companion.Feature.RECORD_CHART_TYPE to "effort")
    )
  ) {
    PreviewSetRecordList()
  }
}

/**
 * Only 2 sessions, days apart - regression check for both the adaptive axis (previously
 * always rendered month/year ticks, which round a 2-day gap to identical text) and the
 * countdown caption that should replace the legend while EffortModel.score() has no
 * SESSION-sourced trend point yet.
 */
@Preview
@Composable
private fun PreviewSetRecordListEffortChartFewSessions() {
  val records = listOf(
    SetRecord(20.0, 8, "X", "Y", Instant.parse("2026-07-12T06:44:50Z"), "Z"),
    SetRecord(20.0, 8, "X", "Y", Instant.parse("2026-07-12T06:46:29Z"), "Z"),
    SetRecord(20.0, 8, "X", "Y", Instant.parse("2026-07-12T06:49:20Z"), "Z"),
    SetRecord(22.5, 8, "X", "Y", Instant.parse("2026-07-26T06:29:26Z"), "Z"),
    SetRecord(22.5, 8, "X", "Y", Instant.parse("2026-07-26T06:31:18Z"), "Z"),
    SetRecord(22.5, 8, "X", "Y", Instant.parse("2026-07-26T06:33:05Z"), "Z")
  )
  val data = PagingData.from(
    records.reversed(),
    sourceLoadStates = LoadStates(
      LoadState.NotLoading(true),
      LoadState.NotLoading(true),
      LoadState.NotLoading(true)
    )
  )

  CompositionLocalProvider(
    LocalFeatures provides ConfigProvider.Companion.RemoteConfig(
      mapOf(ConfigProvider.Companion.Feature.RECORD_CHART_TYPE to "effort")
    )
  ) {
    SetRecordList(
      Modifier
        .background(Color.White)
        .height(500.dp)
        .width(360.dp),
      SetHistory(flowOf(data))
    )
  }
}

@Preview
@Composable
private fun PreviewSetRecordListEmpty() {
  val data = PagingData.empty<SetRecord>(
    sourceLoadStates = LoadStates(
      LoadState.NotLoading(true),
      LoadState.NotLoading(true),
      LoadState.NotLoading(true)
    )
  )

  SetRecordList(
    Modifier
      .background(Color.White)
      .height(500.dp)
      .width(360.dp),
    SetHistory(flowOf(data))
  )
}
