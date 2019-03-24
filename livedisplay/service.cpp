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

#include <dlfcn.h>

#ifdef LIVES_IN_SYSTEM
#define LOG_TAG "lineage.livedisplay@2.0-service-mtk"
#else
#define LOG_TAG "vendor.lineage.livedisplay@2.0-service-mtk"
#endif

#include <android-base/logging.h>
#include <binder/ProcessState.h>
#include <hidl/HidlTransportSupport.h>

#include "AdaptiveBacklight.h"

using android::OK;
using android::sp;
using android::status_t;
using android::hardware::configureRpcThreadpool;
using android::hardware::joinRpcThreadpool;

using ::vendor::lineage::livedisplay::V2_0::IAdaptiveBacklight;
//using ::vendor::lineage::livedisplay::V2_0::IPictureAdjustment;
using ::vendor::lineage::livedisplay::V2_0::mtk::AdaptiveBacklight;
//using ::vendor::lineage::livedisplay::V2_0::mtk::PictureAdjustment;

int main() {
    // HIDL frontend
    sp<AdaptiveBacklight> ab;
    //sp<PictureAdjustment> pa;

    status_t status = OK;

#ifdef LIVES_IN_SYSTEM
    android::ProcessState::initWithDriver("/dev/binder");
#else
    android::ProcessState::initWithDriver("/dev/vndbinder");
#endif

    LOG(INFO) << "LiveDisplay HAL service is starting.";

    ab = new AdaptiveBacklight();
    if (ab == nullptr) {
        LOG(ERROR)
            << "Can not create an instance of LiveDisplay HAL AdaptiveBacklight Iface, exiting.";
    }

    /*pa = new PictureAdjustment(libHandle, cookie);
    if (pa == nullptr) {
        LOG(ERROR)
            << "Can not create an instance of LiveDisplay HAL PictureAdjustment Iface, exiting.";
    }*/

    configureRpcThreadpool(1, true /*callerWillJoin*/);

    status = ab->registerAsService();
    if (status != OK) {
        LOG(ERROR) << "Could not register service for LiveDisplay HAL AdaptiveBacklight Iface ("
                   << status << ")";
    }

    /*status = pa->registerAsService();
    if (status != OK) {
        LOG(ERROR) << "Could not register service for LiveDisplay HAL PictureAdjustment Iface ("
                   << status << ")";
    }*/

    LOG(INFO) << "LiveDisplay HAL service is ready.";
    joinRpcThreadpool();
    // Should not pass this line

    // In normal operation, we don't expect the thread pool to shutdown
    LOG(ERROR) << "LiveDisplay HAL service is shutting down.";
    return 1;
}
