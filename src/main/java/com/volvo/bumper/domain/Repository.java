package com.volvo.bumper.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record Repository(
    String name,
    List<String> repos,
    LocalDate lastBumpDate,
    String lastBumpBy,
    List<String> bumpers,
    String team,
    String comments,
    Map<String, String> extra,
    String error) {
  public Repository {
    repos = repos != null ? List.copyOf(repos) : List.of();
    bumpers = bumpers != null ? List.copyOf(bumpers) : List.of();
    extra = extra != null ? Map.copyOf(extra) : Map.of();
  }

  /** Returns the forge URL for each repo path. */
  public List<String> forgeUrls() {
    return repos.stream().map(r -> ForgeType.fromRepo(r).urlFor(r)).toList();
  }

  /** Creates a repository object representing a failed discovery attempt. */
  public static Repository withError(String name, List<String> repos, String error) {
    return new Repository(name, repos, null, null, null, null, null, null, error);
  }

  /**
   * Merges forge-discovered data into this stored repository.
   *
   * <p>Unions the repo lists (deduplicated) and picks the most recent lastBumpDate.
   */
  public Repository mergeForgeData(Repository forgeData) {
    if (forgeData.error != null) {
      return new Repository(
          this.name,
          this.repos,
          this.lastBumpDate,
          this.lastBumpBy,
          this.bumpers,
          this.team,
          this.comments,
          this.extra,
          forgeData.error);
    }
    var mergedRepos = mergeRepos(this.repos, forgeData.repos);
    LocalDate bestDate;
    String bestAuthor;
    if (this.lastBumpDate != null
        && forgeData.lastBumpDate != null
        && this.lastBumpDate.isAfter(forgeData.lastBumpDate)) {
      bestDate = this.lastBumpDate;
      bestAuthor = this.lastBumpBy;
    } else if (forgeData.lastBumpDate != null) {
      bestDate = forgeData.lastBumpDate;
      bestAuthor = forgeData.lastBumpBy;
    } else {
      bestDate = this.lastBumpDate;
      bestAuthor = this.lastBumpBy;
    }
    return new Repository(
        this.name,
        mergedRepos,
        bestDate,
        bestAuthor,
        this.bumpers,
        this.team,
        this.comments,
        this.extra,
        null);
  }

  private static List<String> mergeRepos(List<String> a, List<String> b) {
    var set = new java.util.LinkedHashSet<>(a);
    set.addAll(b);
    return List.copyOf(set);
  }
}
