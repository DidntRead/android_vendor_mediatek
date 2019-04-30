LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE_TAGS := optional
LOCAL_OVERRIDES_PACKAGES := Nfc
LOCAL_SRC_FILES    := Nfc.apk
LOCAL_CERTIFICATE := platform
LOCAL_MODULE := MtkNfc
LOCAL_MODULE_CLASS := APPS
LOCAL_PRIVILEGED_MODULE := false
LOCAL_SDK_VERSION := 23
include $(BUILD_PREBUILT)

include $(CLEAR_VARS)

#Create link for libs
$(shell mkdir -p $(TARGET_OUT)/app/MtkNfc/lib/arm64; \
	ln -sf /vendor/lib64/libnfc_mt6605_jni.so \
	$(TARGET_OUT)/app/MtkNfc/lib/arm64/libnfc_mt6605_jni.so; \
	ln -sf /vendor/lib64/libmtknfc_dynamic_load_jni.so \
	$(TARGET_OUT)/app/MtkNfc/lib/arm64/libmtknfc_dynamic_load_jni.so)

