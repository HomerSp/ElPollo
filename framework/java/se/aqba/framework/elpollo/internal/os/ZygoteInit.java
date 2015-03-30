package se.aqba.framework.elpollo;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import android.util.Log;

import se.aqba.framework.elpollo.ElPollo;

class ZygoteInit {
    public static void main(String[] args) {
        Log.d("ElPollo", "ZygoteInit");

        ElPollo.instantiate();

        com.android.internal.os.ZygoteInit.main(args);

        ElPollo.finish();
    }
}
