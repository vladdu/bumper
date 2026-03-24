package com.volvo.bumper.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BumpPatternTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Bump axios from 1.0 to 1.5",
        "Upgrade spring-boot to 3.0",
        "renovate: update lodash",
        "dependabot: bump requests from 2.28 to 2.31",
        "chore(deps): update axios to 1.6",
        "build(deps): bump numpy from 1.24 to 1.25",
        "fix(deps-dev): bump eslint from 7 to 8",
        "Update dependencies",
        "update dep versions",
        "update packages in requirements.txt",
        "chore: update lockfile",
      })
  void isBumpCommit_matchesKnownBumpPatterns(String message) {
    assertThat(BumpPattern.isBumpCommit(message)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"Add new feature", "Fix login bug", "Refactor authentication service"})
  void isBumpCommit_rejectsNonBumpMessages(String message) {
    assertThat(BumpPattern.isBumpCommit(message)).isFalse();
  }

  @Test
  void isBumpCommit_caseInsensitive() {
    assertThat(BumpPattern.isBumpCommit("BUMP axios to 2.0")).isTrue();
    assertThat(BumpPattern.isBumpCommit("CHORE(DEPS): update axios")).isTrue();
  }

  @Test
  void isBumpCommit_emptyStringReturnsFalse() {
    assertThat(BumpPattern.isBumpCommit("")).isFalse();
  }
}
