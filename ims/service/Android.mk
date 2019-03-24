LOCAL_PATH:= $(call my-dir)

# Build the Ims OEM implementation including imsservice, imsadapter, imsriladapter.
include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_JAVA_LIBRARIES += telephony-common
LOCAL_JAVA_LIBRARIES += ims-common
LOCAL_STATIC_JAVA_LIBRARIES += wfo-common

# Use SimServs.jar for VoLTE MMTelSS Package
LOCAL_STATIC_JAVA_LIBRARIES += Simservs

LOCAL_PACKAGE_NAME := ImsService
LOCAL_SDK_VERSION := current
LOCAL_CERTIFICATE := platform
LOCAL_PRIVILEGED_MODULE := true

LOCAL_PROGUARD_ENABLED := disabled
LOCAL_PROGUARD_FLAGS := $(proguard.flags)

include $(BUILD_PACKAGE)

# Build java lib for accessing ImsConfigProvider API
include $(CLEAR_VARS)

LOCAL_MODULE := ims-config
LOCAL_SRC_FILES:= src/com/mediatek/ims/config/ConfigRegister.java \
                  src/com/mediatek/ims/config/FeatureRegister.java \
                  src/com/mediatek/ims/config/ImsConfigContract.java \
                  src/com/mediatek/ims/config/ImsConfigSettings.java \
                  src/com/mediatek/ims/config/Register.java \

LOCAL_JAVA_LIBRARIES := ims-common

include $(BUILD_STATIC_JAVA_LIBRARY)
