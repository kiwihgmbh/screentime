package com.kiwih.screentime.config;

/**
 * The deployment has not supplied something the application cannot invent.
 *
 * Carries the action to take as well as the problem, so the failure analyzer
 * can print something a person can act on instead of a stack trace.
 */
public class MissingConfigurationException extends RuntimeException {

    private final String action;

    public MissingConfigurationException(String problem, String action) {
        super(problem);
        this.action = action;
    }

    public String getAction() {
        return action;
    }
}
