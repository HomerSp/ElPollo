#include "elog.h"
#include "elpollo.h"

using namespace elpollo;
using namespace art;

elpollo::Main* elpollo::Main::sInstance = nullptr;

static inline const void* GetQuickToInterpreterBridgeOverride() {
    return reinterpret_cast<void*>(art_quick_to_interpreter_bridge_override);
}

static bool isBigEndian() {
    union {
        uint32_t i;
        char c[4];
    } b = {0x01020304};

    return b.c[0] == 1; 
}

static const void *findMap(const void* code, uint32_t& size) {
    uintptr_t codeAddr = reinterpret_cast<uintptr_t>(code);

    FILE *file = fopen("/proc/self/maps", "r");
    if(file != NULL) {
        char buf[64];
        while(!feof(file)) {
            if(fgets(buf, 64, file) == NULL)
                break;

            buf[strlen(buf)-1] = '\0';

            uint32_t start, end;
            sscanf(buf, "%08x-%08x", &start, &end);

            if(codeAddr >= start && codeAddr < end) {
                size = end - start;
                codeAddr = start;
                break;
            }
        }

        fclose(file);
    }

    return (const void *)codeAddr;
}

static bool isInPackage(const std::string &className, const std::string &package) {
    bool matchWholePackage = true;

    std::string findPackage;
    if(package.find_last_of('*') == package.length() - 1) {
        findPackage = package.substr(0, package.length() - 1);
        matchWholePackage = false;
    } else {
        findPackage = package;
    }

    std::string str = className;
    if(str.find(findPackage) == 0) {
        str = str.substr(findPackage.length());
        if(str.find('.') == 0) {
            str = str.substr(1);
        }

        if(!matchWholePackage && str.length() > 0) {
            return true;
        }

        return (str.find('.') == std::string::npos);
    }

    return false;
}

static bool isMethodMatch(mirror::ArtMethod* meth, std::string className, std::string matchName, bool sig) {
    std::string methodName = className + std::string(".") + meth->GetName();
    if(sig) {
        methodName += meth->GetSignature().ToString();
    }

    return (matchName.compare(methodName) == 0);
}

MethodOverride::MethodOverride(art::Thread* self, jobject jThis, jobject jMethOld, jobject jMethNew, jobjectArray jmatches)
 :  mMutex("MethodOverrideMutex"),
    mIsOverrideCall(false),
    mThread(nullptr) {

    JNIEnv* env = self->GetJniEnv();
    mThis = env->NewGlobalRef(jThis);
    mMeth = env->FromReflectedMethod(jMethOld);
    mMethNew = env->FromReflectedMethod(jMethNew);

    mirror::ArtMethod* newMethod = getMethodNew(self);
    if(newMethod != nullptr) {
        mirror::Class* superClass = newMethod->GetDeclaringClass()->GetSuperClass();
        if(superClass != nullptr) {
            std::string tmp;
            std::string className = superClass->GetDescriptor(&tmp);

            // The OverrideCall listener requires that we transform the arguments into an Object array.
            mIsOverrideCall = (className == "Lse/aqba/framework/elpollo/ElPollo$OverrideCall;");
        }
    }

    mirror::ArtMethod* meth = getMethod(self);

    mOatEntryPoint = meth->GetQuickOatCodePointer();
    mOatEntryPointPage = findMap(mOatEntryPoint, mOatEntryPointPageSize);
    mOatEntryPointSize = meth->GetFrameSizeInBytes();
    mQuickEntryPoint = meth->GetEntryPointFromQuickCompiledCode();
    mInterpreterEntryPoint = meth->GetEntryPointFromInterpreter();

    uint32_t count = env->GetArrayLength(jmatches);
    for (uint32_t i = 0; i < count; i++) {
        jstring string = (jstring) env->GetObjectArrayElement(jmatches, i);
        const char *rawString = env->GetStringUTFChars(string, 0);
        
        mMatches.push_back(rawString);

        env->ReleaseStringUTFChars(string, rawString);
    }
}
MethodOverride::~MethodOverride() {
    JNIEnv* env = Thread::Current()->GetJniEnv();
    env->DeleteGlobalRef(mThis);
}

bool MethodOverride::equals(art::Thread* self, jobject otherThis, jobject otherMeth) {
    if(otherMeth == nullptr) {
        ELOGW(Lib, "Other method cannot be null");
        return false;
    }

    ScopedObjectAccess soa(self);

    mirror::Object* tThis = getThis(self);
    mirror::ArtMethod* tMeth = getMethod(self);

    mirror::Object* oThis = soa.Decode<mirror::Object*>(mThis);
    mirror::ArtMethod* oMeth = mirror::ArtMethod::FromReflectedMethod(soa, otherMeth);

    ELOGV(Lib, "equals %s %p %p vs %s %p %p", tMeth->GetName(), tThis, tMeth, oMeth->GetName(), oThis, oMeth);

    return (oThis == tThis && oMeth == tMeth);
}

bool MethodOverride::canOverride(art::mirror::ArtMethod* caller) {
    // A size of zero matches any caller.
    if(mMatches.size() == 0) {
        return true;
    }

    // If caller is null we have nothing to check against.
    if(caller == nullptr) {
        return false;
    }

    std::string temp;
    std::string callerClass = PrettyDescriptor(caller->GetDeclaringClass()->GetDescriptor(&temp));

    // Check for class matches.
    for(uint32_t i = 0; i < mMatches.size(); i++) {
        if(mMatches[i] == callerClass) {
            return true;
        }
    }
    // Check matches for method including signature.
    for(uint32_t i = 0; i < mMatches.size(); i++) {
        if(isMethodMatch(caller, callerClass, mMatches[i], true)) {
            return true;
        }
    }
    // Check matches for method without signature
    for(uint32_t i = 0; i < mMatches.size(); i++) {
        if(isMethodMatch(caller, callerClass, mMatches[i], false)) {
            return true;
        }
    }
    // Check for package matches.
    for(uint32_t i = 0; i < mMatches.size(); i++) {
        if(isInPackage(callerClass, mMatches[i])) {
            return true;
        }
    }

    return false;
}

