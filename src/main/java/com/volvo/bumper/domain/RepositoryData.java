package com.volvo.bumper.domain;

import java.util.List;

/** Holds both the tracked and ignored repository lists as a single consistent snapshot. */
public record RepositoryData(List<Repository> repos, List<IgnoredRepository> ignored) {

  public static RepositoryData empty() {
    return new RepositoryData(List.of(), List.of());
  }
}
