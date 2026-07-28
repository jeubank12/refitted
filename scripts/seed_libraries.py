# Seeds equipment-library content (PlanKind.LOCATION / PlanKind.EQUIPMENT) into DynamoDB.
#
# A library is authored as one JSON file - see content/libraries/*.json (gitignored; these are
# our own synthesized descriptions, not workout-program content, and don't belong in the repo
# either way). Each file becomes one "Plan" row plus one row per exercise, written the same way
# admin program content already is:
#
#   { "name": "Planet Fitness", "kind": "LOCATION", "description": "...",
#     "exercises": [ { "id": "Quads_Leg Press", "note": "..." }, ... ] }
#
# Setup: see scripts/insert_items.py's header for AWS credential/boto3 setup - this script shares
# that configuration.
#
# Usage:
#   python seed_libraries.py content/libraries/*.json      # seed specific files
#   python seed_libraries.py                                # seed every file in content/libraries
#   python seed_libraries.py --dry-run [files...]           # print batches, write nothing

import argparse
import glob
import json
import os
import sys

TABLE_NAME = "refitted.dev01"
DEFAULT_CONTENT_DIR = os.path.join(os.path.dirname(__file__), "..", "content", "libraries")
BATCH_SIZE = 25

# Mirrors data/src/main/kotlin/com/litus_animae/refitted/data/models/MuscleGroup.kt - an id whose
# prefix isn't in this set is silently invisible in the picker forever, so we catch it here
# instead of finding out on a device. Keep in sync by hand; it changes rarely.
VALID_MUSCLE_PREFIXES = {
    "Chest",
    "Shoulder", "External Rotator",
    "Bicep", "Biceps",
    "Tricep",
    "Core",
    "Traps",
    "Lats", "Back",
    "Lower Back",
    "Glutes",
    "Hamstrings", "Hamstring",
    "Quads", "Quadricep",
    "Calf",
    "Leg",
    "Agility", "Rope",
    "Compound",
}

VALID_KINDS = {"LOCATION", "EQUIPMENT"}

ERROR_HELP_STRINGS = {
    'ConditionalCheckFailedException': 'Condition check specified in the operation failed, review and update the condition check before retrying',
    'TransactionConflictException': 'Operation was rejected because there is an ongoing transaction for the item, generally safe to retry with exponential back-off',
    'ItemCollectionSizeLimitExceededException': 'An item collection is too large for a Local Secondary Index; consider a Global Secondary Index instead',
    'InternalServerError': 'Internal Server Error, generally safe to retry with exponential back-off',
    'ProvisionedThroughputExceededException': 'Request rate is too high - retry with exponential back-off or raise capacity',
    'ResourceNotFoundException': 'One of the tables was not found, verify table exists before retrying',
    'ServiceUnavailable': 'Had trouble reaching DynamoDB, generally safe to retry with exponential back-off',
    'ThrottlingException': 'Request denied due to throttling, generally safe to retry with exponential back-off',
    'UnrecognizedClientException': 'The request signature is incorrect, most likely an invalid AWS access key ID or secret key',
    'ValidationException': 'The input fails to satisfy the constraints specified by DynamoDB, fix input before retrying',
    'RequestLimitExceeded': 'Throughput exceeds the current throughput limit for your account, increase account level throughput before retrying',
}


class ValidationError(Exception):
    pass


