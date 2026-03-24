package com.volvo.bumper.adapter.gerrit;

import com.volvo.bumper.domain.ForgeType;
import com.volvo.bumper.domain.Repository;
import com.volvo.bumper.port.ForgePort;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
@Profile("json")
public class JsonGerritForgeAdapter implements ForgePort {

  private record GerritJsonRepo(String name, String forgeUrl) {}

  private record RepoDetail(LocalDate lastDependencyUpdate, String lastDependencyUpdateBy) {}

  private final ObjectMapper objectMapper;
  private final Path reposFilePath;
  private final Path detailsFilePath;

  @Autowired
  public JsonGerritForgeAdapter(
      ObjectMapper objectMapper,
      @Value("${json.gerrit.file:data/gerrit-repositories.json}") String reposFilePath,
      @Value("${json.gerrit.details.file:data/gerrit-repo-details.json}") String detailsFilePath) {
    this(objectMapper, Path.of(reposFilePath), Path.of(detailsFilePath));
  }

  public JsonGerritForgeAdapter(
      ObjectMapper objectMapper, Path reposFilePath, Path detailsFilePath) {
    this.objectMapper = objectMapper;
    this.reposFilePath = reposFilePath;
    this.detailsFilePath = detailsFilePath;
  }

  @Override
  public ForgeType forgeType() {
    return ForgeType.GERRIT;
  }

  @Override
  public List<Repository> discoverAll() {
    List<GerritJsonRepo> dtos =
        objectMapper.readValue(reposFilePath.toFile(), new TypeReference<>() {});
    Map<String, RepoDetail> details = readDetails();
    return dtos.stream().map(dto -> toRepository(dto, details)).toList();
  }

  private Map<String, RepoDetail> readDetails() {
    if (!Files.exists(detailsFilePath)) {
      return Map.of();
    }
    return objectMapper.readValue(detailsFilePath.toFile(), new TypeReference<>() {});
  }

  private Repository toRepository(GerritJsonRepo dto, Map<String, RepoDetail> details) {
    String repo = GerritForgeNaming.repoFromUrl(dto.forgeUrl());
    String name = dto.name() != null ? dto.name() : GerritForgeNaming.nameFromRepo(repo);
    RepoDetail detail = details.get(repo);
    return new Repository(
        name,
        List.of(repo),
        detail != null ? detail.lastDependencyUpdate() : null,
        detail != null ? detail.lastDependencyUpdateBy() : null,
        null,
        null,
        null,
        null,
        null);
  }
}
