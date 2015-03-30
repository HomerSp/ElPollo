package se.aqba.framework.elpollo.helpers;

import android.util.Log;

import java.lang.String;
import java.lang.reflect.Field;

import se.aqba.framework.elpollo.ElPollo;

public final class ClassHelper {
    private static final String TAG = "ElPollo.ClassHelper";

    /**
     * Get a field value from a class object.
     *
     * @param obj The object to get the field value from, must not be null.
     * @param name Name of the field. May not be null.
     * @param def The default value used when name does not exist.
     * @return The field's current value.
     * @throws IllegalArgumentException When either obj or name are null.
     */
    public static <T> T getFieldValue(Object obj, String name, T def) throws IllegalArgumentException {
        if(obj == null) {
            throw new IllegalArgumentException("obj may not be null");
        }

        return getFieldValue(obj.getClass(), obj, name, def);
    }

    /**
     * Get a field value from a class object.
     *
     * @param klass The class from which to get the field value. May not be null.
     * @param obj The object to get the field value from. May be null if the field is static.
     * @param name Name of the field. May not be null.
     * @param def The default value used when name does not exist.
     * @return The field's current value.
     * @throws IllegalArgumentException When either klass or name are null.
     */
    public static <T> T getFieldValue(Class<?> klass, Object obj, String name, T def) throws IllegalArgumentException {
        if(klass == null) {
            throw new IllegalArgumentException("klass may not be null");
        }

        try {
            return getFieldValue(klass, obj, name);
        } catch(NoSuchFieldException e) {
            Log.e(TAG, "getFieldValue " + name, e);
        }

        return def;
    }

    /**
     * Get a field value from a class object.
     *
     * @param obj The object to get the field value from. May not be null.
     * @param name Name of the field. May not be null.
     * @return The field's current value.
     * @throws NoSuchFieldException When the field could not be found.
     */
    public static <T> T getFieldValue(Object obj, String name) throws NoSuchFieldException, IllegalArgumentException {
        if(obj == null) {
            throw new IllegalArgumentException("obj may not be null");
        }

        return getFieldValue(obj.getClass(), obj, name);
    }
    
    /**
     * Get a field value from a class object.
     *
     * @param klass The class from which to get the field value. May not be null.
     * @param obj The object to get the field value from. May be null if the field is static.
     * @param name Name of the field. May not be null.
     * @return The field's current value.
     * @throws NoSuchFieldException When the field could not be found.
     */
    @SuppressWarnings("unchecked")
    public static <T> T getFieldValue(Class<?> klass, Object obj, String name) throws NoSuchFieldException, IllegalArgumentException {
        if(klass == null) {
            throw new IllegalArgumentException("klass may not be null");
        }
        if(name == null) {
            throw new IllegalArgumentException("name may not be null");
        }

        try {
            Field field = klass.getDeclaredField(name);
            field.setAccessible(true);
            return (T)field.get(obj);
        } catch(IllegalAccessException e) {
            Log.e(TAG, "getFieldValue " + name, e);
        } catch(ClassCastException e) {
            Log.e(TAG, "getFieldValue " + name, e);
        }

        throw new NoSuchFieldException(name);
    }

    /**
     * Set a field's value on a class object.
     *
     * @param obj The instance object. May not be null.
     * @param name Name of the field. May not be null.
     * @param val The value that you want to set the field to.
     * @throws IllegalArgumentException When obj or name is null.
     */
    public static <T> void setFieldValue(Object obj, String name, T val) throws IllegalArgumentException {
        if(obj == null) {
            throw new IllegalArgumentException("obj may not be null");
        }

        setFieldValue(obj.getClass(), obj, name, val);
    }

    /**
     * Set a field's value on a class object.
     *
     * @param klass The class that contains the field. May not be null.
     * @param obj The instance object. May be null if the field is static.
     * @param name Name of the field. May not be null.
     * @param val The value that you want to set the field to.
     * @throws IllegalArgumentException When klass or name is null.
     */
    public static <T> void setFieldValue(Class<?> klass, Object obj, String name, T val) throws IllegalArgumentException {
        if(klass == null) {
            throw new IllegalArgumentException("klass may not be null");
        }
        if(name == null) {
            throw new IllegalArgumentException("name may not be null");
        }

        try {
            Field field = klass.getDeclaredField(name);
            field.setAccessible(true);
            if(val instanceof Boolean) {
                field.setBoolean(obj, ((Boolean)val).booleanValue());
            } else if(val instanceof Byte) {
                field.setByte(obj, ((Byte)val).byteValue());
            } else if(val instanceof Character) {
                field.setChar(obj, ((Character)val).charValue());
            } else if(val instanceof Double) {
                field.setDouble(obj, ((Double)val).doubleValue());
            } else if(val instanceof Float) {
                field.setFloat(obj, ((Float)val).floatValue());
            } else if(val instanceof Integer) {
                field.setInt(obj, ((Integer)val).intValue());
            } else if(val instanceof Long) {
                field.setLong(obj, ((Long)val).longValue());
            } else if(val instanceof Short) {
                field.setShort(obj, ((Short)val).shortValue());
            } else {
                field.set(obj, val);
            }
        } catch(NoSuchFieldException e) {
            Log.e(TAG, "getFieldValue " + name, e);
        } catch(IllegalAccessException e) {
            Log.e(TAG, "getFieldValue " + name, e);
        }
    }

    /**
     * Set an object's class.
     * Internal use only.
     *
     * @param obj The object whose class you want to change.
     * @param objClass The new class.
     * @hide
     */
    public static void setObjectClass(Object obj, Class<?> objClass) {
        ElPollo.setObjectClass(obj, objClass);
    }
}
