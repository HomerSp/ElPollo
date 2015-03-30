LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

include ${LOCAL_PATH}/libsepol.mk

include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	main.cpp

LOCAL_STATIC_LIBRARIES := libsepol_elpollo
LOCAL_SHARED_LIBRARIES := \
	liblog \
	libselinux

LOCAL_CFLAGS := -Wall

LOCAL_LDFLAGS :=  
LOCAL_C_INCLUDES += ${LOCAL_PATH}/include \
					${LOCAL_PATH}/libsepol/include \
                    ${LOCAL_PATH}/../common/include

LOCAL_MODULE:= elpollo_sepol
include $(BUILD_EXECUTABLE)
