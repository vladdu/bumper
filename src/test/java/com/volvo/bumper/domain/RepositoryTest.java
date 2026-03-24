package com.volvo.bumper.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RepositoryTest {

  @Test
  void extra_isDefensivelyCopied() {
    var mutableMap = new HashMap<String, String>();
    mutableMap.put("key", "value");
    var repo =
        new Repository(
            "name", List.of("vgcs-name"), null, null, List.of(), "team-a", "", mutableMap, null);

    mutableMap.put("other", "injected");

    assertThat(repo.extra()).doesNotContainKey("other");
  }

  @Test
  void extra_isEmptyMap_whenNull() {
    var repo =
        new Repository(
            "name", List.of("vgcs-name"), null, null, List.of(), "team-a", "", null, null);
    assertThat(repo.extra()).isEmpty();
  }

  @Test
  void bumpers_isEmptyList_whenNull() {
    var repo =
        new Repository("name", List.of("vgcs-name"), null, null, null, "team-a", "", null, null);
    assertThat(repo.bumpers()).isEmpty();
  }

  @Test
  void bumpers_isDefensivelyCopied() {
    var mutableList = new java.util.ArrayList<String>();
    mutableList.add("alice");
    var repo =
        new Repository(
            "name", List.of("vgcs-name"), null, null, mutableList, "team-a", "", null, null);

    mutableList.add("injected");

    assertThat(repo.bumpers()).doesNotContain("injected");
  }

  @Test
  void repos_isEmptyList_whenNull() {
    var repo = new Repository("name", null, null, null, List.of(), null, null, null, null);
    assertThat(repo.repos()).isEmpty();
  }

  @Test
  void repos_isDefensivelyCopied() {
    var mutableList = new java.util.ArrayList<String>();
    mutableList.add("vgcs-name");
    var repo = new Repository("name", mutableList, null, null, List.of(), null, null, null, null);

    mutableList.add("injected");

    assertThat(repo.repos()).doesNotContain("injected");
  }

  @Test
  void forgeUrls_returnsGitHubUrl_forGitHubRepo() {
    var repo =
        new Repository("name", List.of("vgcs-name"), null, null, List.of(), null, null, null, null);
    assertThat(repo.forgeUrls())
        .containsExactly("https://github.com/VolvoGroup-Internal/vgcs-name");
  }

  @Test
  void forgeUrls_returnsGerritUrl_forGerritRepo() {
    var repo =
        new Repository(
            "api-spec",
            List.of("vgt/connectivity/api-spec"),
            null,
            null,
            List.of(),
            null,
            null,
            null,
            null);
    assertThat(repo.forgeUrls())
        .containsExactly("https://git.vgt.volvo.com/admin/repos/vgt/connectivity/api-spec");
  }

  @Test
  void forgeUrls_returnsBothUrls_forMultiForgeRepo() {
    var repo =
        new Repository(
            "auth-service",
            List.of("vgcs-auth-service", "vgt/connectivity/auth-service"),
            null,
            null,
            List.of(),
            null,
            null,
            null,
            null);
    assertThat(repo.forgeUrls())
        .containsExactly(
            "https://github.com/VolvoGroup-Internal/vgcs-auth-service",
            "https://git.vgt.volvo.com/admin/repos/vgt/connectivity/auth-service");
  }

  @Test
  void forgeUrls_isEmpty_whenReposIsEmpty() {
    var repo = new Repository("name", List.of(), null, null, null, null, null, null, null);
    assertThat(repo.forgeUrls()).isEmpty();
  }

  @Test
  void mergeForgeData_keepsStoreFields_updatesForgeFields() {
    var stored =
        new Repository(
            "r",
            List.of("vgcs-r"),
            LocalDate.of(2025, 1, 1),
            "alice",
            List.of("alice"),
            "my-team",
            "my comment",
            Map.of("k", "v"),
            null);
    var forgeData =
        new Repository(
            "r",
            List.of("vgcs-r-new"),
            LocalDate.of(2026, 3, 1),
            "bob",
            null,
            null,
            null,
            null,
            null);

    var merged = stored.mergeForgeData(forgeData);

    assertThat(merged.repos()).containsExactly("vgcs-r", "vgcs-r-new");
    assertThat(merged.lastBumpDate()).isEqualTo(LocalDate.of(2026, 3, 1));
    assertThat(merged.lastBumpBy()).isEqualTo("bob");
    assertThat(merged.bumpers()).containsExactly("alice");
    assertThat(merged.team()).isEqualTo("my-team");
    assertThat(merged.comments()).isEqualTo("my comment");
    assertThat(merged.extra()).containsEntry("k", "v");
  }

  @Test
  void mergeForgeData_picksMostRecentDate_whenStoredIsNewer() {
    var stored =
        new Repository(
            "r",
            List.of("vgcs-r"),
            LocalDate.of(2026, 3, 1),
            "alice",
            List.of(),
            null,
            null,
            null,
            null);
    var forgeData =
        new Repository(
            "r",
            List.of("vgt/connectivity/r"),
            LocalDate.of(2026, 1, 1),
            "bob",
            null,
            null,
            null,
            null,
            null);

    var merged = stored.mergeForgeData(forgeData);

    assertThat(merged.lastBumpDate()).isEqualTo(LocalDate.of(2026, 3, 1));
    assertThat(merged.lastBumpBy()).isEqualTo("alice");
    assertThat(merged.repos()).containsExactly("vgcs-r", "vgt/connectivity/r");
  }

  @Test
  void mergeForgeData_deduplicatesRepos() {
    var stored =
        new Repository("r", List.of("vgcs-r"), null, null, List.of(), null, null, null, null);
    var forgeData =
        new Repository(
            "r", List.of("vgcs-r"), LocalDate.of(2026, 1, 1), null, null, null, null, null, null);

    var merged = stored.mergeForgeData(forgeData);

    assertThat(merged.repos()).containsExactly("vgcs-r");
  }

  @Test
  void extra_isUnmodifiable() {
    var repo =
        new Repository(
            "name",
            List.of("vgcs-name"),
            null,
            null,
            List.of(),
            "team-a",
            "",
            Map.of("k", "v"),
            null);

    assertThatThrownBy(() -> repo.extra().put("x", "y"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void withError_setsNameReposAndError() {
    var repo = Repository.withError("r", List.of("vgcs-r"), "some error");
    assertThat(repo.name()).isEqualTo("r");
    assertThat(repo.repos()).containsExactly("vgcs-r");
    assertThat(repo.forgeUrls()).containsExactly("https://github.com/VolvoGroup-Internal/vgcs-r");
    assertThat(repo.error()).isEqualTo("some error");
    assertThat(repo.lastBumpDate()).isNull();
    assertThat(repo.lastBumpBy()).isNull();
  }

  @Test
  void mergeForgeData_preservesLastKnownData_whenForgeDataHasError() {
    var stored =
        new Repository(
            "r",
            List.of("vgcs-r"),
            LocalDate.of(2025, 1, 1),
            "alice",
            List.of(),
            "my-team",
            "my comment",
            Map.of("k", "v"),
            null);
    var forgeData = Repository.withError("r", List.of("vgcs-r"), "API error");

    var merged = stored.mergeForgeData(forgeData);

    assertThat(merged.repos()).containsExactly("vgcs-r");
    assertThat(merged.lastBumpDate()).isEqualTo(LocalDate.of(2025, 1, 1));
    assertThat(merged.lastBumpBy()).isEqualTo("alice");
    assertThat(merged.team()).isEqualTo("my-team");
    assertThat(merged.error()).isEqualTo("API error");
  }
}
