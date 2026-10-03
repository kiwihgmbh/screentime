package com.kiwih.screentime.rules;

/**
 * Which value set a setting belongs to. Every rule value exists once for term
 * time and once for the holidays. GLOBAL holds what decides between the two,
 * which therefore cannot belong to either.
 */
public enum SettingsScope {
    TERM("Term values"),
    HOLIDAY("Holiday values"),
    GLOBAL("General");

    private final String label;

    SettingsScope(String label) {
        this.label = label;
    }

    /** How the parent screen and the error messages name this set. */
    public String label() {
        return label;
    }
}
