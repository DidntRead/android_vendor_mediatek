/*
 * Copyright 2019 The LineageOS Project
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

#define LOG_TAG "android.hardware.power@1.1-service.mtk"

#include <android-base/logging.h>
#include <binder/ProcessState.h>
#include <hidl/HidlTransportSupport.h>

#include "Power.h"

using android::hardware::configureRpcThreadpool;
using android::hardware::joinRpcThreadpool;

using android::hardware::power::V1_1::IPower;
using android::hardware::power::V1_1::implementation::Power;

using android::OK;
using android::sp;
using android::status_t;

int main() {
    android::sp<IPower> service = new Power();

    configureRpcThreadpool(1, true);

    status_t status = service->registerAsService();
    if (status != OK) {
        LOG(ERROR) << "Cannot register Power HAL service.";
        return 1;
    }

    LOG(ERROR) << "Power HAL service ready.";

    joinRpcThreadpool();

    LOG(ERROR) << "Power HAL service failed to join thread pool.";
    return 1;
}

