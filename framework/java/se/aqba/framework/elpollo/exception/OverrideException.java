package se.aqba.framework.elpollo.exception;

/**
 * Thrown when the member override, for some reason, could not be loaded.
 * @see se.aqba.framework.elpollo.ElPollo.MemberOverride
 */
public class OverrideException extends Exception {
    public OverrideException(String msg) {
        super(msg);
    }
    public OverrideException(String msg, Throwable tr) {
        super(msg, tr);
    }
    public OverrideException(Throwable tr) {
        super(tr);
    }
}
