package se.aqba.framework.elpollo;

import java.util.List;

/**
 * The base module class, override classes must implement this.
 * @see ElPolloModuleLoader
 */
public abstract class ElPolloModule {
    public ElPolloModule() {

    }

    /**
     * Called when the module is loaded. This is where package and resource listeners should be registered.
     * @see ElPollo.Packages
     * @see ElPollo.Resources
     */
    public abstract void main();

    /**
     * Called when the module is destroyed.
     */
    public abstract void destroy();

    /**
     * Instances can call this method from {@link #destroy} to unregister all overrides.
     */
    final public void destroy(List<ElPollo.MemberOverride> overrides) {
        for(ElPollo.MemberOverride override: overrides ) {
            ElPollo.Overrides.remove(override);
        }

        overrides.clear();
    }
} 
