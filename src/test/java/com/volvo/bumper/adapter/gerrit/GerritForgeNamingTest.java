package com.volvo.bumper.adapter.gerrit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GerritForgeNamingTest {

  @Test
  void repoFromUrl_extractsFullProjectPath() {
    assertThat(
            GerritForgeNaming.repoFromUrl(
                "https://git.vgt.volvo.com/admin/repos/vgt/connectivity/api-specifications"))
        .isEqualTo("vgt/connectivity/api-specifications");
  }

  @Test
  void repoFromUrl_worksWithSingleSegmentPath() {
    assertThat(
            GerritForgeNaming.repoFromUrl(
                "https://git.vgt.volvo.com/admin/repos/vgt/connectivity/auth-service"))
        .isEqualTo("vgt/connectivity/auth-service");
  }

  @Test
  void nameFromRepo_returnsLastSegment() {
    assertThat(GerritForgeNaming.nameFromRepo("vgt/connectivity/api-specifications"))
        .isEqualTo("api-specifications");
  }

  @Test
  void nameFromRepo_returnsLastSegment_forDeeplyNestedPath() {
    assertThat(GerritForgeNaming.nameFromRepo("a/b/c/d")).isEqualTo("d");
  }

  @Test
  void nameFromRepo_returnsValueAsIs_whenNoSlash() {
    assertThat(GerritForgeNaming.nameFromRepo("my-project")).isEqualTo("my-project");
  }
}
