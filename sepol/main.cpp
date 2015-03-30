#include <stdlib.h>
#include <stdio.h>
#include <unistd.h>
#include <selinux/selinux.h>

#include "elog.h"
#include "sepolicy.h"

int main(int argc, char** argv) {
    ELOGD(SE, "main %s", argv[0]);

    //setcon("u:r:init:s0");

    ELOGV(SE, "Updating permissions");

    // Retry 5 times, max 15 (5 * 3) seconds.
    uint32_t i = 0;
    do {
        bool error = false;
        error = error || !policyModifyPermission(true, "zygote", "kernel", "security", "read_policy");
        error = error || !policyModifyPermission(true, "zygote", "kernel", "security", "load_policy");

        if(!error) {
            break;
        }

        sleep(3);
    } while(++i < 5);

    ELOGV(SE, "Done updating permissions");

    return 0;
}