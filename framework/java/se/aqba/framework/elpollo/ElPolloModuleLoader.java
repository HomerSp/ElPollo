package se.aqba.framework.elpollo;

import java.util.ArrayList;

/**
 * Class that's used for determining what classes the system should load as modules.<br/>
 * A module must extend this in a class called Loader under a java sub-package called elpollo.<br/>
 * For example, if your android package name is foo.bar.application, the class name must be foo.bar.application.elpollo.Loader.<br/>
 */
public abstract class ElPolloModuleLoader {
    private ArrayList<Class<? extends ElPolloModule>> mModules = new ArrayList<>();

    public ElPolloModuleLoader() {

    }

    /**
     * Add a new module class that should be loaded by the system.
     * @param module The class of the module that the system should load.
     * @see #main
     */
    public final void add(Class<? extends ElPolloModule> module) {
        mModules.add(module);
    }

    /**
     * Method that's called when the system wants to know what classes to load.<br/>
     * You should call {@link #add(Class)} from here for the classes that you want to load.
     */
    public abstract void main();

    /**
     * Get the list of module classes to load.<br/>
     * Internal use only.
     * @return The list of module classes to load.
     * @hide
     */
    final ArrayList<Class<? extends ElPolloModule>> modules() {
        return mModules;
    }
} 
