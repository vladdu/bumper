package com.volvo.bumper.adapter.gerrit;

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

class JsonGerritForgeAdapterTest {

  @TempDir Path tempDir;

  private final JsonMapper mapper = JsonMapper.builder().build();

  @Test
  void forgeType_returnsGerrit() {
    var adapter =
        new JsonGerritForgeAdapter(
            mapper, tempDir.resolve("empty.json"), tempDir.resolve("no-details.json"));
    assertThat(adapter.forgeType()).isEqualTo(ForgeType.GERRIT);
  }

  @Test
  void discoverAll_readsRepositoriesFromJsonFile() throws Exception {
    var reposFile = tempDir.resolve("gerrit.json");
    var detailsFile = tempDir.resolve("gerrit-details.json");
    Files.writeString(
        reposFile,
        """
                [
                  {
                    "name": "auth-service",
                    "forgeUrl": "https://git.vgt.volvo.com/admin/repos/vgt/connectivity/auth-service"
                  },
                  {
                    "name": "notification-service",
                    "forgeUrl": "https://git.vgt.volvo.com/admin/repos/vgt/connectivity/notification-service"
                  }
                ]
                """);
    Files.writeString(
        detailsFile,
        """
                {
                  "vgt/connectivity/auth-service": { "lastDependencyUpdate": "2026-01-20", "lastDependencyUpdateBy": "anton" },
                  "vgt/connectivity/notification-service": { "lastDependencyUpdate": "2026-01-20" }
                }
                """);

    var adapter = new JsonGerritForgeAdapter(mapper, reposFile, detailsFile);
    List<Repository> repos = adapter.discoverAll();

    assertThat(repos).hasSize(2);
    assertThat(repos.get(0).name()).isEqualTo("auth-service");
    assertThat(repos.get(0).repos()).containsExactly("vgt/connectivity/auth-service");
    assertThat(repos.get(0).forgeUrls())
        .containsExactly("https://git.vgt.volvo.com/admin/repos/vgt/connectivity/auth-service");
    assertThat(repos.get(0).lastBumpDate()).isEqualTo(LocalDate.of(2026, 1, 20));
    assertThat(repos.get(0).lastBumpBy()).isEqualTo("anton");
    assertThat(repos.get(1).name()).isEqualTo("notification-service");
    assertThat(repos.get(1).repos()).containsExactly("vgt/connectivity/notification-service");
    assertThat(repos.get(1).lastBumpDate()).isEqualTo(LocalDate.of(2026, 1, 20));
    assertThat(repos.get(1).lastBumpBy()).isNull();
  }

  @Test
  void discoverAll_returnsNullBumpData_whenRepoNotInDetailsFile() throws Exception {
    var reposFile = tempDir.resolve("gerrit.json");
    var detailsFile = tempDir.resolve("gerrit-details.json");
    Files.writeString(
        reposFile,
        """
                [
                  {
                    "name": "legacy-api",
                    "forgeUrl": "https://git.vgt.volvo.com/admin/repos/vgt/connectivity/legacy-api"
                  }
                ]
                """);
    Files.writeString(detailsFile, "{}");

    var adapter = new JsonGerritForgeAdapter(mapper, reposFile, detailsFile);
    List<Repository> repos = adapter.discoverAll();

    assertThat(repos).hasSize(1);
    assertThat(repos.get(0).name()).isEqualTo("legacy-api");
    assertThat(repos.get(0).lastBumpDate()).isNull();
    assertThat(repos.get(0).lastBumpBy()).isNull();
  }

  @Test
  void discoverAll_derivesNameFromRepo_whenNameNotInJson() throws Exception {
    var reposFile = tempDir.resolve("gerrit.json");
    Files.writeString(
        reposFile,
        """
                [
                  {
                    "forgeUrl": "https://git.vgt.volvo.com/admin/repos/vgt/connectivity/api-spec"
                  }
                ]
                """);

    var adapter = new JsonGerritForgeAdapter(mapper, reposFile, tempDir.resolve("no-details.json"));
    List<Repository> repos = adapter.discoverAll();

    assertThat(repos.get(0).name()).isEqualTo("api-spec");
    assertThat(repos.get(0).repos()).containsExactly("vgt/connectivity/api-spec");
  }
}
