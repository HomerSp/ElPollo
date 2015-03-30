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

#ifndef APP_PROCESS_ARCH_ARM_ASM_SUPPORT_ARM_H_
#define APP_PROCESS_ARCH_ARM_ASM_SUPPORT_ARM_H_

// Offset of field Thread::tls32_.state_and_flags verified in InitCpu
#define THREAD_FLAGS_OFFSET 0
// Offset of field Thread::tls32_.thin_lock_thread_id verified in InitCpu
#define THREAD_ID_OFFSET 12

#define METHOD_QUICK_CODE_OFFSET 40

// Offset of field Thread::tlsPtr_.card_table verified in InitCpu
#define THREAD_CARD_TABLE_OFFSET 120
// Offset of field Thread::tlsPtr_.exception verified in InitCpu
#define THREAD_EXCEPTION_OFFSET 124

#define FRAME_SIZE_SAVE_ALL_CALLEE_SAVE 176
#define FRAME_SIZE_REFS_ONLY_CALLEE_SAVE 32
#define FRAME_SIZE_REFS_AND_ARGS_CALLEE_SAVE 48

#endif // APP_PROCESS_ARCH_ARM_ASM_SUPPORT_ARM_H_
