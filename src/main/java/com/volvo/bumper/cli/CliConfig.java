package com.volvo.bumper.cli;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.shell.core.NonInteractiveShellRunner;
import org.springframework.shell.core.ShellRunner;
import org.springframework.shell.core.command.CommandNotFoundException;
import org.springframework.shell.core.command.CommandParser;
import org.springframework.shell.core.command.CommandRegistry;

@Configuration
class CliConfig {

  @Bean
  @Primary
  ShellRunner errorHandlingShellRunner(
      CommandParser commandParser, CommandRegistry commandRegistry) {
    NonInteractiveShellRunner delegate =
        new NonInteractiveShellRunner(commandParser, commandRegistry);
    return args -> {
      try {
        delegate.run(args);
      } catch (CommandNotFoundException e) {
        System.err.println("Unknown command: '" + e.getCommandName() + "'.");
        delegate.run(new String[] {"help"});
      }
    };
  }
}
