/*
 * Copyright (C) 2014 The Android Open Source Project
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

#ifndef APP_PROCESS_ARCH_ARM64_ASM_SUPPORT_ARM64_S_
#define APP_PROCESS_ARCH_ARM64_ASM_SUPPORT_ARM64_S_ 

#include "asm_support_arm64.h"

// Define special registers.

// Register holding suspend check count down.
// 32-bit is enough for the suspend register.
#define wSUSPEND w19
// xSUSPEND is 64-bit view of wSUSPEND.
// Used to save/restore the register scratched by managed code.
#define xSUSPEND x19
// Register holding Thread::Current().
#define xSELF x18
// x18 is not preserved by aapcs64, save it on xETR(External Thread reg) for restore and later use.
#define xETR x21
// Frame Pointer
#define xFP   x29
// Link Register
#define xLR   x30
// Define the intraprocedural linkage temporary registers.
#define xIP0 x16
#define wIP0 w16
#define xIP1 x17
#define wIP1 w17

    /*
     * Macro that sets up the callee save frame to conform with
     * Runtime::CreateCalleeSaveMethod(kSaveAll)
     */
.macro SETUP_SAVE_ALL_CALLEE_SAVE_FRAME
    adrp xIP0, :got:_ZN3art7Runtime9instance_E
    ldr xIP0, [xIP0, #:got_lo12:_ZN3art7Runtime9instance_E]

    // Our registers aren't intermixed - just spill in order.
    ldr xIP0, [xIP0]  // xIP0 = & (art::Runtime * art::Runtime.instance_) .

    // xIP0 = (ArtMethod*) Runtime.instance_.callee_save_methods[kRefAndArgs]  .
    ldr xIP0, [xIP0, RUNTIME_SAVE_ALL_CALLEE_SAVE_FRAME_OFFSET ]

    sub sp, sp, #176
    .cfi_adjust_cfa_offset 176

    // Ugly compile-time check, but we only have the preprocessor.
#if (FRAME_SIZE_SAVE_ALL_CALLEE_SAVE != 176)
#error "SAVE_ALL_CALLEE_SAVE_FRAME(ARM64) size not as expected."
#endif

    // FP callee-saves
    stp d8, d9,   [sp, #8]
    stp d10, d11, [sp, #24]
    stp d12, d13, [sp, #40]
    stp d14, d15, [sp, #56]

    // Reserved registers
    stp xSELF, xSUSPEND, [sp, #72]
    .cfi_rel_offset x18, 72
    .cfi_rel_offset x19, 80

    // callee-saves
    stp x20, x21, [sp, #88]
    .cfi_rel_offset x20, 88
    .cfi_rel_offset x21, 96

    stp x22, x23, [sp, #104]
    .cfi_rel_offset x22, 104
    .cfi_rel_offset x23, 112

    stp x24, x25, [sp, #120]
    .cfi_rel_offset x24, 120
    .cfi_rel_offset x25, 128

    stp x26, x27, [sp, #136]
    .cfi_rel_offset x26, 136
    .cfi_rel_offset x27, 144

    stp x28, x29, [sp, #152]
    .cfi_rel_offset x28, 152
    .cfi_rel_offset x29, 160

    str xLR, [sp, #168]
    .cfi_rel_offset x30, 168

    // Loads appropriate callee-save-method
    str xIP0, [sp]    // Store ArtMethod* Runtime::callee_save_methods_[kRefsAndArgs]
.endm

    /*
     * Macro that set calls through to artDeliverPendingExceptionFromCode, where the pending
     * exception is Thread::Current()->exception_
     */
.macro DELIVER_PENDING_EXCEPTION
    SETUP_SAVE_ALL_CALLEE_SAVE_FRAME
    mov x0, xSELF
    mov x1, sp

    // Point of no return.
    b artDeliverPendingExceptionFromCode  // artDeliverPendingExceptionFromCode(Thread*, SP)
    brk 0  // Unreached
.endm

.macro RETURN_OR_DELIVER_PENDING_EXCEPTION_REG reg
    ldr \reg, [xSELF, # THREAD_EXCEPTION_OFFSET]   // Get exception field.
    cbnz \reg, 1f
    ret
1:
    DELIVER_PENDING_EXCEPTION
.endm

.macro RETURN_OR_DELIVER_PENDING_EXCEPTION
    RETURN_OR_DELIVER_PENDING_EXCEPTION_REG xIP0
.endm

// Same as above with x1. This is helpful in stubs that want to avoid clobbering another register.
.macro RETURN_OR_DELIVER_PENDING_EXCEPTION_X1
    RETURN_OR_DELIVER_PENDING_EXCEPTION_REG x1
.endm

.macro SETUP_REF_AND_ARGS_CALLEE_SAVE_FRAME_INTERNAL
    sub sp, sp, #224
    .cfi_adjust_cfa_offset 224

    // Ugly compile-time check, but we only have the preprocessor.
#if (FRAME_SIZE_REFS_AND_ARGS_CALLEE_SAVE != 224)
#error "REFS_AND_ARGS_CALLEE_SAVE_FRAME(ARM64) size not as expected."
#endif

    // FP args
    stp d0, d1, [sp, #16]
    stp d2, d3, [sp, #32]
    stp d4, d5, [sp, #48]
    stp d6, d7, [sp, #64]

    // args and x20(callee-save)
    stp x1,  x2, [sp, #80]
    .cfi_rel_offset x1, 80
    .cfi_rel_offset x2, 88

    stp x3,  x4, [sp, #96]
    .cfi_rel_offset x3, 96
    .cfi_rel_offset x4, 104

    stp x5,  x6, [sp, #112]
    .cfi_rel_offset x5, 112
    .cfi_rel_offset x6, 120

    stp x7, x20, [sp, #128]
    .cfi_rel_offset x7, 128
    .cfi_rel_offset x20, 136

    // Callee-saves.
    stp x21, x22, [sp, #144]
    .cfi_rel_offset x21, 144
    .cfi_rel_offset x22, 152

    stp x23, x24, [sp, #160]
    .cfi_rel_offset x23, 160
    .cfi_rel_offset x24, 168

    stp x25, x26, [sp, #176]
    .cfi_rel_offset x25, 176
    .cfi_rel_offset x26, 184

    stp x27, x28, [sp, #192]
    .cfi_rel_offset x27, 192
    .cfi_rel_offset x28, 200

    // x29(callee-save) and LR
    stp x29, xLR, [sp, #208]
    .cfi_rel_offset x29, 208
    .cfi_rel_offset x30, 216

    // Save xSELF to xETR.
    mov xETR, xSELF
.endm

    /*
     * Macro that sets up the callee save frame to conform with
     * Runtime::CreateCalleeSaveMethod(kRefsAndArgs).
     *
     * TODO This is probably too conservative - saving FP & LR.
     */
.macro SETUP_REF_AND_ARGS_CALLEE_SAVE_FRAME
    adrp xIP0, :got:_ZN3art7Runtime9instance_E
    ldr xIP0, [xIP0, #:got_lo12:_ZN3art7Runtime9instance_E]

    // Our registers aren't intermixed - just spill in order.
    ldr xIP0, [xIP0]  // xIP0 = & (art::Runtime * art::Runtime.instance_) .

    // xIP0 = (ArtMethod*) Runtime.instance_.callee_save_methods[kRefAndArgs]  .
    ldr xIP0, [xIP0, RUNTIME_REF_AND_ARGS_CALLEE_SAVE_FRAME_OFFSET ]

    SETUP_REF_AND_ARGS_CALLEE_SAVE_FRAME_INTERNAL

    str xIP0, [sp]    // Store ArtMethod* Runtime::callee_save_methods_[kRefsAndArgs]
.endm

// TODO: Probably no need to restore registers preserved by aapcs64.
.macro RESTORE_REF_AND_ARGS_CALLEE_SAVE_FRAME
    // Restore xSELF.
    mov xSELF, xETR

    // FP args
    ldp d0, d1, [sp, #16]
    ldp d2, d3, [sp, #32]
    ldp d4, d5, [sp, #48]
    ldp d6, d7, [sp, #64]

    // args and x20(callee-save)
    ldp x1,  x2, [sp, #80]
    .cfi_restore x1
    .cfi_restore x2

    ldp x3,  x4, [sp, #96]
    .cfi_restore x3
    .cfi_restore x4

    ldp x5,  x6, [sp, #112]
    .cfi_restore x5
    .cfi_restore x6

    ldp x7, x20, [sp, #128]
    .cfi_restore x7
    .cfi_restore x20

    // Callee-saves.
    ldp x21, x22, [sp, #144]
    .cfi_restore x21
    .cfi_restore x22

    ldp x23, x24, [sp, #160]
    .cfi_restore x23
    .cfi_restore x24

    ldp x25, x26, [sp, #176]
    .cfi_restore x25
    .cfi_restore x26

    ldp x27, x28, [sp, #192]
    .cfi_restore x27
    .cfi_restore x28

    // x29(callee-save) and LR
    ldp x29, xLR, [sp, #208]
    .cfi_restore x29
    .cfi_restore x30

    add sp, sp, #224
    .cfi_adjust_cfa_offset -224
.endm


.macro ENTRY_NO_HIDE name
    .type \name, #function
    .global \name
    /* Cache alignment for function entry */
    .balign 16
\name:
    .cfi_startproc
.endm

.macro END name
    .cfi_endproc
    .size \name, .-\name
.endm

#endif // APP_PROCESS_ARCH_ARM64_ASM_SUPPORT_ARM64_S_