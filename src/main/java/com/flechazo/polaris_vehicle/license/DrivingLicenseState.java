package com.flechazo.polaris_vehicle.license;

/** What the licence card shows in its grade slot. */
public enum DrivingLicenseState {
    ACTIVE,
    REVOKED,
    /** Issued but never opened by its holder. */
    INACTIVE
}
