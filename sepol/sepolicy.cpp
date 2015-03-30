extern "C" {
#include <sepol/sepol.h>
#include <sepol/policydb/policydb.h>
#include <sepol/policydb/services.h>
#include <sepol/policydb/util.h>
}

#include <stdlib.h>
#include <stdio.h>
#include <unistd.h>
#include <sys/prctl.h>
#include <sys/mman.h>
#include <selinux/selinux.h>

#include "elog.h"
#include "sepolicy.h"

uint32_t valueFromPermIndex(class_datum_t *targetClass, uint32_t permIndex) {
    uint32_t ret = 0;
    {
        hashtab_t perms = targetClass->permissions.table;
        for(uint32_t a = 0; a < perms->size; a++) {
            hashtab_ptr_t cur = perms->htable[a];
            if(cur == NULL)
                continue;

            do {
                perm_datum_t *datum = (perm_datum_t *)cur->datum;
                if(datum->s.value == permIndex + 1) {
                    ret = 1 << (datum->s.value - 1);
                }
            } while((cur = cur->next) != NULL && ret == 0);
        }
    }
    if(targetClass->comdatum != NULL){
        hashtab_t perms = targetClass->comdatum->permissions.table;
        for(uint32_t a = 0; a < perms->size; a++) {
            hashtab_ptr_t cur = perms->htable[a];
            if(cur == NULL)
                continue;

            do {
                perm_datum_t *datum = (perm_datum_t *)cur->datum;
                if(datum->s.value == permIndex + 1) {
                    ret = 1 << (datum->s.value - 1);
                    break;
                }
            } while((cur = cur->next) != NULL && ret == 0);
        }
    }

    return ret;
}
uint32_t valueFromPermName(class_datum_t *targetClass, const char *permName) {
    uint32_t ret = 0;

    perm_datum_t *datum = (perm_datum_t *)hashtab_search(targetClass->permissions.table, (char *)permName);
    if(datum == NULL && targetClass->comdatum != NULL) {
        datum = (perm_datum_t *)hashtab_search(targetClass->comdatum->permissions.table, (char *)permName);
    }
    if(datum != NULL) {
        ret = valueFromPermIndex(targetClass, datum->s.value - 1);
    }

    ELOGD(SELib, "valueFromPermName %s returning %u", permName, ret);
    return ret;
}

bool valuePermExists(class_datum_t *targetClass, const char *permName, uint32_t avData) {
    perm_datum_t *datum = (perm_datum_t *)hashtab_search(targetClass->permissions.table, (char *)permName);
    if(datum == NULL && targetClass->comdatum != NULL) {
        datum = (perm_datum_t *)hashtab_search(targetClass->comdatum->permissions.table, (char *)permName);
    }
    if(datum != NULL) {
        return (avData & (1 << (datum->s.value - 1)));
    }

    return false;
}

uint32_t permissionsGetCurrent(class_datum_t *targetClass, uint32_t avData) {
    uint32_t value = 0;
    for(uint32_t i = 0; i < targetClass->permissions.nprim; i++) {
        if(avData & (1 << i)) {
            value += valueFromPermIndex(targetClass, i);
        }
    }

    return value;
}