bool MethodOverride::verify(Thread* self) {
    mirror::ArtMethod* method = getMethod(self);
    mirror::ArtMethod* methodNew = getMethodNew(self);

    std::string methodReturnType = method->GetReturnTypeDescriptor();
    std::string methodNewReturnType = methodNew->GetReturnTypeDescriptor();

    if(methodReturnType == methodNewReturnType || (methodReturnType.length() > 0 && methodReturnType[0] == 'V')) {
        return true;
    }

    StackHandleScope<2> hs(self);

    mirror::Class* methodReturnClass = Runtime::Current()->GetClassLinker()->FindClass(
        self, methodReturnType.c_str(), hs.NewHandle(method->GetClassLoader())
    );
    mirror::Class* methodNewReturnClass = Runtime::Current()->GetClassLinker()->FindClass(
        self, methodNewReturnType.c_str(), hs.NewHandle(methodNew->GetClassLoader())
    );

    if(methodReturnClass == nullptr || methodNewReturnClass == nullptr) {
        return false;
    }

    ELOGI(Lib, "IsAssignableFrom %s vs %s", PrettyDescriptor(methodNewReturnClass).c_str(), PrettyDescriptor(methodReturnClass).c_str());
    if(methodReturnClass->IsAssignableFrom(methodNewReturnClass)) {
        return true;
    }

    if(methodReturnClass->IsPrimitive() && !methodNewReturnClass->IsPrimitive() && methodNewReturnClass->NumInstanceFields() > 0) {
        mirror::ArtField* primitiveField = methodNewReturnClass->GetIFields()->Get(0);

        std::string primitiveType = primitiveField->GetTypeDescriptor();
        ELOGI(Lib, "Check primitive %s", primitiveType.c_str());

        if(primitiveType.length() == 1 && primitiveType[0] != 'L') {
            mirror::Class* primitiveClass = Runtime::Current()->GetClassLinker()->FindPrimitiveClass(primitiveType[0]);
            if(methodReturnClass == primitiveClass) {
                return true;
            }
        }
    }

    self->ClearException();

    return false;
}

mirror::Object* MethodOverride::getThis(Thread* self) {
    ScopedObjectAccess soa(self);
    return soa.Decode<mirror::Object*>(mThis);
}
mirror::ArtMethod* MethodOverride::getMethod(Thread* self) {
    ScopedObjectAccess soa(self);
    return soa.DecodeMethod(mMeth);
}
mirror::ArtMethod* MethodOverride::getMethodNew(Thread* self) {
    ScopedObjectAccess soa(self);
    return soa.DecodeMethod(mMethNew);
}

void MethodOverride::enterFrame(Thread* self) {
    mMutex.Lock(self);

    mThread = self;
    getMethod(self)->SetEntryPointFromInterpreter(mInterpreterEntryPoint);
}

void MethodOverride::exitFrame(Thread* self) {
    getMethod(self)->SetEntryPointFromInterpreter(artInterpreterOverride);
    mThread = nullptr;

    mMutex.Unlock(self);
}

bool MethodOverride::isOverridden(art::Thread* self) {
    return getMethod(self)->GetEntryPointFromInterpreter() == artInterpreterOverride;
}

void MethodOverride::setOverride(Thread* self, bool override) {
    {
        MutexLock lock(self, mMutex);
        mirror::ArtMethod* meth = getMethod(self);

        Runtime::Current()->GetThreadList()->SuspendAll();

        if(override) {
            ELOGV(Lib, "Enabling override %s", PrettyMethod(meth).c_str());

            setOverrideNative(self, true);

           // meth->SetEntryPointFromQuickCompiledCode(GetQuickToInterpreterBridgeOverride());
            meth->SetEntryPointFromInterpreter(artInterpreterOverride);
        } else {
            ELOGV(Lib, "Disabling override %s", PrettyMethod(meth).c_str());
            setOverrideNative(self, false);

            //meth->SetEntryPointFromQuickCompiledCode(mQuickEntryPoint);
            meth->SetEntryPointFromInterpreter(mInterpreterEntryPoint);
        }

        updateEntrypoints(self, meth);

        Runtime::Current()->GetThreadList()->ResumeAll();
    }
}

void MethodOverride::updateEntrypoints(Thread* self, mirror::ArtMethod* method) {
    if(method == nullptr) {
        method = getMethod(self);
    }

    Runtime::Current()->GetInstrumentation()->UpdateMethodsCode(method,
                                               method->GetEntryPointFromQuickCompiledCode(),
                                               nullptr,
                                               false);

    // Update the virtual table, where applicable.
    if(!method->IsDirect()) {
        mirror::ArtMethod* vtableMethod = method->GetDeclaringClass()->GetVTableEntry(method->GetVtableIndex());
        if(vtableMethod != nullptr) {
            ELOGV(Lib, "updateEntrypoints %d, %s vs %s", method->GetVtableIndex(), PrettyMethod(method).c_str(), PrettyMethod(vtableMethod).c_str());

            Runtime::Current()->GetInstrumentation()->UpdateMethodsCode(vtableMethod,
                                                       method->GetEntryPointFromQuickCompiledCode(),
                                                       nullptr,
                                                       false);
        }
    }
}

//#if ELOG_LEVEL <= ELOG_LEVEL_VERBOSE
void logCode(const char *tag, uint8_t *code, uint32_t size) {
    char buf[256] = "";
    for(uint32_t i = 0; i < size; i++) {
        sprintf(buf, "%s%02X ", buf, code[i]);
    }
    buf[strlen(buf) - 1] = '\0';
    ELOGD(Lib, "%s: %s", tag, buf);
}
/*#else
void logCode(const char *tag, uint8_t *code, uint32_t size) {
    // Empty when verbose logging is disabled
}
#endif*/

