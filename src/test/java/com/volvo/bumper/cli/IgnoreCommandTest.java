package com.volvo.bumper.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IgnoreCommandTest {

  @TempDir Path tempDir;

  IgnoreCommand ignoreCommand;

  @BeforeEach
  void setUp() throws Exception {
    ignoreCommand = new IgnoreCommand(FakeDataFixture.create(tempDir));
  }

  @Test
  void ignore_dryRun_prefixesOutput() {
    var output = ignoreCommand.ignore("platform-core", false);
    assertThat(output).startsWith("[DRY RUN]");
  }

  @Test
  void ignore_dryRun_appendsUseWriteHint() {
    var output = ignoreCommand.ignore("platform-core", false);
    assertThat(output).contains("(use --write to apply)");
  }

  @Test
  void ignore_write_showsMovedToIgnoredMessage() {
    var output = ignoreCommand.ignore("platform-core", true);
    assertThat(output).contains("Moved to ignored: platform-core");
  }

  @Test
  void ignore_notFound_returnsErrorMessage() {
    var output = ignoreCommand.ignore("missing-repo", true);
    assertThat(output).contains("Not found");
  }
}
