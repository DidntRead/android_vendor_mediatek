LOCAL_PATH:=$(call my-dir)
include $(CLEAR_VARS)

LOCAL_SHARED_LIBRARIES:= libc libcutils libcustom_nvram libnvram_platform libbase liblog

LOCAL_SRC_FILES:= \
	libnvram.c

LOCAL_C_INCLUDES:= \
    system/core/include/private \
    system/core/fs_mgr/include_fstab/fstab

LOCAL_STATIC_LIBRARIES += libfstab

LOCAL_MODULE:=libnvram
LOCAL_PROPRIETARY_MODULE := true
LOCAL_MODULE_OWNER := mtk
LOCAL_MODULE_TAGS := optional
include $(BUILD_SHARED_LIBRARY)
