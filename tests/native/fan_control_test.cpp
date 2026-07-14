#include "fan_control.h"

#include <cstdlib>
#include <iostream>
#include <map>
#include <set>
#include <string>
#include <vector>

namespace {

using odin::fan::ApplyMode;
using odin::fan::ControlResult;
using odin::fan::FileOps;
using odin::fan::Identity;
using odin::fan::Paths;
using odin::fan::ReadStatus;
using odin::fan::Result;
using odin::fan::Snapshot;

constexpr char kStatePath[] = "/sys/class/gpio5_pwm2/state";
constexpr char kDutyPath[] = "/sys/class/gpio5_pwm2/duty";
constexpr char kPeriodPath[] = "/sys/class/gpio5_pwm2/period";
constexpr char kSpeedPath[] = "/sys/class/gpio5_pwm2/speed";

int tests;

struct FakeFiles {
  std::map<std::string, std::string> values{
      {kStatePath, "0\n"},
      {kDutyPath, "10000\n"},
      {kPeriodPath, "50000\n"},
      {kSpeedPath, "0\n"},
  };
  std::vector<std::string> events;
  std::set<std::string> ignored_writes;
  std::set<std::string> failed_writes;
  std::string period_after_enable;
};

bool ReadFile(void* context, const std::string& path, std::string* value) {
  auto* files = static_cast<FakeFiles*>(context);
  files->events.push_back("R " + path);
  const auto found = files->values.find(path);
  if (found == files->values.end()) {
    return false;
  }
  *value = found->second;
  return true;
}

bool WriteFile(void* context, const std::string& path,
               const std::string& value) {
  auto* files = static_cast<FakeFiles*>(context);
  files->events.push_back("W " + path + " " + value);
  if (files->failed_writes.count(path) != 0) {
    return false;
  }
  if (files->ignored_writes.count(path) == 0) {
    files->values[path] = value + "\n";
  }
  if (path == kStatePath && value == "1" && !files->period_after_enable.empty()) {
    files->values[kPeriodPath] = files->period_after_enable + "\n";
  }
  return true;
}

Identity StockIdentity() {
  return {"Odin2_Mini", "kalama", "kalama", "QCS8550"};
}

Identity LineageIdentity() {
  return {"Odin2 Mini", "odin2_mini", "lineage_odin2_mini", "QCS8550"};
}

Paths ExactPaths() {
  return {kStatePath, kDutyPath, kPeriodPath, kSpeedPath};
}

FileOps Ops(FakeFiles* files) {
  return {ReadFile, WriteFile, files};
}

void Fail(const std::string& message) {
  std::cerr << "FAIL: " << message << '\n';
  std::exit(1);
}

template <typename T>
void AssertEquals(const T& expected, const T& actual,
                  const std::string& message) {
  if (expected != actual) {
    Fail(message);
  }
}

void AssertTrue(bool value, const std::string& message) {
  if (!value) {
    Fail(message);
  }
}

void AssertEvents(const std::vector<std::string>& expected,
                  const FakeFiles& files, const std::string& message) {
  if (expected != files.events) {
    std::cerr << "Expected events:\n";
    for (const std::string& event : expected) {
      std::cerr << "  " << event << '\n';
    }
    std::cerr << "Actual events:\n";
    for (const std::string& event : files.events) {
      std::cerr << "  " << event << '\n';
    }
    Fail(message);
  }
}

void IdentityGateAllowsOnlyExactOdin2MiniIdentities() {
  for (const Identity& identity : {StockIdentity(), LineageIdentity()}) {
    FakeFiles files;
    const ControlResult result = ReadStatus(identity, ExactPaths(), Ops(&files));
    AssertEquals(Result::kAvailable, result.result,
                 "exact Odin2 Mini identity must be accepted");
  }

  for (const Identity& identity : {
           Identity{"Other", "kalama", "kalama", "QCS8550"},
           Identity{"Odin2 Mini", "odin2_mini", "kalama", "QCS8550"},
           Identity{"Odin2 Mini", "odin2_mini", "lineage_odin2_mini", "Other"},
       }) {
    FakeFiles files;
    const ControlResult result = ApplyMode(identity, ExactPaths(), 2, Ops(&files));
    AssertEquals(Result::kUnsupportedDevice, result.result,
                 "unknown or mixed identity must be rejected");
    AssertTrue(files.events.empty(), "identity rejection must perform no I/O");
  }
  ++tests;
}

void ExactPathsAreRequired() {
  Paths paths = ExactPaths();
  paths.speed = "/sys/class/gpio5_pwm2/fan_speed";
  FakeFiles files;
  const ControlResult result = ApplyMode(StockIdentity(), paths, 1, Ops(&files));
  AssertEquals(Result::kUnexpectedPaths, result.result,
               "unexpected sysfs path must be rejected");
  AssertTrue(files.events.empty(), "path rejection must perform no I/O");
  ++tests;
}

void AllowlistedModesUseExactWriteOrderBytesAndReadback() {
  struct Case {
    int mode;
    std::string state;
    std::string high_time;
  };
  for (const Case& test_case : {
           Case{0, "0", "10000"},
           Case{1, "1", "5000"},
           Case{2, "1", "25000"},
       }) {
    FakeFiles files;
    files.values[kSpeedPath] = test_case.mode == 2 ? "3300\n" : "0\n";
    const ControlResult result =
        ApplyMode(StockIdentity(), ExactPaths(), test_case.mode, Ops(&files));
    AssertEquals(Result::kAvailable, result.result,
                 "allowlisted mode must apply");
    AssertEvents({
                     std::string("R ") + kPeriodPath,
                     std::string("W ") + kStatePath + " " + test_case.state,
                     std::string("R ") + kStatePath,
                     std::string("R ") + kPeriodPath,
                     std::string("W ") + kDutyPath + " " + test_case.high_time,
                     std::string("R ") + kDutyPath,
                     std::string("R ") + kSpeedPath,
                 },
                 files, "mode transaction order or bytes changed");
    AssertTrue(result.has_snapshot, "successful mode must return a snapshot");
    AssertEquals(test_case.mode == 2 ? 3300 : 0,
                 result.snapshot.tach_pulses_times_300,
                 "tach value must remain pulses times 300");
  }

  FakeFiles files;
  const ControlResult result = ApplyMode(StockIdentity(), ExactPaths(), 3, Ops(&files));
  AssertEquals(Result::kInvalidMode, result.result,
               "non-allowlisted mode must be rejected");
  AssertTrue(files.events.empty(), "invalid mode must perform no I/O");
  ++tests;
}

void PeriodMismatchRejectsAllWrites() {
  FakeFiles files;
  files.values[kPeriodPath] = "49999\n";
  const ControlResult result = ApplyMode(StockIdentity(), ExactPaths(), 2, Ops(&files));
  AssertEquals(Result::kPeriodMismatch, result.result,
               "unexpected period must reject the transaction");
  AssertEvents({std::string("R ") + kPeriodPath}, files,
               "period mismatch must stop before any write");
  ++tests;
}

void PeriodChangeAfterEnableStopsAndDisables() {
  FakeFiles files;
  files.period_after_enable = "49999";
  const ControlResult result = ApplyMode(StockIdentity(), ExactPaths(), 2, Ops(&files));
  AssertEquals(Result::kPeriodMismatch, result.result,
               "period change during a non-off transaction must fail");
  AssertEvents({
                   std::string("R ") + kPeriodPath,
                   std::string("W ") + kStatePath + " 1",
                   std::string("R ") + kStatePath,
                   std::string("R ") + kPeriodPath,
                   std::string("W ") + kStatePath + " 0",
                   std::string("R ") + kStatePath,
               },
               files, "period change must stop before high-time and disable");
  ++tests;
}

void EnableReadbackMismatchTriggersDisableCleanup() {
  FakeFiles files;
  files.ignored_writes.insert(kStatePath);
  const ControlResult result = ApplyMode(StockIdentity(), ExactPaths(), 2, Ops(&files));
  AssertEquals(Result::kReadbackMismatch, result.result,
               "enable readback mismatch must fail");
  AssertEvents({
                   std::string("R ") + kPeriodPath,
                   std::string("W ") + kStatePath + " 1",
                   std::string("R ") + kStatePath,
                   std::string("W ") + kStatePath + " 0",
                   std::string("R ") + kStatePath,
               },
               files, "enable mismatch must stop and best-effort disable");
  ++tests;
}

void HighTimeReadbackMismatchTriggersDisableCleanup() {
  FakeFiles files;
  files.ignored_writes.insert(kDutyPath);
  const ControlResult result = ApplyMode(StockIdentity(), ExactPaths(), 2, Ops(&files));
  AssertEquals(Result::kReadbackMismatch, result.result,
               "high-time readback mismatch must fail");
  AssertEvents({
                   std::string("R ") + kPeriodPath,
                   std::string("W ") + kStatePath + " 1",
                   std::string("R ") + kStatePath,
                   std::string("R ") + kPeriodPath,
                   std::string("W ") + kDutyPath + " 25000",
                   std::string("R ") + kDutyPath,
                   std::string("W ") + kStatePath + " 0",
                   std::string("R ") + kStatePath,
               },
               files, "high-time mismatch must best-effort disable");
  ++tests;
}

void FailureAfterEnableTriggersDisableCleanup() {
  FakeFiles files;
  files.failed_writes.insert(kDutyPath);
  const ControlResult result = ApplyMode(StockIdentity(), ExactPaths(), 1, Ops(&files));
  AssertEquals(Result::kWriteFailed, result.result,
               "write failure after enable must fail");
  AssertEquals(std::string("0\n"), files.values[kStatePath],
               "write failure after enable must restore off state");
  ++tests;
}

}  // namespace

int main() {
  IdentityGateAllowsOnlyExactOdin2MiniIdentities();
  ExactPathsAreRequired();
  AllowlistedModesUseExactWriteOrderBytesAndReadback();
  PeriodMismatchRejectsAllWrites();
  PeriodChangeAfterEnableStopsAndDisables();
  EnableReadbackMismatchTriggersDisableCleanup();
  HighTimeReadbackMismatchTriggersDisableCleanup();
  FailureAfterEnableTriggersDisableCleanup();
  std::cout << "PASS: " << tests
            << " Odin Settings fan control native tests\n";
  return 0;
}
