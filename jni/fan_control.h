#ifndef ODINSETTINGS_FAN_CONTROL_H_
#define ODINSETTINGS_FAN_CONTROL_H_

#include <string>

namespace odin::fan {

enum class Result : int {
  kAvailable = 0,
  kUnsupportedDevice = 1,
  kUnexpectedPaths = 2,
  kUnavailable = 3,
  kMalformed = 4,
  kPeriodMismatch = 5,
  kWriteFailed = 6,
  kReadbackMismatch = 7,
  kInvalidMode = 8,
};

struct Identity {
  std::string model;
  std::string device;
  std::string product;
  std::string soc_model;
};

struct Paths {
  std::string state;
  std::string duty;
  std::string period;
  std::string speed;
};

struct Snapshot {
  int state;
  int pwm_high_time_ns;
  int tach_pulses_times_300;
};

using ReadFile = bool (*)(void* context, const std::string& path,
                          std::string* value);
using WriteFile = bool (*)(void* context, const std::string& path,
                           const std::string& value);

struct FileOps {
  ReadFile read;
  WriteFile write;
  void* context;
};

struct ControlResult {
  Result result;
  bool has_requested_mode;
  int requested_mode;
  bool has_snapshot;
  Snapshot snapshot;
};

ControlResult ReadStatus(const Identity& identity, const Paths& paths,
                         const FileOps& files);
ControlResult ApplyMode(const Identity& identity, const Paths& paths,
                        int requested_mode, const FileOps& files);

}  // namespace odin::fan

#endif  // ODINSETTINGS_FAN_CONTROL_H_
