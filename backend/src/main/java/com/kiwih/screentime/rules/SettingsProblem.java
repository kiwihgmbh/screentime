package com.kiwih.screentime.rules;

import java.util.List;

/**
 * One rule a set of values breaks. {@code keys} are the fields involved, so the
 * parent screen can show the message under each of them.
 */
public record SettingsProblem(SettingsValidator.Rule rule, List<String> keys, String message) {

    public SettingsProblem {
        keys = List.copyOf(keys);
    }
}
