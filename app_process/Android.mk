LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES_arm :=  arch/arm/quick_override_arm.S
LOCAL_SRC_FILES_arm64 :=    arch/arm64/quick_override_arm64.S
LOCAL_SRC_FILES_x86 :=  arch/x86/quick_override_x86.S

LOCAL_SRC_FILES:= \
	app_main.cpp \
	elpollo.cpp \
	elpollo_jni.cpp

LOCAL_STATIC_LIBRARIES := libsepol_elpollo
LOCAL_SHARED_LIBRARIES := \
	libcutils \
	libutils \
	liblog \
	libbinder \
	libandroid_runtime \
	libselinux \
	libart

include external/libcxx/libcxx.mk

LOCAL_CFLAGS := -DHAVE_ANDROID_OS -D_USING_LIBCXX -DIMT_SIZE=64 -fno-rtti \
  -std=gnu++11 \
  -ggdb3 \
  -Wall \
  -Werror \
  -Wextra \
  -Wno-sign-promo \
  -Wno-unused-parameter \
  -Wstrict-aliasing \
  -fstrict-aliasing \

#LOCAL_LDFLAGS :=  -Wl,--export-dynamic
LOCAL_C_INCLUDES += ${LOCAL_PATH}/include \
					${LOCAL_PATH}/../common/include \
                    ${LOCAL_PATH}/../sepol/include \
					external/valgrind/main/include \
                    external/valgrind/main \
					art \
					art/runtime \
					external/gtest/include \
					frameworks/base/include \
					frameworks/native/include \
                    libnativehelper/include \
                    system/core/include

LOCAL_MODULE:= app_process
LOCAL_MULTILIB := both
LOCAL_MODULE_STEM_32 := app_process32
LOCAL_MODULE_STEM_64 := app_process64
include $(BUILD_EXECUTABLE)

# Create a symlink from app_process to app_process32 or 64
# depending on the target configuration.
include  $(BUILD_SYSTEM)/executable_prefer_symlink.mk
