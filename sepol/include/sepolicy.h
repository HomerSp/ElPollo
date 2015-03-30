#ifndef CUSTOM_ELPOLLO_APP_PROCESS_INCLUDE_SEPOLICY_H_
#define CUSTOM_ELPOLLO_APP_PROCESS_INCLUDE_SEPOLICY_H_

extern bool policyCanRead();
extern bool policyModifyPermission(bool add, const char* sourceT, const char* targetT, const char* targetC, const char* perm);

#endif //CUSTOM_ELPOLLO_APP_PROCESS_INCLUDE_SEPOLICY_H_