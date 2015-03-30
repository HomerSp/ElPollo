#include "asm_support_arm.S"

    .extern artQuickOverride
ENTRY_NO_HIDE art_quick_to_interpreter_bridge_override
    SETUP_REF_AND_ARGS_CALLEE_SAVE_FRAME
    mov     r1, r9                 @ pass Thread::Current
    mov     r2, sp                 @ pass SP
    blx     artQuickOverride    @ (Method* method, Thread*, SP)
    ldr     r2, [r9, #THREAD_EXCEPTION_OFFSET]  @ load Thread::Current()->exception_
    add     sp, #16                @ skip r1-r3, 4 bytes padding.
    .cfi_adjust_cfa_offset -16
    RESTORE_REF_ONLY_CALLEE_SAVE_FRAME
    cbnz    r2, 1f                 @ success if no exception is pending
    bx    lr                       @ return on success
1:
    DELIVER_PENDING_EXCEPTION
END art_quick_to_interpreter_bridge_override
