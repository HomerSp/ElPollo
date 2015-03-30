#ifndef CUSTOM_ELPOLLO_APP_PROCESS_INCLUDE_ELOG_H_
#define CUSTOM_ELPOLLO_APP_PROCESS_INCLUDE_ELOG_H_

#include <android/log.h>

#define ELOG_TAG "ElPollo"

#define ELOG_LEVEL_VERBOSE 0
#define ELOG_LEVEL_DEBUG 1
#define ELOG_LEVEL_INFO 2
#define ELOG_LEVEL_WARN 3
#define ELOG_LEVEL_ERROR 4

#ifndef ELOG_LEVEL
#define ELOG_LEVEL ELOG_LEVEL_DEBUG
#endif

#if ELOG_LEVEL <= ELOG_LEVEL_VERBOSE
#define ELOGV(postfix, data...) __android_log_print(ANDROID_LOG_VERBOSE, ELOG_TAG # postfix, data)
#else
#define ELOGV(postfix, data...)
#endif
#if ELOG_LEVEL <= ELOG_LEVEL_DEBUG
#define ELOGD(postfix, data...) __android_log_print(ANDROID_LOG_DEBUG, ELOG_TAG # postfix, data)
#else
#define ELOGD(postfix, data...)
#endif
#if ELOG_LEVEL <= ELOG_LEVEL_INFO
#define ELOGI(postfix, data...) __android_log_print(ANDROID_LOG_INFO, ELOG_TAG # postfix, data)
#else
#define ELOGI(postfix, data...)
#endif
#if ELOG_LEVEL <= ELOG_LEVEL_WARN
#define ELOGW(postfix, data...) __android_log_print(ANDROID_LOG_WARN, ELOG_TAG # postfix, data)
#else
#define ELOGW(postfix, data...)
#endif
#if ELOG_LEVEL <= ELOG_LEVEL_ERROR
#define ELOGE(postfix, data...) __android_log_print(ANDROID_LOG_ERROR, ELOG_TAG # postfix, data)
#else
#define ELOGE(postfix, data...)
#endif

#endif //CUSTOM_ELPOLLO_APP_PROCESS_INCLUDE_ELOG_H_