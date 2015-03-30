package se.aqba.framework.elpollo;

import android.app.ActivityThread;
import android.app.LoadedApk;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.res.CompatibilityInfo;
import android.content.res.Resources;
import android.preference.Preference;
import android.view.WindowManager;
import android.view.Window;
import android.util.Log;

import dalvik.system.BaseDexClassLoader;
import dalvik.system.PathClassLoader;

import java.io.StringWriter;
import java.io.PrintWriter;
import java.io.File;
import java.lang.String;
import java.util.HashMap;
import java.util.ArrayList;

import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import android.content.res.PResources;
import se.aqba.framework.elpollo.ElPollo;
import se.aqba.framework.elpollo.ElPolloModule;
import se.aqba.framework.elpollo.helpers.ClassHelper;
import se.aqba.framework.elpollo.helpers.MethodHelper;
import se.aqba.framework.elpollo.helpers.ModuleHelper;
import se.aqba.framework.elpollo.InternalLoaders;
import se.aqba.framework.elpollo.exception.ModuleException;
import se.aqba.framework.elpollo.exception.OverrideException;

final class InternalPackageLoader extends ElPolloModule {
    private static final String TAG = "ElPollo.PackageLoader";

    private InternalLoaders mParent;

    private ArrayList<ElPollo.MemberOverride> mOverrides = new ArrayList<ElPollo.MemberOverride>();

    protected InternalPackageLoader(final InternalLoaders parent) {
        mParent = parent; 
    }

    @Override
    public void main() {
        Log.d(TAG, "Creating package loader");

        ElPollo.Packages.addListener(new ElPollo.Packages.Listener() {
            @Override
            public void onLoad(String pkg, ClassLoader classLoader) {
                Log.d(TAG, "onLoad " + pkg);

                try {
                    ElPollo.MemberOverride override = new ElPollo.MemberOverride.Builder()
                        .setSource(Thread.class, "setContextClassLoader")
                        .setTarget(mZygoteInitOverride)
                        .setTypes(ClassLoader.class)
                        .create();

                    ElPollo.Overrides.add(override, "com.android.internal.os.ZygoteInit.handleSystemServerProcess");
                    mOverrides.add(override);
                } catch(OverrideException e) {
                    Log.e(TAG, "Thread", e);
                }

                try {
                    ElPollo.MemberOverride override = new ElPollo.MemberOverride.Builder(ActivityThread.class.getClassLoader())
                        .setSource(ActivityThread.class, "handleBindApplication") 
                        .setTarget(mHandleBindApplicationOverride)
                        .setTypes("android.app.ActivityThread$AppBindData")
                        .create();

                    ElPollo.Overrides.add(override, "android.app.ActivityThread");
                    mOverrides.add(override);
                } catch(OverrideException e) {
                    Log.e(TAG, "ActivityThread", e);
                }

                // A module's shared preferences are loaded differently from regular
                // apps, therefore we need to check if the module is trying to get the
                // preferences from the context, and override it to the module's.
                try {
                    ElPollo.MemberOverride override = new ElPollo.MemberOverride.Builder(classLoader)
                        .setSource("android.app.ContextImpl", "getSharedPreferences") 
                        .setTarget(mSharedPreferencesOverride)
                        .setTypes(String.class, Integer.TYPE)
                        .create();

                    ElPollo.Overrides.add(override);
                    mOverrides.add(override);
                } catch(OverrideException e) {
                    Log.e(TAG, "ActivityThread", e);
                }
            }
        }, ElPollo.Packages.SYSTEM);
    }

    @Override
    public void destroy() {
        super.destroy(mOverrides);
    }

    /* Overrides */
    private ElPollo.OverrideCustom<Thread, Void> mZygoteInitOverride = new ElPollo.OverrideCustom<Thread, Void>() {
        public void call(Thread obj, ClassLoader classLoader) {
            Log.d(TAG, "zygoteInitOverride " + classLoader);

            mParent.setClassLoader(classLoader);

            try {
                ElPollo.MemberOverride override = new ElPollo.MemberOverride.Builder(classLoader)
                    .setSource("com.android.server.SystemServer", "createSystemContext")
                    .setTarget(mSystemServerOverride)
                    .create();

                ElPollo.Overrides.add(override, "com.android.server.SystemServer.run");
                mOverrides.add(override);
            } catch(OverrideException e) {
                Log.e(TAG, "SystemServer", e);
            }

            super.callOriginal(obj, classLoader);
        }
    };
    
    private ElPollo.OverrideCustom<Object, Void> mSystemServerOverride = new ElPollo.OverrideCustom<Object, Void>() {
        public void call(Object obj) {
            Log.d(TAG, "systemServerOverride");

            mParent.triggerLoadPackage(ElPollo.Packages.SERVER);

            super.callOriginal(obj);
        }
    };

    private ElPollo.OverrideCustom<ActivityThread, Void> mHandleBindApplicationOverride = new ElPollo.OverrideCustom<ActivityThread, Void>() {
        public void call(ActivityThread obj, Object appData) {
            try {
                Field appInfoField = Class.forName("android.app.ActivityThread$AppBindData").getDeclaredField("appInfo");
                appInfoField.setAccessible(true);
                Field compatInfoField = Class.forName("android.app.ActivityThread$AppBindData").getDeclaredField("compatInfo");
                compatInfoField.setAccessible(true);

                ApplicationInfo appInfo = (ApplicationInfo)appInfoField.get(appData);
                CompatibilityInfo compatInfo = (CompatibilityInfo)compatInfoField.get(appData);

                LoadedApk loadedApk = obj.getPackageInfoNoCheck(appInfo, compatInfo);
                mParent.triggerLoadPackage(appInfo.packageName, loadedApk.getResDir(), loadedApk.getClassLoader());

                //Log.d(TAG, "handleBindApplication " + appInfo.packageName);
            } catch(Throwable e) {
                Log.e(TAG, "handleBindApplication", e);
            }

            super.callOriginal(obj, appData);
        }
    };

    private ElPollo.OverrideCustom<Context, SharedPreferences> mSharedPreferencesOverride = new ElPollo.OverrideCustom<Context, SharedPreferences>() {
        public SharedPreferences call(Context obj, String name, int mode) {
            if(name != null && name.equals(ModuleHelper.PREFERENCES_NAME)) {
                PResources res = (PResources) obj.getResources();
                if(InternalModules.isLoaded(res.getPackage())) {
                    try {
                        return ModuleHelper.getPreferences(res.getPackage());
                    } catch(ModuleException e) {
                        Log.e(TAG, "getPreferences", e);
                    }
                }
            }

            return super.callOriginal(obj, name, mode);
        }
    };
}
