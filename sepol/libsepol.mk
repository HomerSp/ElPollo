common_src_files := \
	libsepol/src/assertion.c \
	libsepol/src/avrule_block.c \
	libsepol/src/avtab.c \
	libsepol/src/boolean_record.c \
	libsepol/src/booleans.c \
	libsepol/src/conditional.c \
	libsepol/src/constraint.c \
	libsepol/src/context.c \
	libsepol/src/context_record.c \
	libsepol/src/debug.c \
	libsepol/src/ebitmap.c \
	libsepol/src/expand.c \
	libsepol/src/genbools.c \
	libsepol/src/genusers.c \
	libsepol/src/handle.c \
	libsepol/src/hashtab.c \
	libsepol/src/hierarchy.c \
	libsepol/src/iface_record.c \
	libsepol/src/interfaces.c \
	libsepol/src/link.c \
	libsepol/src/mls.c \
	libsepol/src/module.c \
	libsepol/src/node_record.c \
	libsepol/src/nodes.c \
	libsepol/src/polcaps.c \
	libsepol/src/policydb.c \
	libsepol/src/policydb_convert.c \
	libsepol/src/policydb_public.c \
	libsepol/src/port_record.c \
	libsepol/src/ports.c \
	libsepol/src/roles.c \
	libsepol/src/services.c \
	libsepol/src/sidtab.c \
	libsepol/src/symtab.c \
	libsepol/src/user_record.c \
	libsepol/src/users.c \
	libsepol/src/util.c \
	libsepol/src/write.c \
	sepolicy.cpp

common_cflags := \
	-Wall -W  \
	-Wshadow -Wmissing-noreturn \
	-Wmissing-format-attribute \
	-std=c99

ifeq ($(HOST_OS), darwin)
common_cflags += -DDARWIN
endif

common_includes := \
	$(LOCAL_PATH)/include \
	$(LOCAL_PATH)/../common/include \
	$(LOCAL_PATH)/libsepol/include/ \
	$(LOCAL_PATH)/libsepol/src/ \
	bionic/libc/include

##
# libsepol.a
#
include $(CLEAR_VARS)

LOCAL_MODULE := libsepol_elpollo
LOCAL_MODULE_TAGS := optional
LOCAL_C_INCLUDES := $(common_includes) 
LOCAL_CFLAGS := $(common_cflags)
LOCAL_SRC_FILES := $(common_src_files)
LOCAL_MODULE_CLASS := STATIC_LIBRARIES

include $(BUILD_STATIC_LIBRARY)
