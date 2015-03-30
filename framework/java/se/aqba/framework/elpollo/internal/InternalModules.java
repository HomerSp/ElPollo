package se.aqba.framework.elpollo;

import android.content.SharedPreferences;
import android.util.Log;

import dalvik.system.PathClassLoader;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

import se.aqba.framework.elpollo.ElPolloModuleLoader;
import se.aqba.framework.elpollo.ElPolloModule;
import se.aqba.framework.elpollo.exception.ModuleException;
import se.aqba.framework.elpollo.exception.OverrideException;
import se.aqba.framework.elpollo.helpers.ModuleHelper;

final class InternalModules {
    private static final String TAG = "ElPollo.Modules";

    private static final String LOADER_CLASS = ".elpollo.Loader";
    static final String MANAGER_PACKAGE_NAME = "se.aqba.android.elpollo.manager";

    static List<ModuleKey> sLoadedModules = new ArrayList<ModuleKey>();

    private List<ElPollo.MemberOverride> mOverrides = new ArrayList<ElPollo.MemberOverride>();

    InternalModules() {
        
    }

    void destroy() {
        for(ElPollo.MemberOverride override: mOverrides) {
            ElPollo.Overrides.remove(override);
        }

        mOverrides.clear();

        for(ModuleKey moduleKey: sLoadedModules) {
            moduleKey.destroy();
        }

        sLoadedModules.clear();
    }

    void loadAll(ClassLoader baseLoader) {
        Log.d(TAG, "loadAll");

        List<String> loadedModules = new ArrayList<String>();

        try {
            SharedPreferences installerPrefs = ModuleHelper.getPreferences(MANAGER_PACKAGE_NAME);
            Set<String> modules = installerPrefs.getStringSet("modules", new HashSet<String>());
            loadAll(modules, loadedModules, baseLoader);
        } catch(ModuleException e) {
            Log.e(TAG, "loadAll manager", e);
        }

        try {
            SharedPreferences installerPrefs = ModuleHelper.getSystemPreferences();
            Set<String> modules = installerPrefs.getStringSet("modules", new HashSet<String>());
            loadAll(modules, loadedModules, baseLoader);
        } catch(ModuleException e) {
            //Log.e(TAG, "loadAll system manager", e);
        }

        ElPollo.Packages.addListener(mBridgeListener, MANAGER_PACKAGE_NAME);
    }

    private void loadAll(Set<String> modules, List<String> loadedModules, ClassLoader baseLoader) {
        for(String module: modules) {
            String[] arr = module.split("=", 2);
            if(arr.length < 2) {
                continue;
            }

            if(!loadedModules.contains(arr[0])) {
                if(load(arr[0], arr[1], baseLoader)) {
                    loadedModules.add(arr[0]);
                }
            }
        }
    }

    private boolean load(String pkgName, String source, ClassLoader baseLoader) {
        if(!new File(source).exists()) {
            Log.e(TAG, "Module " + pkgName + " does not exist");
            return false;
        }

        Log.d(TAG, "Loading " + pkgName);

        try {
            ClassLoader classLoader = new PathClassLoader(source, baseLoader);

            Class<?> loaderClass = classLoader.loadClass(pkgName + LOADER_CLASS);
            if(!ElPolloModuleLoader.class.isAssignableFrom(loaderClass)) {
                throw(new RuntimeException(loaderClass.getSimpleName() + " does not implement " + ElPolloModuleLoader.class.getSimpleName()));
            }

            ElPollo.Packages.addListener(mBridgeListener, pkgName);
            mBridgeListener.onLoad(pkgName, classLoader);

            ElPolloModuleLoader loader = (ElPolloModuleLoader)loaderClass.getDeclaredConstructor().newInstance();
            ModuleKey moduleKey = new ModuleKey(pkgName, loader);

            // Load the module classes.
            moduleKey.getLoader().main();

            List<Class<? extends ElPolloModule>> overrideClasses = moduleKey.getLoader().modules();
            for(Class<? extends ElPolloModule> overrideClass: overrideClasses) {
                ElPolloModule module = (ElPolloModule)overrideClass.getDeclaredConstructor().newInstance();
                module.main();

                moduleKey.addModule(module);
            }

            sLoadedModules.add(moduleKey);

            return true;
        } catch(Throwable e) {
            Log.e(TAG, "Failed to load module " + pkgName, e);
        }

        return false;
    }

