package com.volvo.bumper.cli;

import com.volvo.bumper.application.RepositoryService;
import org.springframework.shell.core.command.annotation.Argument;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

@Component
public class TrackCommand {

  private final RepositoryService service;

  public TrackCommand(RepositoryService service) {
    this.service = service;
  }

  @Command(
      name = "track",
      group = "Repository commands",
      description = "Move an ignored project to the tracked list")
  public String track(
      @Argument(index = 0, description = "Name of the project to track") String name,
      @Option(
              longName = "write",
              description = "Persist changes to the store (default: dry-run only)",
              required = false,
              defaultValue = "false")
          boolean write) {
    var output = service.track(name, write);
    if (!write) {
      return "[DRY RUN] " + output + System.lineSeparator() + "(use --write to apply)";
    }
    return output;
  }
}
