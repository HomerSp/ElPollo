package se.aqba.framework.elpollo;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.Preference;
import android.view.View;
import android.view.WindowManager;
import android.view.Window;
import android.util.Log;

import dalvik.system.PathClassLoader;

import java.io.File;
import java.lang.String;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import android.content.res.PResources;

import se.aqba.framework.elpollo.InternalLoaders;
import se.aqba.framework.elpollo.InternalNative;
import se.aqba.framework.elpollo.InternalModules;
import se.aqba.framework.elpollo.exception.*;
import se.aqba.framework.elpollo.helpers.MethodHelper;

/**
 * Class that contains various classes and methods to add method overrides and replace resources.<br/>
 * <br/>
 * <br/>
 * To find out how to load your override classes, look at {@link ElPolloModuleLoader} and {@link ElPolloModule}.<br/>
 * For adding overrides, have a look at {@link ElPollo.Packages#addListener(Listener, String...)}, {@link ElPollo.MemberOverride} and {@link ElPollo.Overrides}.<br/>
 * Adding resource replacements can be done using {@link ElPollo.Resources#addListener(Listener, String...)} and the various addReplacement methods.
 */
public final class ElPollo {
    private static final String TAG = "ElPollo";

    private static final String ELPOLLO_VERSION = "0.3";
    private static final int ELPOLLO_VERSION_CODE = 3;
    private static final String ELPOLLO_VERSION_CODENAME = "Skylar";

    private static ElPollo sInstance;

    static void finish() {
        if(sInstance != null) {
            sInstance.destroy();
            sInstance = null;
        }
    }

    static void instantiate() {
        if(sInstance == null) {
            sInstance = new ElPollo();
            sInstance.init();
        }
    }

    private InternalModules mModules;
    private InternalLoaders mLoaders;

    private ElPollo() {
        
    }

    private void init() {
        InternalNative.init();

        mModules = new InternalModules();

        mLoaders = new InternalLoaders();
        mLoaders.init();
    }

    private void destroy() {
        mModules.destroy();
        mLoaders.destroy();

        InternalNative.destroy();
    }


    /**
     * Get the current version name.
     *
     * @return The version name.
     */
    public static String getVersion() {
        return ELPOLLO_VERSION;
    }

    /**
     * Get the current version code.
     *
     * @return The version code.
     */
    public static int getVersionCode() {
        return ELPOLLO_VERSION_CODE;
    }

    /**
     * Get the current version codename.
     *
     * @return The version codename.
     */
    public static String getVersionCodename() {
        return ELPOLLO_VERSION_CODENAME;
    }

    /**
     * Class used for accessing various package related classes and methods.
     */
    public static class Packages {
        /**
         * The system framework.
         * @see ElPollo.Packages.Listener#onLoad(String, ClassLoader)
         */
        public static final String SYSTEM = "system";

        /**
         * The server framework.
         * @see ElPollo.Packages.Listener#onLoad(String, ClassLoader)
         */
        public static final String SERVER = "system_server";

        /**
         * Add a new package load listener.
         *
         * @param listener The listener is called when a new package is loaded.
         * @param pkgs Variable list of packages to listen for, <br/>
         *      may be empty to listen for every package (this is strongly discouraged).
         */
        public static void addListener(Listener listener, String... pkgs) {
            sInstance.mLoaders.addPackageListener(listener, pkgs);
        }

        /**
         * A package load listener.
         * @see #addListener(Listener, String...)
         */
        public static interface Listener {
            /**
             * Called when a package is loaded.<br/>
             * Any overrides you want to add to the package must be done from this callback.
             *
             * @param pkg The name of the package.
             *      Can be one of {@link ElPollo.Packages#SYSTEM}, {@link ElPollo.Packages#SERVER} or an installed package. 
             * @param classLoader The class loader used for loading the package.
             * @see ElPollo.Overrides
             */
            public void onLoad(String pkg, ClassLoader classLoader);
        }
    }

    /**
     * Class used for accessing various resource related classes and methods.
     */
    public static class Resources {
        /**
         * The system framework resources.
         * @see ElPollo.Resources.Listener#onLoad(String, PResources)
         */
        public static final String SYSTEM = "android";

        /**
         * Add a new resource listener that's called when an application's resources are loaded.
         *
         * @param listener The listener is called when a package's resources are loaded.
         * @param pkgs Variable list of packages to listen for,<br/>
         *      may be empty to listen for every package (this is strongly discouraged).
         */
        public static void addListener(Listener listener, String... pkgs) {
            sInstance.mLoaders.addResourceListener(listener, pkgs);
        }