void MethodOverride::setOverrideNative(Thread* self, const bool enableOverride) {
    if(!kForceNativeOverride) {
        return;
    }

    if(mOatEntryPoint == nullptr || mOatEntryPointPage == nullptr || mOatEntryPointPageSize == 0) {
        ELOGE(Lib, "Invalid quick code entry point!");
    }

    // A size of 0 seems to indicate that the method doesn't have its own entry point, 
    // but that the quick code does exist.
    if(mOatEntryPointSize > 0 && mOatEntryPointSize < sizeof(QUICK_OVERRIDE)) {
        ELOGE(Lib, "Quick size %u is smaller than override code size %u!", mOatEntryPointSize, (unsigned int)sizeof(QUICK_OVERRIDE));
        return;
    }

    // Save the original code first.
    if(enableOverride && mSavedCode.size() == 0) {
        mSavedCode.resize(sizeof(QUICK_OVERRIDE));

        mprotect(mOatEntryPointPage, mOatEntryPointPageSize, PROT_EXEC | PROT_READ | PROT_WRITE);

        posix_memalign((void **)&mSavedCode[0], PAGESIZE, mSavedCode.size());
        mprotect(&mSavedCode[0], mSavedCode.size(), PROT_EXEC | PROT_READ | PROT_WRITE);
        memcpy(&mSavedCode[0], mOatEntryPoint, mSavedCode.size());
        mprotect(&mSavedCode[0], mSavedCode.size(), PROT_EXEC | PROT_READ);
    }

    uint8_t size;
    const uint8_t *nativeCode;
    if(enableOverride) {
        nativeCode = QUICK_OVERRIDE;
        size = sizeof(QUICK_OVERRIDE);
    } else {
        nativeCode = &mSavedCode[0];
        size = mSavedCode.size();
    }

    ELOGV(Lib, "setOverrideNative %p, size: %d", mOatEntryPoint, size);

    uint8_t* code = const_cast<uint8_t*>(reinterpret_cast<const uint8_t*>(mOatEntryPoint));

    logCode("Before", code, size);

    const uint8_t* writeCode = reinterpret_cast<const uint8_t*>(nativeCode);
    for(uint32_t i = 0; i < size; i++) {
        code[i] = writeCode[i];
    }

    // Write the address to the override function.
    if(enableOverride) {
        // 32-bit architectures use a 4-byte address
#if defined(__arm__) || defined(__i386__)
        uint32_t overrideAddressSize = sizeof(uint32_t);
#elif defined(__aarch64__)
        uint32_t overrideAddressSize = sizeof(uint64_t);
#endif
        uintptr_t overrideAddress = reinterpret_cast<uintptr_t>(GetQuickToInterpreterBridgeOverride());

        uint32_t y = 0;
        if(isBigEndian()) {
            y = overrideAddressSize - 1;
        }
        for(uint32_t i = 0; i < overrideAddressSize; i++) {
            if(i >= sizeof(uintptr_t)) {
                code[QUICK_OVERRIDE_ADDR + i] = 0x00;
            } else {
                code[QUICK_OVERRIDE_ADDR + i] = ((overrideAddress >> (y * 8)) & 0xFF);
            }

            if(isBigEndian()) {
                y--;
            } else {
                y++;
            }
        }
    }

    logCode("After", code, size);
}


Main::Main() : mOverridesMutex("OverridesMutexPollo") {
}
Main::~Main() {
    for(uint32_t i = 0; i < mOverrides.size(); i++) {
        removeOverride(mOverrides.at(i));
    }

    mOverrides.clear();
}

bool Main::checkOverrideExists(Thread* self, MethodOverride* override) {
    return getOverride(self, override->getMethod(self)) != nullptr;
}

MethodOverride *Main::getOverride(Thread *self, art::mirror::ArtMethod* meth) {
    MutexLock lock(Thread::Current(), mOverridesMutex);
    for(uint32_t i = 0; i < mOverrides.size(); i++) {
        MethodOverride *override = mOverrides.at(i);
        if(override->getMethod(self) == meth)
            return override;
        // We may get here from another method's quick code.
        if(kForceQuickCode && meth->GetQuickOatCodePointer() != nullptr && override->getOatEntryPoint() == meth->GetQuickOatCodePointer())
            return override;
    }

    return NULL;
}

MethodOverride *Main::getOverrideCurrent(art::Thread *self) {
    MethodOverride* ret = nullptr;
    MutexLock lock(Thread::Current(), mOverridesMutex);
    /*for(uint32_t i = 0; i < mOverrides.size(); i++) {
        MethodOverride* override = mOverrides.at(i);
        if(override != nullptr && override->getThread() == self && override->isInOverride()) {
            if(ret == nullptr || (ret->getOverrideStart() < override->getOverrideStart())) {
                ret = override;
            }
        }
    } */

    for(std::vector<MethodOverride*>::reverse_iterator i = mOverrideCurrent.rbegin(); i != mOverrideCurrent.rend(); ++i) {
        MethodOverride* ri = *i;
        if(ri->getThread() == self) {
            ret = ri;
            break;
        }
    }

    if(ret == nullptr) {
        ELOGE(Lib, "Could not find current override in overrides %u", (unsigned int)mOverrides.size());

        for(uint32_t i = 0; i < mOverrides.size(); i++) {
            MethodOverride* override = mOverrides.at(i);
            if(override != nullptr) {
                ELOGE(Lib, "Override %s", PrettyMethod(override->getMethod(self)).c_str());
                ELOGE(Lib, "Override in override %d", override->isInOverride());
                ELOGE(Lib, "Override thread %p vs self %p", override->getThread(), self);
            }
        }
    }

    return ret;
}

void Main::enterFrame(art::Thread* self, MethodOverride* override) {
    override->enterFrame(self);

    MutexLock lock(self, mOverridesMutex);
    mOverrideCurrent.push_back(override);
}

void Main::exitFrame(art::Thread* self, MethodOverride* override) {
    MutexLock lock(self, mOverridesMutex);
    for(std::vector<MethodOverride*>::reverse_iterator i = mOverrideCurrent.rbegin(); i != mOverrideCurrent.rend(); ++i) {
        if(*i == override && (*i)->getThread() == self) {
            mOverrideCurrent.erase(std::next(i).base());
            break;
        }
    }

    override->exitFrame(self);
}

void Main::removeOverrideLocked(MethodOverride* override) {
    // Can't remove a non-existant override...
    if(override == nullptr) {
        return;
    }

    Thread* self = Thread::Current();
    ELOGD(Lib, "Removing override, %s -> %s", PrettyMethod(override->getMethodNew(self)).c_str(), PrettyMethod(override->getMethod(self)).c_str());

    override->setOverride(Thread::Current(), false);
    mOverrides.erase(std::find(mOverrides.begin(), mOverrides.end(), override));
    delete override;
}

