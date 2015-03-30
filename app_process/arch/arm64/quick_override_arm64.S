#include "asm_support_arm64.S"

.extern art_quick_to_quick_bridge_override
ENTRY_NO_HIDE art_quick_to_interpreter_bridge_override
    SETUP_REF_AND_ARGS_CALLEE_SAVE_FRAME   // Set up frame and save arguments.
    //  x0 will contain mirror::ArtMethod* method.
    mov x1, xSELF                          // How to get Thread::Current() ???
    mov x2, sp

    // uint64_t artQuickOverride(mirror::ArtMethod* method, Thread* self,
    //                                      mirror::ArtMethod** sp)
    bl   artQuickOverride

    RESTORE_REF_AND_ARGS_CALLEE_SAVE_FRAME  // TODO: no need to restore arguments in this case.

    fmov d0, x0

    RETURN_OR_DELIVER_PENDING_EXCEPTION
END art_quick_to_interpreter_bridge_override
