package com.volvo.bumper.adapter.confluence;

import com.volvo.bumper.domain.ForgeType;
import com.volvo.bumper.domain.IgnoredRepository;
import com.volvo.bumper.domain.Repository;
import com.volvo.bumper.domain.RepositoryData;
import com.volvo.bumper.port.RepositoryStore;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@Profile("json")
public class JsonConfluenceRepositoryStore implements RepositoryStore {

  private final ObjectMapper objectMapper;
  private final Path readPath;
  private final Path writePath;

  @Autowired
  public JsonConfluenceRepositoryStore(
      ObjectMapper objectMapper,
      @Value("${json.confluence.file.read:data/confluence-data.json}") String readFilePath,
      @Value("${json.confluence.file.write:data/confluence-data.json}") String writeFilePath) {
    this(objectMapper, Path.of(readFilePath), Path.of(writeFilePath));
  }

  public JsonConfluenceRepositoryStore(ObjectMapper objectMapper, Path readPath, Path writePath) {
    this.objectMapper = objectMapper;
    this.readPath = readPath;
    this.writePath = writePath;
  }

  @Override
  public RepositoryData findAll() {
    ReadDataDto data = objectMapper.readValue(readPath.toFile(), ReadDataDto.class);
    return new RepositoryData(deriveRepoNames(data.repos()), deriveIgnoredNames(data.ignored()));
  }

  private static List<Repository> deriveRepoNames(List<Repository> repos) {
    return repos.stream()
        .map(
            r ->
                r.name() == null
                    ? new Repository(
                        ForgeType.nameFromRepo(r.repos().getFirst()),
                        r.repos(),
                        r.lastBumpDate(),
                        r.lastBumpBy(),
                        r.bumpers(),
                        r.team(),
                        r.comments(),
                        r.extra(),
                        r.error())
                    : r)
        .toList();
  }

  private static List<IgnoredRepository> deriveIgnoredNames(List<IgnoredRepository> ignored) {
    return ignored.stream()
        .map(
            ig ->
                ig.name() == null
                    ? new IgnoredRepository(
                        ForgeType.nameFromRepo(ig.repos().getFirst()), ig.repos())
                    : ig)
        .toList();
  }

  @Override
  public void saveAll(RepositoryData data) {
    var dto =
        new WriteDataDto(
            data.repos().stream().map(RepoDto::from).toList(),
            data.ignored().stream().map(IgnoredDto::from).toList());
    objectMapper.writeValue(writePath.toFile(), dto);
  }

  /** Read DTO: deserializes domain types directly. */
  private record ReadDataDto(List<Repository> repos, List<IgnoredRepository> ignored) {}

  /** Write DTO wrapping both lists in a single JSON object. */
  private record WriteDataDto(List<RepoDto> repos, List<IgnoredDto> ignored) {}

  /** Write DTO that excludes the {@code name} field (it is derived from {@code repos}). */
  private record RepoDto(
      List<String> repos,
      LocalDate lastBumpDate,
      String lastBumpBy,
      List<String> bumpers,
      String team,
      String comments,
      Map<String, String> extra) {
    static RepoDto from(Repository r) {
      return new RepoDto(
          r.repos(),
          r.lastBumpDate(),
          r.lastBumpBy(),
          r.bumpers(),
          r.team(),
          r.comments(),
          r.extra());
    }
  }

  /** Write DTO that excludes the {@code name} field (it is derived from {@code repos}). */
  private record IgnoredDto(List<String> repos) {
    static IgnoredDto from(IgnoredRepository r) {
      return new IgnoredDto(r.repos());
    }
  }
}
