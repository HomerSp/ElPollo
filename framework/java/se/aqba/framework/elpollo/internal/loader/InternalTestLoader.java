package se.aqba.framework.elpollo;

import android.app.ActivityThread;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.Preference;
import android.view.View;
import android.view.WindowManager;
import android.view.Window;
import android.util.Log;

import dalvik.system.BaseDexClassLoader;
import dalvik.system.PathClassLoader;

import java.io.File;
import java.lang.String;
import java.util.HashMap;
import java.util.ArrayList;

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import android.content.res.PResources;

import se.aqba.framework.elpollo.ElPollo;
import se.aqba.framework.elpollo.ElPolloModule;
import se.aqba.framework.elpollo.InternalLoaders;
import se.aqba.framework.elpollo.exception.ModuleException;
import se.aqba.framework.elpollo.exception.OverrideException;
import se.aqba.framework.elpollo.helpers.MethodHelper;
import se.aqba.framework.elpollo.helpers.ModuleHelper;

final class InternalTestLoader extends ElPolloModule {
    private static final String TAG = "ElPollo.TestLoader";

    private InternalLoaders mParent;

    private ArrayList<ElPollo.MemberOverride> mOverrides = new ArrayList<ElPollo.MemberOverride>();

    protected InternalTestLoader(final InternalLoaders parent) {
        mParent = parent;
    }

    @Override
    public void main() {
        Log.d(TAG, "Creating test loader");

        ElPollo.Packages.addListener(new ElPollo.Packages.Listener() {
            @Override
            public void onLoad(String pkg, ClassLoader classLoader) {
                Log.d(TAG, "onLoad " + pkg);
                
                try {
                    ElPollo.MemberOverride override = new ElPollo.MemberOverride.Builder()
                        .setSource(InternalTestLoader.class, "testArgs")
                        .setTarget(InternalTestLoader.this, "testArgsOverride")
                        .setTypes(Integer.TYPE, Long.TYPE, Float.TYPE, Double.TYPE)
                        .create();

                    ElPollo.Overrides.add(override);

                    mOverrides.add(override);
                } catch(OverrideException e) {
                    Log.e(TAG, "InternalTestLoader", e);
                }

                try {
                    ElPollo.MemberOverride override = new ElPollo.MemberOverride.Builder()
                        .setSource(InternalTestLoader.class, "testStatic")
                        .setTarget(InternalTestLoader.class, "testStaticOverride")
                        .setTypes(String.class, Boolean.TYPE)
                        .create();

                    ElPollo.Overrides.add(override);

                    mOverrides.add(override);
                } catch(OverrideException e) {
                     Log.e(TAG, "InternalTestLoader", e);
                }

                try {
                    ElPollo.MemberOverride override = new ElPollo.MemberOverride.Builder()
                        .setSource(InternalTestLoader.class, "testEmpty")
                        .setTarget(InternalTestLoader.this, "testEmptyOverride")
                        .create();

                    ElPollo.Overrides.add(override);

                    mOverrides.add(override);
                } catch(OverrideException e) {
                     Log.e(TAG, "InternalTestLoader", e);
                }

                try {
                    ElPollo.MemberOverride override = new ElPollo.MemberOverride.Builder()
                        .setSource(InternalTestLoader.class, "testOverrideCall")
                        .setTarget(
                            new ElPollo.OverrideCall<InternalTestLoader, Void>() {
                                @Override
                                public Void call(InternalTestLoader obj, Object... args) {
                                    Log.d(TAG, "testOverrideCall override " + (String)args[0] + " " + (int)args[1] + " " + (double)args[2] + " " + (long)args[3]);

                                    return null;
                                }
                            })
                        .setTypes(String.class, Integer.TYPE, Double.TYPE, Long.TYPE)
                        .create();

                    ElPollo.Overrides.add(override);

                    mOverrides.add(override);
                } catch(OverrideException e) {
                     Log.e(TAG, "InternalTestLoader", e);
                }

                try {
                    ElPollo.MemberOverride override = new ElPollo.MemberOverride.Builder()
                        .setSource(InternalTestLoader.class, "testCustomCall")
                        .setTarget(
                            new ElPollo.OverrideCustom<InternalTestLoader, Void>() {
                                public void call(InternalTestLoader obj, String arg1, int arg2) {
                                    Log.d(TAG, "testCustomCall override " + arg1 + " " + arg2);
                                }
                            })
                        .setTypes(String.class, Integer.TYPE)
                        .create();

                    ElPollo.Overrides.add(override);

                    mOverrides.add(override);
                } catch(OverrideException e) {
                     Log.e(TAG, "InternalTestLoader", e);
                }

                try {
                    ElPollo.MemberOverride override = new ElPollo.MemberOverride.Builder()
                        .setSource(InternalTestLoader.class, "testMultipleCall")
                        .setTarget(
                            new ElPollo.OverrideCall<InternalTestLoader, Boolean>() {
                                @Override
                                public Boolean call(InternalTestLoader obj, Object... args) {
                                    Log.d(TAG, "testMultipleCall override " + args.length);

                                    super.callOriginal(obj, args);

                                    return false;
                                }
                            })
                        .setAll(true)
                        .create();

                    ElPollo.Overrides.add(override);

                    mOverrides.add(override);
                } catch(OverrideException e) {
                     Log.e(TAG, "InternalTestLoader", e);
                }

                try {
                    ElPollo.MemberOverride override = new ElPollo.MemberOverride.Builder()
                        .setSource(InternalTestLoader.class, "testMultipleCustom")
                        .setTarget(
                            new ElPollo.OverrideCustom<InternalTestLoader, Void>() {
                                public boolean call(InternalTestLoader obj) {
                                    Log.d(TAG, "testMultipleCustom override 1");

                                    super.callOriginal(obj);

                                    return false;
                                }

                                public boolean call(InternalTestLoader obj, int arg1) {
                                    Log.d(TAG, "testMultipleCustom override 2");

                                    super.callOriginal(obj, arg1);

                                    return false;
                                }
                            })
                        .setAll(true)
                        .create();

                    ElPollo.Overrides.add(override);

                    mOverrides.add(override);
                } catch(OverrideException e) {
                     Log.e(TAG, "InternalTestLoader", e);
                }

                try {
                    ElPollo.MemberOverride override = new ElPollo.MemberOverride.Builder()
                        .setSource(InternalTestLoader.class, "testReturnType")
                        .setTarget(
                            new ElPollo.OverrideCall<InternalTestLoader, Integer>() {
                                @Override
                                public Integer call(InternalTestLoader obj, Object... args) {
                                    Log.d(TAG, "testReturnType override");

                                    return super.callOriginal(obj, args);
                                }
                            })
                        .create();

                    ElPollo.Overrides.add(override);

                    mOverrides.add(override);
                } catch(OverrideException e) {
                     Log.e(TAG, "InternalTestLoader", e);
                }

                test("Test");
            }
        }, ElPollo.Packages.SYSTEM);
    }

