LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := elpollo.framework

LOCAL_SRC_FILES := $(call all-java-files-under,java)

LOCAL_JAVACFLAGS := -Xlint:unchecked -Xlint:deprecation -Werror

LOCAL_DEX_PREOPT := false

include $(BUILD_JAVA_LIBRARY)

include $(CLEAR_VARS)

#
# Define a list of source files and AIDL files to document
LOCAL_SRC_FILES:= \
              $(call all-java-files-under,java)
              $(call all-Iaidl-files-under,java)

LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_DROIDDOC_HTML_DIR := docs/html

LOCAL_MODULE := elpollo.documentation

LOCAL_DROIDDOC_OPTIONS:=\
        -offlinemode \
        -title "Android SDK" \
        -proofread $(OUT_DOCS)/$(LOCAL_MODULE)-proofread.txt \
        -todo $(OUT_DOCS)/$(LOCAL_MODULE)-docs-todo.html \
        -sdkvalues $(OUT_DOCS) \
        -hdf android.whichdoc offline

include $(BUILD_DROIDDOC)
