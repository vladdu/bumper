package com.volvo.bumper.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.volvo.bumper.domain.IgnoredRepository;
import com.volvo.bumper.domain.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TableFormatterTest {

  @Test
  void appendTrackedTable_includesColumnHeaders() {
    var sb = new StringBuilder();
    var repos =
        List.of(
            new Repository(
                "my-app",
                List.of("vgcs-my-app"),
                LocalDate.of(2026, 1, 15),
                "alice",
                List.of("alice"),
                "teamA",
                null,
                Map.of(),
                null));
    TableFormatter.appendTrackedTable(sb, repos);
    assertThat(sb.toString())
        .contains("Name")
        .contains("Bumpers")
        .contains("Last Bump Date")
        .contains("Last Bump By")
        .contains("Team")
        .contains("Repo");
  }

  @Test
  void appendTrackedTable_includesRepoData() {
    var sb = new StringBuilder();
    var repos =
        List.of(
            new Repository(
                "my-app",
                List.of("vgcs-my-app"),
                LocalDate.of(2026, 1, 15),
                "alice",
                List.of("alice"),
                "teamA",
                null,
                Map.of(),
                null));
    TableFormatter.appendTrackedTable(sb, repos);
    assertThat(sb.toString()).contains("my-app").contains("2026-01-15").contains("alice");
  }

  @Test
  void appendTrackedTable_showsNeverForNullDate() {
    var sb = new StringBuilder();
    var repos =
        List.of(
            new Repository(
                "my-app",
                List.of("vgcs-my-app"),
                null,
                null,
                List.of(),
                null,
                null,
                Map.of(),
                null));
    TableFormatter.appendTrackedTable(sb, repos);
    assertThat(sb.toString()).contains("never");
  }

  @Test
  void appendTrackedTable_includesExtraColumns() {
    var sb = new StringBuilder();
    var repos =
        List.of(
            new Repository(
                "my-app",
                List.of("vgcs-my-app"),
                null,
                null,
                List.of(),
                null,
                null,
                Map.of("criticality", "high"),
                null));
    TableFormatter.appendTrackedTable(sb, repos);
    assertThat(sb.toString()).contains("criticality").contains("high");
  }

  @Test
  void appendTrackedTable_showsMultipleForgeUrls() {
    var sb = new StringBuilder();
    var repos =
        List.of(
            new Repository(
                "shared-svc",
                List.of("vgcs-shared-svc", "vgt/connectivity/shared-svc"),
                null,
                null,
                List.of(),
                null,
                null,
                Map.of(),
                null));
    TableFormatter.appendTrackedTable(sb, repos);
    assertThat(sb.toString())
        .contains("https://github.com/VolvoGroup-Internal/vgcs-shared-svc")
        .contains("https://git.vgt.volvo.com/admin/repos/vgt/connectivity/shared-svc");
  }

  @Test
  void appendIgnoredTable_includesHeadersAndData() {
    var sb = new StringBuilder();
    var ignored = List.of(new IgnoredRepository("old-lib", List.of("vgcs-old-lib")));
    TableFormatter.appendIgnoredTable(sb, ignored);
    assertThat(sb.toString()).contains("Name").contains("Repo").contains("old-lib");
  }

  @Test
  void appendTable_includesSeparatorLine() {
    var sb = new StringBuilder();
    String[] headers = {"A", "BB"};
    String[][] rows = {{"long-value", "x"}};
    TableFormatter.appendTable(sb, headers, rows);
    var lines = sb.toString().lines().toList();
    assertThat(lines).hasSize(3);
    assertThat(lines.get(1)).contains("----------").contains("--");
  }
}
