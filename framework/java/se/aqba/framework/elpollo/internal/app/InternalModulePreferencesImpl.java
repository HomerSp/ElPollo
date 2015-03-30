package se.aqba.framework.elpollo;

import android.content.SharedPreferences;
import android.os.FileUtils;
import android.os.Looper;
import android.system.ErrnoException;
import android.system.Os;
import android.system.StructStat;
import android.util.Log;

import com.android.internal.util.XmlUtils;

import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.WeakHashMap;
import java.util.HashSet;
import java.util.Set;

final class InternalModulePreferencesImpl implements SharedPreferences {
    private static final String TAG = "ElPollo.Preferences";

    private final File mFile;
    private final File mBackupFile;
    private final int mMode;

    private Map<String, Object> mMap;     // guarded by 'this'
    private int mDiskWritesInFlight = 0;  // guarded by 'this'
    private boolean mLoaded = false;      // guarded by 'this'
    private long mStatTimestamp;          // guarded by 'this'
    private long mStatSize;               // guarded by 'this'

    private final Object mWritingToDiskLock = new Object();
    private static final Object mContent = new Object();
    private final WeakHashMap<SharedPreferences.OnSharedPreferenceChangeListener, Object> mListeners =
            new WeakHashMap<SharedPreferences.OnSharedPreferenceChangeListener, Object>();

    InternalModulePreferencesImpl(File file) {
        mFile = file;
        mBackupFile = makeBackupFile(file);
        mMode = 1;
        mMap = null;
        mLoaded = false;
        startLoadFromDisk();
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        synchronized(this) {
            mListeners.put(listener, mContent);
        }
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        synchronized(this) {
            mListeners.remove(listener);
        }
    }

    private void startLoadFromDisk() {
        synchronized (this) {
            mLoaded = false;
        }
        new Thread("InternalModulePreferencesImpl-load") {
            public void run() {
                synchronized (InternalModulePreferencesImpl.this) {
                    loadFromDiskLocked();
                }
            }
        }.start();
    }

    @SuppressWarnings("unchecked")
    private void loadFromDiskLocked() {
        Log.d(TAG, "loadFromDiskLocked " + mFile.getAbsolutePath());

        if (mLoaded) {
            return;
        }
        if (mBackupFile.exists()) {
            mFile.delete();
            mBackupFile.renameTo(mFile);
        }

        // Debugging
        if (mFile.exists() && !mFile.canRead()) {
            Log.w(TAG, "Attempt to read preferences file " + mFile + " without permission");
        }

        Map<String, Object> map = null;
        StructStat stat = null;
        try {
            stat = Os.stat(mFile.getPath());
            if (mFile.canRead()) {
                BufferedInputStream str = null;
                try {
                    str = new BufferedInputStream(
                            new FileInputStream(mFile), 16*1024);
                    map = (Map<String, Object>)XmlUtils.readMapXml(str);
                } catch (XmlPullParserException e) {
                    Log.w(TAG, "getSharedPreferences", e);
                } catch (FileNotFoundException e) {
                    Log.w(TAG, "getSharedPreferences", e);
                } catch (IOException e) {
                    Log.w(TAG, "getSharedPreferences", e);
                } finally {
                    try {
                        str.close();
                    } catch(IOException e) {
                        // Empty.
                    }
                }
            }
        } catch (ErrnoException e) {
        }
        mLoaded = true;
        if (map != null) {
            mMap = map;
            mStatTimestamp = stat.st_mtime;
            mStatSize = stat.st_size;
        } else {
            mMap = new HashMap<String, Object>();
        }
        notifyAll();
    }

    private static File makeBackupFile(File prefsFile) {
        return new File(prefsFile.getPath() + ".bak");
    }

    void startReloadIfChangedUnexpectedly() {
        synchronized (this) {
            // TODO: wait for any pending writes to disk?
            if (!hasFileChangedUnexpectedly()) {
                Log.d(TAG, "File not changed");

                return;
            }

            Log.d(TAG, "File changed");

            mLoaded = false;
            loadFromDiskLocked();
        }
    }

    // Has the file changed out from under us?  i.e. writes that
    // we didn't instigate.
    private boolean hasFileChangedUnexpectedly() {
        synchronized (this) {
            if (mDiskWritesInFlight > 0) {
                return false;
            }
        }

        final StructStat stat;
        try {
            stat = Os.stat(mFile.getPath());
        } catch (ErrnoException e) {
            return true;
        }

        synchronized (this) {
            return mStatTimestamp != stat.st_mtime || mStatSize != stat.st_size;
        }
    }

