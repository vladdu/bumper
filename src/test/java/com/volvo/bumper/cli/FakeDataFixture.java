package com.volvo.bumper.cli;

import com.volvo.bumper.adapter.confluence.JsonConfluenceRepositoryStore;
import com.volvo.bumper.adapter.gerrit.JsonGerritForgeAdapter;
import com.volvo.bumper.adapter.github.JsonGitHubForgeAdapter;
import com.volvo.bumper.application.RepositoryService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Writes JSON test data to temp files and wires up Json adapters + RepositoryService. */
final class FakeDataFixture {

  static final String CONFLUENCE_JSON =
      """
      {
        "repos": [
          {
            "repos": ["vgcs-platform-core"],
            "bumpers": [],
            "team": "platform-team",
            "comments": "Core platform library"
          },
          {
            "repos": ["vgt/connectivity/auth-service"],
            "bumpers": [],
            "team": "security-team",
            "comments": "Authentication service",
            "extra": { "criticality": "high" }
          },
          {
            "repos": ["vgcs-payments-gateway"],
            "lastBumpDate": "2026-03-18",
            "lastBumpBy": "alice",
            "bumpers": ["alice", "bob"],
            "team": "payments-team",
            "comments": "Payment processing gateway"
          },
          {
            "repos": ["vgt/connectivity/notification-service"],
            "lastBumpDate": "2026-03-19",
            "lastBumpBy": "bob",
            "bumpers": [],
            "team": "infra-team",
            "comments": "Async notification dispatcher"
          }
        ],
        "ignored": [
          { "repos": ["vgcs-old-monolith"] },
          { "repos": ["vgt/connectivity/deprecated-sync"] }
        ]
      }
      """;

  static final String GITHUB_JSON =
      """
      [
        {
          "name": "platform-core",
          "forgeUrl": "https://github.com/VolvoGroup-Internal/vgcs-platform-core"
        },
        {
          "name": "payments-gateway",
          "forgeUrl": "https://github.com/VolvoGroup-Internal/vgcs-payments-gateway"
        },
        {
          "name": "search-service",
          "forgeUrl": "https://github.com/VolvoGroup-Internal/vgcs-search-service"
        },
        {
          "name": "old-monolith",
          "forgeUrl": "https://github.com/VolvoGroup-Internal/vgcs-old-monolith"
        }
      ]
      """;

  static final String GITHUB_DETAILS_JSON =
      """
      {
        "vgcs-platform-core": { "lastDependencyUpdate": "2026-02-14" },
        "vgcs-payments-gateway": { "lastDependencyUpdate": "2026-02-14" },
        "vgcs-old-monolith": { "lastDependencyUpdate": "2026-02-14" }
      }
      """;

  static final String GERRIT_JSON =
      """
      [
        {
          "name": "auth-service",
          "forgeUrl": "https://git.vgt.volvo.com/admin/repos/vgt/connectivity/auth-service"
        },
        {
          "name": "notification-service",
          "forgeUrl": "https://git.vgt.volvo.com/admin/repos/vgt/connectivity/notification-service"
        },
        {
          "name": "legacy-api",
          "forgeUrl": "https://git.vgt.volvo.com/admin/repos/vgt/connectivity/legacy-api"
        },
        {
          "name": "deprecated-sync",
          "forgeUrl": "https://git.vgt.volvo.com/admin/repos/vgt/connectivity/deprecated-sync"
        }
      ]
      """;

  static final String GERRIT_DETAILS_JSON =
      """
      {
        "vgt/connectivity/auth-service": { "lastDependencyUpdate": "2026-01-20", "lastDependencyUpdateBy": "anton" },
        "vgt/connectivity/notification-service": { "lastDependencyUpdate": "2026-01-20" },
        "vgt/connectivity/legacy-api": { "lastDependencyUpdate": "2026-01-20" },
        "vgt/connectivity/deprecated-sync": { "lastDependencyUpdate": "2026-01-20" }
      }
      """;

  private static final JsonMapper MAPPER =
      JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

  /** Writes JSON files to {@code tempDir} and returns a wired-up RepositoryService. */
  static RepositoryService create(Path tempDir) throws Exception {
    var confluencePath = tempDir.resolve("confluence.json");
    var githubPath = tempDir.resolve("github.json");
    var githubDetailsPath = tempDir.resolve("github-details.json");
    var gerritPath = tempDir.resolve("gerrit.json");
    var gerritDetailsPath = tempDir.resolve("gerrit-details.json");
    Files.writeString(confluencePath, CONFLUENCE_JSON);
    Files.writeString(githubPath, GITHUB_JSON);
    Files.writeString(githubDetailsPath, GITHUB_DETAILS_JSON);
    Files.writeString(gerritPath, GERRIT_JSON);
    Files.writeString(gerritDetailsPath, GERRIT_DETAILS_JSON);

    var store = new JsonConfluenceRepositoryStore(MAPPER, confluencePath, confluencePath);
    var github = new JsonGitHubForgeAdapter(MAPPER, githubPath, githubDetailsPath);
    var gerrit = new JsonGerritForgeAdapter(MAPPER, gerritPath, gerritDetailsPath);
    return new RepositoryService(store, List.of(github, gerrit));
  }

  private FakeDataFixture() {}
}
