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

#include "asm_support_arm.h"

#ifndef APP_PROCESS_ARCH_ARM_ASM_SUPPORT_ARM_S_
#define APP_PROCESS_ARCH_ARM_ASM_SUPPORT_ARM_S_

.syntax unified
.arch armv7-a
.thumb

    /* Deliver the given exception */
    .extern artDeliverExceptionFromCode
    /* Deliver an exception pending on a thread */
    .extern artDeliverPendingException

    /*
     * Macro that sets up the callee save frame to conform with
     * Runtime::CreateCalleeSaveMethod(kSaveAll)
     */
.macro SETUP_SAVE_ALL_CALLEE_SAVE_FRAME
    push {r4-r11, lr} @ 9 words of callee saves
    .save {r4-r11, lr}
    .cfi_adjust_cfa_offset 36
    .cfi_rel_offset r4, 0
    .cfi_rel_offset r5, 4
    .cfi_rel_offset r6, 8
    .cfi_rel_offset r7, 12
    .cfi_rel_offset r8, 16
    .cfi_rel_offset r9, 20
    .cfi_rel_offset r10, 24
    .cfi_rel_offset r11, 28
    .cfi_rel_offset lr, 32
    vpush {s0-s31}
    .pad #128
    .cfi_adjust_cfa_offset 128
    sub sp, #12       @ 3 words of space, bottom word will hold Method*
    .pad #12
    .cfi_adjust_cfa_offset 12

     // Ugly compile-time check, but we only have the preprocessor.
#if (FRAME_SIZE_SAVE_ALL_CALLEE_SAVE != 36 + 128 + 12)
#error "SAVE_ALL_CALLEE_SAVE_FRAME(ARM) size not as expected."
#endif
.endm

    /*
     * Macro that set calls through to artDeliverPendingExceptionFromCode, where the pending
     * exception is Thread::Current()->exception_
     */
.macro DELIVER_PENDING_EXCEPTION
    .fnend
    .fnstart
    SETUP_SAVE_ALL_CALLEE_SAVE_FRAME           @ save callee saves for throw
    mov    r0, r9                              @ pass Thread::Current
    mov    r1, sp                              @ pass SP
    b      artDeliverPendingExceptionFromCode  @ artDeliverPendingExceptionFromCode(Thread*, SP)
.endm

    /*
     * Macro that sets up the callee save frame to conform with
     * Runtime::CreateCalleeSaveMethod(kRefsAndArgs).
     */
.macro SETUP_REF_AND_ARGS_CALLEE_SAVE_FRAME
    push {r1-r3, r5-r8, r10-r11, lr}  @ 10 words of callee saves
    .save {r1-r3, r5-r8, r10-r11, lr}
    .cfi_rel_offset r1, 0
    .cfi_rel_offset r2, 4
    .cfi_rel_offset r3, 8
    .cfi_rel_offset r5, 12
    .cfi_rel_offset r6, 16
    .cfi_rel_offset r7, 20
    .cfi_rel_offset r8, 24
    .cfi_rel_offset r10, 28
    .cfi_rel_offset r11, 32
    .cfi_rel_offset lr, 36
    .cfi_adjust_cfa_offset 40
    sub sp, #8                        @ 2 words of space, bottom word will hold Method*
    .pad #8
    .cfi_adjust_cfa_offset 8

    // Ugly compile-time check, but we only have the preprocessor.
#if (FRAME_SIZE_REFS_AND_ARGS_CALLEE_SAVE != 40 + 8)
#error "REFS_AND_ARGS_CALLEE_SAVE_FRAME(ARM) size not as expected."
#endif
.endm

.macro RESTORE_ALL_CALLEE_SAVE_FRAME
    add sp, #12               @ bottom word holds Method*
    vpop {s0-s31}
    pop {r4-r11, lr} @ 7 words of callee saves
    .cfi_restore r4
    .cfi_restore r5
    .cfi_restore r6
    .cfi_restore r7
    .cfi_restore r8
    .cfi_restore r9
    .cfi_restore r10
    .cfi_restore r11
    .cfi_adjust_cfa_offset -164
.endm

.macro RESTORE_REF_ONLY_CALLEE_SAVE_FRAME
    add sp, #4               @ bottom word holds Method*
    pop {r5-r8, r10-r11, lr} @ 7 words of callee saves
    .cfi_restore r5
    .cfi_restore r6
    .cfi_restore r7
    .cfi_restore r8
    .cfi_restore r10
    .cfi_restore r11
    .cfi_adjust_cfa_offset -32
.endm

.macro ENTRY_NO_HIDE name
    .thumb_func
    .type \name, #function
    .global \name
    /* Cache alignment for function entry */
    .balign 16
\name:
    .cfi_startproc
    .fnstart
.endm

.macro END name
    .fnend
    .cfi_endproc
    .size \name, .-\name
.endm

#endif // APP_PROCESS_ARCH_ARM_ASM_SUPPORT_ARM_S_