        /**
         * Add a new system-wide framework resource replacement.
         *
         * @param name The name of the resource.
         * @param type The type of the resource.
         * @param replacement May be a forwarder or an object pertaining to <code>type</code>.
         * @see ElPollo.Resources.Forwarder
         */
        public static void addReplacement(String name, String type, Object replacement) {
            addReplacement("android", name, type, null, replacement);
        }

        /**
         * Add a new resource replacement.
         *
         * @param pkg The package that the resource belongs to.
         * @param name The name of the resource.
         * @param type The type of the resource.
         * @param replacement May be a forwarder or an object pertaining to <code>type</code>.
         * @see ElPollo.Resources.Forwarder
         */
        public static void addReplacement(String pkg, String name, String type, PResources res, Object replacement) {
            if(res == null) {
                res = PResources.getSystem();
            }

            int id = res.getIdentifier(name, type, pkg);
            addReplacement(pkg, id, replacement);
        }

        /**
         * Add a new system-wide framework resource replacement.
         *
         * @param id The id of the resource you want to replace.
         * @param replacement May be a forwarder or an object pertaining to the resource type.
         * @see ElPollo.Resources.Forwarder
         */
        public static void addReplacement(int id, Object replacement) {
            addReplacement("android", id, replacement);
        }

        /**
         * Add a new resource replacement.
         *
         * @param pkg The package whose resource you want to replace.
         * @param id The id of the resource you want to replace.
         * @param replacement May be a forwarder or an object pertaining to the resource type.
         * @see ElPollo.Resources.Forwarder
         */
        public static void addReplacement(String pkg, int id, Object replacement) {
            PResources.addReplacement(pkg, id, replacement);
        }

        /**
         * A package resource load listener.
         * @see #addListener(Listener, String...)
         */
        public static interface Listener {
            /**
             * Called when a package's resources are loaded.<br/>
             * Any resource replacements you want to do have to be done from this callback.
             *
             * @param pkg The name of the package.
             *      Can be {@link ElPollo.Resources#SYSTEM} or an installed package.
             * @param res The resources belonging to the package.
             */
            public void onLoad(String pkg, PResources res);
        } 

        /**
         * A resource forwarder.
         * @see ElPollo.Resources
         */
        public static abstract class Forwarder {
            private PResources.ResType mType;

            /**
             * Listener function used when forwarding a resource.
             *
             * @return The forwarded resource.
             */
            public abstract Object get();

            /**
             * Get the type of the forwarded resource.
             *
             * @return Returns the current resource type.
             * @see android.content.res.PResources.ResType
             */
            public final PResources.ResType getType() {
                return mType;
            }

            /**
             * Sets the type of the forwarded resource.<br/>
             * Internal use only.
             *
             * @param type The resource type.
             * @see android.content.res.PResources.ResType
             * @hide
             */
            public final void setType(PResources.ResType type) {
                mType = type;
            }
        }

        /**
         * A drawable resource forwarder.
         * @see ElPollo.Resources
         */
        public static abstract class DrawableForwarder extends Forwarder {
            /**
             * Instances should override {@link #get(int, int, android.content.res.Resources.Theme)}.
             */
            @Override
            public final Object get() {
                return null;
            }

            /**
             * Listener function used when forwarding a drawable.
             *
             * @param id Id of the original drawable.
             * @param density The density the drawable should be loaded at.
             * @param theme The theme used for loading the drawable.
             * @return The forwarded drawable object.
             */
            public abstract Object get(int id, int density, android.content.res.Resources.Theme theme);
        }

        /**
         * A view forwarder.
         * @see ElPollo.Resources
         */
        public static abstract class ViewForwarder extends Forwarder {
            /**
             * Instances should override {@link #get(int, View)}.
             */
            @Override
            public final Object get() {
                return null;
            }

            /**
             * Listener function used when forwarding an inflated view.
             *
             * @param id Id of the original inflated view.
             * @param view The inflated view.
             * @return The forwarded layout view, returning null will user the original view.
             */
            public abstract View get(int id, View view);
        }
    }

