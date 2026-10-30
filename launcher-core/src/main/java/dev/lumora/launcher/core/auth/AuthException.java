package dev.lumora.launcher.core.auth;

/** Authentication failure with a message that is safe (and meant) to show to the user. */
public class AuthException extends Exception {
    private static final long serialVersionUID = 1L;

    public enum Reason {
        NOT_CONFIGURED, CANCELLED, EXPIRED, DECLINED, NETWORK, NO_XBOX_ACCOUNT, XBOX_REGION_BLOCKED,
        CHILD_ACCOUNT, XBOX_OTHER, APP_NOT_APPROVED, NOT_OWNED, NO_PROFILE, INVALID_RESPONSE, SESSION_INVALID
    }

    private final Reason reason;

    public AuthException(Reason reason, String userMessage) {
        super(userMessage);
        this.reason = reason;
    }

    public AuthException(Reason reason, String userMessage, Throwable cause) {
        super(userMessage, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
