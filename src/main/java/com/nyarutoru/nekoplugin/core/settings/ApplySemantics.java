package com.nyarutoru.nekoplugin.core.settings;

/** Describes when a changed setting takes effect. */
public enum ApplySemantics {
    IMMEDIATE("Applies immediately"),
    FUTURE_ONLY("Affects future actions only"),
    RESCHEDULE("Reschedules related tasks"),
    RECIPE_REBUILD("Rebuilds feature recipes"),
    FEATURE_RESTART("Restarts the feature");

    private final String description;

    ApplySemantics(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
