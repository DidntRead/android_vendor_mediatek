/*
 * Copyright (C) 2019 The LineageOS Project
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

#include <android-base/logging.h>

#include <stdio.h>
#include <string.h>
#include <fstream>

#include "AdaptiveBacklight.h"

namespace vendor {
namespace lineage {
namespace livedisplay {
namespace V2_0 {
namespace mtk {

static constexpr const char *kCabcPath = "/sys/class/graphics/fb0/cabc";

// Methods from ::vendor::lineage::livedisplay::V2_0::IAdaptiveBacklight follow.
Return<bool> AdaptiveBacklight::isEnabled() {
    std::ifstream file(kCabcPath);
    if (!file.is_open()) {
        LOG(ERROR) << "Failed to open " << kCabcPath << ", error=" << errno
            << " (" << strerror(errno) << ")";
	return false;
    } else {
	bool enabled;
	file >> enabled;
	return enabled;
    }
}

Return<bool> AdaptiveBacklight::setEnabled(bool enabled) {
    std::ofstream file(kCabcPath);
    if (!file.is_open()) {
        LOG(ERROR) << "Failed to open " << kCabcPath << ", error=" << errno
            << " (" << strerror(errno) << ")";
	return false;
    } else {
        file << (enabled ? "1" : "0");
        return true;
    }
}

}  // namespace mtk
}  // namespace V2_0
}  // namespace livedisplay
}  // namespace lineage
}  // namespace vendor