void Main::removeOverride(MethodOverride* override) {
    MutexLock lock(Thread::Current(), mOverridesMutex);
    removeOverrideLocked(override);
}

/* Called from Java */
void Main::addOverride(jobject thiz, jobject jMethOriginal, jobject jMethNew, jobjectArray jmatches) {
    Thread *self = Thread::Current();

    MethodOverride *override = new MethodOverride(self, thiz, jMethOriginal, jMethNew, jmatches);
    
    mirror::ArtMethod* method = override->getMethod(self);
    mirror::ArtMethod* methodNew = override->getMethodNew(self);
    if(!override->verify(self)) {
        ELOGE(Lib, "Cannot add override for %s, wrong return type?", PrettyMethod(method).c_str());
        return;
    }

    if(checkOverrideExists(self, override)) {
        ELOGW(Lib, "Override for %s already exists", PrettyMethod(method).c_str());
        return;
    }

    uint32_t index;
    {
        MutexLock lock(Thread::Current(), mOverridesMutex);
        index = mOverrides.size();
        mOverrides.push_back(override);
    }

    ELOGD(Lib, "addOverride %s -> %s @ %d", PrettyMethod(method).c_str(), PrettyMethod(methodNew).c_str(), index);

    override->setOverride(self);
}

void Main::removeOverride(jobject jthiz, jobject jorigMeth) {
    Thread* self = Thread::Current();

    MutexLock lock(Thread::Current(), mOverridesMutex);
    for(uint32_t i = 0; i < mOverrides.size(); i++) {
        MethodOverride* override = mOverrides.at(i);
        if(override != NULL && override->equals(self, jthiz, jorigMeth)) {
            removeOverrideLocked(override);
        }
    }
}

jobject Main::callMethod(jobject jthis, mirror::ArtMethod* method, jobjectArray jargs) {
    ELOGV(Lib, "Calling method %s", PrettyMethod(method).c_str());

    Thread* self = Thread::Current();
    JNIEnv* env = self->GetJniEnv();
    ScopedObjectAccess soa(env);

    std::vector<uint32_t> args;
    if(jargs != nullptr) {
        uint32_t shortyLength;
        const char *shorty = method->GetShorty(&shortyLength);
        uint32_t argsCount = env->GetArrayLength(jargs);

        if(argsCount != shortyLength - 1) {
            ELOGE(Lib, "%s expected %d arguments, got %d!", PrettyMethod(method).c_str(), shortyLength - 1, argsCount);
            return nullptr;
        }

        for (uint32_t i = 0; i < argsCount; i++) {
            jobject jobj = env->GetObjectArrayElement(jargs, i);

            mirror::Object* obj = soa.Decode<mirror::Object*>(jobj);
            if(obj != nullptr && shorty[i + 1] != 'L') {
                mirror::Class* objClass = obj->GetClass();
                mirror::ArtField* primitiveField = objClass->GetIFields()->Get(0);
                if(primitiveField != nullptr) {
                    if(shorty[i + 1] == 'J' || shorty[i + 1] == 'D') {
                        uint64_t value = primitiveField->Get64(obj);
                        args.push_back(value & 0xFFFFFFFF);
                        args.push_back((value >> 32) & 0xFFFFFFFF);
                    } else {
                        args.push_back(primitiveField->Get32(obj));
                    }
                }
            } else {
                args.push_back(StackReference<mirror::Object>::FromMirrorPtr(obj).AsVRegValue());
            }
        }
    }

    mirror::Object* pThis = soa.Decode<mirror::Object*>(jthis);

    ManagedStack fragment;
    self->PushManagedStackFragment(&fragment);

    JValue result;
    art::interpreter::EnterInterpreterFromInvoke(Thread::Current(), method, pThis, args.data(), &result);

    self->PopManagedStackFragment(fragment);

    return soa.AddLocalReference<jobject>(BoxPrimitive(Primitive::GetType(method->GetReturnTypeDescriptor()[0]), result));
}

bool Main::parseMethodArgs(JNIEnv* env, jobject jthis, jobjectArray jargs, std::string &sig, std::vector<uint32_t> &args) {
    ScopedObjectAccess soa(env);

    // Add this pointer first.
    mirror::Object* pThis = soa.Decode<mirror::Object*>(jthis);
    if(pThis != nullptr) {
        args.push_back(StackReference<mirror::Object>::FromMirrorPtr(pThis).AsVRegValue());
    }

    std::string tmp;
    if(jargs != nullptr) {
        sig = "(";

        uint32_t count = env->GetArrayLength(jargs);
        for (uint32_t i = 0; i < count; i++) {
            jobject jsigclass = env->GetObjectArrayElement(jargs, i++);
            mirror::Object* sigObjectClass = soa.Decode<mirror::Object*>(jsigclass);
            if(sigObjectClass == nullptr) {
                continue;
            }

            if(!sigObjectClass->IsClass()) {
                ELOGE(Lib, "Argument %d is not a class type!", i);
                return false;
            }

            mirror::Class* sigClass = sigObjectClass->AsClass();

            // The last item may be a reference to the return type.
            if(i >= count) {
                sig += std::string(")") + sigClass->GetDescriptor(&tmp);

                return true;
            }

            jobject jobj = env->GetObjectArrayElement(jargs, i);
            mirror::Object* obj = soa.Decode<mirror::Object*>(jobj);

            mirror::Class* declaringClass = obj->GetClass();
            if(declaringClass == nullptr || declaringClass->GetIFields() == nullptr) {
                continue;
            }

            sig += sigClass->GetDescriptor(&tmp);

            if(obj != nullptr && sigClass->IsPrimitive()) {
                mirror::ArtField* primitiveField = declaringClass->GetIFields()->Get(0);
                if(primitiveField != nullptr) {
                    if(sigClass->IsPrimitiveLong() || sigClass->IsPrimitiveDouble()) {
                        uint64_t value = primitiveField->Get64(obj);
                        args.push_back(value & 0xFFFFFFFF);
                        args.push_back((value >> 32) & 0xFFFFFFFF);
                    } else {
                        args.push_back(primitiveField->Get32(obj));
                    }
                }
            } else {
                args.push_back(StackReference<mirror::Object>::FromMirrorPtr(obj).AsVRegValue());
            }
        }

        sig += ")";
    }

    return true;
}