    /**
     * Used for adding new member overrides to the system.
     */
    public static class Overrides {
        /**
         * Add a new override to the system.
         *
         * @param override A MemberOverride object specifying the source, override.<br/>
         *      The override object must be an instance of <code>OverrideCustom</code>.
         * @param matches When this is specified <code>override</code> will only be called when<br/>
         *      the method that calls <code>source</code> matches one of these items.<br/>
         *      can be one of method, class name or java package.<br/>
         *      Keep empty to not do any match checking.<br/>
         *<br/><br/>
         *      <code>Examples:<br/>
         *      foo.bar.ClassName.func - must originate from method function in foo.bar.ClassName.<br/>
         *      foo.bar.ClassName - call must originate from class foo.bar.ClassName.<br/>
         *      foo.bar - must originate from java package foo.bar.<br/>
         *      foo.*   - must originate from any package, class or function under foo.</code>
         * @return Returns the MemberOverride object.
         * @see ElPollo.MemberOverride
         */
        public static MemberOverride add(MemberOverride override, String... matches) {
            for(Map.Entry<Member, Method> entry: override.getMembers().entrySet()) {
                InternalNative.addOverride(override.getOverrideThis(), entry.getKey(), entry.getValue(), matches);
            }

            return override;
        }

        /**
         * Remove a previously added override
         *
         * @param override The override object used when adding the override.
         */
        public static void remove(MemberOverride override) {
            for(Map.Entry<Member, Method> entry: override.getMembers().entrySet()) {
                InternalNative.removeOverride(override.getOverrideThis(), entry.getKey());
            }
        }
    }

    /**
     * Object referring to a member override.<br/>
     * Cannot be instantiated directly, please use {@link MemberOverride.Builder}.
     *
     * @see MemberOverride.Builder
     * @see ElPollo.Overrides
     */
    public static class MemberOverride {
        private static class BuilderParams {
            public ClassLoader classLoader;

            public Member source;
            public String sourceClassName;
            public Class<?> sourceClass;
            public String sourceName;

            public Method override;
            public Class<?> overrideClass;
            public String overrideName;
            
            public Object overrideThis;

            public Object[] types = new Object[0];

            private boolean all = false;
        }

        // Map of source and override methods.
        private HashMap<Member, Method> mMethods = new HashMap<>();
        private Object mOverrideThis = null;

        private MemberOverride(BuilderParams params) throws OverrideException {
            init(params);
        }

        /**
         * Get the list of member sources and overrides.
         *
         * @return The member sources and overrides.
         */
        public HashMap<Member, Method> getMembers() {
            return mMethods;
        }

        /**
         * Get the override this object.
         *
         * @return The override this object.
         */
        public Object getOverrideThis() {
            return mOverrideThis;
        }

        private Class<?>[] getParamTypes(ClassLoader classLoader, Object... paramTypes) throws OverrideException {
            Class<?> paramClasses[] = new Class<?>[paramTypes.length];
            for(int i = 0; i < paramTypes.length; i++) {
                Object type = paramTypes[i];
                if(type instanceof Class) {
                    paramClasses[i] = (Class<?>)type;
                } else if(type instanceof String) {
                    try {
                        paramClasses[i] = Class.forName((String)type, false, classLoader);
                    } catch(ClassNotFoundException e) {
                        throw new OverrideException("Could not load class " + type.toString(), e);
                    }
                } else {
                    throw new OverrideException("paramTypes can only consist of classes and strings");
                }
            }

            return paramClasses;
        }

        private List<Member> getMembers(Class<?> sourceClass, String sourceName, boolean all, Class<?>[] paramClasses) throws OverrideException {
            List<Member> ret = new ArrayList<>();

            try {
                if(sourceName != null) {
                    if(all) {
                        for(Method member: sourceClass.getDeclaredMethods()) {
                            if(member.getName().equals(sourceName)) {
                                ret.add(member);
                            }
                        }
                    } else {
                        ret.add(sourceClass.getDeclaredMethod(sourceName, paramClasses));
                    }
                } else {
                    if(all) {
                        for(Constructor<?> member: sourceClass.getDeclaredConstructors()) {
                            ret.add(member);
                        }
                    } else {
                        ret.add(sourceClass.getDeclaredConstructor(paramClasses));
                    }
                }
            } catch(NoSuchMethodException | IllegalArgumentException e) {
                throw(new OverrideException("Could not find member " + sourceName + " in class " + sourceClass.getName(), e));
            }

            return ret;
        }

