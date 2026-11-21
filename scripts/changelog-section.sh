#!/usr/bin/env bash
# Prints the section of one version from CHANGELOG.md (without its "## <version>" line), for the text of a release.
# Usage: scripts/changelog-section.sh <version> [changelog file]
# Fails if the version has no section or the section is empty, so that a release is never made without notes.
set -euo pipefail

version="${1:?usage: changelog-section.sh <version> [changelog file]}"
file="${2:-CHANGELOG.md}"

# The section runs from "## <version>" to the next "## " heading. A version must match the whole heading line, so that
# 0.1.0 does not find 0.1.0-beta.1. Carriage returns are removed (Windows checkouts).
section="$(tr -d '\r' <"$file" | awk -v v="$version" '
    /^## / { if (found) exit; if ($2 == v && NF == 2) { found = 1; next } }
    found { print }
')"

# Without blank lines at the edges; a section with nothing in it is no section.
section="$(printf '%s\n' "$section" | sed -e '/./,$!d' | sed -e ':a' -e '/^\n*$/{$d;N;ba' -e '}')"
if [ -z "$(printf '%s' "$section" | tr -d '[:space:]')" ]; then
    echo "CHANGELOG.md has no section (or an empty one) for version $version" >&2
    exit 1
fi
printf '%s\n' "$section"