    private void awaitLoadedLocked() {
        while (!mLoaded) {
            try {
                wait();
            } catch (InterruptedException unused) {
            }
        }

        startReloadIfChangedUnexpectedly();
    }

    @Override
    public Map<String, ?> getAll() {
        synchronized (this) {
            awaitLoadedLocked();
            //noinspection unchecked
            return new HashMap<String, Object>(mMap);
        }
    }

    @Override
    public String getString(String key, String defValue) {
        synchronized (this) {
            awaitLoadedLocked();
            String v = (String)mMap.get(key);
            return v != null ? v : defValue;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Set<String> getStringSet(String key, Set<String> defValues) {
        synchronized (this) {
            awaitLoadedLocked();
            Set<String> v = (Set<String>) mMap.get(key);
            return v != null ? v : defValues;
        }
    }

    @Override
    public int getInt(String key, int defValue) {
        synchronized (this) {
            awaitLoadedLocked();
            Integer v = (Integer)mMap.get(key);
            return v != null ? v : defValue;
        }
    }

    @Override
    public long getLong(String key, long defValue) {
        synchronized (this) {
            awaitLoadedLocked();
            Long v = (Long)mMap.get(key);
            return v != null ? v : defValue;
        }
    }

    @Override
    public float getFloat(String key, float defValue) {
        synchronized (this) {
            awaitLoadedLocked();
            Float v = (Float)mMap.get(key);
            return v != null ? v : defValue;
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        synchronized (this) {
            awaitLoadedLocked();
            Boolean v = (Boolean)mMap.get(key);
            return v != null ? v : defValue;
        }
    }

    @Override
    public boolean contains(String key) {
        synchronized (this) {
            awaitLoadedLocked();
            return mMap.containsKey(key);
        }
    }

    @Override
    public Editor edit() {
        // TODO: remove the need to call awaitLoadedLocked() when
        // requesting an editor.  will require some work on the
        // Editor, but then we should be able to do:
        //
        //      context.getSharedPreferences(..).edit().putString(..).apply()
        //
        // ... all without blocking.
        synchronized (this) {
            awaitLoadedLocked();
        }

        return new EditorImpl();
    }

    public final class EditorImpl implements Editor {
        private final Map<String, Object> mModified = new HashMap<String, Object>();
        private boolean mClear = false;

        @Override
        public Editor putString(String key, String value) {
            synchronized (this) {
                mModified.put(key, value);
                return this;
            }
        }

        @Override
        public Editor putStringSet(String key, Set<String> values) {
            synchronized (this) {
                mModified.put(key,
                        (values == null) ? null : new HashSet<String>(values));
                return this;
            }
        }

        @Override
        public Editor putInt(String key, int value) {
            synchronized (this) {
                mModified.put(key, value);
                return this;
            }
        }

        @Override
        public Editor putLong(String key, long value) {
            synchronized (this) {
                mModified.put(key, value);
                return this;
            }
        }

        @Override
        public Editor putFloat(String key, float value) {
            synchronized (this) {
                mModified.put(key, value);
                return this;
            }
        }

        @Override
        public Editor putBoolean(String key, boolean value) {
            synchronized (this) {
                mModified.put(key, value);
                return this;
            }
        }

        @Override
        public Editor remove(String key) {
            synchronized (this) {
                mModified.put(key, this);
                return this;
            }
        }

        @Override
        public Editor clear() {
            synchronized (this) {
                mClear = true;
                return this;
            }
        }

        @Override
        public void apply() {
            commit();
        }

        @Override
        public boolean commit() {
            Map<String, Object> mapToWriteToDisk = null;
            List<String> keysModified = null;
            Set<SharedPreferences.OnSharedPreferenceChangeListener> listeners = null;
            boolean changesMade = false;

            synchronized (InternalModulePreferencesImpl.this) {
                // We optimistically don't make a deep copy until
                // a memory commit comes in when we're already
                // writing to disk.
                if (mDiskWritesInFlight > 0) {
                    // We can't modify our mMap as a currently
                    // in-flight write owns it.  Clone it before
                    // modifying it.
                    // noinspection unchecked
                    mMap = new HashMap<String, Object>(mMap);
                }
                mapToWriteToDisk = mMap;
                mDiskWritesInFlight++;

                boolean hasListeners = mListeners.size() > 0;
                if (hasListeners) {
                    keysModified = new ArrayList<String>();
                    listeners =
                            new HashSet<OnSharedPreferenceChangeListener>(mListeners.keySet());
                }

                synchronized (this) {
                    if (mClear) {
                        if (!mMap.isEmpty()) {
                            changesMade = true;
                            mMap.clear();
                        }
                        mClear = false;
                    }

                    for (Map.Entry<String, Object> e : mModified.entrySet()) {
                        String k = e.getKey();
                        Object v = e.getValue();
                        // "this" is the magic value for a removal mutation. In addition,
                        // setting a value to "null" for a given key is specified to be
                        // equivalent to calling remove on that key.
                        if (v == this || v == null) {
                            if (!mMap.containsKey(k)) {
                                continue;
                            }
                            mMap.remove(k);
                        } else {
                            if (mMap.containsKey(k)) {
                                Object existingValue = mMap.get(k);
                                if (existingValue != null && existingValue.equals(v)) {
                                    continue;
                                }
                            }
                            mMap.put(k, v);
                        }

                        changesMade = true;
                        if (hasListeners) {
                            keysModified.add(k);
                        }
                    }

                    mModified.clear();
                }
            }

            boolean result = writeToDisk(mapToWriteToDisk, changesMade);
            notifyListeners(listeners, keysModified);
            return result;
        }

        private void notifyListeners(final Set<SharedPreferences.OnSharedPreferenceChangeListener> listeners, final List<String> keysModified) {
            if (listeners == null || keysModified == null ||
                keysModified.size() == 0) {
                return;
            }
            if (Looper.myLooper() != Looper.getMainLooper()) {
                Log.e(TAG, "A module's preferences can only be modified from the main thread!");
                return;
            }

            for (int i = keysModified.size() - 1; i >= 0; i--) {
                final String key = keysModified.get(i);
                for (OnSharedPreferenceChangeListener listener : listeners) {
                    if (listener != null) {
                        listener.onSharedPreferenceChanged(InternalModulePreferencesImpl.this, key);
                    }
                }
            }
        }
    }

    private boolean writeToDisk(final Map<String, Object> mapToWriteToDisk, final boolean changesMade) {
        synchronized(mWritingToDiskLock) {
            return writeToDiskLocked(mapToWriteToDisk, changesMade);
        }
    }

    private boolean writeToDiskLocked(final Map<String, Object> mapToWriteToDisk, final boolean changesMade) {
        try {
            if (mFile.exists()) {
                if (!changesMade) {
                    return true;
                }
                if (!mBackupFile.exists()) {
                    if (!mFile.renameTo(mBackupFile)) {
                        Log.e(TAG, "Couldn't rename file " + mFile
                              + " to backup file " + mBackupFile);
                        return false;
                    }
                } else {
                    mFile.delete();
                }
            }

            FileOutputStream str = createFileOutputStream(mFile);
            if (str == null) {
                return false;
            }

            XmlUtils.writeMapXml(mapToWriteToDisk, str);
            str.flush();
            str.getFD().sync();
            try {
                str.close();
            } catch(IOException e) {
                // Empty.
            }

            mFile.setReadable(true, false);
            try {
                final StructStat stat = Os.stat(mFile.getPath());
                synchronized (this) {
                    mStatTimestamp = stat.st_mtime;
                    mStatSize = stat.st_size;
                }
            } catch (ErrnoException e) {
                // Do nothing
            }

            // Writing was successful, delete the backup file if there is one.
            mBackupFile.delete();
            return true;
        } catch (XmlPullParserException e) {
            Log.w(TAG, "writeToFile: Got exception:", e);
        } catch (IOException e) {
            Log.w(TAG, "writeToFile: Got exception:", e);
        }

        // Clean up an unsuccessfully written file
        if (mFile.exists()) {
            if (!mFile.delete()) {
                Log.e(TAG, "Couldn't clean up partially-written file " + mFile);
            }
        }

        return false;
    }

    private static FileOutputStream createFileOutputStream(File file) {
        FileOutputStream str = null;
        try {
            str = new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            File parent = file.getParentFile();
            if (!parent.mkdir()) {
                Log.e(TAG, "Couldn't create directory for SharedPreferences file " + file);
                return null;
            }
            FileUtils.setPermissions(
                parent.getPath(),
                FileUtils.S_IRWXU|FileUtils.S_IRWXG|FileUtils.S_IXOTH,
                -1, -1);
            try {
                str = new FileOutputStream(file);
            } catch (FileNotFoundException e2) {
                Log.e(TAG, "Couldn't create SharedPreferences file " + file, e2);
            }
        }
        return str;
    }
} 
