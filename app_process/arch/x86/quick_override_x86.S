/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "asm_support_x86.S"

DEFINE_FUNCTION_NO_HIDE art_quick_to_interpreter_bridge_override
    SETUP_REF_AND_ARGS_CALLEE_SAVE_FRAME   // save frame
    mov %esp, %edx                // remember SP
    PUSH eax                      // alignment padding
    PUSH edx                      // pass SP
    pushl %fs:THREAD_SELF_OFFSET  // pass Thread::Current()
    CFI_ADJUST_CFA_OFFSET(4)
    PUSH eax                      // pass  method
    SETUP_GOT_NOSAVE              // clobbers EBX
    call SYMBOL(artQuickOverride)  // (method, Thread*, SP)
    movd %eax, %xmm0              // place return value also into floating point return value
    movd %edx, %xmm1
    punpckldq %xmm1, %xmm0
    addl LITERAL(16), %esp        // pop arguments
    CFI_ADJUST_CFA_OFFSET(-16)
    RESTORE_REF_ONLY_CALLEE_SAVE_FRAME
    RETURN_OR_DELIVER_PENDING_EXCEPTION    // return or deliver exception
END_FUNCTION art_quick_to_interpreter_bridge_override

DEFINE_FUNCTION_NO_HIDE art_quick_override_jump
    call next
next: pop %edx
    mov 6(%edx), %edx
    jmp *%eax
    .long 4
END_FUNCTION art_quick_override_jump
