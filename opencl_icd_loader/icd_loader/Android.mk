LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    icd.cpp \
    icd_dispatch.cpp \
    icd_mtk.cpp

LOCAL_C_INCLUDES += \
	$(LOCAL_PATH)/../include

LOCAL_EXPORT_C_INCLUDE_DIRS := \
	$(LOCAL_PATH)/../include

LOCAL_SHARED_LIBRARIES += libcutils libutils liblog

LOCAL_MODULE := libOpenCL

LOCAL_CPPFLAGS += -DLOG_TAG=\"OPENCL_ICD_LOADER\" -DCL_TRACE

LOCAL_PROPRIETARY_MODULE := true

include $(BUILD_SHARED_LIBRARY)

