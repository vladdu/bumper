package com.volvo.bumper.cli;

import com.volvo.bumper.application.RepositoryService;
import org.springframework.shell.core.command.annotation.Argument;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

@Component
public class IgnoreCommand {

  private final RepositoryService service;

  public IgnoreCommand(RepositoryService service) {
    this.service = service;
  }

  @Command(
      name = "ignore",
      group = "Repository commands",
      description = "Move a tracked project to the ignored list")
  public String ignore(
      @Argument(index = 0, description = "Name of the project to ignore") String name,
      @Option(
              longName = "write",
              description = "Persist changes to the store (default: dry-run only)",
              required = false,
              defaultValue = "false")
          boolean write) {
    var output = service.ignore(name, write);
    if (!write) {
      return "[DRY RUN] " + output + System.lineSeparator() + "(use --write to apply)";
    }
    return output;
  }
}
