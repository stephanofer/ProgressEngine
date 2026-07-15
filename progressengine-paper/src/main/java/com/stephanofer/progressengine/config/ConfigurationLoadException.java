package com.stephanofer.progressengine.config;

import java.util.List;

public final class ConfigurationLoadException extends Exception {
    private final List<ConfigurationProblem> problems;

    public ConfigurationLoadException(List<ConfigurationProblem> problems) {
        super(format(problems));
        this.problems = List.copyOf(problems);
    }

    public ConfigurationLoadException(ConfigurationProblem problem, Throwable cause) {
        super(problem.path() + ": " + problem.message(), cause);
        this.problems = List.of(problem);
    }

    public List<ConfigurationProblem> problems() {
        return this.problems;
    }

    private static String format(List<ConfigurationProblem> problems) {
        if (problems == null || problems.isEmpty()) {
            return "Configuration is invalid";
        }
        return problems.stream()
            .map(problem -> problem.path() + ": " + problem.message())
            .reduce((left, right) -> left + "; " + right)
            .orElse("Configuration is invalid");
    }
}
