package com.volvo.bumper.adapter.github;

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
public class JsonGitHubForgeAdapter implements ForgePort {

  private record GithubJsonRepo(String name, String forgeUrl) {}

  private record RepoDetail(LocalDate lastDependencyUpdate, String lastDependencyUpdateBy) {}

  private final ObjectMapper objectMapper;
  private final Path reposFilePath;
  private final Path detailsFilePath;

  @Autowired
  public JsonGitHubForgeAdapter(
      ObjectMapper objectMapper,
      @Value("${json.github.file:data/github-repositories.json}") String reposFilePath,
      @Value("${json.github.details.file:data/github-repo-details.json}") String detailsFilePath) {
    this(objectMapper, Path.of(reposFilePath), Path.of(detailsFilePath));
  }

  public JsonGitHubForgeAdapter(
      ObjectMapper objectMapper, Path reposFilePath, Path detailsFilePath) {
    this.objectMapper = objectMapper;
    this.reposFilePath = reposFilePath;
    this.detailsFilePath = detailsFilePath;
  }

  @Override
  public ForgeType forgeType() {
    return ForgeType.GITHUB;
  }

  @Override
  public List<Repository> discoverAll() {
    List<GithubJsonRepo> dtos =
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

  private Repository toRepository(GithubJsonRepo dto, Map<String, RepoDetail> details) {
    String repo = GitHubForgeNaming.repoFromUrl(dto.forgeUrl());
    String name = dto.name() != null ? dto.name() : GitHubForgeNaming.nameFromRepo(repo);
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
