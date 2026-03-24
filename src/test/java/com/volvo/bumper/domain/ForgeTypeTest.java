package com.volvo.bumper.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ForgeTypeTest {

  @Test
  void nameFromRepo_stripsVgcsPrefix_forGitHub() {
    assertThat(ForgeType.nameFromRepo("vgcs-platform-core")).isEqualTo("platform-core");
  }

  @Test
  void nameFromRepo_returnsUnchanged_forGitHubWithoutPrefix() {
    assertThat(ForgeType.nameFromRepo("some-repo")).isEqualTo("some-repo");
  }

  @Test
  void nameFromRepo_takesLastSegment_forGerrit() {
    assertThat(ForgeType.nameFromRepo("vgt/connectivity/auth-service")).isEqualTo("auth-service");
  }

  @Test
  void nameFromRepo_returnsNull_forNull() {
    assertThat(ForgeType.nameFromRepo(null)).isNull();
  }
}
