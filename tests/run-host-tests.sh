#!/bin/bash
set -euo pipefail

repo_dir=$(cd "$(dirname "$0")/.." && pwd)
source_root=$(cd "$repo_dir/../../.." && pwd)
homebrew=${HOMEBREW_PREFIX:-/opt/homebrew}
jdk_home=${JAVA_HOME:-}
javac_cmd=
java_cmd=

if [[ -z "$jdk_home" && -x "$homebrew/bin/brew" ]]; then
  jdk_home="$($homebrew/bin/brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"
fi

host_tag=
case "$(uname -s):$(uname -m)" in
  Linux:x86_64) host_tag=linux-x86 ;;
  Darwin:arm64) host_tag=darwin-arm64 ;;
  Darwin:x86_64) host_tag=darwin-x86 ;;
esac
if [[ -z "$jdk_home" && -n "$host_tag" ]]; then
  source_jdk="$source_root/prebuilts/jdk/jdk21/$host_tag"
  if [[ -x "$source_jdk/bin/javac" && -x "$source_jdk/bin/java" ]]; then
    jdk_home="$source_jdk"
  fi
fi

if [[ -n "$jdk_home" && -x "$jdk_home/bin/javac" && -x "$jdk_home/bin/java" ]]; then
  javac_cmd="$jdk_home/bin/javac"
  java_cmd="$jdk_home/bin/java"
elif command -v javac >/dev/null 2>&1 && command -v java >/dev/null 2>&1; then
  javac_cmd=$(command -v javac)
  java_cmd=$(command -v java)
else
  echo "A JDK is required (set JAVA_HOME or provide javac and java on PATH)." >&2
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

"$javac_cmd" -Xlint:all -Werror -d "$classes_dir" "${sources[@]}"
"$java_cmd" \
  -Dodin.repo_dir="$repo_dir" \
  -cp "$classes_dir" \
  com.odin2.odinsettings.tests.HostTestMain
