package com.volvo.bumper.cli;

import com.volvo.bumper.domain.IgnoredRepository;
import com.volvo.bumper.domain.Repository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Shared table formatting for CLI output of repository data. */
public final class TableFormatter {

  private TableFormatter() {}

  /** Appends a formatted table of tracked repositories to the given StringBuilder. */
  public static void appendTrackedTable(StringBuilder sb, List<Repository> repos) {
    Set<String> extraKeys = new LinkedHashSet<>();
    for (var r : repos) {
      if (r.extra() != null) extraKeys.addAll(r.extra().keySet());
    }

    List<String> headerList =
        new ArrayList<>(
            List.of(
                "Name", "Bumpers", "Last Bump Date", "Last Bump By", "Comments", "Team", "Repo"));
    headerList.addAll(extraKeys);
    String[] headers = headerList.toArray(String[]::new);

    String[][] rows = new String[repos.size()][];
    for (int i = 0; i < repos.size(); i++) {
      var r = repos.get(i);
      List<String> row =
          new ArrayList<>(
              List.of(
                  r.name(),
                  r.bumpers() != null && !r.bumpers().isEmpty()
                      ? String.join(", ", r.bumpers())
                      : "",
                  r.lastBumpDate() != null ? r.lastBumpDate().toString() : "never",
                  r.lastBumpBy() != null ? r.lastBumpBy() : "",
                  r.comments() != null ? r.comments() : "",
                  r.team() != null ? r.team() : "",
                  String.join(", ", r.forgeUrls())));
      for (String key : extraKeys) {
        row.add(r.extra() != null ? r.extra().getOrDefault(key, "") : "");
      }
      rows[i] = row.toArray(String[]::new);
    }
    appendTable(sb, headers, rows);
  }

  /** Appends a formatted table of ignored repositories to the given StringBuilder. */
  public static void appendIgnoredTable(StringBuilder sb, List<IgnoredRepository> ignored) {
    String[] headers = {"Name", "Repo"};
    String[][] rows = new String[ignored.size()][];
    for (int i = 0; i < ignored.size(); i++) {
      var p = ignored.get(i);
      rows[i] = new String[] {p.name(), String.join(", ", p.forgeUrls())};
    }
    appendTable(sb, headers, rows);
  }

  static void appendTable(StringBuilder sb, String[] headers, String[][] rows) {
    int[] widths = new int[headers.length];
    for (int c = 0; c < headers.length; c++) {
      widths[c] = headers[c].length();
    }
    for (String[] row : rows) {
      for (int c = 0; c < row.length; c++) {
        widths[c] = Math.max(widths[c], row[c].length());
      }
    }

    appendRow(sb, headers, widths);
    for (int c = 0; c < headers.length; c++) {
      sb.append(c == 0 ? "" : "  ");
      sb.append("-".repeat(widths[c]));
    }
    sb.append(System.lineSeparator());
    for (String[] row : rows) {
      appendRow(sb, row, widths);
    }
  }

  private static void appendRow(StringBuilder sb, String[] values, int[] widths) {
    for (int c = 0; c < values.length; c++) {
      if (c > 0) sb.append("  ");
      sb.append(String.format("%-" + widths[c] + "s", values[c]));
    }
    sb.append(System.lineSeparator());
  }
}
