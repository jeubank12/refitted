#!/usr/bin/env python3
"""Validate a workout plan-spec JSON and emit DynamoDB batch-write items.

Usage:
  plan_to_dynamo.py plan.plan.json --summary
  plan_to_dynamo.py plan.plan.json --out out_dir [--table refitted-exercise]

The plan-spec schema is documented in the workout-formatter SKILL.md.
Output files are `aws dynamodb batch-write-item --request-items file://...` shaped,
25 items per batch.
"""
import argparse
import json
import re
import sys
from pathlib import Path

STEP_RE = re.compile(r"^\d+(\.\d+)?(\.[a-z])?$")
PRIMARY_RE = re.compile(r"^(\d+(?:\.\d+)?)(\.[a-z])?$")
SUPERSET_RE = re.compile(r"^(\d+)\.(\d+)$")

BATCH_SIZE = 25


class PlanError(Exception):
    pass


def fail(errors):
    for e in errors:
        print(f"ERROR: {e}", file=sys.stderr)
    sys.exit(1)


def validate(plan):
    errors = []
    warnings = []

    workout = plan.get("workout", "")
    if not isinstance(workout, str) or not workout.strip():
        errors.append("'workout' must be a non-empty string")
    elif workout != workout.strip():
        errors.append("'workout' has leading/trailing whitespace")

    days = plan.get("days")
    if not isinstance(days, list) or not days:
        fail(errors + ["'days' must be a non-empty list"])

    seen_days = set()
    exercise_descriptions = {}
    for d in days:
        day = d.get("day")
        if not isinstance(day, int) or day < 1:
            errors.append(f"day {day!r}: must be an integer >= 1")
            continue
        if day in seen_days:
            errors.append(f"day {day}: duplicated")
        seen_days.add(day)

        rest = bool(d.get("restDay", False))
        sets = d.get("sets", [])
        if rest and sets:
            errors.append(f"day {day}: restDay with sets — remove one")
            continue
        if not rest and not sets:
            errors.append(f"day {day}: no sets and not a restDay")
            continue

        seen_steps = set()
        primaries = {}
        for s in sets:
            step = s.get("step", "")
            loc = f"day {day} step {step!r}"
            if not STEP_RE.match(step or ""):
                errors.append(f"{loc}: invalid step (want N, N.M, optionally .a/.b suffix)")
                continue
            if step in seen_steps:
                errors.append(f"{loc}: duplicated step")
            seen_steps.add(step)

            m = PRIMARY_RE.match(step)
            primary, alt = m.group(1), m.group(2)
            primaries.setdefault(primary, set()).add(alt or "")

            leading = int(primary.split(".")[0])
            if leading > 9:
                warnings.append(
                    f"{loc}: primary step > 9 sorts lexicographically ('10' before '2') — keep <= 9"
                )

            name = s.get("exercise", "")
            if "_" not in name or not name.split("_", 1)[0] or not name.split("_", 1)[1]:
                errors.append(f"{loc}: exercise must be 'Category_Movement Name', got {name!r}")
            desc = s.get("exerciseDescription")
            if desc:
                prior = exercise_descriptions.get(name)
                if prior is not None and prior != desc:
                    errors.append(
                        f"{loc}: exerciseDescription for {name!r} conflicts with an earlier day"
                    )
                exercise_descriptions[name] = desc

            n_sets = s.get("sets")
            if not isinstance(n_sets, int) or n_sets < 1:
                errors.append(f"{loc}: 'sets' must be an integer >= 1")
            reps = s.get("reps", 0)
            if not isinstance(reps, int) or reps < 0:
                errors.append(f"{loc}: 'reps' must be an integer >= 0")
            for field in ("repsRange", "rest"):
                v = s.get(field, 0)
                if not isinstance(v, int) or v < 0:
                    errors.append(f"{loc}: '{field}' must be an integer >= 0")
            seq = s.get("repsSequence", [])
            if seq:
                if not all(isinstance(r, int) and r >= 0 for r in seq):
                    errors.append(f"{loc}: 'repsSequence' must be integers >= 0")
                elif isinstance(n_sets, int) and len(seq) != n_sets:
                    warnings.append(f"{loc}: repsSequence length {len(seq)} != sets {n_sets}")
            tl, tlu = s.get("timeLimit"), s.get("timeLimitUnit")
            if (tl is None) != (tlu is None):
                errors.append(f"{loc}: timeLimit and timeLimitUnit must be set together")
            if tlu not in (None, "seconds", "minutes"):
                errors.append(f"{loc}: timeLimitUnit must be 'seconds' or 'minutes'")

        # alternates: bare step must not coexist with lettered variants
        for primary, alts in primaries.items():
            if "" in alts and len(alts) > 1:
                errors.append(
                    f"day {day} step {primary}: bare step mixed with alternates {sorted(a for a in alts if a)}"
                )
        # superset sanity: a lone N.M member is pointless
        super_groups = {}
        for primary in primaries:
            m = SUPERSET_RE.match(primary)
            if m:
                super_groups.setdefault(m.group(1), set()).add(m.group(2))
        for leader, members in super_groups.items():
            if len(members) < 2:
                warnings.append(
                    f"day {day}: superset {leader}.x has a single member — use step {leader} instead"
                )
            if leader in primaries:
                errors.append(f"day {day}: step {leader} coexists with superset members {leader}.x")

    max_day = max(seen_days, default=0)
    missing = sorted(set(range(1, max_day + 1)) - seen_days)
    if missing:
        warnings.append(f"days {missing} missing — mark no-lift days with restDay: true")

    if errors:
        fail(errors)
    for w in warnings:
        print(f"WARNING: {w}", file=sys.stderr)


