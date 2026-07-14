#include "ayn/fan_status.h"

#include <android-base/file.h>
#include <android-base/properties.h>
#include <jni.h>

#include <array>
#include <string>

namespace {

constexpr jint kResultAvailable = 0;
constexpr jint kResultUnsupported = 1;
constexpr jint kResultUnexpectedPaths = 2;
constexpr jint kResultUnavailable = 3;
constexpr jint kResultMalformed = 4;

bool ReadFile(void*, const std::string& path, std::string* value) {
  return android::base::ReadFileToString(path, value);
}

jint ToJavaResult(ayn::fan::FanStatusResult result) {
  switch (result) {
    case ayn::fan::FanStatusResult::kAvailable:
      return kResultAvailable;
    case ayn::fan::FanStatusResult::kUnsupportedDevice:
      return kResultUnsupported;
    case ayn::fan::FanStatusResult::kUnexpectedPaths:
      return kResultUnexpectedPaths;
    case ayn::fan::FanStatusResult::kUnavailableRead:
      return kResultUnavailable;
    case ayn::fan::FanStatusResult::kMalformedValue:
      return kResultMalformed;
  }
  return kResultMalformed;
}

}  // namespace

extern "C" JNIEXPORT jintArray JNICALL
Java_com_odin2_odinsettings_platform_NativeFanStatusReader_nativeRead(
    JNIEnv* env, jclass) {
  const ayn::fan::FanStatusIdentity identity{
      android::base::GetProperty("ro.product.device", ""),
      android::base::GetProperty("ro.vendor.retro.name", ""),
      android::base::GetProperty("ro.product.vendor.model", ""),
  };
  const ayn::fan::FanStatusPaths paths{
      "/sys/class/gpio5_pwm2/state",
      "/sys/class/gpio5_pwm2/duty",
  };
  const ayn::fan::FanStatusRead status =
      ayn::fan::ReadFanStatus(identity, paths, ReadFile, nullptr);

  std::array<jint, 3> values{ToJavaResult(status.result), -1, -1};
  if (status.result == ayn::fan::FanStatusResult::kAvailable &&
      status.snapshot.has_value()) {
    values[1] = status.snapshot->state;
    values[2] = status.snapshot->duty;
  }

  jintArray output = env->NewIntArray(static_cast<jsize>(values.size()));
  if (output == nullptr) {
    return nullptr;
  }
  env->SetIntArrayRegion(output, 0, static_cast<jsize>(values.size()),
                         values.data());
  return output;
}
