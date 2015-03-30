package se.aqba.framework.elpollo.helpers;

import android.util.Log;

import java.lang.String;

import se.aqba.framework.elpollo.ElPollo;

public final class MethodHelper {
    private static final String TAG = "ElPollo.MethodHelper";

    /**
     * Call the original method. May only be used from inside an override method.
     *
     * @param pThis This object, should be the first parameter passed to the override method.
     * @param args List of arguments that should be passed to the original method,
     *      the number of arguments must match.
     * @return What was returned from the original method.
     */
    @SuppressWarnings("unchecked")
    public static <T> T callOriginal(Object pThis, Object... args) {
        try {
            return (T)ElPollo.callOriginal(pThis, args);
        } catch(ClassCastException e) {
            Log.e(TAG, "callOriginal", e);
        }

        return null;
    }

    /**
     * Call the original method, which must be static. May only be used from inside an override method.
     *
     * @param args List of arguments that should be passed to the original method,
     *      the number of arguments must match.
     * @return What was returned from the original method.
     */
    @SuppressWarnings("unchecked")
    public static <T> T callOriginalStatic(Object... args) {
        try {
            return (T)ElPollo.callOriginal(null, args);
        } catch(ClassCastException e) {
            Log.e(TAG, "callOriginal", e);
        }

        return null;
    }

    /**
     * Call a super method of the original method. May only be used from inside an override method.
     *
     * @param pThis This object, should be the first parameter passed to the override method.
     * @param args List of classes and arguments that should be passed to the super method,
     *      for example:
     *      Integer.TYPE, 10, String.class, "Test".
     * @return What was returned from the super method.
     */
    @SuppressWarnings("unchecked")
    public static <T> T callSuper(Object pThis, Object... args) {
        try {
            return (T)ElPollo.callSuper(pThis, args);
        } catch(ClassCastException e) {
            Log.e(TAG, "callSuper", e);
        }

        return null;
    }

    /**
     * Call a method on the specified object.
     *
     * @param pThis This object, will also be used for resolving the method.
     * @param name Name of the method to call.
     * @param args List of classes and arguments that should be passed to the method.
     *      The last argument must refer to the return type, or can be omitted if it's void.
     *      for example:
     *      Integer.TYPE, 10, String.class, "Test", Integer.TYPE.
     * @return What was returned from the method.
     */
    @SuppressWarnings("unchecked")
    public static <T> T callMethod(Object pThis, String name, Object... args){
        try {
            return (T)ElPollo.callMethod(pThis, name, args);
        } catch(ClassCastException e) {
            Log.e(TAG, "callMethod", e);
        }

        return null;
    }
}
