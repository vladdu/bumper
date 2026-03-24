package com.volvo.bumper.adapter.gerrit;

/**
 * Utility for converting between Gerrit repository URLs and the canonical repo path/name used in
 * this application.
 *
 * <p>Gerrit URL format: {@code https://git.vgt.volvo.com/admin/repos/<path>} where {@code <path>}
 * is the full slash-delimited project path (e.g. {@code vgt/connectivity/api-specifications}).
 *
 * <p>The display {@code name} is the last path segment (e.g. {@code api-specifications}).
 */
final class GerritForgeNaming {

  private static final String URL_BASE = "https://git.vgt.volvo.com/admin/repos/";

  private GerritForgeNaming() {}

  /**
   * Extracts the repository path from a Gerrit URL.
   *
   * @param url a Gerrit repository URL (e.g. {@code
   *     https://git.vgt.volvo.com/admin/repos/vgt/connectivity/api-specifications})
   * @return the full project path (e.g. {@code vgt/connectivity/api-specifications})
   */
  static String repoFromUrl(String url) {
    int idx = url.indexOf("/admin/repos/");
    return url.substring(idx + "/admin/repos/".length());
  }

  /**
   * Derives the display name from a Gerrit repository path by taking the last segment.
   *
   * @param repo the full project path (e.g. {@code vgt/connectivity/api-specifications})
   * @return the last path segment (e.g. {@code api-specifications})
   */
  static String nameFromRepo(String repo) {
    int slash = repo.lastIndexOf('/');
    return slash >= 0 ? repo.substring(slash + 1) : repo;
  }
}
