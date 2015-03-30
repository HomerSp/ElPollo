package se.aqba.framework.elpollo;

import android.app.ActivityThread;
import android.app.LoadedApk;
import android.app.ResourcesManager;
import android.content.Context;
import android.content.res.CompatibilityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.ResourcesKey;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.os.IBinder;
import android.preference.Preference;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.Window;
import android.util.ArrayMap;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;

import org.xmlpull.v1.XmlPullParser;

import dalvik.system.BaseDexClassLoader;
import dalvik.system.PathClassLoader;

import java.io.StringWriter;
import java.io.PrintWriter;
import java.io.File;
import java.lang.String;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.ArrayList;

import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import android.content.res.PResources;
import android.content.res.PTypedArray;

import se.aqba.framework.elpollo.ElPollo;
import se.aqba.framework.elpollo.ElPolloModule;
import se.aqba.framework.elpollo.helpers.ClassHelper;
import se.aqba.framework.elpollo.helpers.MethodHelper;
import se.aqba.framework.elpollo.InternalLoaders;
import se.aqba.framework.elpollo.exception.OverrideException;

final class InternalResourcesLoader extends ElPolloModule {
    private static final String TAG = "ElPollo.ResourcesLoader";
 
    private InternalLoaders mParent;

    private ArrayList<ElPollo.MemberOverride> mOverrides = new ArrayList<ElPollo.MemberOverride>();

    protected InternalResourcesLoader(final InternalLoaders parent) {
        mParent = parent;
    }

    @Override
    public void main() {
        Log.d(TAG, "Creating resources loader");

        ElPollo.Packages.addListener(new ElPollo.Packages.Listener() {
            @Override
            public void onLoad(String pkg, ClassLoader classLoader) {
                Log.d(TAG, "onLoad " + pkg);

                try {
                    ElPollo.MemberOverride override = new ElPollo.MemberOverride.Builder()
                        .setSource(ResourcesManager.class, "getTopLevelResources")
                        .setTarget(InternalResourcesLoader.this, "getTopLevelResourcesOverride")
                        .setTypes(String.class, String[].class,
                            String[].class, String[].class, Integer.TYPE,
                            Configuration.class, CompatibilityInfo.class, IBinder.class)
                        .create();

                    ElPollo.Overrides.add(override);
                    mOverrides.add(override);
                } catch(OverrideException e) {
                    Log.e(TAG, "ResourcesManager", e);
                }

                try {
                    ElPollo.MemberOverride override = new ElPollo.MemberOverride.Builder()
                        .setSource(TypedArray.class, "obtain")
                        .setTarget(InternalResourcesLoader.this, "typedArrayObtainOverride")
                        .setTypes(Resources.class, Integer.TYPE)
                        .create();

                    ElPollo.Overrides.add(override);
                    mOverrides.add(override);
                } catch(OverrideException e) {
                    Log.e(TAG, "ResourcesManager", e);
                }

                try {
                    ElPollo.MemberOverride override = new ElPollo.MemberOverride.Builder()
                        .setSource(LayoutInflater.class, "inflate")
                        .setTarget(InternalResourcesLoader.this, "inflateOverride")
                        .setTypes(XmlPullParser.class, ViewGroup.class, Boolean.TYPE)
                        .create();

                    ElPollo.Overrides.add(override);
                    mOverrides.add(override);
                } catch(OverrideException e) {
                    Log.e(TAG, "ResourcesManager", e);
                }
            }
        }, ElPollo.Packages.SYSTEM);

        // Override system resources.
        ClassHelper.setFieldValue(Resources.class, null, "mSystem", new PResources());
    }

    @Override
    public void destroy() {
        super.destroy(mOverrides);
    }

    private Resources getTopLevelResourcesOverride(ResourcesManager obj, String resDir, String[] splitResDirs,
            String[] overlayDirs, String[] libDirs, int displayId,
            Configuration overrideConfiguration, CompatibilityInfo compatInfo, IBinder token) {

        final float scale = compatInfo.applicationScale;
        ResourcesKey key = new ResourcesKey(resDir, displayId, overrideConfiguration, scale, token);

        ArrayMap<ResourcesKey, WeakReference<Resources>> mActiveResources = ClassHelper.getFieldValue(obj, "mActiveResources", null);

        boolean resExists = false;
        synchronized(obj) {
            if(mActiveResources != null) {
                WeakReference<Resources> r = mActiveResources.get(key);
                resExists = r != null && r.get() != null && r.get().getAssets().isUpToDate();
            }
        }

        Resources ret = MethodHelper.callOriginal(obj, resDir, splitResDirs, overlayDirs, libDirs, displayId, overrideConfiguration, compatInfo, token);
        if(resExists || ret == null) {
            if(ret == null) {
                Log.w(TAG, "ret == null for " + resDir);
            }

            return ret;
        }

        ret = new PResources(ret, token, resDir);
        //Log.d(TAG, "Overriding resources for " + resDir + " " + ((PResources)ret).getPackage());

        synchronized(obj) {
            mActiveResources.put(key, new WeakReference<Resources>(ret));
        }

        mParent.triggerLoadResources((PResources)ret);

        return ret;
    }

    private TypedArray typedArrayObtainOverride(Class<?> objClass, Resources res, int num) {
        TypedArray ret = MethodHelper.callOriginalStatic(res, num);

        return PTypedArray.fromTypedArray(ret);
    }

    private View inflateOverride(LayoutInflater obj, XmlPullParser parser, ViewGroup root, boolean attachToRoot) {
        View view = MethodHelper.callOriginal(obj, parser, root, attachToRoot);

        if(obj != null && obj.getContext() != null && parser instanceof XmlResourceParser) {
            Resources res = obj.getContext().getResources();
            if(res instanceof PResources) {
                PResources pres = (PResources)res;

                int id = pres.getLayoutId((XmlResourceParser)parser);
                if(id != 0) {
                    View ret = pres.getReplacement(id, PResources.ResType.ResTypeLayoutInflater, view);
                    if(ret != null) {
                        return ret;
                    }
                }
            }
        }

        return view;
    }
}
