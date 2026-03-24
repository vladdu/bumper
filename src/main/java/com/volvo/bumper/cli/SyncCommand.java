package com.volvo.bumper.cli;

import com.volvo.bumper.application.RepositoryService;
import com.volvo.bumper.domain.Repository;
import java.util.Comparator;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

@Component
public class SyncCommand {

  private final RepositoryService service;

  public SyncCommand(RepositoryService service) {
    this.service = service;
  }

  @Command(
      name = "sync",
      group = "Repository commands",
      description = "Sync repository data from forges and update Confluence")
  public String sync(
      @Option(
              longName = "verbose",
              shortName = 'v',
              description = "Show detailed sync logging",
              required = false,
              defaultValue = "false")
          boolean verbose,
      @Option(
              longName = "write",
              description = "Persist changes to the store (default: dry-run only)",
              required = false,
              defaultValue = "false")
          boolean write) {
    var data = service.sync(write);
    var result = data.result();
    var sb = new StringBuilder();
    if (verbose) {
      sb.append(String.join(System.lineSeparator(), data.logs()));
      sb.append(System.lineSeparator()).append(System.lineSeparator());
    }
    sb.append(
        String.format(
            "Sync complete: %d/%d repos updated, %d newly ignored (%d total ignored)",
            result.updated(), result.total(), result.newlyIgnored(), result.totalIgnored()));
    if (!result.newRegularNames().isEmpty()) {
      sb.append(System.lineSeparator()).append("New tracked projects:");
      for (var name : result.newRegularNames()) {
        sb.append(System.lineSeparator()).append("  ").append(name);
      }
    }
    if (!result.newIgnoredNames().isEmpty()) {
      sb.append(System.lineSeparator()).append("New ignored projects:");
      for (var name : result.newIgnoredNames()) {
        sb.append(System.lineSeparator()).append("  ").append(name);
      }
    }
    if (!result.forgeErrors().isEmpty()) {
      sb.append(System.lineSeparator()).append("Forge errors:");
      for (var err : result.forgeErrors()) {
        sb.append(System.lineSeparator()).append("  ").append(err);
      }
    }
    sb.append(System.lineSeparator()).append(System.lineSeparator());
    sb.append("=== Tracked repositories ===").append(System.lineSeparator());
    if (data.repos().isEmpty()) {
      sb.append("(none)").append(System.lineSeparator());
    } else {
      var sorted = data.repos().stream().sorted(Comparator.comparing(Repository::name)).toList();
      TableFormatter.appendTrackedTable(sb, sorted);
    }
    sb.append(System.lineSeparator());
    sb.append("=== Ignored projects ===").append(System.lineSeparator());
    if (data.ignored().isEmpty()) {
      sb.append("(none)").append(System.lineSeparator());
    } else {
      TableFormatter.appendIgnoredTable(sb, data.ignored());
    }

    var output = sb.toString().stripTrailing();
    if (!write) {
      return "[DRY RUN] " + output + System.lineSeparator() + "Use --write to apply changes.";
    }
    return output;
  }
}
