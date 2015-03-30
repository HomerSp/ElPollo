package se.aqba.framework.elpollo.exception;

/**
 * Thrown when there is a problem loading shared preferences for a module.
 * @see se.aqba.framework.elpollo.helpers.MethodHelper
 */
public class ModuleException extends Exception {
    public ModuleException(String msg) {
        super(msg);
    }
    public ModuleException(String msg, Throwable tr) {
        super(msg, tr);
    }
    public ModuleException(Throwable tr) {
        super(tr);
    }
}
