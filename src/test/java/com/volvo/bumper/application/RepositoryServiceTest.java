package com.volvo.bumper.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.volvo.bumper.domain.ForgeType;
import com.volvo.bumper.domain.IgnoredRepository;
import com.volvo.bumper.domain.Repository;
import com.volvo.bumper.domain.RepositoryData;
import com.volvo.bumper.port.ForgePort;
import com.volvo.bumper.port.RepositoryStore;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RepositoryServiceTest {

  // Hand-rolled fakes -- no Mockito, no Spring

  static class InMemoryStore implements RepositoryStore {
    private List<Repository> repos;
    private List<IgnoredRepository> ignored;

    InMemoryStore(List<Repository> initial) {
      this.repos = new ArrayList<>(initial);
      this.ignored = new ArrayList<>();
    }

    InMemoryStore(List<Repository> initial, List<IgnoredRepository> initialIgnored) {
      this.repos = new ArrayList<>(initial);
      this.ignored = new ArrayList<>(initialIgnored);
    }

    @Override
    public RepositoryData findAll() {
      return new RepositoryData(List.copyOf(repos), List.copyOf(ignored));
    }

    @Override
    public void saveAll(RepositoryData data) {
      this.repos = new ArrayList<>(data.repos());
      this.ignored = new ArrayList<>(data.ignored());
    }
  }

  static class StubForgePort implements ForgePort {
    private final ForgeType type;
    private final List<Repository> discovered;

    StubForgePort(ForgeType type, List<Repository> discovered) {
      this.type = type;
      this.discovered = discovered;
    }

    @Override
    public ForgeType forgeType() {
      return type;
    }

    @Override
    public List<Repository> discoverAll() {
      return discovered;
    }
  }

  static Repository githubRepo(String name) {
    return new Repository(
        name, List.of("vgcs-" + name), null, null, List.of(), "team", "", Map.of(), null);
  }

  static Repository forgeRepo(String name, ForgeType forge, String url, LocalDate date) {
    String repo = forge == ForgeType.GITHUB ? "vgcs-" + name : "vgt/connectivity/" + name;
    return new Repository(name, List.of(repo), date, null, List.of(), null, null, null, null);
  }

  // --- list ---

  @Test
  void list_returnsAllReposFromStore() {
    var repo = githubRepo("myrepo");
    var service = new RepositoryService(new InMemoryStore(List.of(repo)), List.of());

    assertThat(service.listAll().repos()).containsExactly(repo);
  }

  @Test
  void list_sortsByName() {
    var repoC = githubRepo("charlie");
    var repoA = githubRepo("alpha");
    var repoB = githubRepo("bravo");
    var service = new RepositoryService(new InMemoryStore(List.of(repoC, repoA, repoB)), List.of());

    assertThat(service.listAll().repos())
        .extracting(Repository::name)
        .containsExactly("alpha", "bravo", "charlie");
  }

  @Test
  void listIgnored_returnsIgnoredProjectsFromStore() {
    var ignored = new IgnoredRepository("old-thing", List.of("vgcs-old-thing"));
    var store = new InMemoryStore(List.of(), List.of(ignored));
    var service = new RepositoryService(store, List.of());

    assertThat(service.listAll().ignored()).containsExactly(ignored);
  }

  // --- sync ---

  @Test
  void sync_updatesLastDependencyUpdate_andCountsChange() {
    var newDate = LocalDate.of(2026, 1, 15);
    var store = new InMemoryStore(List.of(githubRepo("myrepo")));
    var port =
        new StubForgePort(
            ForgeType.GITHUB,
            List.of(
                forgeRepo("myrepo", ForgeType.GITHUB, "https://github.com/org/myrepo", newDate)));
    var service = new RepositoryService(store, List.of(port));

    var result = service.sync(true).result();

    assertThat(result.updated()).isEqualTo(1);
    assertThat(result.total()).isEqualTo(1);
    assertThat(result.newlyIgnored()).isEqualTo(0);
    assertThat(result.newRegularNames()).containsExactly("myrepo");
    assertThat(result.newIgnoredNames()).isEmpty();
    assertThat(store.findAll().repos().get(0).lastBumpDate()).isEqualTo(newDate);
  }

  @Test
  void sync_doesNotCount_whenDateUnchanged() {
    var date = LocalDate.of(2026, 1, 15);
    var repo =
        new Repository(
            "repo", List.of("vgcs-repo"), date, null, List.of(), "team", "", Map.of(), null);
    var store = new InMemoryStore(List.of(repo));
    var port =
        new StubForgePort(ForgeType.GITHUB, List.of(forgeRepo("repo", ForgeType.GITHUB, "", date)));
    var service = new RepositoryService(store, List.of(port));

    var result = service.sync(true).result();

    assertThat(result.updated()).isEqualTo(0);
    assertThat(result.total()).isEqualTo(1);
    assertThat(result.newRegularNames()).isEmpty();
  }

  @Test
  void sync_doesNotReportNewRegular_whenDateChangedButWasNotNull() {
    var oldDate = LocalDate.of(2026, 1, 10);
    var newDate = LocalDate.of(2026, 1, 15);
    var repo =
        new Repository(
            "repo", List.of("vgcs-repo"), oldDate, null, List.of(), "team", "", Map.of(), null);
    var store = new InMemoryStore(List.of(repo));
    var port =
        new StubForgePort(
            ForgeType.GITHUB, List.of(forgeRepo("repo", ForgeType.GITHUB, "", newDate)));
    var service = new RepositoryService(store, List.of(port));

    var result = service.sync(true).result();

    assertThat(result.updated()).isEqualTo(1);
    assertThat(result.newRegularNames()).isEmpty();
  }

  @Test
  void sync_skipsRepo_whenNoForgePortMatches() {
    var repo =
        new Repository(
            "repo",
            List.of("vgt/connectivity/repo"),
            null,
            null,
            List.of(),
            "team",
            "",
            Map.of(),
            null);
    var store = new InMemoryStore(List.of(repo));
    // only a GITHUB port -- GERRIT repo not returned by any forge
    var port = new StubForgePort(ForgeType.GITHUB, List.of());
    var service = new RepositoryService(store, List.of(port));

    var result = service.sync(true).result();

    assertThat(result.updated()).isEqualTo(0);
    assertThat(result.total()).isEqualTo(1);
    assertThat(store.findAll().repos().get(0)).isEqualTo(repo);
  }

  @Test
  void sync_doesNotCount_whenForgeReturnsEmpty() {
    var store = new InMemoryStore(List.of(githubRepo("myrepo")));
    var port = new StubForgePort(ForgeType.GITHUB, List.of());
    var service = new RepositoryService(store, List.of(port));

    var result = service.sync(true).result();

    assertThat(result.updated()).isEqualTo(0);
    assertThat(result.total()).isEqualTo(1);
  }

  @Test
  void sync_savesAllRepos_includingUnchanged() {
    var date = LocalDate.of(2026, 1, 15);
    var github = githubRepo("github-repo");
    var gerrit =
        new Repository(
            "gerrit-repo",
            List.of("vgt/connectivity/gerrit-repo"),
            null,
            null,
            List.of(),
            "team",
            "",
            Map.of(),
            null);
    var store = new InMemoryStore(List.of(github, gerrit));
    // only GITHUB port -- gerrit repo unchanged, github repo updated
    var port =
        new StubForgePort(
            ForgeType.GITHUB, List.of(forgeRepo("github-repo", ForgeType.GITHUB, "", date)));
    var service = new RepositoryService(store, List.of(port));

    service.sync(true);

    var saved = store.findAll().repos();
    assertThat(saved).hasSize(2);
    assertThat(
            saved.stream()
                .filter(r -> r.name().equals("github-repo"))
                .findFirst()
                .orElseThrow()
                .lastBumpDate())
        .isEqualTo(date);
    assertThat(
            saved.stream()
                .filter(r -> r.name().equals("gerrit-repo"))
                .findFirst()
                .orElseThrow()
                .lastBumpDate())
        .isNull();
  }

  @Test
  void sync_preservesStoreFields_afterMerge() {
    var date = LocalDate.of(2026, 1, 15);
    var stored =
        new Repository(
            "myrepo",
            List.of("vgcs-myrepo"),
            null,
            null,
            List.of("alice"),
            "my-team",
            "important comment",
            Map.of("k", "v"),
            null);
    var store = new InMemoryStore(List.of(stored));
    var port =
        new StubForgePort(
            ForgeType.GITHUB, List.of(forgeRepo("myrepo", ForgeType.GITHUB, "", date)));
    var service = new RepositoryService(store, List.of(port));

    service.sync(true);

    var updated = store.findAll().repos().get(0);
    assertThat(updated.lastBumpDate()).isEqualTo(date);
    assertThat(updated.bumpers()).containsExactly("alice");
    assertThat(updated.team()).isEqualTo("my-team");
    assertThat(updated.comments()).isEqualTo("important comment");
    assertThat(updated.extra()).containsEntry("k", "v");
  }

  @Test
  void sync_addsDiscoveredUnknownRepos_toIgnoredList() {
    var trackedRepo = githubRepo("tracked-repo");
    var store = new InMemoryStore(List.of(trackedRepo));
    var port =
        new StubForgePort(
            ForgeType.GITHUB,
            List.of(
                forgeRepo("tracked-repo", ForgeType.GITHUB, "", LocalDate.now()),
                forgeRepo("unknown-repo", ForgeType.GITHUB, "", LocalDate.now())));
    var service = new RepositoryService(store, List.of(port));

    var result = service.sync(true).result();

    assertThat(result.newlyIgnored()).isEqualTo(1);
    assertThat(result.newIgnoredNames()).containsExactly("unknown-repo");
    assertThat(store.findAll().ignored()).hasSize(1);
    assertThat(store.findAll().ignored().get(0).name()).isEqualTo("unknown-repo");
  }

  @Test
  void sync_doesNotDuplicateAlreadyIgnoredRepos() {
    var trackedRepo = githubRepo("tracked-repo");
    var alreadyIgnored = new IgnoredRepository("ignored-repo", List.of("vgcs-ignored-repo"));
    var store = new InMemoryStore(List.of(trackedRepo), List.of(alreadyIgnored));
    var port =
        new StubForgePort(
            ForgeType.GITHUB,
            List.of(
                forgeRepo("tracked-repo", ForgeType.GITHUB, "", LocalDate.now()),
                forgeRepo("ignored-repo", ForgeType.GITHUB, "", LocalDate.now())));
    var service = new RepositoryService(store, List.of(port));

    var result = service.sync(true).result();

    assertThat(result.newlyIgnored()).isEqualTo(0);
    assertThat(result.newIgnoredNames()).isEmpty();
    assertThat(store.findAll().ignored()).containsExactly(alreadyIgnored);
    assertThat(store.findAll().repos().stream().map(Repository::name))
        .doesNotContain("ignored-repo");
  }

  @Test
  void sync_addsToIgnored_whenForgeDiscoversUnknownRepoWithNoDate() {
    var trackedRepo = githubRepo("tracked-repo");
    var store = new InMemoryStore(List.of(trackedRepo));
    var port =
        new StubForgePort(
            ForgeType.GITHUB,
            List.of(
                forgeRepo("tracked-repo", ForgeType.GITHUB, "", LocalDate.now()),
                forgeRepo("new-repo", ForgeType.GITHUB, "", null)));
    var service = new RepositoryService(store, List.of(port));

    var result = service.sync(true).result();

    assertThat(result.newlyIgnored()).isEqualTo(1);
    assertThat(result.newIgnoredNames()).containsExactly("new-repo");
    assertThat(store.findAll().ignored()).hasSize(1);
    assertThat(store.findAll().ignored().get(0).name()).isEqualTo("new-repo");
    assertThat(store.findAll().repos().stream().map(Repository::name)).doesNotContain("new-repo");
  }

  @Test
  void sync_dryRun_doesNotSaveToStore() {
    var newDate = LocalDate.of(2026, 1, 15);
    var repo = githubRepo("myrepo");
    var store = new InMemoryStore(List.of(repo));
    var port =
        new StubForgePort(
            ForgeType.GITHUB,
            List.of(
                forgeRepo("myrepo", ForgeType.GITHUB, "", newDate),
                forgeRepo("new-repo", ForgeType.GITHUB, "", null)));
    var service = new RepositoryService(store, List.of(port));

    service.sync(false);

    assertThat(store.findAll().repos()).containsExactly(repo);
    assertThat(store.findAll().ignored()).isEmpty();
  }

  @Test
  void sync_mergesSameNameFromDifferentForges() {
    var githubDate = LocalDate.of(2026, 1, 10);
    var gerritDate = LocalDate.of(2026, 1, 20);
    var stored =
        new Repository(
            "shared-svc",
            List.of("vgcs-shared-svc"),
            null,
            null,
            List.of(),
            "team",
            "",
            Map.of(),
            null);
    var store = new InMemoryStore(List.of(stored));
    var githubPort =
        new StubForgePort(
            ForgeType.GITHUB, List.of(forgeRepo("shared-svc", ForgeType.GITHUB, "", githubDate)));
    var gerritPort =
        new StubForgePort(
            ForgeType.GERRIT, List.of(forgeRepo("shared-svc", ForgeType.GERRIT, "", gerritDate)));
    var service = new RepositoryService(store, List.of(githubPort, gerritPort));

    service.sync(true);

    var saved = store.findAll().repos();
    assertThat(saved).hasSize(1);
    var merged = saved.get(0);
    assertThat(merged.name()).isEqualTo("shared-svc");
    assertThat(merged.repos())
        .containsExactlyInAnyOrder("vgcs-shared-svc", "vgt/connectivity/shared-svc");
    assertThat(merged.lastBumpDate()).isEqualTo(gerritDate);
  }

  // --- ignore ---

  @Test
  void ignore_movesTrackedRepoToIgnored() {
    var repo = githubRepo("myrepo");
    var store = new InMemoryStore(List.of(repo));
    var service = new RepositoryService(store, List.of());

    var result = service.ignore("myrepo", true);

    assertThat(result).contains("Moved to ignored: myrepo");
    assertThat(store.findAll().repos()).isEmpty();
    assertThat(store.findAll().ignored()).hasSize(1);
    assertThat(store.findAll().ignored().get(0).name()).isEqualTo("myrepo");
  }

  @Test
  void ignore_dryRun_doesNotSaveToStore() {
    var repo = githubRepo("myrepo");
    var store = new InMemoryStore(List.of(repo));
    var service = new RepositoryService(store, List.of());

    var result = service.ignore("myrepo", false);

    assertThat(result).contains("Moved to ignored: myrepo");
    assertThat(store.findAll().repos()).containsExactly(repo);
    assertThat(store.findAll().ignored()).isEmpty();
  }

  @Test
  void ignore_returnsError_whenNotFound() {
    var store = new InMemoryStore(List.of(githubRepo("other")));
    var service = new RepositoryService(store, List.of());

    var result = service.ignore("missing", true);

    assertThat(result).contains("Not found");
    assertThat(store.findAll().repos()).hasSize(1);
  }

  // --- track ---

  @Test
  void track_movesIgnoredRepoToTracked_withForgeData() {
    var date = LocalDate.of(2026, 2, 14);
    var store =
        new InMemoryStore(
            List.of(),
            List.of(new IgnoredRepository("search-service", List.of("vgcs-search-service"))));
    var port =
        new StubForgePort(
            ForgeType.GITHUB, List.of(forgeRepo("search-service", ForgeType.GITHUB, "", date)));
    var service = new RepositoryService(store, List.of(port));

    var result = service.track("search-service", true);

    assertThat(result).contains("Now tracking: search-service");
    assertThat(store.findAll().ignored()).isEmpty();
    assertThat(store.findAll().repos()).hasSize(1);
    var tracked = store.findAll().repos().get(0);
    assertThat(tracked.name()).isEqualTo("search-service");
    assertThat(tracked.lastBumpDate()).isEqualTo(date);
  }

  @Test
  void track_dryRun_doesNotSaveToStore() {
    var ignoredRepo = new IgnoredRepository("search-service", List.of("vgcs-search-service"));
    var store = new InMemoryStore(List.of(), List.of(ignoredRepo));
    var service = new RepositoryService(store, List.of());

    var result = service.track("search-service", false);

    assertThat(result).contains("Now tracking: search-service");
    assertThat(store.findAll().ignored()).containsExactly(ignoredRepo);
    assertThat(store.findAll().repos()).isEmpty();
  }

  @Test
  void track_returnsError_whenNotFound() {
    var store = new InMemoryStore(List.of());
    var service = new RepositoryService(store, List.of());

    var result = service.track("missing", true);

    assertThat(result).contains("Not found");
  }

  @Test
  void track_worksWithoutForgeData() {
    var store =
        new InMemoryStore(
            List.of(), List.of(new IgnoredRepository("orphan", List.of("vgcs-orphan"))));
    var port = new StubForgePort(ForgeType.GITHUB, List.of());
    var service = new RepositoryService(store, List.of(port));

    var result = service.track("orphan", true);

    assertThat(result).contains("Now tracking: orphan");
    assertThat(store.findAll().ignored()).isEmpty();
    assertThat(store.findAll().repos()).hasSize(1);
    var tracked = store.findAll().repos().get(0);
    assertThat(tracked.name()).isEqualTo("orphan");
    assertThat(tracked.lastBumpDate()).isNull();
  }

  // --- forge errors ---

  static class ThrowingForgePort implements ForgePort {
    private final ForgeType type;
    private final String errorMessage;

    ThrowingForgePort(ForgeType type, String errorMessage) {
      this.type = type;
      this.errorMessage = errorMessage;
    }

    @Override
    public ForgeType forgeType() {
      return type;
    }

    @Override
    public List<Repository> discoverAll() {
      throw new RuntimeException(errorMessage);
    }
  }

  @Test
  void sync_reportsForgeError_whenForgeThrows() {
    var store = new InMemoryStore(List.of());
    var throwing = new ThrowingForgePort(ForgeType.GITHUB, "connection refused");
    var service = new RepositoryService(store, List.of(throwing));

    var result = service.sync(false).result();

    assertThat(result.forgeErrors()).hasSize(1);
    assertThat(result.forgeErrors().get(0)).contains("GITHUB").contains("connection refused");
  }

  @Test
  void sync_continuesWithOtherForges_whenOneForgeThrows() {
    var gerritRepo = forgeRepo("gerrit-project", ForgeType.GERRIT, "", null);
    var store = new InMemoryStore(List.of());
    var throwing = new ThrowingForgePort(ForgeType.GITHUB, "timeout");
    var healthy = new StubForgePort(ForgeType.GERRIT, List.of(gerritRepo));
    var service = new RepositoryService(store, List.of(throwing, healthy));

    var data = service.sync(false);

    assertThat(data.result().forgeErrors()).hasSize(1);
    assertThat(data.result().forgeErrors().get(0)).contains("GITHUB");
    // gerrit-project was discovered and added to ignored (new, unknown repo)
    assertThat(data.result().newIgnoredNames()).containsExactly("gerrit-project");
  }

  @Test
  void sync_forgeErrors_areEmptyWhenNoErrors() {
    var store = new InMemoryStore(List.of(githubRepo("myrepo")));
    var port =
        new StubForgePort(
            ForgeType.GITHUB,
            List.of(forgeRepo("myrepo", ForgeType.GITHUB, "", LocalDate.of(2026, 1, 1))));
    var service = new RepositoryService(store, List.of(port));

    var result = service.sync(false).result();

    assertThat(result.forgeErrors()).isEmpty();
  }

  @Test
  void track_continuesWithNoForgeData_whenForgeThrows() {
    var store =
        new InMemoryStore(
            List.of(), List.of(new IgnoredRepository("myrepo", List.of("vgcs-myrepo"))));
    var throwing = new ThrowingForgePort(ForgeType.GITHUB, "403 Forbidden");
    var service = new RepositoryService(store, List.of(throwing));

    var result = service.track("myrepo", true);

    assertThat(result).contains("Now tracking: myrepo");
    assertThat(store.findAll().repos()).hasSize(1);
    assertThat(store.findAll().repos().get(0).lastBumpDate()).isNull();
  }
}
