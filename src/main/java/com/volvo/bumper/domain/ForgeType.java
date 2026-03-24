package com.volvo.bumper.domain;

public enum ForgeType {
  GITHUB,
  GERRIT;

  /** Infers the forge type from the repository's full path name. Gerrit repos contain a slash. */
  public static ForgeType fromRepo(String repo) {
    return repo != null && repo.contains("/") ? GERRIT : GITHUB;
  }

  /** Returns the full URL for the given repository path in this forge. */
  public String urlFor(String repo) {
    return switch (this) {
      case GITHUB -> "https://github.com/VolvoGroup-Internal/" + repo;
      case GERRIT -> "https://git.vgt.volvo.com/admin/repos/" + repo;
    };
  }

  /**
   * Derives the display name from a repository path.
   *
   * <p>GitHub repos strip the {@code vgcs-} prefix (e.g. {@code vgcs-foo} becomes {@code foo}).
   * Gerrit repos take the last path segment (e.g. {@code vgt/connectivity/foo} becomes {@code
   * foo}).
   */
  public static String nameFromRepo(String repo) {
    if (repo == null) return null;
    ForgeType forge = fromRepo(repo);
    return switch (forge) {
      case GITHUB -> repo.startsWith("vgcs-") ? repo.substring("vgcs-".length()) : repo;
      case GERRIT -> {
        int slash = repo.lastIndexOf('/');
        yield slash >= 0 ? repo.substring(slash + 1) : repo;
      }
    };
  }
}
