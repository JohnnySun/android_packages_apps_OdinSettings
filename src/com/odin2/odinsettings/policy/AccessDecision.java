package com.odin2.odinsettings.policy;

public final class AccessDecision {
    public final boolean allowed;
    public final String reason;

    private AccessDecision(boolean allowed, String reason) {
        this.allowed = allowed;
        this.reason = reason;
    }

    public static AccessDecision allow(String reason) {
        return new AccessDecision(true, reason);
    }

    public static AccessDecision deny(String reason) {
        return new AccessDecision(false, reason);
    }
}
