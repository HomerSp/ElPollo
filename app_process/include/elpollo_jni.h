#ifndef CUSTOM_ELPOLLO_APP_PROCESS_INCLUDE_ELPOLLO_H_
#define CUSTOM_ELPOLLO_APP_PROCESS_INCLUDE_ELPOLLO_H_

#include <iostream>
#include "sepolicy.h"

namespace android {
    class AppRuntime;
}

namespace elpolloJNI {
    const char RUNTIME_INIT[] = "se.aqba.framework.elpollo.RuntimeInit";
    const char ZYGOTE_INIT[] = "se.aqba.framework.elpollo.ZygoteInit";

    const char FRAMEWORK_FILE[] = "/system/framework/elpollo.framework.jar";

    static bool sIsEnabled = true;

    class ElPolloRuntime
    {
    public:
        static void onVmCreated(JNIEnv* env);

    private:
        static void registerNatives(JNIEnv* env);

    };

    static void addClassPathEntryToEnv(const char *env, const char *framework) {
        const char* current = getenv(env);

        std::string modified = "";
        if(current != NULL) {
            if(std::string(current).find(framework) == std::string::npos) {
                modified = std::string(framework) + std::string(":") + current;
            }
        } else {
            modified = std::string(framework);
        }

        __android_log_print(ANDROID_LOG_DEBUG, "ElPolloJni", "addClassPathEntry %s -> %s", env, modified.c_str());
        setenv(env, modified.c_str(), 1);
    }

    static void addClassPathEntry() {
        addClassPathEntryToEnv("CLASSPATH", FRAMEWORK_FILE);
        addClassPathEntryToEnv("BOOTCLASSPATH", FRAMEWORK_FILE);
    }

    static bool shouldSetContext() {
        FILE *file = fopen("/proc/self/attr/current", "r");
        if(file != NULL) {
            char buf[256];
            fgets(buf, 256, file);

            fclose(file);
            return !strcmp(buf, "u:r:zygote:s0");
        }

        return false;
    }

    static bool modifySepolicy() {
        if(!shouldSetContext()) {
            return true;
        }

        ELOGD(Jni, "Updating permissions");

        // The elpollo_sepol may run simultaneously to this (from init.d), so wait a bit before we start it.
        bool canRead = policyCanRead();
        if(!canRead) {
            sleep(3);
            canRead = policyCanRead();
        }
        
        if(!canRead) {
            pid_t childPid = fork();
            if(childPid == 0) {
                execl("/system/xbin/elpollo_sepol", "/system/xbin/elpollo_sepol", NULL);
            } else if(childPid < 0) {
                ELOGE(Jni, "Could not start elpollo_sepol");
            } else {
                waitpid(childPid, NULL, 0);
            }
        }

        // Retry 5 times, max 15 (5 * 3) seconds.
        uint32_t i = 0;
        do {
            bool error = false;
            error = error || !policyModifyPermission(true, "init", "init", "process", "setcurrent");
            error = error || !policyModifyPermission(true, "init", "zygote_exec", "file", "execute_no_trans");
            error = error || !policyModifyPermission(true, "init", "zygote", "process", "dyntransition");
            error = error || !policyModifyPermission(true, "zygote", "zygote", "process", "execmem");
            error = error || !policyModifyPermission(true, "zygote", "app_data_file", "dir", "search");
            error = error || !policyModifyPermission(true, "zygote", "app_data_file", "file", "read");
            error = error || !policyModifyPermission(true, "zygote", "app_data_file", "file", "getattr");
            error = error || !policyModifyPermission(true, "zygote", "app_data_file", "file", "open");

            if(!error) {
                sIsEnabled = true;

                break;
            }

            sIsEnabled = false;

            // Wait for 3 seconds.
            sleep(3);
        } while(++i < 5);

        ELOGD(Jni, "Done updating permissions");

        return sIsEnabled;
    }

    static bool useFramework() {
        FILE* file = fopen(FRAMEWORK_FILE, "rb");
        if(!file) {
            return false;
        }

        fclose(file);
        return sIsEnabled;
    }
}

#endif
