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

#ifndef APP_PROCESS_ARCH_ARM64_ASM_SUPPORT_ARM64_H_
#define APP_PROCESS_ARCH_ARM64_ASM_SUPPORT_ARM64_H_

// Offset of field Thread::suspend_count_
#define THREAD_FLAGS_OFFSET 0
// Offset of field Thread::thin_lock_thread_id_
#define THREAD_ID_OFFSET 12

#define METHOD_QUICK_CODE_OFFSET 40

// Offset of field Thread::card_table_
#define THREAD_CARD_TABLE_OFFSET 120
// Offset of field Thread::exception_
#define THREAD_EXCEPTION_OFFSET 128

#define FRAME_SIZE_SAVE_ALL_CALLEE_SAVE 176
#define FRAME_SIZE_REFS_ONLY_CALLEE_SAVE 96
#define FRAME_SIZE_REFS_AND_ARGS_CALLEE_SAVE 224

#define RUNTIME_SAVE_ALL_CALLEE_SAVE_FRAME_OFFSET 0
#define RUNTIME_REF_AND_ARGS_CALLEE_SAVE_FRAME_OFFSET 16

#endif // APP_PROCESS_ARCH_ARM64_ASM_SUPPORT_ARM64_H_