        private void init(final BuilderParams params) throws OverrideException {
            Class<?> paramClasses[] = getParamTypes(params.classLoader, params.types);

            Class<?> sourceClass = params.sourceClass;

            List<Member> sources = null;
            if(params.source == null) {
                if(params.sourceClassName != null) {
                    try {
                        sourceClass = Class.forName(params.sourceClassName, false, params.classLoader);
                    } catch(ClassNotFoundException e) {
                        throw new OverrideException("Could not load the source class " + params.sourceClassName, e);
                    }
                }

                if(sourceClass == null && params.sourceName == null) {
                    throw new OverrideException("The source class and name may not be null");
                }

                sources = getMembers(sourceClass, params.sourceName, params.all, paramClasses);
            } else {
                sources = new ArrayList<>();
                sources.add(params.source);
            }

            if(sources == null) {
                throw new OverrideException("Could not find the source method");
            }

            if(params.override == null) {
                Class<?> overrideClass = null;
                if(params.overrideClass != null) {
                    overrideClass = params.overrideClass;
                } else if(params.overrideThis != null) {
                    overrideClass = params.overrideThis.getClass();
                }
                
                if(overrideClass == null) {
                    throw new OverrideException("Could not get the override class");
                }

                String overrideName = params.overrideName;
                if(overrideName == null && params.overrideThis != null && params.overrideThis instanceof OverrideCustom) {
                    overrideName = "call";
                }

                if(overrideName == null) {
                    throw new OverrideException("The override method name may not be null");
                }

                for(Member source: sources) {
                    Class<?> sourceTypeClasses[];
                    if(source instanceof Method) {
                        sourceTypeClasses = ((Method)source).getParameterTypes();
                    } else {
                        sourceTypeClasses = ((Constructor)source).getParameterTypes();
                    }

                    Method override = null;
                    if(OverrideCall.class.isAssignableFrom(overrideClass)) {
                        for(Method method: overrideClass.getDeclaredMethods()) {
                            if(!method.getName().equals(overrideName) || method.isSynthetic()) {
                                continue;
                            }

                            Class<?> overrideTypeClasses[] = method.getParameterTypes();
                            if(overrideTypeClasses.length != 2) {
                                continue;
                            }

                            boolean matches = false;
                            if((Modifier.isStatic(source.getModifiers()) && overrideTypeClasses[0] == Class.class) || overrideTypeClasses[0].isAssignableFrom(sourceClass)) {
                                if(overrideTypeClasses[1].isAssignableFrom(Object[].class)) {
                                    matches = true;
                                }
                            }

                            if(matches) {
                                override = method;
                                break;
                            }
                        }
                    } else {
                        for(Method method: overrideClass.getDeclaredMethods()) {
                            if(!method.getName().equals(overrideName) || method.isSynthetic()) {
                                continue;
                            }

                            Class<?> overrideTypeClasses[] = method.getParameterTypes();
                            if(sourceTypeClasses.length != overrideTypeClasses.length - 1) {
                                continue;
                            }

                            boolean matches = false;
                            if((Modifier.isStatic(source.getModifiers()) && overrideTypeClasses[0] == Class.class) || overrideTypeClasses[0].isAssignableFrom(sourceClass)) {
                                if(sourceTypeClasses.length == 0) {
                                    matches = true;
                                }

                                for(int i = 0; i < sourceTypeClasses.length; i++) {
                                    if(!overrideTypeClasses[i + 1].isAssignableFrom(sourceTypeClasses[i])) {
                                        break;
                                    }

                                    if(i == sourceTypeClasses.length - 1) {
                                        matches = true;
                                    }
                                }
                            }

                            if(matches) {
                                override = method;
                                break;
                            }
                        }
                    }

                    if(override == null) {
                        throw new OverrideException("Could not find override " + overrideName + " in class " + overrideClass.getName());
                    }

                    mMethods.put(source, override);
                }
            } else {
                for(Member source: sources) {
                    mMethods.put(source, params.override);
                }
            }

            mOverrideThis = params.overrideThis;

            for(Map.Entry<Member, Method> entry: mMethods.entrySet()) {
                Method override = (Method)entry.getValue();

                if(mOverrideThis == null && !Modifier.isStatic(override.getModifiers())) {
                    throw new OverrideException("The override method " + override.getName() + " in class " + override.getClass().getName() + " must be declared static");
                }
            }
        }

        /**
         * Class that's used for building a {@link MemberOverride} object for use with {@link ElPollo.Overrides#add(ElPollo.MemberOverride, String...)}.<br/>
         * @see ElPollo.Overrides
         */
        public static class Builder {
            private BuilderParams mParams = new BuilderParams();

