package se.aqba.framework.elpollo;

import android.app.ActivityThread;
import android.app.LoadedApk;
import android.content.Context;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Message;
import android.os.Looper;
import android.preference.Preference;
import android.system.Os;
import android.view.WindowManager;
import android.view.Window;
import android.util.Log;

import dalvik.system.BaseDexClassLoader;
import dalvik.system.PathClassLoader;

import com.android.internal.os.RuntimeInit;

import java.io.File;
import java.lang.String;

import java.util.AbstractMap;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import android.content.res.PResources;

import se.aqba.framework.elpollo.ElPollo;
import se.aqba.framework.elpollo.helpers.MethodHelper;

import se.aqba.framework.elpollo.ElPolloModule;
import se.aqba.framework.elpollo.InternalPackageLoader;
import se.aqba.framework.elpollo.InternalResourcesLoader;
import se.aqba.framework.elpollo.InternalTestLoader;

/**
 * Internal use only
 *
 * @hide
 */
final class InternalLoaders {
    private static final String TAG = "ElPollo.Loaders";

    private ClassLoader mClassLoader;

    private ArrayList<Map.Entry<String, ElPollo.Packages.Listener>> mPackageListeners = new ArrayList<Map.Entry<String, ElPollo.Packages.Listener>>();
    private ArrayList<Map.Entry<String, ElPollo.Resources.Listener>> mResourceListeners = new ArrayList<Map.Entry<String, ElPollo.Resources.Listener>>();

    private ArrayList<ElPolloModule> mLoaders = new ArrayList<ElPolloModule>();

    private int mInt = 0;
    private Handler mHandler;

    protected InternalLoaders() {
        Log.d(TAG, "Creating helpers");

        //mClassLoader = new PathClassLoader(Os.getenv("SYSTEMSERVERCLASSPATH"), Thread.currentThread().getContextClassLoader());
    }

    protected void addPackageListener(ElPollo.Packages.Listener listener, String... pkgs) {
        if(pkgs.length <= 0) {
            pkgs = new String[] {"*"};
        }

        for(String pkg: pkgs) {
            Log.d(TAG, "addListener " + pkg);
            mPackageListeners.add(new AbstractMap.SimpleEntry<String, ElPollo.Packages.Listener>(pkg, listener));
        }
    }
    protected void addResourceListener(ElPollo.Resources.Listener listener, String... pkgs) {
        if(pkgs.length <= 0) {
            pkgs = new String[] {"*"};
        }

        for(String pkg: pkgs) {
            Log.d(TAG, "addResourceListener " + pkg);
            mResourceListeners.add(new AbstractMap.SimpleEntry<String, ElPollo.Resources.Listener>(pkg, listener));
        }
    }

    protected ClassLoader getClassLoader() {
        return mClassLoader;
    }

    protected void setClassLoader(ClassLoader classLoader) {
        mClassLoader = classLoader;
    }

    protected void init() {
        mLoaders.add(new InternalPackageLoader(this));
        mLoaders.add(new InternalResourcesLoader(this));
        mLoaders.add(new InternalTestLoader(this));

        for(ElPolloModule loader: mLoaders) {
            loader.main();
        }

        ElPollo.getModules().loadAll(Thread.currentThread().getContextClassLoader());

        // Trigger a load of the system package.
        triggerLoadPackage(ElPollo.Packages.SYSTEM, Thread.currentThread().getContextClassLoader());
        triggerLoadResources(ElPollo.Resources.SYSTEM, null);
    }

    protected void destroy() {
        for(ElPolloModule loader: mLoaders) {
            loader.destroy();
        }

        mLoaders.clear();
    }

    protected void triggerLoadPackage(String pkg) {
        triggerLoadPackage(pkg, null, mClassLoader);
    }

    protected void triggerLoadPackage(String pkg, ClassLoader classLoader) {
        triggerLoadPackage(pkg, null, classLoader);
    }

    protected void triggerLoadPackage(String pkg, String resDir, ClassLoader classLoader) {
        //Log.d(TAG, "triggerLoadPackage " + resDir);

        for(Map.Entry<String, ElPollo.Packages.Listener> entry: mPackageListeners) {
            try {
                if(entry.getKey().equals(pkg) || entry.getKey().equals("*")) {
                    entry.getValue().onLoad(pkg, classLoader);
                }
            } catch(Throwable e) {
                Log.e(TAG, "Caught exception in " + entry.getKey() + " when invoking " + pkg + " load listener", e);
            }
        } 
        
    } 

    protected void triggerLoadResources(PResources res) {
        if(res == null) {
            return;
        }

        triggerLoadResources(res.getPackage(), res);
    }

    protected void triggerLoadResources(String pkg, PResources res) {
        for(Map.Entry<String, ElPollo.Resources.Listener> entry: mResourceListeners) {
            try {
                if(entry.getKey().equals(pkg) || entry.getKey().equals("*")) {
                    entry.getValue().onLoad(pkg, res);
                }
            } catch(Throwable e) {
                Log.e(TAG, "Caught exception in " + entry.getKey() + " when invoking " + pkg + " resource load listener", e);
            }
        } 
    }
}
