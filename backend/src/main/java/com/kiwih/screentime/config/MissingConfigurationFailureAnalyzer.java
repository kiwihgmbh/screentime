package com.kiwih.screentime.config;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Turns a missing setting into the short block Spring Boot prints at the
 * bottom of a failed startup, instead of a stack trace nobody reads.
 */
public class MissingConfigurationFailureAnalyzer
        extends AbstractFailureAnalyzer<MissingConfigurationException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, MissingConfigurationException cause) {
        return new FailureAnalysis(cause.getMessage(), cause.getAction(), cause);
    }
}
