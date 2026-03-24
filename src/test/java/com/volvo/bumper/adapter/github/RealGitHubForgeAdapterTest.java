package com.volvo.bumper.adapter.github;

import static org.assertj.core.api.Assertions.assertThat;

import com.volvo.bumper.adapter.github.RealGitHubForgeAdapter.GitHubFilter;
import com.volvo.bumper.config.GitHubProperties;
import com.volvo.bumper.domain.ForgeType;
import com.volvo.bumper.domain.Repository;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class RealGitHubForgeAdapterTest {

  private static final GitHubProperties FAKE_PROPS = new GitHubProperties("test-token");
  private static final GitHubFilter FILTER = new GitHubFilter("MyOrg", "my-team");
  private static final JsonMapper JSON = JsonMapper.builder().build();

  /** Stub that overrides HTTP methods so tests run without real network calls. */
  private static class StubGitHubAdapter extends RealGitHubForgeAdapter {
    private final JsonNode reposPage;
    private final JsonNode commitsPage;

    StubGitHubAdapter(JsonNode reposPage, JsonNode commitsPage) {
      super(FAKE_PROPS, FILTER);
      this.reposPage = reposPage;
      this.commitsPage = commitsPage;
    }

    @Override
    protected JsonNode getTeamReposPage(String url) throws IOException, InterruptedException {
      // return empty array on page 2+ to stop pagination
      if (url.contains("page=1")) return reposPage;
      return JSON.readTree("[]");
    }

    @Override
    protected JsonNode getCommitsPage(String url) throws IOException, InterruptedException {
      if (url.contains("page=1")) return commitsPage;
      return JSON.readTree("[]");
    }
  }

  private static JsonNode repos(String... names) throws Exception {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < names.length; i++) {
      if (i > 0) sb.append(",");
      sb.append("{\"name\":\"")
          .append(names[i])
          .append("\",")
          .append("\"html_url\":\"https://github.com/MyOrg/")
          .append(names[i])
          .append("\"}");
    }
    sb.append("]");
    return JSON.readTree(sb.toString());
  }

  private static JsonNode commitsWithBump() throws Exception {
    return JSON.readTree(
        """
        [
          {
            "commit": {
              "message": "Bump axios from 1 to 2",
              "author": {"name": "Alice", "date": "2024-03-15T10:00:00Z"}
            },
            "html_url": "https://github.com/MyOrg/my-repo/commit/abc"
          }
        ]
        """);
  }

  private static JsonNode commitsWithoutBump() throws Exception {
    return JSON.readTree(
        """
        [
          {
            "commit": {
              "message": "Add new feature",
              "author": {"name": "Bob", "date": "2024-03-10T10:00:00Z"}
            },
            "html_url": "https://github.com/MyOrg/my-repo/commit/def"
          }
        ]
        """);
  }

  @Test
  void forgeType_returnsGitHub() throws Exception {
    var adapter = new StubGitHubAdapter(repos(), JSON.readTree("[]"));
    assertThat(adapter.forgeType()).isEqualTo(ForgeType.GITHUB);
  }

  @Test
  void discoverAll_returnsOneRepositoryPerTeamRepo() throws Exception {
    var adapter = new StubGitHubAdapter(repos("repo-a", "repo-b"), commitsWithoutBump());
    List<Repository> result = adapter.discoverAll();
    assertThat(result).hasSize(2);
  }

  @Test
  void discoverAll_buildsCorrectForgeUrl() throws Exception {
    var adapter = new StubGitHubAdapter(repos("my-repo"), commitsWithoutBump());
    Repository repo = adapter.discoverAll().get(0);
    assertThat(repo.forgeUrls()).containsExactly("https://github.com/VolvoGroup-Internal/my-repo");
  }

  @Test
  void discoverAll_populatesBumpDateAndAuthorFromCommit() throws Exception {
    var adapter = new StubGitHubAdapter(repos("my-repo"), commitsWithBump());
    Repository repo = adapter.discoverAll().get(0);
    assertThat(repo.lastBumpDate()).isEqualTo(LocalDate.of(2024, 3, 15));
    assertThat(repo.lastBumpBy()).isEqualTo("Alice");
  }

  @Test
  void discoverAll_returnsNullDateWhenNoBumpCommit() throws Exception {
    var adapter = new StubGitHubAdapter(repos("my-repo"), commitsWithoutBump());
    Repository repo = adapter.discoverAll().get(0);
    assertThat(repo.lastBumpDate()).isNull();
    assertThat(repo.lastBumpBy()).isNull();
  }

  @Test
  void discoverAll_storeOwnedFieldsAreNull() throws Exception {
    var adapter = new StubGitHubAdapter(repos("my-repo"), commitsWithoutBump());
    Repository repo = adapter.discoverAll().get(0);
    assertThat(repo.team()).isNull();
    assertThat(repo.comments()).isNull();
    assertThat(repo.extra()).isEmpty();
  }

  @Test
  void discoverAll_emptyRepoListReturnsEmptyResult() throws Exception {
    var adapter = new StubGitHubAdapter(JSON.readTree("[]"), JSON.readTree("[]"));
    assertThat(adapter.discoverAll()).isEmpty();
  }

  @Test
  void discoverAll_setsErrorOnRepo_whenBuildFails() throws Exception {
    var failingAdapter =
        new StubGitHubAdapter(repos("good-repo", "bad-repo"), commitsWithBump()) {
          @Override
          protected JsonNode getCommitsPage(String url) throws IOException, InterruptedException {
            if (url.contains("bad-repo")) throw new RuntimeException("API error for bad-repo");
            return super.getCommitsPage(url);
          }
        };

    List<Repository> result = failingAdapter.discoverAll();

    assertThat(result).hasSize(2);
    Repository good =
        result.stream().filter(r -> r.name().equals("good-repo")).findFirst().orElseThrow();
    Repository bad =
        result.stream().filter(r -> r.name().equals("bad-repo")).findFirst().orElseThrow();
    assertThat(good.error()).isNull();
    assertThat(bad.error()).contains("API error for bad-repo");
  }
}
