LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_SRC_FILES := gui.cpp
LOCAL_SHARED_LIBRARIES := libbinder libui libgui
LOCAL_MODULE := libmtkshim_gui
LOCAL_C_INCLUDES := frameworks/native/include
LOCAL_PROPRIETARY_MODULE := true
LOCAL_MODULE_TAGS := optional
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_SRC_FILES := audio.cpp
LOCAL_SHARED_LIBRARIES := libmedia
LOCAL_MODULE := libmtkshim_audio
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_PROPRIETARY_MODULE := true
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_SRC_FILES := CameraParameters.cpp camera.cpp
LOCAL_SHARED_LIBRARIES := libdpframework
LOCAL_MODULE := libmtkshim_camera
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_PROPRIETARY_MODULE := true
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_SRC_FILES := ui.cpp
LOCAL_SHARED_LIBRARIES := libui
LOCAL_MODULE := libmtkshim_ui
LOCAL_PROPRIETARY_MODULE := true
include $(BUILD_SHARED_LIBRARY)