bool permissionsModify(policydb_t &pdb, const char *psourceType, const char *ptargetType, const char *ptargetClass, const char *oldPerm, bool addPerm) {
    uint32_t value = 0;
    uint32_t index = 0;

    avtab_t avs = pdb.te_avtab;
    for(uint32_t i = 0; i < avs.nslot; i++, index++) {
        avtab_ptr_t cur = avs.htable[i];
        if(cur == NULL)
            continue;

        do {
            char *targetClassName = pdb.p_class_val_to_name[cur->key.target_class - 1];
            char *targetTypeName = pdb.p_type_val_to_name[cur->key.target_type - 1];
            char *sourceTypeName = pdb.p_type_val_to_name[cur->key.source_type - 1];

            class_datum_t *targetClass = pdb.class_val_to_struct[cur->key.target_class - 1];
            type_datum_t *targetType = pdb.type_val_to_struct[cur->key.target_type - 1];
            type_datum_t *sourceType = pdb.type_val_to_struct[cur->key.source_type - 1];

            if(strcmp(targetClassName, ptargetClass) || strcmp(targetTypeName, ptargetType) || strcmp(sourceTypeName, psourceType)) {
                continue;
            }

            ELOGD(SELib, "%s->%s (%s) @ %d", sourceTypeName, targetTypeName, targetClassName, index);

            if((cur->key.specified & AVTAB_AV) != 0) {
                value = permissionsGetCurrent(targetClass, cur->datum.data);
                ELOGD(SELib, "Current permissions: %u", value);
                if(addPerm && valuePermExists(targetClass, oldPerm, cur->datum.data)) {
                    ELOGI(SELib, "Cannot add permission: %s already exists", oldPerm);
                    continue;
                } else if(!addPerm && !valuePermExists(targetClass, oldPerm, cur->datum.data)) {
                    ELOGI(SELib, "Cannot remove permission: %s does not exist", oldPerm);
                    continue;
                }

                if(addPerm) {
                    ELOGD(SELib, "Adding permission %s", oldPerm);
                    value += valueFromPermName(targetClass, oldPerm);
                } else {
                    ELOGD(SELib, "Removing permission %s", oldPerm);
                    value -= valueFromPermName(targetClass, oldPerm); 
                }

                cur->datum.data = value;
            }

            break;
        } while((cur = cur->next) != NULL);
    }

    //return value != 0;

    // Try adding a new type
    if(value == 0) {
        type_datum_t *sourceType = (type_datum_t *)hashtab_search(pdb.p_types.table, (char *)psourceType);
        type_datum_t *targetType = (type_datum_t *)hashtab_search(pdb.p_types.table, (char *)ptargetType);
        class_datum_t *targetClass = (class_datum_t *)hashtab_search(pdb.p_classes.table, (char *)ptargetClass);

        if(sourceType != NULL && targetClass != NULL) {
            if(targetType == NULL) {
                ELOGD(SELib, "Adding new type %s", ptargetType);

                type_datum_t *tmpTarget = (type_datum_t *)malloc(sizeof(type_datum_t));
                memcpy(tmpTarget, sourceType, sizeof(type_datum_t));
                tmpTarget->primary = 1;
                tmpTarget->flavor = TYPE_TYPE;
                tmpTarget->s.value = pdb.p_types.nprim + 1;

                hashtab_insert(pdb.p_types.table, strdup(ptargetType), tmpTarget);

                pdb.p_types.nprim++;

                uint32_t nprim = pdb.p_types.nprim;
                pdb.type_attr_map = (ebitmap_t*)realloc(pdb.type_attr_map, pdb.p_types.nprim * sizeof(ebitmap_t));
                pdb.attr_type_map = (ebitmap_t*)realloc(pdb.attr_type_map, pdb.p_types.nprim * sizeof(ebitmap_t));
                ebitmap_init(&pdb.type_attr_map[nprim]);
                ebitmap_init(&pdb.attr_type_map[nprim]);
                
                ebitmap_node_t *tnode;
                uint32_t j = 0;
                
                ebitmap_for_each_bit(&pdb.type_attr_map[nprim], tnode, j) {
                    if (!ebitmap_node_get_bit(tnode, j) || nprim == j)
                        continue;

                    ebitmap_set_bit(&pdb.attr_type_map[j], nprim, 1);
                    ebitmap_set_bit(&pdb.type_attr_map[nprim], nprim, 1);
                }

                targetType = tmpTarget;
            }

            ELOGD(SELib, "Inserting new class %s", ptargetClass);

            avtab_key_t avKey;
            memset(&avKey, 0, sizeof(avtab_key_t));
            avKey.source_type = sourceType->s.value;
            avKey.target_type = targetType->s.value;
            avKey.target_class = targetClass->s.value;
            avKey.specified = AVTAB_ALLOWED;

            avtab_datum_t avDatum;
            memset(&avDatum, 0, sizeof(avtab_datum_t));
            avDatum.data = valueFromPermName(targetClass, oldPerm);

            avtab_insert(&pdb.te_avtab, &avKey, &avDatum);
        }

        policydb_index_others(NULL, &pdb, 0);

        return true;
    }

    return value != 0;
}

bool permissionAdd(policydb_t &pdb, const char *psourceType, const char *ptargetType, const char *ptargetClass, const char *oldPerm) {
    return permissionsModify(pdb, psourceType, ptargetType, ptargetClass, oldPerm, true);
}
bool permissionRemove(policydb_t &pdb, const char *psourceType, const char *ptargetType, const char *ptargetClass, const char *oldPerm) {
    return permissionsModify(pdb, psourceType, ptargetType, ptargetClass, oldPerm, false);
}

void writePolicy(policydb_t *policydb) {
    policy_file pf;

    /* Compute the length for the new policy image. */
    policy_file_init(&pf);
    pf.type = PF_LEN;
    pf.handle = NULL;
    if (policydb_write(policydb, &pf)) {
        return;
    }

    /* Allocate the new policy image. */
    pf.type = PF_USE_MEMORY;
    pf.data = new char[pf.len];
    if (!pf.data) {
        return;
    }

    /* Need to save len and data prior to modification by policydb_write. */
    uint32_t tmp_len = pf.len;
    char *tmp_data = pf.data;

    /* Write out the new policy image. */
    if (policydb_write(policydb, &pf)) {
        return;
    }

    if(security_load_policy(tmp_data, tmp_len) != 0) {
        ELOGE(SELib, "Could not load the new policy");
    }

    delete [] tmp_data;
}

bool policyCanRead() {
    FILE *file = fopen("/sys/fs/selinux/policy", "rb");
    if(!file) {
        ELOGW(SELib, "Failed to open SELinux policy db %d", errno);
        return false;
    }

    fclose(file);

    return true;
}

bool policyModifyPermission(bool add, const char* sourceContext, const char* targetContext, const char* targetClass, const char* perm)
{
    policydb_t policydb;
    sidtab_t sidtab;

    FILE *file = fopen("/sys/fs/selinux/policy", "rb");
    if(!file) {
        ELOGW(SELib, "Failed to open SELinux policy db %d", errno);
        return false;
    }

    policy_file pf;
    policy_file_init(&pf);
    pf.type = PF_USE_STDIO;
    pf.fp = file;
    if (policydb_init(&policydb)) {
        fclose(file);
        return false;
    }
    policydb_read(&policydb, &pf, 1);

    ELOGV(SELib, "Found db %s", policydb.name);

    bool ret = false;
    if(add) {
        ret = permissionAdd(policydb, sourceContext, targetContext, targetClass, perm);
    } else {
        ret = permissionRemove(policydb, sourceContext, targetContext, targetClass, perm);
    }

    ELOGV(SELib, "Modify permission returned %d", ret);

    writePolicy(&policydb);

    ELOGV(SELib, "Done writing new policy");

    policydb_destroy(&policydb);
    fclose(file);

    ELOGV(SELib, "Done");

    if(!ret) {
        ELOGE(SELib, "Policy modify failed");
    } else {
        ELOGI(SELib, "Policy modified");
    }

    return ret;
}
