package com.volvo.bumper.config;

import com.volvo.bumper.adapter.confluence.RealConfluenceRepositoryStore;
import com.volvo.bumper.adapter.gerrit.RealGerritForgeAdapter;
import com.volvo.bumper.adapter.gerrit.RealGerritForgeAdapter.GerritFilter;
import com.volvo.bumper.adapter.github.RealGitHubForgeAdapter;
import com.volvo.bumper.adapter.github.RealGitHubForgeAdapter.GitHubFilter;
import com.volvo.bumper.port.ForgePort;
import com.volvo.bumper.port.RepositoryStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.Assert;
import tools.jackson.databind.ObjectMapper;

/**
 * Spring configuration for the "real" profile.
 *
 * <p>Wires {@link RealGerritForgeAdapter}, {@link RealGitHubForgeAdapter}, and {@link
 * RealConfluenceRepositoryStore} with their hard-coded filter configuration and validates that the
 * required credentials are present.
 */
@Configuration
@Profile("real")
public class RealAdaptersConfig {

  private static final String GERRIT_NAMESPACE = "vgt/connectivity/";
  private static final String GERRIT_ACL_GROUP = "VGCS-ConnectivityServices";
  private static final String GITHUB_ORG = "VolvoGroup-Internal";
  private static final String GITHUB_TEAM = "vgcs-cos";

  @Bean
  ForgePort realGerritForgeAdapter(GerritProperties gerrit, ObjectMapper mapper) {
    Assert.hasText(
        gerrit.user(), "GERRIT_SSH_USER must not be blank (required for profile 'real')");
    Assert.hasText(gerrit.key(), "GERRIT_SSH_KEY must not be blank (required for profile 'real')");
    return new RealGerritForgeAdapter(
        gerrit, mapper, new GerritFilter(GERRIT_NAMESPACE, GERRIT_ACL_GROUP));
  }

  @Bean
  ForgePort realGitHubForgeAdapter(GitHubProperties github, ObjectMapper mapper) {
    Assert.hasText(github.token(), "GITHUB_TOKEN must not be blank (required for profile 'real')");
    return new RealGitHubForgeAdapter(github, mapper, new GitHubFilter(GITHUB_ORG, GITHUB_TEAM));
  }

  @Bean
  RepositoryStore realConfluenceRepositoryStore(
      ConfluenceProperties confluence, ObjectMapper mapper) {
    Assert.hasText(
        confluence.url(), "CONFLUENCE_URL must not be blank (required for profile 'real')");
    Assert.hasText(
        confluence.username(),
        "CONFLUENCE_USERNAME must not be blank (required for profile 'real')");
    Assert.hasText(
        confluence.apiToken(),
        "CONFLUENCE_API_TOKEN must not be blank (required for profile 'real')");
    Assert.hasText(
        confluence.pageIdWrite(),
        "CONFLUENCE_PAGE_ID_WRITE must not be blank (required for profile 'real')");
    return new RealConfluenceRepositoryStore(confluence, mapper);
  }
}
