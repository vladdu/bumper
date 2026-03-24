package com.volvo.bumper.adapter.confluence;

import static org.assertj.core.api.Assertions.assertThat;

import com.volvo.bumper.domain.IgnoredRepository;
import com.volvo.bumper.domain.Repository;
import com.volvo.bumper.domain.RepositoryData;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

class JsonConfluenceRepositoryStoreTest {

  @TempDir Path tempDir;

  private final JsonMapper mapper =
      JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

  @Test
  void findAll_readsRepositoriesFromJsonFile() throws Exception {
    var file =
        writeCombinedFile(
            """
        [
          {
            "repos": ["vgcs-platform-core"],
            "bumpers": [],
            "team": "platform-team",
            "comments": "Core platform library"
          },
          {
            "repos": ["vgcs-payments-gateway"],
            "lastBumpDate": "2026-03-18",
            "lastBumpBy": "alice",
            "bumpers": ["alice"],
            "team": "payments-team",
            "comments": "Payment processing gateway"
          }
        ]
        """,
            "[]");

    var store = new JsonConfluenceRepositoryStore(mapper, file, file);
    List<Repository> repos = store.findAll().repos();

    assertThat(repos).hasSize(2);
    assertThat(repos.get(0).name()).isEqualTo("platform-core");
    assertThat(repos.get(0).repos()).containsExactly("vgcs-platform-core");
    assertThat(repos.get(0).team()).isEqualTo("platform-team");
    assertThat(repos.get(0).lastBumpDate()).isNull();
    assertThat(repos.get(1).name()).isEqualTo("payments-gateway");
    assertThat(repos.get(1).lastBumpDate()).isEqualTo(LocalDate.of(2026, 3, 18));
    assertThat(repos.get(1).lastBumpBy()).isEqualTo("alice");
    assertThat(repos.get(1).bumpers()).containsExactly("alice");
  }

  @Test
  void findAll_readsGerritRepoCorrectly() throws Exception {
    var file =
        writeCombinedFile(
            """
        [
          {
            "repos": ["vgt/connectivity/auth-service"],
            "bumpers": [],
            "team": "security-team",
            "comments": "Auth"
          }
        ]
        """,
            "[]");

    var store = new JsonConfluenceRepositoryStore(mapper, file, file);
    List<Repository> repos = store.findAll().repos();

    assertThat(repos.get(0).name()).isEqualTo("auth-service");
    assertThat(repos.get(0).forgeUrls())
        .containsExactly("https://git.vgt.volvo.com/admin/repos/vgt/connectivity/auth-service");
  }

  @Test
  void findAll_readsExtraMap() throws Exception {
    var file =
        writeCombinedFile(
            """
        [
          {
            "repos": ["vgt/connectivity/auth-service"],
            "team": "security-team",
            "comments": "Auth",
            "extra": { "criticality": "high" }
          }
        ]
        """,
            "[]");

    var store = new JsonConfluenceRepositoryStore(mapper, file, file);
    List<Repository> repos = store.findAll().repos();

    assertThat(repos.get(0).extra()).containsEntry("criticality", "high");
  }

  @Test
  void findAllIgnored_readsIgnoredFromJsonFile() throws Exception {
    var file =
        writeCombinedFile(
            "[]",
            """
        [
          {
            "repos": ["vgcs-old-monolith"]
          },
          {
            "repos": ["vgt/connectivity/deprecated-sync"]
          }
        ]
        """);

    var store = new JsonConfluenceRepositoryStore(mapper, file, file);
    List<IgnoredRepository> ignored = store.findAll().ignored();

    assertThat(ignored)
        .containsExactly(
            new IgnoredRepository("old-monolith", List.of("vgcs-old-monolith")),
            new IgnoredRepository("deprecated-sync", List.of("vgt/connectivity/deprecated-sync")));
  }

  @Test
  void saveAll_roundTrips() throws Exception {
    var file = writeEmptyCombinedFile();
    var store = new JsonConfluenceRepositoryStore(mapper, file, file);

    var repo =
        new Repository(
            "test-repo",
            List.of("vgcs-test-repo"),
            LocalDate.of(2026, 3, 18),
            "alice",
            List.of("alice", "bob"),
            "my-team",
            "a comment",
            Map.of("key", "value"),
            null);
    store.saveAll(new RepositoryData(List.of(repo), List.of()));

    List<Repository> loaded = store.findAll().repos();
    assertThat(loaded).containsExactly(repo);
  }

  @Test
  void saveAllIgnored_roundTrips() throws Exception {
    var file = writeEmptyCombinedFile();
    var store = new JsonConfluenceRepositoryStore(mapper, file, file);

    var ignored = new IgnoredRepository("old-thing", List.of("vgt/connectivity/old-thing"));
    store.saveAll(new RepositoryData(List.of(), List.of(ignored)));

    List<IgnoredRepository> loaded = store.findAll().ignored();
    assertThat(loaded).containsExactly(ignored);
  }

  @Test
  void saveAll_doesNotSerializeNameOrForgeUrls() throws Exception {
    var file = writeEmptyCombinedFile();
    var store = new JsonConfluenceRepositoryStore(mapper, file, file);
    var repo =
        new Repository("name", List.of("vgcs-name"), null, null, List.of(), null, null, null, null);
    store.saveAll(new RepositoryData(List.of(repo), List.of()));

    String json = Files.readString(file);
    assertThat(json).doesNotContain("\"name\"");
    assertThat(json).doesNotContain("\"forgeUrls\"");
    assertThat(json).contains("\"repos\"");
  }

  @Test
  void saveAll_writesToDifferentFileThanRead() throws Exception {
    var readFile =
        writeCombinedFile(
            """
        [
          {
            "repos": ["vgcs-platform-core"],
            "bumpers": [],
            "team": "platform-team",
            "comments": "Core platform library"
          }
        ]
        """,
            "[]");
    var writeFile = tempDir.resolve("write.json");
    Files.writeString(
        writeFile,
        """
        { "repos": [], "ignored": [] }
        """);

    String originalContent = Files.readString(readFile);
    var store = new JsonConfluenceRepositoryStore(mapper, readFile, writeFile);

    RepositoryData data = store.findAll();
    store.saveAll(data);

    assertThat(Files.readString(readFile)).isEqualTo(originalContent);
    assertThat(Files.readString(writeFile)).isNotEqualTo(originalContent);
    // verify written data is readable
    var readBack = new JsonConfluenceRepositoryStore(mapper, writeFile, writeFile);
    assertThat(readBack.findAll().repos()).hasSize(1);
    assertThat(readBack.findAll().repos().getFirst().name()).isEqualTo("platform-core");
  }

  private Path writeCombinedFile(String reposJson, String ignoredJson) throws Exception {
    var file = tempDir.resolve("data.json");
    Files.writeString(file, "{ \"repos\": " + reposJson + ", \"ignored\": " + ignoredJson + " }");
    return file;
  }

  private Path writeEmptyCombinedFile() throws Exception {
    return writeCombinedFile("[]", "[]");
  }
}