            public Builder() {

            }

            public Builder(ClassLoader classLoader) {
                mParams.classLoader = classLoader;
            }

            /**
             * Create the member override object.
             * @return The member override object.
             * @throws OverrideException When the source or override could not be found.
             */
            public MemberOverride create() throws OverrideException {
                return new MemberOverride(mParams);
            }

            /**
             * Set the source to the constructor of <code>sourceClass</code>, matching {@link #setTypes(Object...)}.
             * @param sourceClass The class from where to find the constructor.
             * @return The {@link Builder} object, so you can link calls.
             */
            public Builder setSource(Class<?> sourceClass) {
                mParams.source = null;
                mParams.sourceClassName = null;
                mParams.sourceClass = sourceClass;
                mParams.sourceName = null;

                return this;
            }

            /**
             * Set the source to the constructor of <code>sourceClassName</code>, matching {@link #setTypes(Object...)}.
             * @param sourceClassName The class name from where to find the constructor, this will be looked up using the class loader.
             * @return The {@link Builder} object, so you can link calls.
             */
            public Builder setSource(String sourceClassName) {
                mParams.source = null;
                mParams.sourceClassName = sourceClassName;
                mParams.sourceClass = null;
                mParams.sourceName = null;

                return this;
            }

            /**
             * Set the source to the method <code>sourceName</code> in <code>sourceClass</code>, matching {@link #setTypes(Object...)}.
             * @param sourceClass The class from where to find the method.
             * @param sourceName The method name.
             * @return The {@link Builder} object, so you can link calls.
             */
            public Builder setSource(Class<?> sourceClass, String sourceName) {
                mParams.source = null;
                mParams.sourceClassName = null;
                mParams.sourceClass = sourceClass;
                mParams.sourceName = sourceName;

                return this;
            }

            /**
             * Set the source to the method <code>sourceName</code> in <code>sourceClass</code>, matching {@link #setTypes(Object...)}.
             * @param sourceClassName The class from where to find the method, this will be looked up using the class loader.
             * @param sourceName The source method name.
             * @return The {@link Builder} object, so you can link calls.
             */
            public Builder setSource(String sourceClassName, String sourceName) {
                mParams.source = null;
                mParams.sourceClassName = sourceClassName;
                mParams.sourceClass = null;
                mParams.sourceName = sourceName;

                return this;
            }

            /**
             * Set the source to the member <code>source</code>, this can be a constructor or a method.
             * @param source The source member.
             * @return The {@link Builder} object, so you can link calls.
             */
            public Builder setSource(Member source) {
                mParams.source = source;
                mParams.sourceClassName = null;
                mParams.sourceClass = null;
                mParams.sourceName = null;

                return this;
            }

            /**
             * Set the override to the static method <code>overrideName</code> in <code>overrideClass</code>, matching {@link #setTypes(Object...)}.<br/>
             * The fist argument of the override method must be a reference to the source object.
             * @param overrideClass The class from where to find the method.
             * @param overrideName The method name.
             * @return The {@link Builder} object, so you can link calls.
             */
            public Builder setTarget(Class<?> overrideClass, String overrideName) {
                mParams.override = null;
                mParams.overrideClass = overrideClass;
                mParams.overrideName = overrideName;
                mParams.overrideThis = null;

                return this;
            }

            /**
             * Set the override to the method <code>overrideName</code> in the class of <code>overrideThis</code>, matching {@link #setTypes(Object...)}.<br/>
             * The fist argument of the override method must be a reference to the source object.
             * @param overrideThis The object that will be used for finding the method.
             * @param overrideName The method name.
             * @return The {@link Builder} object, so you can link calls.
             */
            public Builder setTarget(ElPolloModule overrideThis, String overrideName) {
                mParams.override = null;
                mParams.overrideClass = null;
                mParams.overrideName = overrideName;
                mParams.overrideThis = overrideThis;

                return this;
            }

            /**
             * Set the override to the method <code>call</code> in the class of <code>overrideObj</code>, matching {@link #setTypes(Object...)}.<br/>
             * The fist argument of the override method must be a reference to the source object.
             * @param overrideObj The call method of this object will be used as the override.
             * @return The {@link Builder} object, so you can link calls.
             * @see ElPollo.OverrideCustom
             */
            public Builder setTarget(ElPollo.OverrideCustom<?,?> overrideObj) {
                mParams.override = null;
                mParams.overrideClass = null;
                mParams.overrideName = null;
                mParams.overrideThis = overrideObj;

                return this;
            }

