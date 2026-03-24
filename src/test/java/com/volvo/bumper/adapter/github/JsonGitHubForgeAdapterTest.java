package com.volvo.bumper.adapter.github;

import static org.assertj.core.api.Assertions.assertThat;

import com.volvo.bumper.domain.ForgeType;
import com.volvo.bumper.domain.Repository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class JsonGitHubForgeAdapterTest {

  @TempDir Path tempDir;

  private final JsonMapper mapper = JsonMapper.builder().build();

  @Test
  void forgeType_returnsGitHub() {
    var adapter =
        new JsonGitHubForgeAdapter(
            mapper, tempDir.resolve("empty.json"), tempDir.resolve("no-details.json"));
    assertThat(adapter.forgeType()).isEqualTo(ForgeType.GITHUB);
  }

  @Test
  void discoverAll_readsRepositoriesFromJsonFile() throws Exception {
    var reposFile = tempDir.resolve("github.json");
    var detailsFile = tempDir.resolve("github-details.json");
    Files.writeString(
        reposFile,
        """
                [
                  {
                    "name": "my-repo",
                    "forgeUrl": "https://github.com/VolvoGroup-Internal/vgcs-my-repo"
                  },
                  {
                    "name": "other-repo",
                    "forgeUrl": "https://github.com/VolvoGroup-Internal/vgcs-other-repo"
                  }
                ]
                """);
    Files.writeString(
        detailsFile,
        """
                {
                  "vgcs-my-repo": { "lastDependencyUpdate": "2026-02-14", "lastDependencyUpdateBy": "bob" },
                  "vgcs-other-repo": { "lastDependencyUpdate": "2026-02-14" }
                }
                """);

    var adapter = new JsonGitHubForgeAdapter(mapper, reposFile, detailsFile);
    List<Repository> repos = adapter.discoverAll();

    assertThat(repos).hasSize(2);
    assertThat(repos.get(0).name()).isEqualTo("my-repo");
    assertThat(repos.get(0).repos()).containsExactly("vgcs-my-repo");
    assertThat(repos.get(0).forgeUrls())
        .containsExactly("https://github.com/VolvoGroup-Internal/vgcs-my-repo");
    assertThat(repos.get(0).lastBumpDate()).isEqualTo(LocalDate.of(2026, 2, 14));
    assertThat(repos.get(0).lastBumpBy()).isEqualTo("bob");
    assertThat(repos.get(1).name()).isEqualTo("other-repo");
    assertThat(repos.get(1).repos()).containsExactly("vgcs-other-repo");
    assertThat(repos.get(1).lastBumpDate()).isEqualTo(LocalDate.of(2026, 2, 14));
    assertThat(repos.get(1).lastBumpBy()).isNull();
  }

  @Test
  void discoverAll_returnsNullBumpData_whenRepoNotInDetailsFile() throws Exception {
    var reposFile = tempDir.resolve("github.json");
    var detailsFile = tempDir.resolve("github-details.json");
    Files.writeString(
        reposFile,
        """
                [
                  {
                    "name": "search-service",
                    "forgeUrl": "https://github.com/VolvoGroup-Internal/vgcs-search-service"
                  }
                ]
                """);
    Files.writeString(detailsFile, "{}");

    var adapter = new JsonGitHubForgeAdapter(mapper, reposFile, detailsFile);
    List<Repository> repos = adapter.discoverAll();

    assertThat(repos).hasSize(1);
    assertThat(repos.get(0).name()).isEqualTo("search-service");
    assertThat(repos.get(0).lastBumpDate()).isNull();
    assertThat(repos.get(0).lastBumpBy()).isNull();
  }

  @Test
  void discoverAll_derivesNameFromRepo_whenNameNotInJson() throws Exception {
    var reposFile = tempDir.resolve("github.json");
    Files.writeString(
        reposFile,
        """
                [
                  {
                    "forgeUrl": "https://github.com/VolvoGroup-Internal/vgcs-platform-core"
                  }
                ]
                """);

    var adapter = new JsonGitHubForgeAdapter(mapper, reposFile, tempDir.resolve("no-details.json"));
    List<Repository> repos = adapter.discoverAll();

    assertThat(repos.get(0).name()).isEqualTo("platform-core");
    assertThat(repos.get(0).repos()).containsExactly("vgcs-platform-core");
  }
}
