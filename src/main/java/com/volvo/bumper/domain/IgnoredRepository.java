package com.volvo.bumper.domain;

import java.util.List;

public record IgnoredRepository(String name, List<String> repos) {

  public IgnoredRepository {
    repos = repos != null ? List.copyOf(repos) : List.of();
  }

  /** Returns the forge URL for each repo path. */
  public List<String> forgeUrls() {
    return repos.stream().map(r -> ForgeType.fromRepo(r).urlFor(r)).toList();
  }
}
