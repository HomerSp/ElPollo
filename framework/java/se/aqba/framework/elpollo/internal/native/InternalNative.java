package se.aqba.framework.elpollo;

import android.content.res.Resources;
import android.preference.Preference;
import android.view.WindowManager;
import android.view.Window;
import android.util.Log;

import dalvik.system.BaseDexClassLoader;

import java.io.File;
import java.lang.String;

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Calls to native functions.
 * Internal use only.
 *
 * @hide
 */
final class InternalNative {
    protected static native void init();
    protected static native void destroy();

    protected static native void addOverride(Object pThis, Member orig, Member override, String[] matches);
    protected static native void removeOverride(Object pThis, Member orig);

    protected static native Object callOriginal(Object pThis, Object[] args);
    protected static native Object callSuper(Object pThis, Object[] args);
    protected static native Object callMethod(Object pThis, String name, Object[] args);

    protected static native void setObjectClass(Object obj, Class<?> objClass);

    protected static native void runGC();
}