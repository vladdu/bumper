package com.volvo.bumper.adapter.confluence;

import com.volvo.bumper.config.ConfluenceProperties;
import com.volvo.bumper.domain.IgnoredRepository;
import com.volvo.bumper.domain.Repository;
import com.volvo.bumper.domain.RepositoryData;
import com.volvo.bumper.port.RepositoryStore;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Live Confluence implementation of {@link RepositoryStore}.
 *
 * <p>Reads and writes Confluence pages that contain two HTML tables:
 *
 * <ol>
 *   <li>Regular (tracked) repositories with columns: {@code name}, {@code forge}, {@code url},
 *       {@code last_bump_date}, {@code last_bump_by}, {@code team}, {@code comments}
 *   <li>Ignored repositories with columns: {@code name}, {@code forge}, {@code url}
 * </ol>
 *
 * <p>Reads are performed against the page identified by {@link ConfluenceProperties#pageIdRead()};
 * writes target the page identified by {@link ConfluenceProperties#pageIdWrite()}.
 *
 * <p>{@link #findAll()} issues a single HTTP request and parses both tables, returning a consistent
 * {@link RepositoryData} snapshot. {@link #saveAll(RepositoryData)} issues a single HTTP request to
 * fetch column-header structure and version, then writes both tables atomically.
 *
 * <p>Optimistic concurrency: when both page IDs are equal the adapter remembers the page version
 * seen on the last successful read. If the page has been modified externally before the next write,
 * an {@link IllegalStateException} is thrown instead of overwriting the changes. When the IDs
 * differ the version check is skipped (the version of the read page says nothing about the write
 * page).
 *
 * <p>Wired by {@link com.volvo.bumper.config.RealAdaptersConfig} when the {@code real} Spring
 * profile is active.
 */
public class RealConfluenceRepositoryStore implements RepositoryStore {

  private static final String COL_NAME = "name";
  private static final String COL_REPO = "repo";
  private static final String COL_BUMPERS = "bumpers";
  private static final String COL_LAST_BUMP_DATE = "last_bump_date";
  private static final String COL_LAST_BUMP_BY = "last_bump_by";
  private static final String COL_TEAM = "team";
  private static final String COL_COMMENTS = "comments";

  private static final List<String> DEFAULT_REGULAR_HEADERS =
      List.of(
          COL_NAME,
          COL_BUMPERS,
          COL_LAST_BUMP_DATE,
          COL_LAST_BUMP_BY,
          COL_COMMENTS,
          COL_TEAM,
          COL_REPO);
  private static final List<String> DEFAULT_IGNORED_HEADERS = List.of(COL_NAME, COL_REPO);
  private static final Set<String> KNOWN_REGULAR_COLUMNS =
      Set.of(
          COL_NAME,
          COL_REPO,
          COL_BUMPERS,
          COL_LAST_BUMP_DATE,
          COL_LAST_BUMP_BY,
          COL_TEAM,
          COL_COMMENTS);

  private final ConfluenceProperties config;
  private final ObjectMapper mapper;
  private final HttpClient httpClient;
  private final String baseUrl;
  private final String authHeader;

  /**
   * Tracks the Confluence page version seen on the last successful read or write. {@code -1} means
   * no read has occurred yet; in that case the concurrency check is skipped.
   */
  private final AtomicInteger lastKnownVersion = new AtomicInteger(-1);

  /** For tests in the same package — creates a plain {@link ObjectMapper} and no HTTP client. */
  RealConfluenceRepositoryStore(ConfluenceProperties config) {
    this(config, new ObjectMapper());
  }

  public RealConfluenceRepositoryStore(ConfluenceProperties config, ObjectMapper mapper) {
    this.config = config;
    this.mapper = mapper;
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    String url = config.url();
    this.baseUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    this.authHeader = "Bearer " + config.apiToken();
  }

  // -------------------------------------------------------------------------
  // RepositoryStore implementation
  // -------------------------------------------------------------------------

  @Override
  public RepositoryData findAll() {
    PageData page = getPageData(config.pageIdRead());
    if (config.pageIdRead().equals(config.pageIdWrite())) {
      lastKnownVersion.set(page.version());
    }
    Document doc = Jsoup.parse(page.html());
    return new RepositoryData(parseRegularTable(doc), parseIgnoredTable(doc));
  }

  @Override
  public void saveAll(RepositoryData data) {
    PageData page = getPageData(config.pageIdWrite());
    checkVersion(page.version());
    Document doc = Jsoup.parse(page.html());
    List<String> regularHeaders = tableHeadersFromDoc(doc, 0, DEFAULT_REGULAR_HEADERS);
    List<String> ignoredHeaders = tableHeadersFromDoc(doc, 1, DEFAULT_IGNORED_HEADERS);
    String newHtml =
        buildRegularTable(data.repos(), regularHeaders)
            + "\n"
            + buildIgnoredTable(data.ignored(), ignoredHeaders);
    updatePageContent(newHtml, page.title(), page.version());
    lastKnownVersion.set(page.version() + 1);
  }

  // -------------------------------------------------------------------------
  // Concurrency guard
  // -------------------------------------------------------------------------

  private void checkVersion(int fetchedVersion) {
    int known = lastKnownVersion.get();
    if (known != -1 && fetchedVersion != known) {
      throw new IllegalStateException(
          "Confluence page was modified externally (expected version "
              + known
              + ", got "
              + fetchedVersion
              + "). Reload and retry.");
    }
  }

  // -------------------------------------------------------------------------
  // HTML parsing
  // -------------------------------------------------------------------------

  private List<Repository> parseRegularTable(Document doc) {
    Elements tables = doc.select("table");
    if (tables.isEmpty()) return new ArrayList<>();
    return parseRepositoryRows(tables.get(0));
  }

  private List<IgnoredRepository> parseIgnoredTable(Document doc) {
    Elements tables = doc.select("table");
    if (tables.size() < 2) return new ArrayList<>();
    return parseIgnoredRows(tables.get(1));
  }

  private List<Repository> parseRepositoryRows(Element table) {
    List<String> headers = tableHeaders(table);
    int nameCol = headers.indexOf(COL_NAME);
    int repoCol = headers.indexOf(COL_REPO);
    int bumpersCol = headers.indexOf(COL_BUMPERS);
    int dateCol = headers.indexOf(COL_LAST_BUMP_DATE);
    int authorCol = headers.indexOf(COL_LAST_BUMP_BY);
    int teamCol = headers.indexOf(COL_TEAM);
    int commentsCol = headers.indexOf(COL_COMMENTS);

    List<Repository> repos = new ArrayList<>();
    Elements rows = dataRows(table);
    for (Element row : rows) {
      Elements cells = row.select("> td, > th");
      String name = cellText(cells, nameCol);
      if (name.isEmpty()) continue;
      List<String> repoList = parseRepoLinks(cells, repoCol);
      List<String> bumpers = parseBumpers(cellText(cells, bumpersCol));
      LocalDate date = parseDate(cellText(cells, dateCol));
      String author = cellText(cells, authorCol);
      String team = cellText(cells, teamCol);
      String comments = cellText(cells, commentsCol);
      Map<String, String> extra = new LinkedHashMap<>();
      for (int i = 0; i < headers.size(); i++) {
        if (!KNOWN_REGULAR_COLUMNS.contains(headers.get(i))) {
          extra.put(headers.get(i), cellText(cells, i));
        }
      }
      repos.add(
          new Repository(
              name,
              repoList,
              date,
              author.isEmpty() ? null : author,
              bumpers,
              team.isEmpty() ? null : team,
              comments.isEmpty() ? null : comments,
              extra.isEmpty() ? null : extra,
              null));
    }
    return repos;
  }

  private List<IgnoredRepository> parseIgnoredRows(Element table) {
    List<String> headers = tableHeaders(table);
    int nameCol = headers.indexOf(COL_NAME);
    int repoCol = headers.indexOf(COL_REPO);

    List<IgnoredRepository> result = new ArrayList<>();
    Elements rows = dataRows(table);
    for (Element row : rows) {
      Elements cells = row.select("> td, > th");
      String name = cellText(cells, nameCol);
      if (name.isEmpty()) continue;
      List<String> repoList = parseRepoLinks(cells, repoCol);
      result.add(new IgnoredRepository(name, repoList));
    }
    return result;
  }

  // -------------------------------------------------------------------------
  // HTML building
  // -------------------------------------------------------------------------

  private static String buildRegularTable(List<Repository> repos, List<String> headers) {
    StringBuilder sb = new StringBuilder();
    sb.append("<table><thead><tr>");
    for (String h : headers) {
      sb.append("<th>").append(h).append("</th>");
    }
    sb.append("</tr></thead><tbody>");
    for (Repository r : repos) {
      sb.append("<tr>");
      for (String h : headers) {
        sb.append("<td>").append(safe(regularColumnValue(r, h))).append("</td>");
      }
      sb.append("</tr>");
    }
    sb.append("</tbody></table>");
    return sb.toString();
  }

  private static String regularColumnValue(Repository r, String header) {
    return switch (header) {
      case COL_NAME -> r.name();
      case COL_REPO -> buildRepoLinks(r.repos(), r.forgeUrls());
      case COL_BUMPERS -> r.bumpers() != null ? String.join(", ", r.bumpers()) : "";
      case COL_LAST_BUMP_DATE -> r.lastBumpDate() != null ? r.lastBumpDate().toString() : null;
      case COL_LAST_BUMP_BY -> r.lastBumpBy();
      case COL_TEAM -> r.team();
      case COL_COMMENTS -> r.comments();
      default -> r.extra().getOrDefault(header, "");
    };
  }

  private static String buildIgnoredTable(List<IgnoredRepository> ignored, List<String> headers) {
    StringBuilder sb = new StringBuilder();
    sb.append("<table><thead><tr>");
    for (String h : headers) {
      sb.append("<th>").append(h).append("</th>");
    }
    sb.append("</tr></thead><tbody>");
    for (IgnoredRepository r : ignored) {
      sb.append("<tr>");
      for (String h : headers) {
        sb.append("<td>").append(safe(ignoredColumnValue(r, h))).append("</td>");
      }
      sb.append("</tr>");
    }
    sb.append("</tbody></table>");
    return sb.toString();
  }

  private static String ignoredColumnValue(IgnoredRepository r, String header) {
    return switch (header) {
      case COL_NAME -> r.name();
      case COL_REPO -> buildRepoLinks(r.repos(), r.forgeUrls());
      default -> "";
    };
  }

  private static String buildRepoLinks(List<String> repos, List<String> forgeUrls) {
    if (repos.isEmpty()) return "";
    var parts = new ArrayList<String>(repos.size());
    for (int i = 0; i < repos.size(); i++) {
      parts.add("<a href=\"" + safe(forgeUrls.get(i)) + "\">" + safe(repos.get(i)) + "</a>");
    }
    return String.join(", ", parts);
  }

  /**
   * Extracts repo paths from a cell that may contain {@code <a>} links. Falls back to plain text if
   * no links are found.
   */
  private static List<String> parseRepoLinks(Elements cells, int index) {
    if (index < 0 || index >= cells.size()) return List.of();
    Element cell = cells.get(index);
    Elements links = cell.select("a");
    if (!links.isEmpty()) {
      return links.stream().map(a -> a.text().strip()).filter(s -> !s.isEmpty()).toList();
    }
    String text = cell.text().strip();
    if (text.isEmpty()) return List.of();
    return java.util.Arrays.stream(text.split(","))
        .map(String::strip)
        .filter(s -> !s.isEmpty())
        .toList();
  }

  // -------------------------------------------------------------------------
  // Confluence REST API helpers
  // -------------------------------------------------------------------------

  private record PageData(String html, String title, int version) {}

  private PageData getPageData(String pageId) {
    JsonNode body = apiGet("/rest/api/content/" + pageId + "?expand=body.storage,version");
    return new PageData(
        body.path("body").path("storage").path("value").asString(""),
        body.path("title").asString("Untitled"),
        body.path("version").path("number").asInt(1));
  }

  /**
   * Performs an authenticated GET request to the Confluence REST API.
   *
   * <p>Protected to allow test subclasses to return fixture data without a real HTTP connection.
   *
   * @param path the API path (e.g. {@code /rest/api/content/12345?expand=body.storage,version})
   * @return the parsed JSON response body
   * @throws RuntimeException on non-2xx responses or I/O errors
   */
  protected JsonNode apiGet(String path) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(baseUrl + path))
              .header("Authorization", authHeader)
              .header("Accept", "application/json")
              .timeout(Duration.ofSeconds(30))
              .GET()
              .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new RuntimeException(
            "Confluence API error " + response.statusCode() + " for " + path);
      }
      return mapper.readTree(response.body());
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Confluence API error: " + e.getMessage(), e);
    }
  }

  /**
   * Sends an authenticated PUT request to update the Confluence page body.
   *
   * <p>Protected to allow test subclasses to capture the generated HTML without making a real HTTP
   * call.
   *
   * @param newHtml the full new page body (Confluence storage format)
   * @param title the page title (unchanged)
   * @param currentVersion the current version number; the update bumps it by 1
   * @throws RuntimeException on non-2xx responses or I/O errors
   */
  protected void updatePageContent(String newHtml, String title, int currentVersion) {
    ObjectNode payload = mapper.createObjectNode();
    payload.put("type", "page");
    payload.put("title", title);
    ObjectNode version = payload.putObject("version");
    version.put("number", currentVersion + 1);
    ObjectNode bodyNode = payload.putObject("body");
    ObjectNode storage = bodyNode.putObject("storage");
    storage.put("value", newHtml);
    storage.put("representation", "storage");
    try {
      String json = mapper.writeValueAsString(payload);
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(baseUrl + "/rest/api/content/" + config.pageIdWrite()))
              .header("Authorization", authHeader)
              .header("Content-Type", "application/json")
              .timeout(Duration.ofSeconds(30))
              .PUT(HttpRequest.BodyPublishers.ofString(json))
              .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new RuntimeException(
            "Confluence update failed: HTTP " + response.statusCode() + " — " + response.body());
      }
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Confluence update error: " + e.getMessage(), e);
    }
  }

  // -------------------------------------------------------------------------
  // Table parsing utilities
  // -------------------------------------------------------------------------

  private static List<String> tableHeadersFromDoc(
      Document doc, int tableIndex, List<String> defaultHeaders) {
    Elements tables = doc.select("table");
    if (tables.size() <= tableIndex) return defaultHeaders;
    List<String> h = tableHeaders(tables.get(tableIndex));
    return h.isEmpty() ? defaultHeaders : h;
  }

  private static List<String> tableHeaders(Element table) {
    Element headerRow = table.selectFirst("thead > tr");
    if (headerRow == null) headerRow = table.selectFirst("tr");
    if (headerRow == null) return List.of();
    List<String> headers = new ArrayList<>();
    for (Element th : headerRow.select("th, td")) {
      headers.add(th.text().strip().toLowerCase());
    }
    return headers;
  }

  private static Elements dataRows(Element table) {
    Elements tbodyRows = table.select("tbody > tr");
    if (!tbodyRows.isEmpty()) {
      Elements result = new Elements();
      for (Element row : tbodyRows) {
        if (!row.select("td").isEmpty()) result.add(row);
      }
      return result;
    }
    Elements allRows = table.select("tr");
    return allRows.size() > 1 ? new Elements(allRows.subList(1, allRows.size())) : new Elements();
  }

  private static String cellText(Elements cells, int index) {
    if (index < 0 || index >= cells.size()) return "";
    return cells.get(index).text().strip();
  }

  private static List<String> parseBumpers(String value) {
    if (value == null || value.isBlank()) return List.of();
    return java.util.Arrays.stream(value.split(","))
        .map(String::strip)
        .filter(s -> !s.isEmpty())
        .toList();
  }

  private static LocalDate parseDate(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  /** Returns empty string for null values to avoid "null" text in HTML. */
  private static String safe(String value) {
    return value != null ? value : "";
  }
}
