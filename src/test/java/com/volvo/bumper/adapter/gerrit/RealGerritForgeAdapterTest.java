package com.volvo.bumper.adapter.gerrit;

import static org.assertj.core.api.Assertions.assertThat;

import com.volvo.bumper.adapter.gerrit.RealGerritForgeAdapter.GerritFilter;
import com.volvo.bumper.config.GerritProperties;
import com.volvo.bumper.domain.ForgeType;
import com.volvo.bumper.domain.Repository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RealGerritForgeAdapterTest {

  private static final GerritProperties FAKE_PROPS = new GerritProperties("test-user", "/dev/null");
  private static final GerritFilter FILTER =
      new GerritFilter("vgt/connectivity/", "VGCS-ConnectivityServices");

  /** Stub that overrides {@code runSshCommand} so tests run without a real SSH connection. */
  private static class StubGerritAdapter extends RealGerritForgeAdapter {
    private final String lsProjectsOutput;
    private final String queryOutput;

    StubGerritAdapter(String lsProjectsOutput, String queryOutput) {
      super(FAKE_PROPS, FILTER);
      this.lsProjectsOutput = lsProjectsOutput;
      this.queryOutput = queryOutput;
    }

    @Override
    protected String runSshCommand(List<String> commandTokens) {
      if (commandTokens.contains("ls-projects")) return lsProjectsOutput;
      return queryOutput;
    }
  }

  private static final String LS_OUTPUT =
      "vgt/connectivity/svc-alpha\nvgt/connectivity/svc-beta\nvgt/connectivity/other\n";

  private static final String BUMP_QUERY_OUTPUT =
      "{\"number\":\"42\",\"subject\":\"Bump axios from 1 to 2\","
          + "\"lastUpdated\":1700000000,"
          + "\"owner\":{\"name\":\"Alice\"},"
          + "\"currentPatchSet\":{\"author\":{\"name\":\"Alice\"}}}\n"
          + "{\"type\":\"stats\",\"rowCount\":1,\"runTimeMilliseconds\":5}\n";

  private static final String NO_BUMP_QUERY_OUTPUT =
      "{\"number\":\"1\",\"subject\":\"Add feature X\","
          + "\"lastUpdated\":1700000000,"
          + "\"owner\":{\"name\":\"Bob\"},"
          + "\"currentPatchSet\":{\"author\":{\"name\":\"Bob\"}}}\n";

  @Test
  void forgeType_returnsGerrit() {
    var adapter = new StubGerritAdapter(LS_OUTPUT, "");
    assertThat(adapter.forgeType()).isEqualTo(ForgeType.GERRIT);
  }

  @Test
  void discoverAll_returnsOneRepositoryPerProject() {
    var adapter = new StubGerritAdapter(LS_OUTPUT, NO_BUMP_QUERY_OUTPUT);
    List<Repository> repos = adapter.discoverAll();
    assertThat(repos).hasSize(3);
  }

  @Test
  void discoverAll_buildsForgeUrl() {
    var adapter = new StubGerritAdapter("vgt/connectivity/my-svc\n", NO_BUMP_QUERY_OUTPUT);
    Repository repo = adapter.discoverAll().get(0);
    assertThat(repo.forgeUrls())
        .containsExactly("https://git.vgt.volvo.com/admin/repos/vgt/connectivity/my-svc");
  }

  @Test
  void discoverAll_usesLastPathSegmentAsName() {
    var adapter = new StubGerritAdapter("vgt/connectivity/my-svc\n", NO_BUMP_QUERY_OUTPUT);
    Repository repo = adapter.discoverAll().get(0);
    assertThat(repo.name()).isEqualTo("my-svc");
  }

  @Test
  void discoverAll_populatesBumpDateAndAuthorFromCommit() {
    var adapter = new StubGerritAdapter("vgt/connectivity/my-svc\n", BUMP_QUERY_OUTPUT);
    Repository repo = adapter.discoverAll().get(0);
    assertThat(repo.lastBumpDate()).isEqualTo(LocalDate.of(2023, 11, 14));
    assertThat(repo.lastBumpBy()).isEqualTo("Alice");
  }

  @Test
  void discoverAll_returnsNullDateWhenNoBumpCommitFound() {
    var adapter = new StubGerritAdapter("vgt/connectivity/my-svc\n", NO_BUMP_QUERY_OUTPUT);
    Repository repo = adapter.discoverAll().get(0);
    assertThat(repo.lastBumpDate()).isNull();
    assertThat(repo.lastBumpBy()).isNull();
  }

  @Test
  void discoverAll_skipsMalformedJsonLines() {
    String mixed = "not-json\n" + BUMP_QUERY_OUTPUT;
    var adapter = new StubGerritAdapter("vgt/connectivity/my-svc\n", mixed);
    Repository repo = adapter.discoverAll().get(0);
    assertThat(repo.lastBumpDate()).isNotNull();
  }

  @Test
  void discoverAll_storeOwnedFieldsAreNull() {
    var adapter = new StubGerritAdapter("vgt/connectivity/my-svc\n", NO_BUMP_QUERY_OUTPUT);
    Repository repo = adapter.discoverAll().get(0);
    assertThat(repo.team()).isNull();
    assertThat(repo.comments()).isNull();
    assertThat(repo.extra()).isEmpty();
  }

  @Test
  void discoverAll_setsErrorOnRepo_whenBuildFails() {
    var failingAdapter =
        new StubGerritAdapter(LS_OUTPUT, BUMP_QUERY_OUTPUT) {
          @Override
          protected String runSshCommand(List<String> commandTokens) {
            if (commandTokens.stream().anyMatch(t -> t.contains("svc-beta"))) {
              throw new RuntimeException("SSH error for svc-beta");
            }
            return super.runSshCommand(commandTokens);
          }
        };

    List<Repository> result = failingAdapter.discoverAll();

    assertThat(result).hasSize(3);
    Repository good =
        result.stream().filter(r -> r.name().equals("svc-alpha")).findFirst().orElseThrow();
    Repository bad =
        result.stream().filter(r -> r.name().equals("svc-beta")).findFirst().orElseThrow();
    assertThat(good.error()).isNull();
    assertThat(bad.error()).contains("SSH error for svc-beta");
  }
}