    static boolean isLoaded(String pkg) {
        for(ModuleKey key: sLoadedModules) {
            if(key.getPackage().equals(pkg)) {
                return true;
            }
        }

        return false;
    }

    private ElPollo.Packages.Listener mBridgeListener = new ElPollo.Packages.Listener() {
        @Override
        public void onLoad(String pkg, ClassLoader classLoader) {
            if(classLoader != null) {
                Log.d(TAG, "onLoad " + pkg + " classLoader: " + classLoader.toString());
            }

            String bridgeName = "se.aqba.framework.elpollo.bridge.ElPolloBridge";

            // Check that the bridge class exists.
            try {
                Class.forName(bridgeName, false, classLoader);
            } catch(Exception e) {
                // Ignore
                return;
            }

            try {
                ElPollo.MemberOverride override = new ElPollo.MemberOverride.Builder(classLoader)
                    .setSource(bridgeName, "isInstalled") 
                    .setTarget(new ElPollo.OverrideCall<Class<?>, Boolean>() {
                        @Override
                        public Boolean call(Class<?> objClass, Object... args) {
                            return true;
                        }
                    })
                    .create();

                ElPollo.Overrides.add(override);
                mOverrides.add(override);
            } catch(OverrideException e) {
                Log.e(TAG, "Manager", e);
            }

            try {
                ElPollo.MemberOverride override = new ElPollo.MemberOverride.Builder(classLoader)
                    .setSource(bridgeName, "getVersion") 
                    .setTarget(new ElPollo.OverrideCall<Class<?>, String>() {
                        @Override
                        public String call(Class<?> objClass, Object... args) {
                            return ElPollo.getVersion();
                        }
                    })
                    .create();

                ElPollo.Overrides.add(override);
                mOverrides.add(override);
            } catch(OverrideException e) {
                Log.e(TAG, "Manager", e);
            }

            try {
                ElPollo.MemberOverride override = new ElPollo.MemberOverride.Builder(classLoader)
                    .setSource(bridgeName, "getVersionCode") 
                    .setTarget(new ElPollo.OverrideCall<Class<?>, Integer>() {
                        @Override
                        public Integer call(Class<?> objClass, Object... args) {
                            return ElPollo.getVersionCode();
                        }
                    })
                    .create();

                ElPollo.Overrides.add(override);
                mOverrides.add(override);
            } catch(OverrideException e) {
                Log.e(TAG, "Manager", e);
            }

            try {
                ElPollo.MemberOverride override = new ElPollo.MemberOverride.Builder(classLoader)
                    .setSource(bridgeName, "getVersionCodename") 
                    .setTarget(new ElPollo.OverrideCall<Class<?>, String>() {
                        @Override
                        public String call(Class<?> objClass, Object... args) {
                            return ElPollo.getVersionCodename();
                        }
                    })
                    .create();

                ElPollo.Overrides.add(override);
                mOverrides.add(override);
            } catch(OverrideException e) {
                Log.e(TAG, "Manager", e);
            }

            try {
                ElPollo.MemberOverride override = new ElPollo.MemberOverride.Builder(classLoader)
                    .setSource(bridgeName, "getSystemPreferences")
                    .setTarget(new ElPollo.OverrideCall<Class, SharedPreferences>() {
                        @Override
                        public SharedPreferences call(Class objClass, Object... args) {
                            try {
                                return ModuleHelper.getSystemPreferences();
                            } catch(ModuleException e) {
                                Log.e(TAG, "getSystemPreferences", e);
                            }

                            return null;
                        }
                    })
                    .create();

                ElPollo.Overrides.add(override);
                mOverrides.add(override);
            } catch(OverrideException e) {
                Log.e(TAG, "Manager", e);
            }
        }
    };

    private class ModuleKey {
        private String mPackage;
        private ElPolloModuleLoader mLoader;
        private List<ElPolloModule> mModules = new ArrayList<ElPolloModule>();

        public ModuleKey(String pkg, ElPolloModuleLoader loader) {
            // Package must never be null.
            mPackage = (pkg == null)?"":pkg;
            mLoader = loader;
        }

        public void addModule(ElPolloModule module) {
            mModules.add(module);
        }

        public String getPackage() {
            return mPackage;
        }

        public ElPolloModuleLoader getLoader() {
            return mLoader;
        }

        public void destroy() {
            for(ElPolloModule module: mModules) {
                module.destroy();
            }

            mModules.clear();
        }
    }
}
