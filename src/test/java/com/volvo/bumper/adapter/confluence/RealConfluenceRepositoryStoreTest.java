package com.volvo.bumper.adapter.confluence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.volvo.bumper.config.ConfluenceProperties;
import com.volvo.bumper.domain.IgnoredRepository;
import com.volvo.bumper.domain.Repository;
import com.volvo.bumper.domain.RepositoryData;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class RealConfluenceRepositoryStoreTest {

  private static final ConfluenceProperties PROPS =
      new ConfluenceProperties(
          "https://test.atlassian.net", "user", "token", "read-id", "write-id");

  private static final ConfluenceProperties PROPS_SAME_PAGE =
      new ConfluenceProperties("https://test.atlassian.net", "user", "token", "page-id", "page-id");

  private static final ObjectMapper MAPPER = new ObjectMapper();

  // -------------------------------------------------------------------------
  // Fixtures
  // -------------------------------------------------------------------------

  private static JsonNode pageJson(String html, int version) {
    ObjectNode root = MAPPER.createObjectNode();
    root.put("title", "My Page");
    root.putObject("version").put("number", version);
    root.putObject("body").putObject("storage").put("value", html);
    return root;
  }

  private static String regularTableHtml(List<Repository> repos) {
    StringBuilder sb = new StringBuilder("<table>");
    sb.append("<thead><tr>")
        .append("<th>name</th><th>bumpers</th><th>last_bump_date</th><th>last_bump_by</th>")
        .append("<th>comments</th><th>team</th><th>repo</th>")
        .append("</tr></thead><tbody>");
    for (Repository r : repos) {
      sb.append("<tr>")
          .append("<td>")
          .append(r.name())
          .append("</td>")
          .append("<td>")
          .append(r.bumpers() != null ? String.join(", ", r.bumpers()) : "")
          .append("</td>")
          .append("<td>")
          .append(r.lastBumpDate() != null ? r.lastBumpDate() : "")
          .append("</td>")
          .append("<td>")
          .append(r.lastBumpBy() != null ? r.lastBumpBy() : "")
          .append("</td>")
          .append("<td>")
          .append(r.comments() != null ? r.comments() : "")
          .append("</td>")
          .append("<td>")
          .append(r.team() != null ? r.team() : "")
          .append("</td>")
          .append("<td>");
      var urls = r.forgeUrls();
      for (int i = 0; i < r.repos().size(); i++) {
        if (i > 0) sb.append(", ");
        sb.append("<a href=\"")
            .append(urls.get(i))
            .append("\">")
            .append(r.repos().get(i))
            .append("</a>");
      }
      sb.append("</td></tr>");
    }
    sb.append("</tbody></table>");
    return sb.toString();
  }

  private static String ignoredTableHtml(List<IgnoredRepository> ignored) {
    StringBuilder sb = new StringBuilder("<table>");
    sb.append("<thead><tr>").append("<th>name</th><th>repo</th>").append("</tr></thead><tbody>");
    for (IgnoredRepository r : ignored) {
      sb.append("<tr>").append("<td>").append(r.name()).append("</td>").append("<td>");
      var urls = r.forgeUrls();
      for (int i = 0; i < r.repos().size(); i++) {
        if (i > 0) sb.append(", ");
        sb.append("<a href=\"")
            .append(urls.get(i))
            .append("\">")
            .append(r.repos().get(i))
            .append("</a>");
      }
      sb.append("</td></tr>");
    }
    sb.append("</tbody></table>");
    return sb.toString();
  }

  // -------------------------------------------------------------------------
  // Stub
  // -------------------------------------------------------------------------

  private static class StubConfluenceStore extends RealConfluenceRepositoryStore {
    private final Queue<JsonNode> responses;
    String capturedHtml;
    String lastGetPath;
    int updateCallCount = 0;

    StubConfluenceStore(JsonNode... responses) {
      this(PROPS, responses);
    }

    StubConfluenceStore(ConfluenceProperties props, JsonNode... responses) {
      super(props);
      this.responses = new ArrayDeque<>(List.of(responses));
    }

    @Override
    protected JsonNode apiGet(String path) {
      lastGetPath = path;
      JsonNode next = responses.poll();
      if (next == null) throw new IllegalStateException("No more stub responses");
      return next;
    }

    @Override
    protected void updatePageContent(String html, String title, int currentVersion) {
      capturedHtml = html;
      updateCallCount++;
    }
  }

  // -------------------------------------------------------------------------
  // findAll tests
  // -------------------------------------------------------------------------

  @Test
  void findAll_parsesRepositoriesFromFirstTable() {
    List<Repository> repos =
        List.of(
            new Repository(
                "svc-alpha",
                List.of("vgt/connectivity/svc-alpha"),
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null),
            new Repository(
                "svc-beta",
                List.of("vgcs-svc-beta"),
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null));
    String html = regularTableHtml(repos) + ignoredTableHtml(List.of());
    var store = new StubConfluenceStore(pageJson(html, 3));

    List<Repository> result = store.findAll().repos();

    assertThat(result).hasSize(2);
    assertThat(result.get(0).name()).isEqualTo("svc-alpha");
    assertThat(result.get(1).name()).isEqualTo("svc-beta");
  }

  @Test
  void findAll_parsesAllRepositoryFields() {
    List<Repository> repos =
        List.of(
            new Repository(
                "svc-alpha",
                List.of("vgt/connectivity/svc-alpha"),
                LocalDate.of(2025, 6, 15),
                "Alice",
                List.of("Alice", "Bob"),
                "team-a",
                "some notes",
                null,
                null));
    String html = regularTableHtml(repos);
    var store = new StubConfluenceStore(pageJson(html, 1));

    Repository r = store.findAll().repos().get(0);

    assertThat(r.name()).isEqualTo("svc-alpha");
    assertThat(r.repos()).containsExactly("vgt/connectivity/svc-alpha");
    assertThat(r.forgeUrls())
        .containsExactly("https://git.vgt.volvo.com/admin/repos/vgt/connectivity/svc-alpha");
    assertThat(r.lastBumpDate()).isEqualTo(LocalDate.of(2025, 6, 15));
    assertThat(r.lastBumpBy()).isEqualTo("Alice");
    assertThat(r.bumpers()).containsExactly("Alice", "Bob");
    assertThat(r.team()).isEqualTo("team-a");
    assertThat(r.comments()).isEqualTo("some notes");
  }

  @Test
  void findAll_parsesMultipleRepoLinksInCell() {
    String html =
        "<table><thead><tr>"
            + "<th>name</th><th>bumpers</th><th>last_bump_date</th><th>last_bump_by</th>"
            + "<th>comments</th><th>team</th><th>repo</th>"
            + "</tr></thead><tbody>"
            + "<tr><td>shared-svc</td><td></td><td></td><td></td><td></td><td></td>"
            + "<td><a href=\"https://github.com/VolvoGroup-Internal/vgcs-shared-svc\">vgcs-shared-svc</a>, "
            + "<a href=\"https://git.vgt.volvo.com/admin/repos/vgt/connectivity/shared-svc\">vgt/connectivity/shared-svc</a></td>"
            + "</tr></tbody></table>";
    var store = new StubConfluenceStore(pageJson(html, 1));

    Repository r = store.findAll().repos().get(0);

    assertThat(r.repos()).containsExactly("vgcs-shared-svc", "vgt/connectivity/shared-svc");
  }

  @Test
  void findAll_returnsEmptyListWhenNoTableFound() {
    var store = new StubConfluenceStore(pageJson("<p>nothing here</p>", 1));
    assertThat(store.findAll().repos()).isEmpty();
  }

  @Test
  void findAll_returnsEmptyListWhenFirstTableEmpty() {
    String html =
        "<table><thead><tr><th>name</th><th>bumpers</th><th>last_bump_date</th>"
            + "<th>last_bump_by</th><th>comments</th><th>team</th><th>repo</th>"
            + "</tr></thead><tbody></tbody></table>";
    var store = new StubConfluenceStore(pageJson(html, 1));
    assertThat(store.findAll().repos()).isEmpty();
  }

  // -------------------------------------------------------------------------
  // findAllIgnored tests
  // -------------------------------------------------------------------------

  @Test
  void findAllIgnored_parsesIgnoredFromSecondTable() {
    List<IgnoredRepository> ignored =
        List.of(
            new IgnoredRepository("ignored-a", List.of("vgt/connectivity/ignored-a")),
            new IgnoredRepository("ignored-b", List.of("vgcs-ignored-b")));
    String html = regularTableHtml(List.of()) + ignoredTableHtml(ignored);
    var store = new StubConfluenceStore(pageJson(html, 2));

    List<IgnoredRepository> result = store.findAll().ignored();

    assertThat(result).hasSize(2);
    assertThat(result.get(0).name()).isEqualTo("ignored-a");
    assertThat(result.get(1).name()).isEqualTo("ignored-b");
  }

  @Test
  void findAllIgnored_returnsEmptyListWhenLessThanTwoTables() {
    String html = regularTableHtml(List.of());
    var store = new StubConfluenceStore(pageJson(html, 1));
    assertThat(store.findAll().ignored()).isEmpty();
  }

  // -------------------------------------------------------------------------
  // saveAll tests
  // -------------------------------------------------------------------------

  @Test
  void saveAll_writesUpdatedFirstTable() {
    String existingHtml = regularTableHtml(List.of()) + ignoredTableHtml(List.of());
    var store = new StubConfluenceStore(pageJson(existingHtml, 1));

    List<Repository> newRepos =
        List.of(
            new Repository(
                "new-svc", List.of("vgcs-new-svc"), null, null, List.of(), null, null, null, null));
    store.saveAll(new RepositoryData(newRepos, List.of()));

    assertThat(store.capturedHtml).contains("new-svc");
  }

  @Test
  void saveAll_preservesIgnoredTable() {
    List<IgnoredRepository> ignored =
        List.of(new IgnoredRepository("ignored-x", List.of("vgt/connectivity/ignored-x")));
    String existingHtml = regularTableHtml(List.of()) + ignoredTableHtml(ignored);
    var store = new StubConfluenceStore(pageJson(existingHtml, 1));

    var newRepos =
        List.of(
            new Repository(
                "new-svc", List.of("vgcs-new-svc"), null, null, List.of(), null, null, null, null));
    store.saveAll(new RepositoryData(newRepos, ignored));

    assertThat(store.capturedHtml).contains("ignored-x");
    assertThat(store.capturedHtml).contains("new-svc");
  }

  // -------------------------------------------------------------------------
  // saveAllIgnored tests (now unified under saveAll)
  // -------------------------------------------------------------------------

  @Test
  void saveAllIgnored_writesUpdatedSecondTable() {
    String existingHtml = regularTableHtml(List.of()) + ignoredTableHtml(List.of());
    var store = new StubConfluenceStore(pageJson(existingHtml, 1));

    store.saveAll(
        new RepositoryData(
            List.of(), List.of(new IgnoredRepository("new-ignored", List.of("vgcs-new-ignored")))));

    assertThat(store.capturedHtml).contains("new-ignored");
  }

  @Test
  void saveAllIgnored_preservesRegularTable() {
    List<Repository> repos =
        List.of(
            new Repository(
                "kept-svc",
                List.of("vgt/connectivity/kept-svc"),
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null));
    String existingHtml = regularTableHtml(repos) + ignoredTableHtml(List.of());
    var store = new StubConfluenceStore(pageJson(existingHtml, 1));

    store.saveAll(
        new RepositoryData(
            repos, List.of(new IgnoredRepository("new-ignored", List.of("vgcs-new-ignored")))));

    assertThat(store.capturedHtml).contains("kept-svc");
    assertThat(store.capturedHtml).contains("new-ignored");
  }

  // -------------------------------------------------------------------------
  // Version concurrency tests
  // -------------------------------------------------------------------------

  @Test
  void saveAll_throwsWhenVersionConflictDetected() {
    String html = regularTableHtml(List.of()) + ignoredTableHtml(List.of());
    var store = new StubConfluenceStore(PROPS_SAME_PAGE, pageJson(html, 5), pageJson(html, 6));
    store.findAll();

    assertThatThrownBy(() -> store.saveAll(new RepositoryData(List.of(), List.of())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("expected version 5")
        .hasMessageContaining("got 6");

    assertThat(store.updateCallCount).isZero();
  }

  @Test
  void saveAllIgnored_throwsWhenVersionConflictDetected() {
    String html = regularTableHtml(List.of()) + ignoredTableHtml(List.of());
    var store = new StubConfluenceStore(PROPS_SAME_PAGE, pageJson(html, 3), pageJson(html, 4));
    store.findAll();

    assertThatThrownBy(() -> store.saveAll(new RepositoryData(List.of(), List.of())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("expected version 3")
        .hasMessageContaining("got 4");

    assertThat(store.updateCallCount).isZero();
  }

  @Test
  void saveAll_skipsLockingWhenPagesAreDifferent() {
    String html = regularTableHtml(List.of()) + ignoredTableHtml(List.of());
    var store = new StubConfluenceStore(PROPS, pageJson(html, 1), pageJson(html, 99));
    store.findAll();

    store.saveAll(new RepositoryData(List.of(), List.of()));

    assertThat(store.updateCallCount).isEqualTo(1);
  }

  // -------------------------------------------------------------------------
  // Confluence round-trip: header row in tbody (Confluence storage format)
  // -------------------------------------------------------------------------

  @Test
  void findAll_ignoresHeaderRowWhenConfluenceMovesItToTbody() {
    String html =
        "<table><tbody>"
            + "<tr><th>name</th><th>bumpers</th><th>last_bump_date</th><th>last_bump_by</th><th>comments</th><th>team</th><th>repo</th></tr>"
            + "<tr><td>my-svc</td><td></td><td></td><td></td><td></td><td></td><td>vgcs-my-svc</td></tr>"
            + "</tbody></table>";
    var store = new StubConfluenceStore(pageJson(html, 1));

    List<Repository> result = store.findAll().repos();

    assertThat(result).hasSize(1);
    assertThat(result.get(0).name()).isEqualTo("my-svc");
  }

  @Test
  void saveAllIgnored_doesNotWriteHeaderRowAsDataWhenConfluenceMovesItToTbody() {
    String confluenceHtml =
        "<table><tbody>"
            + "<tr><th>name</th><th>bumpers</th><th>last_bump_date</th><th>last_bump_by</th><th>comments</th><th>team</th><th>repo</th></tr>"
            + "<tr><td>real-svc</td><td></td><td></td><td></td><td></td><td></td>"
            + "<td><a href=\"https://git.vgt.volvo.com/admin/repos/vgt/connectivity/real-svc\">vgt/connectivity/real-svc</a></td></tr>"
            + "</tbody></table>"
            + "<table><tbody>"
            + "<tr><th>name</th><th>repo</th></tr>"
            + "</tbody></table>";
    var store = new StubConfluenceStore(pageJson(confluenceHtml, 1));
    var data = store.findAll();

    var writeStore = new StubConfluenceStore(pageJson(confluenceHtml, 1));
    writeStore.saveAll(
        new RepositoryData(
            data.repos(),
            List.of(new IgnoredRepository("new-ignored", List.of("vgcs-new-ignored")))));

    assertThat(writeStore.capturedHtml).contains("real-svc");
    assertThat(writeStore.capturedHtml).doesNotContain("<td>name</td>");
  }

  @Test
  void findAll_usesReadPageId() {
    String html = regularTableHtml(List.of()) + ignoredTableHtml(List.of());
    var store = new StubConfluenceStore(pageJson(html, 1));

    store.findAll();

    assertThat(store.lastGetPath).contains("read-id");
  }

  @Test
  void saveAll_usesWritePageId() {
    String html = regularTableHtml(List.of()) + ignoredTableHtml(List.of());
    var store = new StubConfluenceStore(pageJson(html, 1));

    store.saveAll(new RepositoryData(List.of(), List.of()));

    assertThat(store.lastGetPath).contains("write-id");
  }

  // -------------------------------------------------------------------------
  // Column structure preservation tests
  // -------------------------------------------------------------------------

  @Test
  void findAll_populatesExtraColumnsIntoRepositoryExtra() {
    String html =
        "<table><thead><tr>"
            + "<th>name</th><th>bumpers</th><th>last_bump_date</th><th>last_bump_by</th>"
            + "<th>comments</th><th>team</th><th>repo</th><th>priority</th>"
            + "</tr></thead><tbody>"
            + "<tr><td>svc-alpha</td><td></td><td></td><td></td><td></td><td></td>"
            + "<td>vgcs-svc-alpha</td><td>high</td></tr>"
            + "</tbody></table>";
    var store = new StubConfluenceStore(pageJson(html, 1));

    Repository r = store.findAll().repos().get(0);

    assertThat(r.name()).isEqualTo("svc-alpha");
    assertThat(r.extra()).containsEntry("priority", "high");
  }

  @Test
  void saveAll_preservesColumnOrderFromWritePage() {
    String existingHtml =
        "<table><thead><tr>"
            + "<th>comments</th><th>team</th><th>name</th><th>bumpers</th>"
            + "<th>last_bump_date</th><th>last_bump_by</th><th>repo</th>"
            + "</tr></thead><tbody></tbody></table>"
            + ignoredTableHtml(List.of());
    var store = new StubConfluenceStore(pageJson(existingHtml, 1));

    store.saveAll(
        new RepositoryData(
            List.of(
                new Repository(
                    "svc-beta",
                    List.of("vgcs-svc-beta"),
                    null,
                    null,
                    List.of(),
                    "team-a",
                    "a note",
                    null,
                    null)),
            List.of()));

    assertThat(store.capturedHtml)
        .contains("<th>comments</th><th>team</th><th>name</th><th>bumpers</th>");
  }

  @Test
  void saveAll_preservesExtraColumnDataInRows() {
    String existingHtml =
        "<table><thead><tr>"
            + "<th>name</th><th>bumpers</th><th>last_bump_date</th><th>last_bump_by</th>"
            + "<th>comments</th><th>team</th><th>repo</th><th>priority</th>"
            + "</tr></thead><tbody>"
            + "<tr><td>svc-alpha</td><td></td><td></td><td></td><td></td><td></td>"
            + "<td>vgcs-svc-alpha</td><td>high</td></tr>"
            + "</tbody></table>"
            + ignoredTableHtml(List.of());
    var store = new StubConfluenceStore(pageJson(existingHtml, 1));
    Repository svcAlpha =
        new Repository(
            "svc-alpha",
            List.of("vgcs-svc-alpha"),
            null,
            null,
            List.of(),
            null,
            null,
            Map.of("priority", "high"),
            null);

    store.saveAll(new RepositoryData(List.of(svcAlpha), List.of()));

    assertThat(store.capturedHtml).contains("<th>priority</th>");
    assertThat(store.capturedHtml).contains("<td>high</td>");
  }

  @Test
  void saveAllIgnored_preservesIgnoredTableColumnOrder() {
    String existingHtml =
        regularTableHtml(List.of())
            + "<table><thead><tr>"
            + "<th>repo</th><th>name</th>"
            + "</tr></thead><tbody></tbody></table>";
    var store = new StubConfluenceStore(pageJson(existingHtml, 1));

    store.saveAll(
        new RepositoryData(
            List.of(),
            List.of(
                new IgnoredRepository("svc-ignored", List.of("vgt/connectivity/svc-ignored")))));

    assertThat(store.capturedHtml).contains("<th>repo</th><th>name</th>");
    assertThat(store.capturedHtml).contains("svc-ignored");
  }
}
