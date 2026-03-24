package com.volvo.bumper.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ListCommandTest {

  @TempDir Path tempDir;

  ListCommand listCommand;

  @BeforeEach
  void setUp() throws Exception {
    listCommand = new ListCommand(FakeDataFixture.create(tempDir));
  }

  @Test
  void list_outputContainsTrackedSectionHeader() {
    assertThat(listCommand.list()).contains("=== Tracked repositories ===");
  }

  @Test
  void list_outputContainsIgnoredSectionHeader() {
    assertThat(listCommand.list()).contains("=== Ignored projects ===");
  }

  @Test
  void list_outputContainsAllTrackedRepoNames() {
    var output = listCommand.list();
    assertThat(output)
        .contains("platform-core")
        .contains("auth-service")
        .contains("payments-gateway")
        .contains("notification-service");
  }

  @Test
  void list_outputContainsAllIgnoredRepoNames() {
    var output = listCommand.list();
    assertThat(output).contains("old-monolith").contains("deprecated-sync");
  }

  @Test
  void list_outputContainsTableColumnHeaders() {
    var output = listCommand.list();
    assertThat(output)
        .contains("Name")
        .contains("Bumpers")
        .contains("Last Bump Date")
        .contains("Last Bump By")
        .contains("Comments")
        .contains("Team")
        .contains("Repo");
  }

  @Test
  void list_extraKeysAppearAsSeparateColumnHeaders() {
    var output = listCommand.list();
    assertThat(output).contains("criticality");
  }

  @Test
  void list_extraValuesAppearInCorrectRow() {
    var output = listCommand.list();
    var lines = output.lines().toList();
    var authServiceLine =
        lines.stream().filter(l -> l.contains("auth-service")).findFirst().orElseThrow();
    assertThat(authServiceLine).contains("high");

    var platformCoreLine =
        lines.stream().filter(l -> l.contains("platform-core")).findFirst().orElseThrow();
    assertThat(platformCoreLine).doesNotContain("high");
  }
}