    @Override
    public void destroy() {
        super.destroy(mOverrides);
    }

    private void test(String str) {
        Log.d(TAG, "Before: " + str);

        /*
            Testing various arguments.
        */
        testArgs(0xffff, 0x100000000000L, 25.0f, 100.00001f);

        /*
            A static member override will get the class as the first argument,
            as opposed to the object that other overrides get
        */
        testStatic("Test1", true);

        /* 
            Due to how ART works this will always return false, even though it has been
            overridden. This is because ART optimises the method and essentially
            removes it, leaving only the return value. 
            There is currently no way around this.
        */
        boolean empty = testEmpty();
        Log.d(TAG, "Empty: " + empty);

        /*
            Testing MethodHelper.callMethod.
        */
        MethodHelper.callMethod(this, "testMethodHelper", Integer.TYPE, 123);

        /*
            Testing shared preferences.
        */
        testPrefs();

        testCustomCall("Custom", 50);
        testOverrideCall("Override", 100, 1.025f, 1000000);

        /*
            Testing multiple overrides.
        */
        Log.d(TAG, "testMultipleCall1 return " + testMultipleCall());
        Log.d(TAG, "testMultipleCall2 return " + testMultipleCall(0));

        Log.d(TAG, "testMultipleCustom1 return " + testMultipleCustom());
        Log.d(TAG, "testMultipleCustom2 return " + testMultipleCustom(0));

        testReturnType();
    }

    private void testArgs(int a, long b, float c, double d) {
        Log.d(TAG, "testArgs " + a + ", " + b + ", " + c + ", " + d);
    }

    private static void testStatic(String str, boolean b) {
        Log.d(TAG, "testStatic " + str + " " + b);
    }

    private boolean testEmpty() {
        return false;
    }

    private void testOverrideCall(String str, int a, double testDouble, long testLong) {
        Log.d(TAG, "testOverrideCall " + str + " " + a + " " + testDouble + " " + testLong);
    }

    private void testCustomCall(String str, int a) {
        Log.d(TAG, "testCustomCall " + str + " " + a);
    }

    private void testMethodHelper(int a) {
        Log.d(TAG, "testMethodHelper " + a);
    }

    private void testPrefs() {
        try {
            SharedPreferences prefs = ModuleHelper.getPreferences(InternalModules.MANAGER_PACKAGE_NAME);
            Log.d(TAG, "testPrefs " + (prefs != null));
        } catch(ModuleException e) {
            Log.e(TAG, "testPrefs", e);
        }
    }

    private boolean testMultipleCall() {
        Log.d(TAG, "testMultipleCall1");
        return true;
    }

    private boolean testMultipleCall(int a) {
        Log.d(TAG, "testMultipleCall2 " + a);
        return true;
    }

    private boolean testMultipleCustom() {
        Log.d(TAG, "testMultipleCustom1");
        return true;
    }

    private boolean testMultipleCustom(int a) {
        Log.d(TAG, "testMultipleCustom2 " + a);
        return true;
    }

    private boolean testReturnType() {
        Log.d(TAG, "testReturnType");
        return true;
    }

    /* Overrides */
    private void testArgsOverride(Object obj, int a, long b, float c, double d) {
        Log.d(TAG, "testArgsOverride " + a + ", " + b + ", " + c + ", " + d);

        MethodHelper.callOriginal(obj, a, b, c, d);
    }

    private static void testStaticOverride(Class<?> objClass, String str, boolean b) {
        Log.d(TAG, "testStaticOverride " + objClass.getSimpleName() + " " + str + " " + b);

        MethodHelper.callOriginalStatic(str, b);
    }

    private boolean testEmptyOverride(Object obj) {
        return true;
    }
}
