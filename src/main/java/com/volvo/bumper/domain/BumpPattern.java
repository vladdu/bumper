package com.volvo.bumper.domain;

import java.util.regex.Pattern;

/**
 * Shared utility for detecting dependency-bump commit messages.
 *
 * <p>Matches commit messages that bump or upgrade dependencies, including Renovate and Dependabot
 * automated commits as well as conventional-commit style {@code chore(deps):} prefixes.
 */
public final class BumpPattern {

  private BumpPattern() {}

  private static final Pattern BUMP_RE =
      Pattern.compile(
          "\\b(bump|upgrade|renovate|dependabot)\\b"
              + "|\\b\\w+\\(deps?(?:-dev)?\\)"
              + "|\\bupdate\\b.{0,40}\\b(dep|dependencies|dependency|requirements|packages|versions?|lockfile)\\b",
          Pattern.CASE_INSENSITIVE);

  /**
   * Returns {@code true} when {@code message} looks like a dependency-bump commit message.
   *
   * @param message first line of a commit message.
   * @return {@code true} if the message matches the bump pattern.
   */
  public static boolean isBumpCommit(String message) {
    return BUMP_RE.matcher(message).find();
  }
}
