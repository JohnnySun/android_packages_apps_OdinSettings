#!/bin/bash
set -euo pipefail

repo_dir=$(cd "$(dirname "$0")/.." && pwd)
source_root=$(cd "$repo_dir/../../.." && pwd)
homebrew=${HOMEBREW_PREFIX:-/opt/homebrew}
jdk_home=${JAVA_HOME:-}
javac_cmd=
java_cmd=
cxx_cmd=${CXX:-}

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

if [[ -z "$cxx_cmd" ]]; then
  cxx_cmd=$(command -v c++ || true)
fi
if [[ -z "$cxx_cmd" ]]; then
  echo "A C++ compiler is required (set CXX or provide c++ on PATH)." >&2
  exit 1
fi

test_dir=$(mktemp -d "${TMPDIR:-/tmp}/odinsettings-tests.XXXXXX")
classes_dir="$test_dir/classes"
mkdir -p "$classes_dir"
trap 'rm -rf "$test_dir"' EXIT

"$cxx_cmd" \
  -std=c++17 \
  -Wall \
  -Wextra \
  -Werror \
  -I"$repo_dir/jni" \
  "$repo_dir/jni/fan_control.cpp" \
  "$repo_dir/tests/native/fan_control_test.cpp" \
  -o "$test_dir/fan_control_test"
"$test_dir/fan_control_test"

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
sources+=(
  "$repo_dir/src/com/odin2/odinsettings/platform/NativeFanController.java"
)

"$javac_cmd" -Xlint:all -Werror -d "$classes_dir" "${sources[@]}"
"$java_cmd" \
  -Dodin.repo_dir="$repo_dir" \
  -cp "$classes_dir" \
  com.odin2.odinsettings.tests.HostTestMain
