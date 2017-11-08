package com.kaltura.dtg;

import android.content.Context;
import android.util.Log;

import java.lang.reflect.Field;

public class AppBuildConfig {
    public static boolean DEBUG;

    public static Object get(Context context, String fieldName) {
        String className = context.getPackageName() + ".BuildConfig";
        try {
            Class<?> clazz = Class.forName(className);
            Field field = clazz.getField(fieldName);
            return field.get(null);
        } catch (ClassNotFoundException e) {
            Log.e("AppBuildConfig", "Could not find class " + className + ". "
                    + "Setting AppBuildConfig.DEBUG to false");
            return null;
        } catch (Exception e) {
            Log.e("AppBuildConfig", "Error", e);
            return null;
        }
    }

    public static void init(Context context) {
        Boolean debug = (Boolean) get(context, "DEBUG");
        DEBUG = debug != null ? debug : false;
    }
}
