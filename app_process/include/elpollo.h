#ifndef CUSTOM_ELPOLLO_ELPOLLO_H_
#define CUSTOM_ELPOLLO_ELPOLLO_H_

#include <android/log.h>

#include <runtime/runtime.h>
#include <runtime/class_linker.h>
#include <runtime/thread.h>
#include <runtime/vmap_table.h>
#include <runtime/entrypoints/quick/callee_save_frame.h>
#include <runtime/interpreter/interpreter.h>
#include <runtime/mirror/class.h>
#include <runtime/mirror/class-inl.h>
#include <runtime/mirror/class_loader.h>
#include <runtime/reflection.h>
#include <runtime/thread_list.h>

#include <selinux/selinux.h>

#include <jni.h>

#include <stdlib.h>
#include <stdio.h>
#include <unistd.h>
#include <sys/prctl.h>
#include <dlfcn.h>

namespace elpollo {
    // Enable overrides.
    static constexpr bool kEnableOverrides = true;
    // Whether we should inject a jump in the original quick code.
    // This fixes issues where jumps to quick code are hardcoded (due to optimisation).
    static constexpr bool kForceNativeOverride = true;
    // Force the code to jump to quick code (when applicable).
    // Disable to use interpreter mode.
    static constexpr bool kForceQuickCode = false;

    static constexpr size_t kBytesStackArgLocation = 4;

    static constexpr size_t kQuickCalleeSaveFrame_RefAndArgs_FrameSize =
          art::GetCalleeSaveFrameSize(art::kRuntimeISA, art::Runtime::kRefsAndArgs);

#if defined(__arm__)
        static constexpr size_t kPointerSize = 4;
        static constexpr bool kQuickSoftFloatAbi = true;  // This is a soft float ABI.
        static constexpr size_t kNumQuickGprArgs = 3;  // 3 arguments passed in GPRs.
        static constexpr size_t kNumQuickFprArgs = 0;  // 0 arguments passed in FPRs.
        static constexpr size_t kQuickCalleeSaveFrame_RefAndArgs_Fpr1Offset =
            art::arm::ArmCalleeSaveFpr1Offset(art::Runtime::kRefsAndArgs);  // Offset of first FPR arg.
        static constexpr size_t kQuickCalleeSaveFrame_RefAndArgs_Gpr1Offset =
            art::arm::ArmCalleeSaveGpr1Offset(art::Runtime::kRefsAndArgs);  // Offset of first GPR arg.
        static size_t GprIndexToGprOffset(uint32_t gpr_index) {
            return gpr_index * art::GetBytesPerGprSpillLocation(art::kRuntimeISA);
        }
        static size_t FprIndexToFprOffset(uint32_t fpr_index) {
            return fpr_index * art::GetBytesPerFprSpillLocation(art::kRuntimeISA);
        }
 
        //asm(
        //    "ldr pc, [pc, #0]"
        //    ".long 0x0"
        //);
        static constexpr uint8_t QUICK_OVERRIDE[] = {0xDF, 0xF8, 0x00, 0xF0, 0x00, 0x00, 0x00, 0x00};
        static constexpr uint32_t QUICK_OVERRIDE_ADDR = 4;
#elif defined(__aarch64__)
        static constexpr size_t kPointerSize = 8;
        static constexpr bool kQuickSoftFloatAbi = false;  // This is a hard float ABI.
        static constexpr size_t kNumQuickGprArgs = 7;  // 7 arguments passed in GPRs.
        static constexpr size_t kNumQuickFprArgs = 8;  // 8 arguments passed in FPRs.
        static constexpr size_t kQuickCalleeSaveFrame_RefAndArgs_Fpr1Offset =
            art::arm64::Arm64CalleeSaveFpr1Offset(art::Runtime::kRefsAndArgs);  // Offset of first FPR arg.
        static constexpr size_t kQuickCalleeSaveFrame_RefAndArgs_Gpr1Offset =
            art::arm64::Arm64CalleeSaveGpr1Offset(art::Runtime::kRefsAndArgs);  // Offset of first GPR arg.
        static size_t GprIndexToGprOffset(uint32_t gpr_index) {
            return gpr_index * art::GetBytesPerGprSpillLocation(art::kRuntimeISA);
        }
        static size_t FprIndexToFprOffset(uint32_t fpr_index) {
            return fpr_index * art::GetBytesPerFprSpillLocation(art::kRuntimeISA);
        }

        
        //asm(
        //    "ldr x1, #8"
        //    "br  x1"
        //    ".long 0x0"
        //);
        