def load_library(path):
    with open(path, "r", encoding="utf-8") as f:
        library = json.load(f)

    name = library.get("name")
    kind = library.get("kind")
    exercises = library.get("exercises", [])

    if not name:
        raise ValidationError(f"{path}: missing \"name\"")
    if kind not in VALID_KINDS:
        raise ValidationError(f"{path}: \"kind\" must be one of {sorted(VALID_KINDS)}, got {kind!r}")
    if not exercises:
        raise ValidationError(f"{path}: \"exercises\" is empty")

    seen_ids = set()
    for exercise in exercises:
        exercise_id = exercise.get("id", "")
        if "_" not in exercise_id:
            raise ValidationError(f"{path}: exercise id {exercise_id!r} is missing its \"{{Muscle}}_\" prefix")
        prefix = exercise_id.split("_", 1)[0]
        if prefix not in VALID_MUSCLE_PREFIXES:
            raise ValidationError(
                f"{path}: exercise id {exercise_id!r} has prefix {prefix!r}, "
                f"which MuscleGroup doesn't query - it would never appear in the picker"
            )
        if exercise_id in seen_ids:
            raise ValidationError(f"{path}: duplicate exercise id {exercise_id!r}")
        seen_ids.add(exercise_id)

    return library


def build_items(library):
    items = [
        {
            "Id": {"S": "Plan"},
            "Disc": {"S": library["name"]},
            "Description": {"S": library.get("description", "")},
            "Kind": {"S": library["kind"]},
        }
    ]
    for exercise in library["exercises"]:
        item = {
            "Id": {"S": exercise["id"]},
            "Disc": {"S": library["name"]},
        }
        note = exercise.get("note", "")
        if note:
            item["Note"] = {"S": note}
        items.append(item)
    return items


def chunk_batches(items, table_name=TABLE_NAME, size=BATCH_SIZE):
    chunks = [items[i:i + size] for i in range(0, len(items), size)]
    return [
        {table_name: [{"PutRequest": {"Item": item}} for item in chunk]}
        for chunk in chunks
    ]


def create_dynamodb_client(region="us-east-2"):
    # Imported lazily so --dry-run works without boto3 installed - it's exactly the mode someone
    # reaches for before setting up AWS credentials in the first place.
    import boto3
    return boto3.client("dynamodb", region_name=region)


def execute_batch_write(dynamodb_client, batches):
    from botocore.exceptions import ClientError
    for batch in batches:
        try:
            dynamodb_client.batch_write_item(RequestItems=batch)
            print(f"  wrote batch of {sum(len(v) for v in batch.values())} items")
        except ClientError as error:
            handle_error(error)


def handle_error(error):
    error_code = error.response['Error']['Code']
    error_message = error.response['Error']['Message']
    help_string = ERROR_HELP_STRINGS.get(error_code, 'Unrecognized error code')
    print(f'[{error_code}] {help_string}. Error message: {error_message}')


def resolve_paths(args):
    if args.files:
        return args.files
    pattern = os.path.join(DEFAULT_CONTENT_DIR, "*.json")
    return sorted(glob.glob(pattern))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("files", nargs="*", help=f"library JSON files (default: {DEFAULT_CONTENT_DIR}/*.json)")
    parser.add_argument("--dry-run", action="store_true", help="print batches instead of writing to DynamoDB")
    parser.add_argument("--region", default="us-east-2")
    args = parser.parse_args()

    paths = resolve_paths(args)
    if not paths:
        print(f"No library files found (looked in {DEFAULT_CONTENT_DIR}).")
        sys.exit(1)

    libraries = []
    had_errors = False
    for path in paths:
        try:
            libraries.append((path, load_library(path)))
        except ValidationError as error:
            print(f"VALIDATION ERROR: {error}")
            had_errors = True

    if had_errors:
        print("\nFix the errors above before seeding - a bad prefix seeds an exercise nobody will ever see.")
        sys.exit(1)

    dynamodb_client = None if args.dry_run else create_dynamodb_client(region=args.region)

    for path, library in libraries:
        items = build_items(library)
        batches = chunk_batches(items)
        print(f"{library['name']} ({library['kind']}): {len(items) - 1} exercises, {len(batches)} batch(es)")
        if args.dry_run:
            for item in items:
                print(f"  {json.dumps(item)}")
        else:
            execute_batch_write(dynamodb_client, batches)

    print("\nDone." if not args.dry_run else "\nDry run complete - nothing was written.")


if __name__ == "__main__":
    main()
