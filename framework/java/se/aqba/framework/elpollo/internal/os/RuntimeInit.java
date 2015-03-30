package se.aqba.framework.elpollo;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import android.util.Log;

import se.aqba.framework.elpollo.ElPollo;

class RuntimeInit {
    public static void main(String[] args) {
        Log.d("ElPollo", "RuntimeInit");

        ElPollo.instantiate();

        com.android.internal.os.RuntimeInit.main(args);

        ElPollo.finish();
    }
}
