#!/bin/bash
set -euo pipefail

repo_dir=$(cd "$(dirname "$0")/.." && pwd)
homebrew=${HOMEBREW_PREFIX:-/opt/homebrew}
jdk_home=${JAVA_HOME:-}

if [[ -z "$jdk_home" && -x "$homebrew/bin/brew" ]]; then
  jdk_home="$($homebrew/bin/brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"
fi

if [[ ! -x "$jdk_home/bin/javac" || ! -x "$jdk_home/bin/java" ]]; then
  echo "OpenJDK 17 is required (set JAVA_HOME or install Homebrew openjdk@17)." >&2
  exit 1
fi

classes_dir=$(mktemp -d "${TMPDIR:-/tmp}/odinsettings-tests.XXXXXX")
trap 'rm -rf "$classes_dir"' EXIT

sources=()
while IFS= read -r source; do
  sources+=("$source")
done < <(
  find \
    "$repo_dir/src/com/odin2/odinsettings/domain" \
    "$repo_dir/src/com/odin2/odinsettings/display" \
    "$repo_dir/src/com/odin2/odinsettings/hardware" \
    "$repo_dir/src/com/odin2/odinsettings/policy" \
    "$repo_dir/src/com/odin2/odinsettings/service" \
    "$repo_dir/tests/src" \
    -name '*.java' -print | sort
)

"$jdk_home/bin/javac" -Xlint:all -Werror -d "$classes_dir" "${sources[@]}"
"$jdk_home/bin/java" \
  -Dodin.repo_dir="$repo_dir" \
  -cp "$classes_dir" \
  com.odin2.odinsettings.tests.HostTestMain