        static constexpr uint8_t QUICK_OVERRIDE[] = {0x41, 0x00, 0x00, 0x58, 0x20, 0x00, 0x1F, 0xD6, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        static constexpr uint32_t QUICK_OVERRIDE_ADDR = 8;
#elif defined(__i386__)
        static constexpr size_t kPointerSize = 4;
        static constexpr bool kQuickSoftFloatAbi = true;  // This is a soft float ABI.
        static constexpr size_t kNumQuickGprArgs = 3;  // 3 arguments passed in GPRs.
        static constexpr size_t kNumQuickFprArgs = 0;  // 0 arguments passed in FPRs.
        static constexpr size_t kQuickCalleeSaveFrame_RefAndArgs_Fpr1Offset = 0;  // Offset of first FPR arg.
        static constexpr size_t kQuickCalleeSaveFrame_RefAndArgs_Gpr1Offset = 4;  // Offset of first GPR arg.
        static constexpr size_t kQuickCalleeSaveFrame_RefAndArgs_LrOffset = 28;  // Offset of return address.
        static size_t GprIndexToGprOffset(uint32_t gpr_index) {
            return gpr_index * art::GetBytesPerGprSpillLocation(art::kRuntimeISA);
        }
        static size_t FprIndexToFprOffset(uint32_t fpr_index) {
            return fpr_index * art::GetBytesPerFprSpillLocation(art::kRuntimeISA);
        }

        /*
        asm(
            "call next\n"
            "next:\n"
            "pop %edx\n"
            "mov 6(%edx), %edx\n"
            "jmp *%edx\n"
            ".long 0x0"
        );
        */
        
        static constexpr uint8_t QUICK_OVERRIDE[] = {0xE8, 0x00, 0x00, 0x00, 0x00, 0x5A, 0x8B, 0x52, 0x0B, 0xFF, 0xE2, 0x00, 0x00, 0x00, 0x00};
        static constexpr uint32_t QUICK_OVERRIDE_ADDR = 11;
#endif

    class Main;

    static jobject mpThis;

    class MethodOverride {
    public:
        MethodOverride(art::Thread* self, jobject jThis, jobject jMethOld, jobject jMethNew, jobjectArray jmatches);
        ~MethodOverride();

        bool equals(art::Thread* self, jobject otherThis, jobject otherMeth);

        bool canOverride(art::mirror::ArtMethod* caller);

        bool verify(art::Thread* self);

        art::mirror::Object* getThis(art::Thread* self);
        art::mirror::ArtMethod* getMethod(art::Thread* self);
        art::mirror::ArtMethod* getMethodNew(art::Thread* self);

        const std::vector<uint8_t> &getSavedCode() {
            return mSavedCode;
        }
        const uint8_t* getOatEntryPoint() {
            return reinterpret_cast<const uint8_t *>(mOatEntryPoint);
        }

        void enterFrame(art::Thread* self);
        void exitFrame(art::Thread* self);

        bool isInOverride() const {
            return mThread != nullptr;
        } 

        bool isOverridden(art::Thread* self);

        bool isOverrideCall() const {
            return mIsOverrideCall;
        }

        void setOverride(art::Thread* self, bool override = true);

        art::Thread* getThread() {
            return mThread;
        }

    protected:
        void updateEntrypoints(art::Thread* self, art::mirror::ArtMethod* method = nullptr);
        void setOverrideNative(art::Thread* self, const bool enableOverride);

    private:
        art::Mutex mMutex;

        jobject mThis;
        jmethodID mMeth;
        jmethodID mMethNew;  

        std::vector<std::string> mMatches;

        bool mIsOverrideCall;   // The override is an instance of IOverrideCall.

        std::vector<uint8_t> mSavedCode;

        art::Thread *mThread;

        const void* mOatEntryPoint;
        uint32_t mOatEntryPointSize;

        const void* mOatEntryPointPage;
        uint32_t mOatEntryPointPageSize;
        const void* mQuickEntryPoint;
        const art::mirror::EntryPointFromInterpreter* mInterpreterEntryPoint;
    };

    class Main {
    public:
        static Main* instance() {
            if(Main::sInstance == nullptr)
                Main::sInstance = new Main();
            
            return Main::sInstance;
        }
        static void destroy() {
            if(Main::sInstance != nullptr)
                delete Main::sInstance;

            Main::sInstance = nullptr;
        }

        bool checkOverrideExists(art::Thread* self, MethodOverride* override);

        MethodOverride *getOverride(art::Thread *self, art::mirror::ArtMethod* meth);
        MethodOverride *getOverrideCurrent(art::Thread *self);

        void enterFrame(art::Thread* self, MethodOverride* override);
        void exitFrame(art::Thread* self, MethodOverride* override);

        /* Called from Java */
        void addOverride(jobject thiz, jobject jmethOriginal, jobject jmethNew, jobjectArray jmatches);

        void removeOverride(MethodOverride* override);
        void removeOverride(jobject thiz, jobject jmethOrig);

        jobject callOriginal(jobject jthis, jobjectArray jargs);
        jobject callSuper(jobject jthis, jobjectArray jargs);
        jobject callMethod(jobject jthis, jstring jname, jobjectArray jargs);

        void setObjectClass(jobject jthis, jclass klass);

    protected:
        jobject callMethod(jobject jthis, art::mirror::ArtMethod* method, jobjectArray jargs);

        bool parseMethodArgs(JNIEnv* env, jobject jthis, jobjectArray jargs, std::string &sig, std::vector<uint32_t> &args);

        void removeOverrideLocked(MethodOverride* override);

    private:
        static Main* sInstance;

        Main();
        ~Main();

        art::Mutex mOverridesMutex;
        std::vector<MethodOverride*> mOverrides;
        std::vector<MethodOverride*> mOverrideCurrent;

    };
}

extern "C" void artInterpreterToCompiledCodeBridge(art::Thread* self, art::MethodHelper& mh,
                                                   const art::DexFile::CodeItem* code_item,
                                                   art::ShadowFrame* shadow_frame, art::JValue* result);
extern "C" uint64_t artQuickToInterpreterBridge(art::mirror::ArtMethod* method, art::Thread* self,
                                                art::StackReference<art::mirror::ArtMethod>* sp);
extern "C" void artInterpreterToInterpreterBridge(art::Thread* self, art::MethodHelper& mh,
                                                  const art::DexFile::CodeItem* code_item,
                                                  art::ShadowFrame* shadow_frame, art::JValue* result);

extern "C" void art_quick_to_interpreter_bridge_override(art::mirror::ArtMethod*);


extern "C" uint64_t artQuickOverride(art::mirror::ArtMethod* method, art::Thread* self,
                                                art::StackReference<art::mirror::ArtMethod>* sp);
extern "C" void artInterpreterOverride(art::Thread* self, art::MethodHelper& mh,
                                                  const art::DexFile::CodeItem* code_item,
                                                  art::ShadowFrame* shadow_frame, art::JValue* result);

#endif //CUSTOM_ELPOLLO_ELPOLLO_H_ 
