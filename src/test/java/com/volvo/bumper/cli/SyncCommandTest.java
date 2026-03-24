package com.volvo.bumper.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SyncCommandTest {

  @TempDir Path tempDir;

  SyncCommand syncCommand;

  @BeforeEach
  void setUp() throws Exception {
    syncCommand = new SyncCommand(FakeDataFixture.create(tempDir));
  }

  @Test
  void sync_dryRun_prefixesOutput() {
    var output = syncCommand.sync(false, false);
    assertThat(output).startsWith("[DRY RUN]");
  }

  @Test
  void sync_dryRun_appendsUseWriteHint() {
    var output = syncCommand.sync(false, false);
    assertThat(output).contains("Use --write to apply changes.");
  }

  @Test
  void sync_write_doesNotPrefixDryRun() {
    var output = syncCommand.sync(false, true);
    assertThat(output).startsWith("Sync complete:");
  }

  @Test
  void sync_showsSummaryLine() {
    var output = syncCommand.sync(false, true);
    // 2 of 4 tracked repos updated (those with null dates), 2 new repos discovered
    assertThat(output).contains("2/4 repos updated, 2 newly ignored");
  }

  @Test
  void sync_verbose_includesLogs() {
    var output = syncCommand.sync(true, true);
    assertThat(output).contains("Before sync:");
  }

  @Test
  void sync_nonVerbose_doesNotIncludeLogs() {
    var output = syncCommand.sync(false, true);
    assertThat(output).doesNotContain("Before sync:");
  }

  @Test
  void sync_showsNewIgnoredProjects() {
    var output = syncCommand.sync(false, true);
    assertThat(output)
        .contains("New ignored projects:")
        .contains("search-service")
        .contains("legacy-api");
  }

  @Test
  void sync_outputContainsTrackedRepositoriesTable() {
    var output = syncCommand.sync(false, true);
    assertThat(output)
        .contains("=== Tracked repositories ===")
        .contains("Name")
        .contains("Last Bump Date");
  }

  @Test
  void sync_outputContainsIgnoredProjectsTable() {
    var output = syncCommand.sync(false, true);
    assertThat(output).contains("=== Ignored projects ===");
  }

  @Test
  void sync_outputContainsTrackedRepoNames() {
    var output = syncCommand.sync(false, true);
    assertThat(output)
        .contains("platform-core")
        .contains("auth-service")
        .contains("payments-gateway")
        .contains("notification-service");
  }

  @Test
  void sync_trackedReposAreSortedByName() {
    var output = syncCommand.sync(false, true);
    var tableStart = output.indexOf("=== Tracked repositories ===");
    var table = output.substring(tableStart);
    var authIdx = table.indexOf("auth-service");
    var notifIdx = table.indexOf("notification-service");
    var payIdx = table.indexOf("payments-gateway");
    var platIdx = table.indexOf("platform-core");
    assertThat(authIdx).isLessThan(notifIdx);
    assertThat(notifIdx).isLessThan(payIdx);
    assertThat(payIdx).isLessThan(platIdx);
  }
}