jobject Main::callSuper(jobject jthis, jobjectArray jargs) {
    Thread* self = Thread::Current();
    JNIEnv* env = self->GetJniEnv();
    ScopedObjectAccess soa(env);

    MethodOverride* methOverride = getOverrideCurrent(self);
    if (methOverride == nullptr) {
        ELOGE(Lib, "Could not find current method...");
        return nullptr;
    }

    mirror::ArtMethod* method = methOverride->getMethod(self);
    if (method == nullptr) {
        ELOGE(Lib, "Could not load current method...");
        return nullptr;
    }

    std::string sig = "";
    std::vector<uint32_t> args;
    if(!parseMethodArgs(env, jthis, jargs, sig, args)) {
        ELOGE(Lib, "Super method %s not found", PrettyMethod(method).c_str());
        return nullptr;
    }

    std::string name = method->GetName();

    if (sig.size() <= 0) {
        ELOGE(Lib, "Super method %s not found", PrettyMethod(method).c_str());
        return nullptr;
    }

    sig += method->GetReturnTypeDescriptor();

    mirror::Class* superClass = method->GetDeclaringClass()->GetSuperClass();
    if (superClass == nullptr) {
        std::string tmp;
        ELOGE(Lib, "%s does not have a super class!", method->GetDeclaringClass()->GetDescriptor(&tmp));
        return nullptr;
    }

    mirror::ArtMethod* superMethod = superClass->FindDeclaredDirectMethod(name.c_str(), sig.c_str());
    if (superMethod == nullptr) {
        superMethod = superClass->FindDeclaredVirtualMethod(name.c_str(), sig.c_str());
    }
    if (superMethod == nullptr) {
        ELOGE(Lib, "Super method %s%s not found", name.c_str(), sig.c_str());
        return nullptr;
    }

    mirror::Object* pThis = soa.Decode<mirror::Object*>(jthis);

    ELOGV(Lib, "Calling super %s", PrettyMethod(superMethod).c_str());

    uint32_t* argData = args.data();
    uint32_t argsSize = args.size();
    if(superMethod->IsStatic()) {
        argData++;
        argsSize--;
    }

    JValue result;
    superMethod->Invoke(self, argData, argsSize * sizeof(uint32_t), &result, superMethod->GetShorty());

    return soa.AddLocalReference<jobject>(BoxPrimitive(Primitive::GetType(superMethod->GetReturnTypeDescriptor()[0]), result));
}

jobject Main::callOriginal(jobject jthis, jobjectArray jargs) {
    Thread* self = Thread::Current();
    JNIEnv* env = self->GetJniEnv();
    ScopedObjectAccess soa(env);

    MethodOverride* methOverride = getOverrideCurrent(self);
    if (methOverride == nullptr) {
        ELOGE(Lib, "Could not find original override...");
        return nullptr;
    }

    mirror::ArtMethod* method = methOverride->getMethod(self);
    if (method == nullptr) {
        ELOGE(Lib, "Could not load original method...");
        return nullptr;
    }

    return callMethod(jthis, method, jargs);
}

jobject Main::callMethod(jobject jthis, jstring jname, jobjectArray jargs) {
    Thread* self = Thread::Current();
    JNIEnv* env = self->GetJniEnv();
    ScopedObjectAccess soa(env);

    const char *tmpName = env->GetStringUTFChars(jname, 0);
    std::string name = tmpName;
    env->ReleaseStringUTFChars(jname, tmpName);

    std::string sig = "";
    std::vector<uint32_t> args;
    if(!parseMethodArgs(env, jthis, jargs, sig, args)) {
        ELOGE(Lib, "Call method %s not found", name.c_str());
        return nullptr;
    }

    if(sig.back() == ')') {
        sig += "V";
    }

    mirror::Object* pThis = soa.Decode<mirror::Object*>(jthis);
    if(pThis == nullptr) {
        ELOGE(Lib, "Could not decode object");
        return nullptr;
    }

    mirror::Class* declaringClass = pThis->GetClass();
    if(declaringClass == nullptr) {
        ELOGE(Lib, "Could not decode declaring class");
        return nullptr;
    }

    mirror::ArtMethod* callMethod = declaringClass->FindDeclaredDirectMethod(name.c_str(), sig.c_str());
    if (callMethod == nullptr) {
        callMethod = declaringClass->FindDeclaredVirtualMethod(name.c_str(), sig.c_str());
    }
    if (callMethod == nullptr) {
        ELOGE(Lib, "Call method %s%s not found", name.c_str(), sig.c_str());
        return nullptr;
    }

    ELOGV(Lib, "Calling method %s", PrettyMethod(callMethod).c_str());

    uint32_t* argData = args.data();
    uint32_t argsSize = args.size();
    if(callMethod->IsStatic()) {
        argData++;
        argsSize--;
    }

    JValue result;
    callMethod->Invoke(self, argData, argsSize * sizeof(uint32_t), &result, callMethod->GetShorty());

    if(self->IsExceptionPending()) {
        return nullptr;
    }

    mirror::Object* ret = BoxPrimitive(Primitive::GetType(callMethod->GetReturnTypeDescriptor()[0]), result);
    if(ret == nullptr || self->IsExceptionPending()) {
        return nullptr;
    }

    return soa.AddLocalReference<jobject>(ret);
}

void Main::setObjectClass(jobject jthis, jclass klass) {
    Thread* self = Thread::Current();
    JNIEnv* env = self->GetJniEnv();
    ScopedObjectAccess soa(env);

    mirror::Object* pThis = soa.Decode<mirror::Object*>(jthis);
    mirror::Class* pKlass = soa.Decode<mirror::Class*>(klass);

    pThis->SetClass(pKlass);
}


