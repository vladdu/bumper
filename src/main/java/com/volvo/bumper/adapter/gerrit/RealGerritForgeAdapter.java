package com.volvo.bumper.adapter.gerrit;

import com.volvo.bumper.config.GerritProperties;
import com.volvo.bumper.domain.BumpPattern;
import com.volvo.bumper.domain.ForgeType;
import com.volvo.bumper.domain.Repository;
import com.volvo.bumper.port.ForgePort;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Live Gerrit implementation of {@link ForgePort}.
 *
 * <p>Discovers repositories in a Gerrit namespace via SSH {@code gerrit ls-projects}, then queries
 * merged changes per project to find the latest dependency-bump commit.
 *
 * <p>Wired by {@link com.volvo.bumper.config.RealAdaptersConfig} when the {@code real} Spring
 * profile is active.
 */
public class RealGerritForgeAdapter implements ForgePort {

  /**
   * Configures the set of projects this adapter should discover.
   *
   * @param namespace Gerrit project namespace prefix (e.g. {@code vgt/connectivity/})
   * @param aclGroup Gerrit group used to filter projects by ACL membership
   */
  public record GerritFilter(String namespace, String aclGroup) {}

  private static final String GERRIT_HOST = "git.vgt.volvo.com";
  private static final int GERRIT_SSH_PORT = 29418;
  private static final String GERRIT_BASE_URL = "https://git.vgt.volvo.com";
  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private final GerritProperties config;
  private final ObjectMapper mapper;
  private final GerritFilter filter;

  /** For tests in the same package — uses a plain {@link ObjectMapper}. */
  RealGerritForgeAdapter(GerritProperties config, GerritFilter filter) {
    this(config, new ObjectMapper(), filter);
  }

  public RealGerritForgeAdapter(GerritProperties config, ObjectMapper mapper, GerritFilter filter) {
    this.config = config;
    this.mapper = mapper;
    this.filter = filter;
  }

  /** Returns the active filter. Overridable in tests. */
  protected GerritFilter filter() {
    return filter;
  }

  @Override
  public ForgeType forgeType() {
    return ForgeType.GERRIT;
  }

  @Override
  public List<Repository> discoverAll() {
    GerritFilter f = filter();
    List<String> projects = listProjects(f.namespace(), f.aclGroup());
    try (ExecutorService executor =
        Executors.newFixedThreadPool(24, Thread.ofVirtual().factory())) {
      List<Future<Repository>> futures = new ArrayList<>(projects.size());
      for (String project : projects) {
        futures.add(executor.submit(() -> buildRepository(project)));
      }
      List<Repository> repos = new ArrayList<>(projects.size());
      for (int i = 0; i < futures.size(); i++) {
        String project = projects.get(i);
        try {
          repos.add(futures.get(i).get());
        } catch (ExecutionException e) {
          Throwable cause = e.getCause();
          String msg = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getName();
          String name = GerritForgeNaming.nameFromRepo(project);
          repos.add(Repository.withError(name, List.of(project), msg));
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

  private List<String> listProjects(String namespace, String aclGroup) {
    String output =
        runSshCommand(List.of("gerrit", "ls-projects", "-p", namespace, "--has-acl-for", aclGroup));
    List<String> projects = new ArrayList<>();
    for (String line : output.split("\n")) {
      String name = line.strip();
      if (!name.isEmpty()) {
        projects.add(name);
      }
    }
    return projects;
  }

  private Repository buildRepository(String project) {
    String name = GerritForgeNaming.nameFromRepo(project);
    BumpCommit bump = findLatestBumpCommit(project);
    return new Repository(
        name, List.of(project), bump.date(), bump.author(), null, null, null, null, null);
  }

  private record BumpCommit(LocalDate date, String author) {
    static BumpCommit empty() {
      return new BumpCommit(null, null);
    }
  }

  private BumpCommit findLatestBumpCommit(String project) {
    String output =
        runSshCommand(
            List.of(
                "gerrit",
                "query",
                "--format=JSON",
                "--current-patch-set",
                "status:merged",
                "project:" + project,
                "limit:200"));

    for (String line : output.split("\n")) {
      line = line.strip();
      if (line.isEmpty()) continue;
      try {
        JsonNode data = mapper.readTree(line);
        String subject = data.path("subject").asText("");
        if (subject.isEmpty() || !BumpPattern.isBumpCommit(subject)) continue;

        long ts =
            data.has("lastUpdated")
                ? data.get("lastUpdated").asLong()
                : data.path("createdOn").asLong(0);
        LocalDate date = Instant.ofEpochSecond(ts).atZone(ZoneId.systemDefault()).toLocalDate();

        JsonNode patchSet = data.path("currentPatchSet");
        String author = patchSet.path("author").path("name").asText("");
        if (author.isEmpty()) {
          author = data.path("owner").path("name").asText("");
        }
        return new BumpCommit(date, author.isEmpty() ? null : author);
      } catch (Exception e) {
        // skip malformed lines (e.g. the trailing stats object)
      }
    }
    return BumpCommit.empty();
  }

  /**
   * Runs the given Gerrit command over SSH using the system {@code ssh} binary.
   *
   * <p>Protected to allow test subclasses to supply fixture output without a real SSH connection.
   *
   * @param commandTokens the Gerrit command tokens (e.g. {@code ["gerrit", "ls-projects", ...]})
   * @return stdout of the command
   * @throws RuntimeException when the command exits non-zero or an I/O error occurs
   */
  protected String runSshCommand(List<String> commandTokens) {
    String user = config.user();
    String keyPath = config.key();
    List<String> tokens = new ArrayList<>();
    tokens.add("ssh");
    tokens.add("-l");
    tokens.add(user);
    tokens.add("-i");
    tokens.add(keyPath);
    tokens.add("-p");
    tokens.add(String.valueOf(GERRIT_SSH_PORT));
    tokens.add(GERRIT_HOST);
    tokens.addAll(commandTokens);
    try {
      ProcessBuilder pb = new ProcessBuilder(tokens).redirectErrorStream(false);
      Process process = pb.start();
      byte[] stdoutBytes = process.getInputStream().readAllBytes();
      byte[] stderrBytes = process.getErrorStream().readAllBytes();
      int exitCode = process.waitFor();
      String stderr = new String(stderrBytes, StandardCharsets.UTF_8);
      if (exitCode != 0) {
        throw new RuntimeException(
            "Gerrit SSH command failed (exit " + exitCode + "): " + stderr.strip());
      }
      return new String(stdoutBytes, StandardCharsets.UTF_8);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Gerrit SSH error: " + e.getMessage(), e);
    }
  }
}