def s(v):
    return {"S": str(v)}


def n(v):
    return {"N": str(v)}


def build_items(plan):
    workout = plan["workout"]
    items = []

    plan_item = {"Id": s("Plan"), "Disc": s(workout)}
    if plan.get("description"):
        plan_item["Description"] = s(plan["description"])
    labels = plan.get("globalAlternateLabels") or []
    if labels:
        plan_item["GlobalAlternateLabels"] = s(";".join(labels))
    items.append(plan_item)

    exercises = {}
    for d in sorted(plan["days"], key=lambda d: d["day"]):
        day = d["day"]
        if d.get("restDay"):
            items.append({"Id": s(day), "Disc": s(workout), "Exercises": {"SS": ["0"]}})
            continue
        steps = [x["step"] for x in d["sets"]]
        items.append({"Id": s(day), "Disc": s(workout), "Exercises": {"SS": steps}})
        for x in d["sets"]:
            item = {
                "Id": s(f"{day}.{x['step']}"),
                "Disc": s(workout),
                "Name": s(x["exercise"]),
                "Reps": n(x.get("reps", 0)),
                "Sets": n(x["sets"]),
                "ToFailure": {"BOOL": bool(x.get("toFailure", False))},
                "Rest": n(x.get("rest", 0)),
                "RepsRange": n(x.get("repsRange", 0)),
            }
            if x.get("note"):
                item["Note"] = s(x["note"])
            if x.get("repsUnit"):
                item["RepsUnit"] = s(x["repsUnit"])
            if x.get("repsSequence"):
                item["RepsSequence"] = s(",".join(str(r) for r in x["repsSequence"]))
            if x.get("timeLimit") is not None:
                item["TimeLimit"] = n(x["timeLimit"])
                item["TimeLimitUnit"] = s(x["timeLimitUnit"])
            items.append(item)
            if x.get("exerciseDescription"):
                exercises[x["exercise"]] = x["exerciseDescription"]
            else:
                exercises.setdefault(x["exercise"], None)

    for name, desc in sorted(exercises.items()):
        ex = {"Id": s(name), "Disc": s(workout)}
        if desc:
            ex["Note"] = s(desc)
        items.append(ex)

    return items


def reps_display(x):
    reps = x.get("reps", 0)
    if x.get("repsSequence"):
        target = ",".join(str(r) for r in x["repsSequence"])
    elif x.get("repsRange"):
        target = f"{reps}-{reps + x['repsRange']}"
    else:
        target = str(reps)
    unit = f" {x['repsUnit']}" if x.get("repsUnit") else ""
    failure = " (or to failure)" if x.get("toFailure") else ""
    return f"{x['sets']} x {target}{unit}{failure}"


def print_summary(plan, item_count):
    print(f"Plan: {plan['workout']}")
    if plan.get("description"):
        print(f"  {plan['description']}")
    for d in sorted(plan["days"], key=lambda d: d["day"]):
        if d.get("restDay"):
            print(f"\nDay {d['day']}: REST")
            continue
        print(f"\nDay {d['day']}:")
        for x in d["sets"]:
            note = f"  — {x['note']}" if x.get("note") else ""
            print(f"  [{x['step']:>5}] {x['exercise']}: {reps_display(x)}{note}")
    print(f"\n{item_count} DynamoDB items total")


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("plan", type=Path)
    ap.add_argument("--table", default="refitted-exercise")
    ap.add_argument("--out", type=Path, help="directory for batch-NN.json files")
    ap.add_argument("--summary", action="store_true", help="print review summary only")
    args = ap.parse_args()

    plan = json.loads(args.plan.read_text())
    validate(plan)
    items = build_items(plan)

    if args.summary or not args.out:
        print_summary(plan, len(items))
        if not args.out:
            print("\n(no --out given; nothing written)")
        return

    args.out.mkdir(parents=True, exist_ok=True)
    for i in range(0, len(items), BATCH_SIZE):
        chunk = items[i : i + BATCH_SIZE]
        body = {args.table: [{"PutRequest": {"Item": item}} for item in chunk]}
        path = args.out / f"batch-{i // BATCH_SIZE:02d}.json"
        path.write_text(json.dumps(body, indent=2) + "\n")
        print(f"wrote {path} ({len(chunk)} items)")
    print(
        f"\nupload with:\n  for f in {args.out}/batch-*.json; do "
        f'aws dynamodb batch-write-item --request-items "file://$f" --region us-east-2; done'
    )


if __name__ == "__main__":
    main()
