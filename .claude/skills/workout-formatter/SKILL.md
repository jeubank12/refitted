---
name: workout-formatter
description: Convert a free-form workout description (document, markdown, or scraped web page) into Refitted app data — the DynamoDB items (Plan, Days, ExerciseSets, Exercises) that drive the Android app. Use when asked to import, format, or upload a workout plan/program into the app.
---

# Workout Description → Refitted App Models

Turn any human-readable workout program into the single-table DynamoDB items the app
consumes. The pipeline is: **source text → plan-spec JSON → validate/emit → review →
upload**. The intermediate plan-spec JSON is the canonical artifact — keep it, review it
with the user, and only then generate DynamoDB items from it.

Read `reference/data-model.md` before converting — it defines every field's semantics
and how the app renders them.

## Step 1 — Extract the source text

- **.docx**: `python3 .claude/skills/workout-formatter/scripts/docx_to_text.py <file.docx>`
- **Web page** (scraper mode): fetch the URL, reduce to the plan text. Same pipeline from here on.
- **Markdown/plain text**: use as-is.

## Step 2 — Convert to plan-spec JSON

Write a `<plan-name>.plan.json` file. Schema (see `examples/week-one-plan.json` for a
complete worked example):

```json
{
  "workout": "Unique Plan Name",          // DynamoDB Disc; shown in the plan picker
  "description": "One-line plan summary", // shown in the plan picker
  "globalAlternateLabels": [],            // only for plans with plan-wide A/B variants
  "days": [
    {
      "day": 1,
      "restDay": false,
      "sets": [
        {
          "step": "1",
          "exercise": "Glutes_Glute Kickback",
          "exerciseDescription": "How-to text, stored once per exercise",
          "sets": 3,
          "reps": 12,
          "repsRange": 0,
          "repsUnit": "",
          "repsSequence": [],
          "toFailure": false,
          "rest": 90,
          "timeLimit": null,
          "timeLimitUnit": null,
          "note": "Per-set coaching cue / load guidance"
        }
      ]
    },
    { "day": 6, "restDay": true }
  ]
}
```

### Conversion cheat sheet

| Source notation | Plan-spec encoding |
|---|---|
| `3 × 12` | `sets: 3, reps: 12` |
| `3 × 10–12` (rep range) | `sets: 3, reps: 10, repsRange: 2` (app shows "10-12") |
| `4 × 20–30 sec hold` | `sets: 4, reps: 20, repsRange: 10, repsUnit: "seconds"` |
| `12, 10, 8` (pyramid) | `sets: 3, repsSequence: [12, 10, 8]` (also set `reps` to the first value) |
| `AMRAP` / `to failure` | `toFailure: true` |
| Timed set / EMOM-style cap | `timeLimit: 30, timeLimitUnit: "seconds"` (or `"minutes"`) — renders a timer |
| Superset A1/A2 (or "then immediately") | steps `"1.1"` and `"1.2"` (same leading number = one superset) |
| "X **or** Y" (either movement) | alternate steps `"1.a"` and `"1.b"` — app shows a toggle |
| Rest between sets | `rest` in seconds (single value — pick within a stated range) |
| Rest / no-lift day | `{ "day": N, "restDay": true }` |
| Load guidance ("~2 RIR", "light") | goes in `note` — the app tracks actual weight via records |
| Day heading / session intent | no day-level field exists; fold into the first set's `note` if it matters |

### Conventions

- **Exercise ids** are `Category_Movement Name` (e.g. `Shoulders_Cable Lateral Raise`).
  The app displays only the part after the first `_`. Use muscle-group categories
  (Glutes, Hamstrings, Shoulders, Back, Chest, Biceps, Triceps, Calves, Core, Legs).
  Reuse the exact same id when a movement appears on multiple days — the
  `exerciseDescription` is stored once per id and must not conflict.
- **`note` vs `exerciseDescription`**: the note is per-set (load guidance, tempo, cues
  for that day); the description is per-movement (setup, machine, form) and shown
  wherever the exercise appears.
- **Days are 1-based and contiguous** through the plan's last day; `totalDays` is
  inferred from the max day. Weekly programs typically use days 1–7 (Mon–Sun).
- **Max 9 primary steps per day.** Steps sort lexicographically in the app
  (`"10"` sorts before `"2"`), so a tenth step would render out of order. Fold
  optional finishers into notes or supersets if a day runs long.
- Numbered steps only: `N`, `N.M` (superset member), with optional `.a`/`.b`/`.c`
  alternate suffix on either form.

## Step 3 — Validate and emit DynamoDB items

```bash
python3 .claude/skills/workout-formatter/scripts/plan_to_dynamo.py <plan.plan.json> --summary
python3 .claude/skills/workout-formatter/scripts/plan_to_dynamo.py <plan.plan.json> --out <dir> [--table refitted-exercise]
```

`--summary` prints a human-readable day-by-day table — **show this to the user for
review before uploading**. The emit run writes `batch-NN.json` files (25 items per
batch, `aws dynamodb batch-write-item` request shape).

## Step 4 — Upload

Requires AWS credentials. Table names: `refitted-exercise` (prod), `refitted.dev01`
(dev — see `scripts/insert_items.py` at the repo root for prior art). **Confirm the
target table with the user before writing anything.**

```bash
for f in <dir>/batch-*.json; do
  aws dynamodb batch-write-item --request-items "file://$f" --region us-east-2
done
```

A plan is only **visible** to users whose group lists it. After uploading, add the
workout to the right group definition (`Id` = group name, `Disc` = `"Groups"`):

```bash
aws dynamodb update-item --table-name <table> --region us-east-2 \
  --key '{"Id": {"S": "<group>"}, "Disc": {"S": "Groups"}}' \
  --update-expression "ADD Workouts :w" \
  --expression-attribute-values '{":w": {"SS": ["<workout name>"]}}'
```

Ask the user which group (e.g. their personal/test group) — never add to a paid group
without explicit confirmation.

## Future modes (not yet built)

- **Scraper mode**: given a URL to an online program, fetch → extract the schedule →
  same pipeline from Step 2. The plan-spec JSON stays the contract.
- **Build mode**: an in-app flow where a user assembles a free-form workout and tracks
  sets as they go. The current model can fake it today: emit a 1-day plan (or one day
  per session) into a personal group and extend it as sessions happen — but a real
  build mode needs app work (user-owned plans, an append-a-set UI). Treat requests for
  this as app feature design, not a formatting task.
