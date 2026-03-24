package com.volvo.bumper.adapter.github;

/**
 * Utility for converting between GitHub repository URLs and the canonical repo path/name used in
 * this application.
 *
 * <p>GitHub URL format: {@code https://github.com/VolvoGroup-Internal/<repo>}
 *
 * <p>GitHub repo naming convention: the stored {@code repo} field is the full repository name (e.g.
 * {@code vgcs-asset-connectivity-cdk-utils}). The display {@code name} strips the {@code vgcs-}
 * prefix if present.
 */
final class GitHubForgeNaming {

  private static final String URL_PREFIX = "https://github.com/VolvoGroup-Internal/";

  private GitHubForgeNaming() {}

  /**
   * Extracts the repository path from a GitHub URL.
   *
   * @param url a GitHub repository URL (e.g. {@code
   *     https://github.com/VolvoGroup-Internal/vgcs-foo})
   * @return the repository name segment (e.g. {@code vgcs-foo})
   */
  static String repoFromUrl(String url) {
    return url.substring(url.lastIndexOf('/') + 1);
  }

  /**
   * Derives the display name from a GitHub repository path by stripping the {@code vgcs-} prefix.
   *
   * @param repo the repository name (e.g. {@code vgcs-foo})
   * @return the display name (e.g. {@code foo}); unchanged if no prefix present
   */
  static String nameFromRepo(String repo) {
    return repo.startsWith("vgcs-") ? repo.substring("vgcs-".length()) : repo;
  }
}
