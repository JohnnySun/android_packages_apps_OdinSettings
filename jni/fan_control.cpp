#include "fan_control.h"

#include <cerrno>
#include <climits>
#include <cstdlib>
#include <string>

namespace odin::fan {
namespace {

constexpr char kStatePath[] = "/sys/class/gpio5_pwm2/state";
constexpr char kDutyPath[] = "/sys/class/gpio5_pwm2/duty";
constexpr char kPeriodPath[] = "/sys/class/gpio5_pwm2/period";
constexpr char kSpeedPath[] = "/sys/class/gpio5_pwm2/speed";

constexpr int kRequiredPeriodNs = 50000;
constexpr int kOffMode = 0;
constexpr int kQuietMode = 1;
constexpr int kSportMode = 2;
constexpr int kOffHighTimeNs = 10000;
constexpr int kQuietHighTimeNs = 5000;
constexpr int kSportHighTimeNs = 25000;

enum class ReadResult {
  kOk,
  kUnavailable,
  kMalformed,
};

ControlResult Error(Result result, int requested_mode = -1) {
  return {
      result,
      requested_mode >= kOffMode && requested_mode <= kSportMode,
      requested_mode,
      false,
      {-1, -1, -1},
  };
}

ControlResult Available(int requested_mode, const Snapshot& snapshot) {
  return {
      Result::kAvailable,
      requested_mode >= kOffMode && requested_mode <= kSportMode,
      requested_mode,
      true,
      snapshot,
  };
}

bool IsSupportedIdentity(const Identity& identity) {
  const bool stock = identity.model == "Odin2_Mini" &&
                     identity.device == "kalama" &&
                     identity.product == "kalama";
  const bool lineage = identity.model == "Odin2 Mini" &&
                       identity.device == "odin2_mini" &&
                       identity.product == "lineage_odin2_mini";
  return (stock || lineage) && identity.soc_model == "QCS8550";
}

bool AreExactPaths(const Paths& paths) {
  return paths.state == kStatePath && paths.duty == kDutyPath &&
         paths.period == kPeriodPath && paths.speed == kSpeedPath;
}

std::string Trim(const std::string& value) {
  const std::string whitespace = " \t\r\n";
  const size_t start = value.find_first_not_of(whitespace);
  if (start == std::string::npos) {
    return "";
  }
  const size_t end = value.find_last_not_of(whitespace);
  return value.substr(start, end - start + 1);
}

ReadResult ReadInteger(const FileOps& files, const std::string& path,
                       int* parsed) {
  if (files.read == nullptr || parsed == nullptr) {
    return ReadResult::kUnavailable;
  }
  std::string raw;
  if (!files.read(files.context, path, &raw)) {
    return ReadResult::kUnavailable;
  }
  const std::string value = Trim(raw);
  if (value.empty()) {
    return ReadResult::kMalformed;
  }

  errno = 0;
  char* end = nullptr;
  const long number = std::strtol(value.c_str(), &end, 10);
  if (errno != 0 || end == value.c_str() || *end != '\0' || number < 0 ||
      number > INT_MAX) {
    return ReadResult::kMalformed;
  }
  *parsed = static_cast<int>(number);
  return ReadResult::kOk;
}

Result ToResult(ReadResult result) {
  switch (result) {
    case ReadResult::kOk:
      return Result::kAvailable;
    case ReadResult::kUnavailable:
      return Result::kUnavailable;
    case ReadResult::kMalformed:
      return Result::kMalformed;
  }
  return Result::kMalformed;
}

Result CheckPeriod(const Paths& paths, const FileOps& files) {
  int period = -1;
  const ReadResult read = ReadInteger(files, paths.period, &period);
  if (read != ReadResult::kOk) {
    return ToResult(read);
  }
  return period == kRequiredPeriodNs ? Result::kAvailable
                                     : Result::kPeriodMismatch;
}

Result WriteAndVerify(const FileOps& files, const std::string& path,
                      int expected) {
  if (files.write == nullptr ||
      !files.write(files.context, path, std::to_string(expected))) {
    return Result::kWriteFailed;
  }
  int actual = -1;
  const ReadResult read = ReadInteger(files, path, &actual);
  if (read != ReadResult::kOk) {
    return ToResult(read);
  }
  return actual == expected ? Result::kAvailable
                            : Result::kReadbackMismatch;
}

void BestEffortDisable(const Paths& paths, const FileOps& files) {
  (void)WriteAndVerify(files, paths.state, 0);
}

ControlResult ReadSnapshot(const Paths& paths, const FileOps& files,
                           int requested_mode) {
  Snapshot snapshot{-1, -1, -1};
  ReadResult read = ReadInteger(files, paths.state, &snapshot.state);
  if (read != ReadResult::kOk) {
    return Error(ToResult(read), requested_mode);
  }
  read = ReadInteger(files, paths.duty, &snapshot.pwm_high_time_ns);
  if (read != ReadResult::kOk) {
    return Error(ToResult(read), requested_mode);
  }
  read = ReadInteger(files, paths.speed, &snapshot.tach_pulses_times_300);
  if (read != ReadResult::kOk) {
    return Error(ToResult(read), requested_mode);
  }
  if (snapshot.state != 0 && snapshot.state != 1) {
    return Error(Result::kMalformed, requested_mode);
  }
  return Available(requested_mode, snapshot);
}

}  // namespace

ControlResult ReadStatus(const Identity& identity, const Paths& paths,
                         const FileOps& files) {
  if (!IsSupportedIdentity(identity)) {
    return Error(Result::kUnsupportedDevice);
  }
  if (!AreExactPaths(paths)) {
    return Error(Result::kUnexpectedPaths);
  }
  const Result period = CheckPeriod(paths, files);
  if (period != Result::kAvailable) {
    return Error(period);
  }
  return ReadSnapshot(paths, files, -1);
}

ControlResult ApplyMode(const Identity& identity, const Paths& paths,
                        int requested_mode, const FileOps& files) {
  if (!IsSupportedIdentity(identity)) {
    return Error(Result::kUnsupportedDevice, requested_mode);
  }
  if (!AreExactPaths(paths)) {
    return Error(Result::kUnexpectedPaths, requested_mode);
  }
  if (requested_mode < kOffMode || requested_mode > kSportMode) {
    return Error(Result::kInvalidMode);
  }

  Result result = CheckPeriod(paths, files);
  if (result != Result::kAvailable) {
    return Error(result, requested_mode);
  }

  const bool non_off = requested_mode != kOffMode;
  const int target_state = non_off ? 1 : 0;
  const int target_high_time = requested_mode == kQuietMode
                                   ? kQuietHighTimeNs
                                   : requested_mode == kSportMode
                                         ? kSportHighTimeNs
                                         : kOffHighTimeNs;

  result = WriteAndVerify(files, paths.state, target_state);
  if (result != Result::kAvailable) {
    if (non_off) {
      BestEffortDisable(paths, files);
    }
    return Error(result, requested_mode);
  }

  result = CheckPeriod(paths, files);
  if (result != Result::kAvailable) {
    if (non_off) {
      BestEffortDisable(paths, files);
    }
    return Error(result, requested_mode);
  }

  result = WriteAndVerify(files, paths.duty, target_high_time);
  if (result != Result::kAvailable) {
    if (non_off) {
      BestEffortDisable(paths, files);
    }
    return Error(result, requested_mode);
  }

  int tach = -1;
  const ReadResult tach_read = ReadInteger(files, paths.speed, &tach);
  if (tach_read != ReadResult::kOk) {
    if (non_off) {
      BestEffortDisable(paths, files);
    }
    return Error(ToResult(tach_read), requested_mode);
  }
  return Available(requested_mode, {target_state, target_high_time, tach});
}

}  // namespace odin::fan
