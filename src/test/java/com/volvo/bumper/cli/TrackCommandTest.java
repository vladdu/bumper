package com.volvo.bumper.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrackCommandTest {

  @TempDir Path tempDir;

  TrackCommand trackCommand;

  @BeforeEach
  void setUp() throws Exception {
    trackCommand = new TrackCommand(FakeDataFixture.create(tempDir));
  }

  @Test
  void track_dryRun_prefixesOutput() {
    var output = trackCommand.track("old-monolith", false);
    assertThat(output).startsWith("[DRY RUN]");
  }

  @Test
  void track_dryRun_appendsUseWriteHint() {
    var output = trackCommand.track("old-monolith", false);
    assertThat(output).contains("(use --write to apply)");
  }

  @Test
  void track_write_showsTrackingMessage() {
    var output = trackCommand.track("old-monolith", true);
    assertThat(output).contains("Now tracking: old-monolith");
  }

  @Test
  void track_notFound_returnsErrorMessage() {
    var output = trackCommand.track("missing-repo", true);
    assertThat(output).contains("Not found");
  }
}
