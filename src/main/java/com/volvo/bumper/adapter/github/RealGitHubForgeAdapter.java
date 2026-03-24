package com.volvo.bumper.adapter.github;

import com.volvo.bumper.config.GitHubProperties;
import com.volvo.bumper.domain.BumpPattern;
import com.volvo.bumper.domain.ForgeType;
import com.volvo.bumper.domain.Repository;
import com.volvo.bumper.port.ForgePort;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Live GitHub implementation of {@link ForgePort}.
 *
 * <p>Discovers repositories that belong to a GitHub team, then scans commit history per repository
 * to find the latest dependency-bump commit.
 *
 * <p>Wired by {@link com.volvo.bumper.config.RealAdaptersConfig} when the {@code real} Spring
 * profile is active.
 */
public class RealGitHubForgeAdapter implements ForgePort {

  /**
   * Configures the GitHub organisation and team this adapter should discover repositories for.
   *
   * @param org GitHub organisation slug (e.g. {@code VolvoGroup-Internal})
   * @param team GitHub team slug (e.g. {@code vgcs-cos})
   */
  public record GitHubFilter(String org, String team) {}

  private static final String GITHUB_API_URL = "https://api.github.com";

  private final GitHubProperties config;
  private final ObjectMapper mapper;
  private final HttpClient httpClient;
  private final GitHubFilter filter;

  /** For tests in the same package — uses a plain {@link ObjectMapper} and default HTTP client. */
  RealGitHubForgeAdapter(GitHubProperties config, GitHubFilter filter) {
    this(config, new ObjectMapper(), filter);
  }

  public RealGitHubForgeAdapter(GitHubProperties config, ObjectMapper mapper, GitHubFilter filter) {
    this.config = config;
    this.mapper = mapper;
    this.httpClient = HttpClients.newClient();
    this.filter = filter;
  }

  /** Returns the active filter. Overridable in tests. */
  protected GitHubFilter filter() {
    return filter;
  }

  @Override
  public ForgeType forgeType() {
    return ForgeType.GITHUB;
  }

  @Override
  public List<Repository> discoverAll() {
    GitHubFilter f = filter();
    List<RepoRef> refs = listTeamRepos(f.org(), f.team());
    try (ExecutorService executor =
        Executors.newFixedThreadPool(24, Thread.ofVirtual().factory())) {
      List<Future<Repository>> futures = new ArrayList<>(refs.size());
      for (RepoRef ref : refs) {
        futures.add(executor.submit(() -> buildRepository(f.org(), ref)));
      }
      List<Repository> repos = new ArrayList<>(refs.size());
      for (int i = 0; i < futures.size(); i++) {
        RepoRef ref = refs.get(i);
        try {
          repos.add(futures.get(i).get());
        } catch (ExecutionException e) {
          Throwable cause = e.getCause();
          String msg = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getName();
          String repo = ref.name();
          String name = GitHubForgeNaming.nameFromRepo(repo);
          repos.add(Repository.withError(name, List.of(repo), msg));
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new RuntimeException(e);
        }
      }
      return repos;
    }
  }

  // -------------------------------------------------------------------------
  // Private helpers
  // -------------------------------------------------------------------------

  private record RepoRef(String name, String htmlUrl) {}

  private List<RepoRef> listTeamRepos(String org, String team) {
    String baseUrl = GITHUB_API_URL + "/orgs/" + org + "/teams/" + team + "/repos";
    List<RepoRef> refs = new ArrayList<>();
    try {
      for (int page = 1; ; page++) {
        JsonNode batch = getTeamReposPage(baseUrl + "?per_page=100&page=" + page);
        if (!batch.isArray() || batch.isEmpty()) break;
        for (JsonNode item : batch) {
          refs.add(new RepoRef(item.get("name").asText(), item.get("html_url").asText()));
        }
        if (batch.size() < 100) break;
      }
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException("Failed to list GitHub team repos: " + e.getMessage(), e);
    }
    return refs;
  }

  private Repository buildRepository(String org, RepoRef ref) {
    String repo = ref.name();
    String name = GitHubForgeNaming.nameFromRepo(repo);
    BumpCommit bump = findLatestBumpCommit(org, repo);
    return new Repository(
        name, List.of(repo), bump.date(), bump.author(), null, null, null, null, null);
  }

  private record BumpCommit(LocalDate date, String author) {
    static BumpCommit empty() {
      return new BumpCommit(null, null);
    }
  }

  private BumpCommit findLatestBumpCommit(String org, String repoName) {
    String baseUrl = GITHUB_API_URL + "/repos/" + org + "/" + repoName + "/commits";
    try {
      for (int page = 1; page <= 2; page++) {
        JsonNode commits = getCommitsPage(baseUrl + "?per_page=100&page=" + page);
        if (!commits.isArray() || commits.isEmpty()) break;
        for (JsonNode commit : commits) {
          String message = commit.path("commit").path("message").asText("");
          String firstLine =
              message.contains("\n") ? message.substring(0, message.indexOf('\n')) : message;
          if (!BumpPattern.isBumpCommit(firstLine)) continue;
          String dateStr = commit.path("commit").path("author").path("date").asText("");
          LocalDate date =
              dateStr.length() >= 10 ? LocalDate.parse(dateStr.substring(0, 10)) : null;
          String author = commit.path("commit").path("author").path("name").asText("");
          return new BumpCommit(date, author.isEmpty() ? null : author);
        }
        if (commits.size() < 100) break;
      }
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException(
          "GitHub API error for " + org + "/" + repoName + ": " + e.getMessage(), e);
    }
    return BumpCommit.empty();
  }

  /**
   * Fetches a page of team repository listing results from GitHub.
   *
   * <p>Protected to allow test subclasses to supply fixture data without real HTTP requests.
   *
   * @param url full URL with pagination parameters
   * @return parsed JSON array node
   */
  protected JsonNode getTeamReposPage(String url) throws IOException, InterruptedException {
    return get(url);
  }

  /**
   * Fetches a page of commit results from GitHub.
   *
   * <p>Protected to allow test subclasses to supply fixture data without real HTTP requests.
   *
   * @param url full URL with pagination parameters
   * @return parsed JSON array node
   */
  protected JsonNode getCommitsPage(String url) throws IOException, InterruptedException {
    return get(url);
  }

  private JsonNode get(String url) throws IOException, InterruptedException {
    String token = config.token();
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + token)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build();
    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new RuntimeException("GitHub API error " + response.statusCode() + " for " + url);
    }
    return mapper.readTree(response.body());
  }
}
