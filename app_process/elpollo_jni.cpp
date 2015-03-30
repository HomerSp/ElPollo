#include <jni.h>

#include <stdlib.h>
#include <stdio.h>
#include <unistd.h>
#include <sys/prctl.h>

#include "elog.h"
#include "elpollo.h"
#include "elpollo_jni.h"

struct NativeMethod {
    const char* name;
    const char* sig;
    const void* funcPtr;
};

void Java_se_aqba_framework_elpollo_InternalNative_init(JNIEnv* env, jclass) {
    ELOGI(Jni, "Native init");
    elpollo::Main::instance();
}

void Java_se_aqba_framework_elpollo_InternalNative_destroy(JNIEnv* env, jclass) {
    ELOGI(Jni, "Native destroy");
    elpollo::Main::destroy();
}

void Java_se_aqba_framework_elpollo_InternalNative_addOverride(JNIEnv* env, jclass, jobject thiz, jobject joriginal, jobject joverride, jobjectArray jmatches) {
    elpollo::Main* instance = elpollo::Main::instance();
    instance->addOverride(thiz, joriginal, joverride, jmatches);
}

void Java_se_aqba_framework_elpollo_InternalNative_removeOverride(JNIEnv* env, jclass, jobject thiz, jobject joriginal) {
    elpollo::Main* instance = elpollo::Main::instance();
    instance->removeOverride(thiz, joriginal);
}

jobject Java_se_aqba_framework_elpollo_InternalNative_callOriginal(JNIEnv* env, jclass, jobject jthis, jobjectArray args) {
    elpollo::Main* instance = elpollo::Main::instance();
    return instance->callOriginal(jthis, args);
}

jobject Java_se_aqba_framework_elpollo_InternalNative_callSuper(JNIEnv* env, jclass, jobject jthis, jobjectArray args) {
    elpollo::Main* instance = elpollo::Main::instance();
    return instance->callSuper(jthis, args);
}

jobject Java_se_aqba_framework_elpollo_InternalNative_callMethod(JNIEnv* env, jclass, jobject jthis, jstring name, jobjectArray args) {
    elpollo::Main* instance = elpollo::Main::instance();
    return instance->callMethod(jthis, name, args);
}

void Java_se_aqba_framework_elpollo_InternalNative_setObjectClass(JNIEnv* env, jclass, jobject jthis, jclass klass) {
    elpollo::Main* instance = elpollo::Main::instance();
    instance->setObjectClass(jthis, klass);
}


void elpolloJNI::ElPolloRuntime::onVmCreated(JNIEnv* env) {
    ELOGI(Jni, "onVmCreated");

    // Register the native functions.
    registerNatives(env);
}

void elpolloJNI::ElPolloRuntime::registerNatives(JNIEnv* env) {
    #define EL_NATIVE_METHOD(name, sig) { \
        #name, sig, (const void *)&Java_se_aqba_framework_elpollo_InternalNative_##name, \
    }
    static const NativeMethod nativeMethods[] = {
        EL_NATIVE_METHOD(init, "()V"),
        EL_NATIVE_METHOD(destroy, "()V"),
        EL_NATIVE_METHOD(addOverride, "(Ljava/lang/Object;Ljava/lang/reflect/Member;Ljava/lang/reflect/Member;[Ljava/lang/String;)V"),
        EL_NATIVE_METHOD(removeOverride, "(Ljava/lang/Object;Ljava/lang/reflect/Member;)V"),
        EL_NATIVE_METHOD(callOriginal, "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;"),
        EL_NATIVE_METHOD(callSuper, "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;"),
        EL_NATIVE_METHOD(callMethod, "(Ljava/lang/Object;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;"),
        EL_NATIVE_METHOD(setObjectClass, "(Ljava/lang/Object;Ljava/lang/Class;)V")
    };
    #undef EL_NATIVE_METHOD

    art::ScopedObjectAccess soa(art::ThreadForEnv(env));
    art::StackHandleScope<2> hs(soa.Self());

    art::Handle<art::mirror::Class> class_loader_class(
        hs.NewHandle(soa.Decode<art::mirror::Class*>(art::WellKnownClasses::java_lang_ClassLoader)));

    art::mirror::ArtMethod* getSystemClassLoader =
        class_loader_class->FindDirectMethod("getSystemClassLoader", "()Ljava/lang/ClassLoader;");

    art::JValue result = InvokeWithJValues(soa, nullptr, soa.EncodeMethod(getSystemClassLoader), nullptr);
   /* art::Handle<art::mirror::ClassLoader> class_loader(
        hs.NewHandle(art::down_cast<art::mirror::ClassLoader*>(result.GetL())));*/
    art::Handle<art::mirror::ClassLoader> class_loader(
        hs.NewHandle((art::mirror::ClassLoader *)result.GetL()));

    art::ClassLinker* classLinker = art::Runtime::Current()->GetClassLinker();
    art::mirror::Class* elpolloClass = classLinker->FindClass(soa.Self(), "Lse/aqba/framework/elpollo/InternalNative;", class_loader);
    if(elpolloClass == nullptr) {
        ELOGE(Jni, "Could not find elpollo native class");
        return;
    }
    for(uint32_t i = 0; i < sizeof(nativeMethods) / sizeof(nativeMethods[0]); i++) {
        art::mirror::ArtMethod* m = elpolloClass->FindDeclaredDirectMethod(nativeMethods[i].name, nativeMethods[i].sig);
        if (m == nullptr) {
            m = elpolloClass->FindDeclaredVirtualMethod(nativeMethods[i].name, nativeMethods[i].sig);
        }
        if (m == nullptr) {
            ELOGW(Jni, "Could not find native method %s%s", nativeMethods[i].name, nativeMethods[i].sig);
            continue;
        }

        ELOGI(Jni, "Registering native %s", art::PrettyMethod(m).c_str());
        m->RegisterNative(soa.Self(), nativeMethods[i].funcPtr, false);
    }
}