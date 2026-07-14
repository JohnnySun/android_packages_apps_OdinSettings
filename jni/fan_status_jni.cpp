#include "fan_control.h"

#include <android-base/file.h>
#include <android-base/properties.h>
#include <jni.h>

#include <array>
#include <string>

namespace {

bool ReadFile(void*, const std::string& path, std::string* value) {
  return android::base::ReadFileToString(path, value);
}

bool WriteFile(void*, const std::string& path, const std::string& value) {
  return android::base::WriteStringToFile(value, path);
}

odin::fan::Identity CurrentIdentity() {
  return {
      android::base::GetProperty("ro.product.model", ""),
      android::base::GetProperty("ro.product.device", ""),
      android::base::GetProperty("ro.product.name", ""),
      android::base::GetProperty("ro.soc.model", ""),
  };
}

odin::fan::Paths FanPaths() {
  return {
      "/sys/class/gpio5_pwm2/state",
      "/sys/class/gpio5_pwm2/duty",
      "/sys/class/gpio5_pwm2/period",
      "/sys/class/gpio5_pwm2/speed",
  };
}

odin::fan::FileOps Files() {
  return {ReadFile, WriteFile, nullptr};
}

jintArray ToJavaResult(JNIEnv* env, const odin::fan::ControlResult& result) {
  std::array<jint, 5> values{
      static_cast<jint>(result.result),
      result.has_requested_mode ? result.requested_mode : -1,
      -1,
      -1,
      -1,
  };
  if (result.has_snapshot) {
    values[2] = result.snapshot.state;
    values[3] = result.snapshot.pwm_high_time_ns;
    values[4] = result.snapshot.tach_pulses_times_300;
  }

  jintArray output = env->NewIntArray(static_cast<jsize>(values.size()));
  if (output == nullptr) {
    return nullptr;
  }
  env->SetIntArrayRegion(output, 0, static_cast<jsize>(values.size()),
                         values.data());
  return output;
}

}  // namespace

extern "C" JNIEXPORT jintArray JNICALL
Java_com_odin2_odinsettings_platform_NativeFanController_nativeRead(
    JNIEnv* env, jclass) {
  return ToJavaResult(
      env, odin::fan::ReadStatus(CurrentIdentity(), FanPaths(), Files()));
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_odin2_odinsettings_platform_NativeFanController_nativeApply(
    JNIEnv* env, jclass, jint requested_mode) {
  return ToJavaResult(
      env, odin::fan::ApplyMode(CurrentIdentity(), FanPaths(), requested_mode,
                                Files()));
}