uint64_t artQuickOverride(art::mirror::ArtMethod* method, art::Thread* self,
        art::StackReference<art::mirror::ArtMethod>* sp)
    SHARED_LOCKS_REQUIRED(art::Locks::mutator_lock_) {

    // The method should never be null, but....
    if(method == nullptr) {
        FinishCalleeSaveFrameSetup(self, sp, Runtime::kRefsAndArgs);

        ELOGW(Lib, "artQuickOverride, method == nullptr");
        return 0;
    }

    ELOGV(Lib, "artQuickOverride %s", PrettyMethod(method).c_str());

    // Can't override abstract methods.
    if (method->IsAbstract()) {
        FinishCalleeSaveFrameSetup(self, sp, Runtime::kRefsAndArgs);

        ThrowAbstractMethodError(method);
        return 0;
    }

    uint8_t* previous_sp = reinterpret_cast<uint8_t*>(sp) + kQuickCalleeSaveFrame_RefAndArgs_FrameSize;
    mirror::ArtMethod* callingMethod = reinterpret_cast<StackReference<mirror::ArtMethod>*>(previous_sp)->AsMirrorPtr();

    MethodOverride *methOverride = Main::instance()->getOverride(self, method);

    // The override was not found. We can only end up here if the override
    // was removed after entering this method, but before getOverride.
    if(methOverride == nullptr) {
        ELOGV(Lib, "methOverride == nullptr for %s", PrettyMethod(method).c_str());
        return artQuickToInterpreterBridge(method, self, sp);
    }

    FinishCalleeSaveFrameSetup(self, sp, Runtime::kRefsAndArgs);

    // Are we overriding this method?
    bool shouldOverride = kEnableOverrides && !methOverride->isInOverride() && methOverride->canOverride(callingMethod);

    // Does the call originate from method we should be overriding? ART optimises methods
    // so that if it finds several methods that do the same thing it will
    // only use one of them, which means that method will point to a different
    // method than the one we expected.
    // This will also be false when we call the original method from an override.
    bool isOrigin = shouldOverride && method == methOverride->getMethod(self);
    if(!isOrigin) {
        shouldOverride = false;
    }

    if(callingMethod != nullptr) {
        std::string tmp;
        ELOGV(Lib, "From Method: %s, Class: %s, method: %s", PrettyMethod(callingMethod).c_str(), callingMethod->GetDeclaringClass()->GetDescriptor(&tmp), PrettyMethod(method).c_str());
    } else {
        ELOGV(Lib, "From UNKNOWN method");
    }

    art::mirror::ArtMethod* callMethod;
    if(shouldOverride) {
        callMethod = methOverride->getMethodNew(self);

        // Could not access the method to call, so we can safely revert the override
        // and call the original method.
        if(callMethod == nullptr) {
            Main::instance()->removeOverride(methOverride);
            callMethod = method;
            shouldOverride = false;
        } else if(callMethod->IsAbstract()) {
            ELOGW(Lib, "DEV: Override method may not be abstract %s.", PrettyMethod(callMethod).c_str());
            callMethod = method;
            shouldOverride = false;
        }
    } else {
        callMethod = method;
    }

    // We are entering an override method.
    if(shouldOverride) {
        Main::instance()->enterFrame(self, methOverride);
    }

    // Make sure that GC does not run while we are copying the arguments.
    const char* old_cause = self->StartAssertNoThreadSuspension("QuickToInterpreterBridgeOverride");

    // Get the arguments from the stack.
    uint8_t* stackStart = reinterpret_cast<uint8_t*>(sp);
    uint8_t* stackGprStart = reinterpret_cast<uint8_t*>(stackStart + kQuickCalleeSaveFrame_RefAndArgs_Gpr1Offset);
    uint8_t* stackFprStart = reinterpret_cast<uint8_t*>(stackStart + kQuickCalleeSaveFrame_RefAndArgs_Fpr1Offset);

    uint8_t* stackArgStart;
    if(kQuickSoftFloatAbi) {
        stackArgStart = reinterpret_cast<uint8_t*>(stackStart + kQuickCalleeSaveFrame_RefAndArgs_FrameSize + sizeof(StackReference<art::mirror::ArtMethod>) + (kNumQuickGprArgs * art::GetBytesPerGprSpillLocation(art::kRuntimeISA)));
    } else {
        stackArgStart = reinterpret_cast<uint8_t*>(stackStart + sizeof(StackReference<art::mirror::ArtMethod>));
    }

    uint32_t shortyLength;
    const char* shorty = method->GetShorty(&shortyLength);

    bool isOverrideCall = shouldOverride && methOverride->isOverrideCall();

    std::vector<uint32_t> args;
    std::vector<jobject> jargs; // These will be cleaned up when the override is done.

    // Reserve room for this, calling this, and arguments.
    uint32_t argsSize = 2;
    if(!isOverrideCall) {
        for(uint32_t i = 1; i < shortyLength; i++) {
            argsSize++;
            if(Primitive::FieldSize(Primitive::GetType(shorty[i])) > 4) {
                argsSize++;
            }
        }
    } else {
        argsSize++;
    }

    ELOGV(Lib, "Reserving %d args", argsSize);
    args.reserve(argsSize);

    uint8_t* regPointer;
    uint32_t gprIndex = 0;
    uint32_t fprIndex = 0;
    uint32_t argIndex = 0;

    // Set the this argument to be the override class, if we are overriding and the call
    // method is not static.
    if(shouldOverride && !callMethod->IsStatic()) {
        StackReference<mirror::Object> ref = StackReference<mirror::Object>::FromMirrorPtr(methOverride->getThis(self));
        args.push_back(ref.AsVRegValue());
    }

    // Set the this argument for the original class, or the class if it's static and we are overriding.
    if(!method->IsStatic()) {
        if(!kQuickSoftFloatAbi || kNumQuickGprArgs == 0) {
            regPointer = stackArgStart + (argIndex * kBytesStackArgLocation);
            argIndex++;
        } else {
            regPointer = stackGprStart + GprIndexToGprOffset(gprIndex);
            gprIndex++;            
        }

        args.push_back(*reinterpret_cast<uint32_t*>(regPointer));
    } else if(shouldOverride) {
        args.push_back(StackReference<mirror::Object>::FromMirrorPtr(method->GetDeclaringClass()).AsVRegValue());
    }

    // If we are not using the OverrideCall class, pass the arguments as they are.
    if(!isOverrideCall) {
        for(uint32_t i = 1; i < shortyLength; i++) {
            Primitive::Type type = Primitive::GetType(shorty[i]);

            if(Primitive::FieldSize(type) != 8) {
                if(!kQuickSoftFloatAbi || gprIndex == kNumQuickGprArgs) {
                    if(!kQuickSoftFloatAbi && (type == Primitive::Type::kPrimFloat) && fprIndex < kNumQuickFprArgs) {
                        regPointer = stackFprStart + FprIndexToFprOffset(fprIndex);
                        fprIndex++;
                    } else {
                        regPointer = stackArgStart + (argIndex * kBytesStackArgLocation);
                        argIndex++;
                    }
                } else {
                    regPointer = stackGprStart + GprIndexToGprOffset(gprIndex);
                    gprIndex++;
                }

                args.push_back(*reinterpret_cast<uint32_t*>(regPointer));
            } else {
                /* This following code is a bit of a mess */
                uint8_t *lowValuePointer;
                uint8_t *highValuePointer;

                if(!kQuickSoftFloatAbi || gprIndex == kNumQuickGprArgs) {
                    if(!kQuickSoftFloatAbi && (type == Primitive::Type::kPrimDouble) && fprIndex < kNumQuickFprArgs) {
                        if(art::GetBytesPerFprSpillLocation(art::kRuntimeISA) == 4) {
                            lowValuePointer = stackFprStart + FprIndexToFprOffset(fprIndex);
                            fprIndex++;
                            if(fprIndex == kNumQuickFprArgs) {
                                highValuePointer = stackArgStart + (argIndex * kBytesStackArgLocation);
                                argIndex++;
                            } else {
                                highValuePointer = stackFprStart + FprIndexToFprOffset(fprIndex);
                                fprIndex++;
                            }
                        } else {
                            lowValuePointer = stackFprStart + FprIndexToFprOffset(fprIndex);
                            highValuePointer = stackFprStart + FprIndexToFprOffset(fprIndex) + kBytesStackArgLocation;
                            fprIndex++;
                        }
                    } else {
                        lowValuePointer = stackArgStart + (argIndex * kBytesStackArgLocation);
                        argIndex++;
                        highValuePointer = stackArgStart + (argIndex * kBytesStackArgLocation);
                        argIndex++;
                    }
                } else {
                    if(art::GetBytesPerGprSpillLocation(art::kRuntimeISA) == 4) {
                        if(gprIndex == kNumQuickGprArgs) {
                            lowValuePointer = stackArgStart + (argIndex * kBytesStackArgLocation);
                            argIndex++;
                        } else {
                            lowValuePointer = stackGprStart + GprIndexToGprOffset(gprIndex);
                            gprIndex++;
                        }
                        
                        if(gprIndex == kNumQuickGprArgs) {
                            highValuePointer = stackArgStart + (argIndex * kBytesStackArgLocation);
                            argIndex++;
                        } else {
                            highValuePointer = stackGprStart + GprIndexToGprOffset(gprIndex);
                            gprIndex++;
                        }
                    } else {
                        lowValuePointer = stackGprStart + GprIndexToGprOffset(gprIndex);
                        highValuePointer = stackGprStart + GprIndexToGprOffset(gprIndex) + kBytesStackArgLocation;
                        gprIndex++;
                    }
                }

                uint32_t lowValue = *reinterpret_cast<uint32_t*>(lowValuePointer);
                uint32_t highValue = *reinterpret_cast<uint32_t*>(highValuePointer);

                args.push_back(lowValue);
                args.push_back(highValue);
            }
        }

    // Else put the arguments into an object array.
    } else {
        ScopedObjectAccess soa(self);

        jobjectArray argsArr = soa.Env()->NewObjectArray(shortyLength - 1, WellKnownClasses::java_lang_Object, nullptr);
        for(uint32_t i = 1; i < shortyLength; i++) {
            Primitive::Type type = Primitive::GetType(shorty[i]);

            jobject obj;

            if(Primitive::FieldSize(type) != 8) {
                if(!kQuickSoftFloatAbi || gprIndex == kNumQuickGprArgs) {
                    if(!kQuickSoftFloatAbi && (type == Primitive::Type::kPrimFloat) && fprIndex < kNumQuickFprArgs) {
                        regPointer = stackFprStart + FprIndexToFprOffset(fprIndex);
                        fprIndex++;
                    } else {
                        regPointer = stackArgStart + (argIndex * kBytesStackArgLocation);
                        argIndex++;
                    }
                } else {
                    regPointer = stackGprStart + GprIndexToGprOffset(gprIndex);
                    gprIndex++;
                }

                JValue value;
                value.SetI(*reinterpret_cast<uint32_t*>(regPointer));
                obj = soa.AddLocalReference<jobject>(BoxPrimitive(type, value));
            } else {
                /* This following code is a bit of a mess */
                uint8_t *lowValuePointer;
                uint8_t *highValuePointer;

                if(!kQuickSoftFloatAbi || gprIndex == kNumQuickGprArgs) {
                    if(!kQuickSoftFloatAbi && (type == Primitive::Type::kPrimDouble) && fprIndex < kNumQuickFprArgs) {
                        if(art::GetBytesPerFprSpillLocation(art::kRuntimeISA) == 4) {
                            lowValuePointer = stackFprStart + FprIndexToFprOffset(fprIndex);
                            fprIndex++;
                            if(fprIndex == kNumQuickFprArgs) {
                                highValuePointer = stackArgStart + (argIndex * kBytesStackArgLocation);
                                argIndex++;
                            } else {
                                highValuePointer = stackFprStart + FprIndexToFprOffset(fprIndex);
                                fprIndex++;
                            }
                        } else {
                            lowValuePointer = stackFprStart + FprIndexToFprOffset(fprIndex);
                            highValuePointer = stackFprStart + FprIndexToFprOffset(fprIndex) + kBytesStackArgLocation;
                            fprIndex++;
                        }
                    } else {
                        lowValuePointer = stackArgStart + (argIndex * kBytesStackArgLocation);
                        argIndex++;
                        highValuePointer = stackArgStart + (argIndex * kBytesStackArgLocation);
                        argIndex++;
                    }
                } else {
                    if(art::GetBytesPerGprSpillLocation(art::kRuntimeISA) == 4) {
                        if(gprIndex == kNumQuickGprArgs) {
                            lowValuePointer = stackArgStart + (argIndex * kBytesStackArgLocation);
                            argIndex++;
                        } else {
                            lowValuePointer = stackGprStart + GprIndexToGprOffset(gprIndex);
                            gprIndex++;
                        }

                        if(gprIndex == kNumQuickGprArgs) {
                            highValuePointer = stackArgStart + (argIndex * kBytesStackArgLocation);
                            argIndex++;
                        } else {
                            highValuePointer = stackGprStart + GprIndexToGprOffset(gprIndex);
                            gprIndex++;
                        }
                    } else {
                        lowValuePointer = stackGprStart + GprIndexToGprOffset(gprIndex);
                        highValuePointer = stackGprStart + GprIndexToGprOffset(gprIndex) + kBytesStackArgLocation;
                        gprIndex++;
                    }
                }

                uint64_t lowValue = *reinterpret_cast<uint32_t*>(lowValuePointer);
                uint64_t highValue = *reinterpret_cast<uint32_t*>(highValuePointer);

                JValue value;
                value.SetJ((lowValue & 0xffffffffULL) | (highValue << 32));
                obj = soa.AddLocalReference<jobject>(BoxPrimitive(type, value));
            }

            jargs.push_back(obj);

            soa.Env()->SetObjectArrayElement(argsArr, i - 1, obj);
        }

        jargs.push_back(argsArr);

        mirror::Object* objArgsArr = soa.Decode<mirror::Object*>(argsArr);
        args.push_back(StackReference<mirror::Object>::FromMirrorPtr(objArgsArr).AsVRegValue());
    }

    ELOGV(Lib, "Invoking %s, args %d", PrettyMethod(callMethod).c_str(), args.size());
    /*for(uint32_t i = 0; i < args.size(); i++) {
        ELOGV(Lib, " arg #%d = %08x", i, args[i]);
    }*/

    self->EndAssertNoThreadSuspension(old_cause);

    //uint64_t ret = artQuickToInterpreterBridge(method, self, sp);

    JValue result;

    // Invoke will call the quick code, and we only want that when we will end up in the override
    // since the original quick code has been overridden to jump to our override code,
    // which would result in an endless loop.
    if((!kForceNativeOverride || (kForceQuickCode && isOrigin)) && callMethod->GetQuickOatCodePointer() != nullptr) {
        uint32_t callShortyLength;
        const char *callShorty = callMethod->GetShorty(&shortyLength);

        callMethod->Invoke(self, args.data(), args.size() * sizeof(uint32_t), &result, callShorty);
    } else {
        ManagedStack fragment;
        self->PushManagedStackFragment(&fragment);

        if(callMethod->IsStatic()) {
            art::interpreter::EnterInterpreterFromInvoke(self, callMethod, nullptr, args.data(), &result);
        } else {
            mirror::Object* receiver = reinterpret_cast<StackReference<mirror::Object>*>(&args.at(0))->AsMirrorPtr();
            art::interpreter::EnterInterpreterFromInvoke(self, callMethod, receiver, args.data() + 1, &result);
        }

        self->PopManagedStackFragment(fragment);
    }

    if(shouldOverride) {
        Main::instance()->exitFrame(self, methOverride);
    }

    // Unbox the result, if applicable. This is required when using OverrideCall as it's using generics,
    // and as such the return value must be an object.
    JValue unboxedResult;
    unboxedResult.SetJ(result.GetJ());
    if(!self->IsExceptionPending() && shouldOverride && callMethod->GetReturnTypeDescriptor() != nullptr && method->GetReturnTypeDescriptor() != nullptr) {

        // We only want to unbox objects.
        std::string overrideReturnType = callMethod->GetReturnTypeDescriptor();
        if(overrideReturnType.length() > 1 && overrideReturnType[0] == 'L') {

            // The return type must be a primitive and not void.
            std::string returnType = method->GetReturnTypeDescriptor();
            if(returnType.length() == 1 && returnType[0] != 'V') {

                // Try to lookup the primitive class.
                mirror::Class* resultClass = Runtime::Current()->GetClassLinker()->FindPrimitiveClass(returnType[0]);
                if(resultClass != nullptr) {
                    mirror::Object* resultObj = result.GetL();
                    if(resultObj != nullptr && (resultClass->IsPrimitive() || resultObj->InstanceOf(resultClass))) {
                        ELOGV(Lib, "Unboxing return from %s to %s", PrettyDescriptor(resultObj->GetClass()).c_str(), PrettyDescriptor(resultClass).c_str());

                        // Attempt to unbox the result.
                        if(!UnboxPrimitiveForField(resultObj, resultClass, nullptr, &unboxedResult)) {
                            unboxedResult.SetJ(result.GetJ());
                        }
                    } else if(!resultObj->InstanceOf(resultClass)) {
                        ELOGE(Lib, "Unexpected override return type from %s - should be %s", PrettyMethod(method).c_str(), PrettyDescriptor(resultClass).c_str());
                    
                        unboxedResult.SetJ(0);
                    }
                }
            }
        }
    }

    // Not cleaning up the jni objects causes the local reference store to run out of space.
    if(jargs.size() > 0) {
        ScopedObjectAccess soa(self);
        for(uint32_t i = 0; i < jargs.size(); i++) {
            soa.Env()->DeleteLocalRef(jargs.at(i));
        }
    }

    //return ret;

    return unboxedResult.GetJ();
}

void artInterpreterOverride(art::Thread* self, art::MethodHelper& mh,
                                                  const art::DexFile::CodeItem* codeItem,
                                                  art::ShadowFrame* shadowFrame, art::JValue* result) {

    mirror::ArtMethod* method = mh.GetMethod();

    ELOGV(Lib, "elpollo.artQuick.artInterpreterOverride %s", PrettyMethod(method).c_str());

    artInterpreterToInterpreterBridge(self, mh, codeItem, shadowFrame, result);
}
