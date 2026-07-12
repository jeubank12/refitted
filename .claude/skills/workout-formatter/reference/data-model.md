# Refitted DynamoDB Data Model

Everything lives in **one table** (`refitted-exercise` in prod). Partition key `Id`
(string), sort key `Disc` (string), plus a `Reverse-index` GSI with the keys flipped.
For plan content, `Disc` is always the workout (plan) name; the item type is
distinguished by the shape of `Id`.

Kotlin sources of truth: `dynamo/src/main/kotlin/com/litus_animae/refitted/dynamo/entities/`
and the domain models in `data/src/main/kotlin/com/litus_animae/refitted/data/models/`.

## Item types

### Plan (`DynamoWorkoutPlan`)

| Attribute | Type | Notes |
|---|---|---|
| `Id` | S | literal `"Plan"` |
| `Disc` | S | workout name — unique across all plans, shown in the plan picker |
| `Description` | S | shown in the plan picker |
| `GlobalAlternateLabels` | S | `;`-separated labels; only for plans with a plan-wide A/B toggle (omit otherwise) |

### Day (`DynamoWorkoutDay`)

| Attribute | Type | Notes |
|---|---|---|
| `Id` | S | day number as string, 1-based (`"1"`, `"2"`, …) |
| `Disc` | S | workout name |
| `Exercises` | SS | the day's step ids; a **rest day** is exactly `{"0"}` |

`totalDays` = max day number found; rest days = days whose `Exercises` contains `"0"`.
The app queries days with `attribute_exists(Exercises)`, so every day item needs the set.

### ExerciseSet (`DynamoExerciseSet`)

| Attribute | Type | Notes |
|---|---|---|
| `Id` | S | `"{day}.{step}"` (e.g. `"1.2"`, `"1.2.1"`, `"1.3.a"`) — queried with `BEGINS_WITH "{day}."` |
| `Disc` | S | workout name |
| `Name` | S | exercise id, format `Category_Movement Name`; **must exactly equal** an Exercise item's `Id` (lookup is `EQ`) |
| `Note` | S | per-set coaching note, shown with the set |
| `Reps` | N | target reps (or seconds when `RepsUnit` = `"seconds"`); bottom of the range |
| `Sets` | N | number of sets |
| `ToFailure` | BOOL | renders "(or to failure)" |
| `Rest` | N | rest between sets, seconds |
| `RepsUnit` | S | unit label, capitalized in UI (e.g. `"seconds"` → "Seconds"); empty/absent = plain reps |
| `RepsRange` | N | app renders `Reps`–`Reps+RepsRange` (e.g. Reps=10, Range=2 → "10-12") |
| `TimeLimit` | N | optional; renders a countdown timer |
| `TimeLimitUnit` | S | `"seconds"` (default) or `"minutes"` |
| `RepsSequence` | S | comma-separated per-set reps (e.g. `"12,10,8"`); overrides `Reps` per set index |

### Exercise (`DynamoExercise`)

| Attribute | Type | Notes |
|---|---|---|
| `Id` | S | `Category_Movement Name` — app shows only the part after the first `_` |
| `Disc` | S | workout name (exercises are **per-plan**, not global) |
| `Note` | S | movement description: machine, setup, form |

If the Exercise item is missing the app falls back to an empty description, so sets
never break — but always emit the item when a description exists.

### Group definition (`DynamoGroupDefinition`)

| Attribute | Type | Notes |
|---|---|---|
| `Id` | S | group name (from the user's Firebase token claim; `"anon"`/`"free"` defaults) |
| `Disc` | S | literal `"Groups"` |
| `Workouts` | SS | plan names this group can see |

A plan that is uploaded but not listed in the viewing user's group **does not appear**
in the app.

## Step grammar (`ExerciseSet.step`, the part of `Id` after the day)

| Step | Meaning |
|---|---|
| `2` | plain step 2 of the day |
| `2.1`, `2.2` | superset: same leading number groups them; UI links them and cycles through members |
| `3.a`, `3.b` | alternates of step 3: user toggles between them, does one |
| `2.1.a` | alternate of superset member `2.1` |

Parsing rules (from `ExerciseSet.kt`): `primaryStep` strips a trailing `.letter`;
supersets are detected by `\d+.\d+`; alternates are grouped by equal `primaryStep`.
Do **not** mix a bare step with alternates of the same number (`3` alongside `3.a`).

**Ordering caveat**: steps sort as strings (`order by primaryStep, superSetStep,
alternateStep` in `ExerciseDao`), so `"10"` sorts before `"2"`. Keep primary steps ≤ 9
per day.

## How the app renders a set (from `RepsDisplay.kt`)

- `RepsRange > 0` → "Reps-Reps+Range", plus "(or to failure)" when `ToFailure`
- `RepsUnit` non-blank → the unit becomes the label (e.g. "Seconds" instead of "Reps")
- `RepsSequence` present → per-set target from the sequence
- `TimeLimit` present → countdown timer is shown for the set
- Weight/load is **not** part of the plan — users log actual weight, and `Note` carries
  load guidance ("~2 RIR", "light — pump work")
