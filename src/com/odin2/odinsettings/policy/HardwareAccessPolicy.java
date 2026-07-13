package com.odin2.odinsettings.policy;

public final class HardwareAccessPolicy {
    public AccessDecision evaluate(DeviceIdentity identity) {
        boolean recognizedModel = "Odin2_Mini".equals(identity.model)
                || "Odin2 Mini".equals(identity.model);
        if (!recognizedModel) {
            return AccessDecision.deny("Unrecognized model");
        }
        boolean stockIdentity = "kalama".equals(identity.device)
                && "kalama".equals(identity.product);
        boolean lineageIdentity = "odin2_mini".equals(identity.device)
                && "lineage_odin2_mini".equals(identity.product);
        if (!stockIdentity && !lineageIdentity) {
            return AccessDecision.deny("Unexpected device or product identity");
        }
        if (!"QCS8550".equalsIgnoreCase(identity.socModel)) {
            return AccessDecision.deny("Unexpected SoC identity");
        }
        return AccessDecision.allow("Recognized Odin2 Mini identity");
    }
}
