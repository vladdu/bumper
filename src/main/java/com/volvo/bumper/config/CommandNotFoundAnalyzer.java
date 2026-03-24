package com.volvo.bumper.config;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.shell.core.command.CommandNotFoundException;

/**
 * Replaces Spring Boot's raw stack trace with a friendly message when an unknown command is
 * entered.
 */
public class CommandNotFoundAnalyzer extends AbstractFailureAnalyzer<CommandNotFoundException> {

  @Override
  protected FailureAnalysis analyze(Throwable rootFailure, CommandNotFoundException cause) {
    String commandName = cause.getCommandName();
    String description =
        commandName != null && !commandName.isBlank()
            ? "Unknown command: '" + commandName + "'"
            : "An unknown command was entered.";
    return new FailureAnalysis(
        description, "Run 'bumper help' to see a list of available commands.", cause);
  }
}
