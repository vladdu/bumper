package com.volvo.bumper.adapter.github;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GitHubForgeNamingTest {

  @Test
  void repoFromUrl_extractsLastPathSegment() {
    assertThat(
            GitHubForgeNaming.repoFromUrl(
                "https://github.com/VolvoGroup-Internal/vgcs-asset-connectivity-cdk-utils"))
        .isEqualTo("vgcs-asset-connectivity-cdk-utils");
  }

  @Test
  void repoFromUrl_worksWithSingleSegment() {
    assertThat(GitHubForgeNaming.repoFromUrl("https://github.com/VolvoGroup-Internal/vgcs-foo"))
        .isEqualTo("vgcs-foo");
  }

  @Test
  void nameFromRepo_stripsVgcsPrefix() {
    assertThat(GitHubForgeNaming.nameFromRepo("vgcs-platform-core")).isEqualTo("platform-core");
  }

  @Test
  void nameFromRepo_returnsUnchanged_whenNoPrefixPresent() {
    assertThat(GitHubForgeNaming.nameFromRepo("some-repo")).isEqualTo("some-repo");
  }

  @Test
  void nameFromRepo_doesNotStripPartialPrefix() {
    assertThat(GitHubForgeNaming.nameFromRepo("vgcs")).isEqualTo("vgcs");
  }
}
