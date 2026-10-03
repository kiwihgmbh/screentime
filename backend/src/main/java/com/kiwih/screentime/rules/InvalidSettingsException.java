package com.kiwih.screentime.rules;

import java.util.List;
import java.util.stream.Collectors;

/** A settings change refused because the values do not hold together. Maps to 400. */
public class InvalidSettingsException extends RuleViolation {

    private final List<SettingsProblem> problems;

    public InvalidSettingsException(List<SettingsProblem> problems) {
        super(Kind.INVALID, problems.stream().map(SettingsProblem::message).collect(Collectors.joining(" ")));
        this.problems = List.copyOf(problems);
    }

    public List<SettingsProblem> problems() {
        return problems;
    }
}