            /**
             * Set the override to the static method <code>override</code>.<br/>
             * The fist argument of the override method must be a reference to the source object.
             * @param override The override method.
             * @return The {@link Builder} object, so you can link calls.
             */
            public Builder setTarget(Method override) {
                mParams.override = override;
                mParams.overrideClass = null;
                mParams.overrideName = null;
                mParams.overrideThis = null;

                return this;
            }

            /**
             * Set the override to the method <code>override</code> of the class of <code>overrideThis</code>.<br/>
             * The fist argument of the override method must be a reference to the source object.
             * @param override The override method.
             * @return The {@link Builder} object, so you can link calls.
             */
            public Builder setTarget(Object overrideThis, Method override) {
                mParams.override = override;
                mParams.overrideClass = null;
                mParams.overrideName = null;
                mParams.overrideThis = overrideThis;

                return this;
            }

            /**
             * Set the source member types.
             * @param types Variable list of classes used for finding the source and override methods.<br/>
             *      If specified as a string it will be loaded from the class loader.
             * @return The {@link Builder} object, so you can link calls.
             */
            public Builder setTypes(Object... types) {
                mParams.types = new Object[types.length];
                System.arraycopy(types, 0, mParams.types, 0, types.length);

                return this;
            }

            /**
             * Set whether we should override all found members.
             * @param all The new state.
             * @return The {@link Builder} object, so you can link calls.
             */
            public Builder setAll(boolean all) {
                mParams.all = all;

                return this;
            }
        }
    }

    /**
     * Used as an alternative to implement a custom method in the override class.<br/>
     * Instances of this object MUST implement a call method matching the original method.<br/>
     *<br/>
     * <code>Example:<br/>
     *      public void call(Object obj, int arg1, String arg2)</code>
     *
     * @see MemberOverride
     * @see OverrideCall
     */
    public static abstract class OverrideCustom<ObjType,ReturnType> {
        /**
         * Call the original method.
         *
         * @see helpers.MethodHelper#callOriginal(Object, Object...)
         */
        @SuppressWarnings("unchecked")
        public final ReturnType callOriginal(ObjType obj, Object... args) {
            Object ret = MethodHelper.callOriginal(obj, args);

            try {
                return (ReturnType)ret;
            } catch(ClassCastException e) {
                Log.e(TAG, "Wrong return type?", e);
            }

            return null;
        }
    }

    /**
     * An alternative of {@link OverrideCustom} that implements a 
     *
     * @see OverrideCustom
     */
    public static abstract class OverrideCall<ObjType,ReturnType> extends OverrideCustom<ObjType,ReturnType> {
        public abstract ReturnType call(ObjType obj, Object... args);
    }

    /**
     * Get an instance of the internal modules class.
     * Internal use only.
     *
     * @hide
     */
    static InternalModules getModules() {
        return sInstance.mModules;
    }

    /**
     * Get the boot and server class loader.
     * Internal use only.
     *
     * @hide
     */
    static ClassLoader getClassLoader() {
        return sInstance.mLoaders.getClassLoader();
    }

    /**
     * Get the shared preferences.
     * Internal use only.
     *
     * @see ModuleHelper
     * @hide
     */
    public static SharedPreferences getPreferences(File file) {
        return new InternalModulePreferencesImpl(file);
    }

    /**
     * Call the original method.
     * Internal use only.
     *
     * @see MethodHelper
     * @hide
     */
    public static Object callOriginal(Object pThis, Object[] args) {
        return InternalNative.callOriginal(pThis, args);
    }

    /**
     * Call the super method.
     * Internal use only.
     *
     * @see MethodHelper
     * @hide
     */
    public static Object callSuper(Object pThis, Object[] args) {
        return InternalNative.callSuper(pThis, args);
    }

    /**
     * Call a method.
     * Internal use only.
     *
     * @see MethodHelper
     * @hide
     */
    public static Object callMethod(Object pThis, String name, Object[] args) {
        return InternalNative.callMethod(pThis, name, args);
    }

    /**
     * Set an object's class.
     * Internal use only.
     *
     * @see helpers.ClassHelper
     * @hide
     */
    public static void setObjectClass(Object obj, Class<?> objClass) {
        InternalNative.setObjectClass(obj, objClass);
    }
}
