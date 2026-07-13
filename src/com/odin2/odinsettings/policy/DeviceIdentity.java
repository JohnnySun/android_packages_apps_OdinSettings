package com.odin2.odinsettings.policy;

public final class DeviceIdentity {
    public final String model;
    public final String device;
    public final String product;
    public final String socModel;

    public DeviceIdentity(String model, String device, String product, String socModel) {
        this.model = normalize(model);
        this.device = normalize(device);
        this.product = normalize(product);
        this.socModel = normalize(socModel);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
