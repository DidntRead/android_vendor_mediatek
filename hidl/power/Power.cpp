/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "android.hardware.power@1.1-impl.mtk"

#include <android-base/logging.h>
#include <utils/Errors.h>
#include "Power.h"

#include <fstream>

namespace android {
namespace hardware {
namespace power {
namespace V1_1 {
namespace implementation {

static constexpr const char *kTouchDt2wPath = "/sys/lenovo_tp_gestures/tpd_suspend_status";
static constexpr const char *kRushBoostPath = "/proc/hps/rush_boost_enabled";
static constexpr const char *kFpsUpperBoundPath = "/d/ged/hal/fps_upper_bound";

// Methods from ::android::hardware::power::V1_0::IPower follow.
Return<void> Power::setInteractive(bool interactive)  {
    (void)interactive;
    return Void();
}

Return<void> Power::powerHint(PowerHint hint, int32_t data)  {
    switch(hint) {
        case PowerHint::LOW_POWER: {
            std::ofstream rushBoost(kRushBoostPath);
            std::ofstream fpsBound(kFpsUpperBoundPath);
            if (!rushBoost.is_open()) {
                LOG(ERROR) << "Failed to open " << kRushBoostPath << ", error=" << errno
                   << " (" << strerror(errno) << ")";
            }
            if (!fpsBound.is_open()) {
                LOG(ERROR) << "Failed to open " << kFpsUpperBoundPath << ", error=" << errno
                   << " (" << strerror(errno) << ")";
            }
            rushBoost << (data ? "0" : "1");
            fpsBound << (data ? "30" : "60");
	    break;
        }
        default:
	    break;
    }
    return Void();
}

Return<void> Power::setFeature(Feature feature, bool activate)  {
    switch(feature) {
        case Feature::POWER_FEATURE_DOUBLE_TAP_TO_WAKE: {
	    std::ofstream file(kTouchDt2wPath);
            if (!file.is_open()) {
                 LOG(ERROR) << "Failed to open " << kTouchDt2wPath << ", error=" << errno
                   << " (" << strerror(errno) << ")";
            }
            file << (activate ? "1" : "0");
            break;
        }
    }
    return Void();
}

Return<void> Power::getPlatformLowPowerStats(getPlatformLowPowerStats_cb _hidl_cb)  {
    hidl_vec<PowerStatePlatformSleepState> states;
    states.resize(0);
    _hidl_cb(states, Status::SUCCESS);
    return Void();
}


// Methods from ::android::hardware::power::V1_0::IPower follow.
Return<void> Power::getSubsystemLowPowerStats(getSubsystemLowPowerStats_cb _hidl_cb) {
    hidl_vec<PowerStateSubsystem> subsystems;
    subsystems.resize(0);
    _hidl_cb(subsystems, Status::SUCCESS);
    return Void();
}

Return<void> Power::powerHintAsync(PowerHint hint, int32_t data) {
    return powerHint(hint, data);
}

} // namespace implementation
}  // namespace V1_1
}  // namespace power
}  // namespace hardware
}  // namespace android
