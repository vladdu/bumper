package com.volvo.bumper.cli;

import com.volvo.bumper.application.RepositoryService;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.stereotype.Component;

@Component
public class ListCommand {

  private final RepositoryService service;

  public ListCommand(RepositoryService service) {
    this.service = service;
  }

  @Command(
      name = "list",
      group = "Repository commands",
      description = "List all tracked and ignored repositories")
  public String list() {
    var data = service.listAll();
    var repos = data.repos();
    var ignored = data.ignored();
    var sb = new StringBuilder();

    sb.append("=== Tracked repositories ===").append(System.lineSeparator());
    if (repos.isEmpty()) {
      sb.append("(none)").append(System.lineSeparator());
    } else {
      TableFormatter.appendTrackedTable(sb, repos);
    }

    sb.append(System.lineSeparator());
    sb.append("=== Ignored projects ===").append(System.lineSeparator());
    if (ignored.isEmpty()) {
      sb.append("(none)").append(System.lineSeparator());
    } else {
      TableFormatter.appendIgnoredTable(sb, ignored);
    }

    return sb.toString().stripTrailing();
  }
}
