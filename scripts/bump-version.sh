#!/usr/bin/env bash
# Bumps versionCode and versionName in app/build.gradle.
#
# Usage:
#   bump-version.sh release [patch|minor|major]
#     Strip -SNAPSHOT, optionally bump semver component, increment versionCode.
#
#   bump-version.sh snapshot
#     Increment patch and append -SNAPSHOT, increment versionCode.
#
# Outputs VERSION_NAME and VERSION_CODE to $GITHUB_OUTPUT when set.
set -euo pipefail

BUILD_GRADLE="${GRADLE_FILE:-app/build.gradle}"
MODE="${1:?Usage: $0 <release|snapshot> [patch|minor|major]}"
BUMP_TYPE="${2:-patch}"

CURRENT_VERSION_CODE=$(grep -oP 'versionCode = \K[0-9]+' "$BUILD_GRADLE")
CURRENT_VERSION_NAME=$(grep -oP 'versionName = "\K[^"]+' "$BUILD_GRADLE")

BASE_VERSION="${CURRENT_VERSION_NAME%-SNAPSHOT}"
IFS='.' read -r MAJOR MINOR PATCH <<< "$BASE_VERSION"

case "$MODE" in
    release)
        case "$BUMP_TYPE" in
            major) MAJOR=$((MAJOR + 1)); MINOR=0; PATCH=0 ;;
            minor) MINOR=$((MINOR + 1)); PATCH=0 ;;
            patch) ;;
            *) echo "Unknown bump type: $BUMP_TYPE" >&2; exit 1 ;;
        esac
        NEW_VERSION_NAME="$MAJOR.$MINOR.$PATCH"
        ;;
    snapshot)
        PATCH=$((PATCH + 1))
        NEW_VERSION_NAME="$MAJOR.$MINOR.$PATCH-SNAPSHOT"
        ;;
    *)
        echo "Unknown mode: $MODE" >&2
        exit 1
        ;;
esac

NEW_VERSION_CODE=$((CURRENT_VERSION_CODE + 1))

echo "Bumping: $CURRENT_VERSION_NAME (code $CURRENT_VERSION_CODE) -> $NEW_VERSION_NAME (code $NEW_VERSION_CODE)"

sed -i "s/versionCode = ${CURRENT_VERSION_CODE}/versionCode = ${NEW_VERSION_CODE}/" "$BUILD_GRADLE"
sed -i "s/versionName = \"${CURRENT_VERSION_NAME}\"/versionName = \"${NEW_VERSION_NAME}\"/" "$BUILD_GRADLE"

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
    echo "VERSION_NAME=$NEW_VERSION_NAME" >> "$GITHUB_OUTPUT"
    echo "VERSION_CODE=$NEW_VERSION_CODE" >> "$GITHUB_OUTPUT"
fi
