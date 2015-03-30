package se.aqba.framework.elpollo.helpers;

import android.app.ActivityThread;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.IPackageManager;
import android.os.Environment;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;

import java.io.File;
import java.lang.String;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import java.util.HashMap;

import se.aqba.framework.elpollo.ElPollo;
import se.aqba.framework.elpollo.exception.ModuleException;

public final class ModuleHelper {
    private static final String TAG = "ElPollo.ModuleHelper";

    public static final String PREFERENCES_NAME = "elpollo_preferences";
    public static final String PREFERENCES_FILE = PREFERENCES_NAME + ".xml";
    public static final String SYSTEM_PREFERENCES_FILE = "/system/etc/" + PREFERENCES_FILE;

    private static final HashMap<String, SharedPreferences> sPreferences = new HashMap<>();

    /**
     * Get a module's shared preferences.
     *
     * @param module Package name of the module.
     * @return The shared preferences for the module.
     * @throws ModuleException If the preferences could not be loaded.
     */
    public static SharedPreferences getPreferences(String module) throws ModuleException {
        synchronized(sPreferences) {
            if(sPreferences.containsKey(module)) {
                return sPreferences.get(module);
            }
        }

        File prefsFile = getSharedPrefsFile(module);
        /*if(!prefsFile.exists()) {
            throw new ModuleException("getPreferences " + module + " does not exist");
        }*/

        try {
            Log.v(TAG, "getPreferences " + prefsFile.getAbsoluteFile());
            SharedPreferences sp = ElPollo.getPreferences(prefsFile);

            synchronized(sPreferences) {
                sPreferences.put(module, sp);
            }

            return sp;
        } catch(Exception e) {
            throw new ModuleException("getPreferences " + module, e);
        }
    }

    /**
     * Get the system's shared preferences from {@value #SYSTEM_PREFERENCES_FILE}.
     *
     * @return The shared preferences for the system.
     * @throws ModuleException If the preferences could not be loaded.
     */
    public static SharedPreferences getSystemPreferences() throws ModuleException {
        synchronized(sPreferences) {
            if(sPreferences.containsKey(null)) {
                return sPreferences.get(null);
            }
        }

        File prefsFile = new File(SYSTEM_PREFERENCES_FILE);
        /*if(!prefsFile.exists()) {
            throw new ModuleException("getSystemPreferences file does not exist");
        }*/

        try {
            Log.v(TAG, "getSystemPreferences " + prefsFile.getAbsoluteFile());
            SharedPreferences sp = ElPollo.getPreferences(prefsFile);
            synchronized(sPreferences) {
                sPreferences.put(null, sp);
            }
            return sp;
        } catch(Exception e) {
            throw new ModuleException("getSystemPreferences", e);
        }
    }

    private static File getSharedPrefsFile(String pkg) {
        File dataDir = new File(Environment.getDataDirectory(), "data");
        File pkgDir = new File(dataDir, pkg);
        File prefsDir = new File(pkgDir, "shared_prefs");
        return new File(prefsDir, PREFERENCES_FILE);
    }
}
