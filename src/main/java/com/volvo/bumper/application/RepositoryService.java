package com.volvo.bumper.application;

import com.volvo.bumper.domain.IgnoredRepository;
import com.volvo.bumper.domain.Repository;
import com.volvo.bumper.domain.RepositoryData;
import com.volvo.bumper.domain.SyncData;
import com.volvo.bumper.domain.SyncResult;
import com.volvo.bumper.port.ForgePort;
import com.volvo.bumper.port.RepositoryStore;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RepositoryService {

  private static final Logger log = LoggerFactory.getLogger(RepositoryService.class);

  private final RepositoryStore store;
  private final List<ForgePort> forgePorts;

  public RepositoryService(RepositoryStore store, List<ForgePort> forgePorts) {
    this.store = store;
    this.forgePorts = List.copyOf(forgePorts);
  }

  public RepositoryData listAll() {
    var data = store.findAll();
    var sorted = data.repos().stream().sorted(Comparator.comparing(Repository::name)).toList();
    return new RepositoryData(sorted, data.ignored());
  }

  public String ignore(String name, boolean write) {
    var data = store.findAll();
    var repos = new ArrayList<>(data.repos());
    var match = repos.stream().filter(r -> r.name().equals(name)).findFirst();
    if (match.isEmpty()) {
      return "Not found in tracked repositories: " + name;
    }
    var repo = match.get();
    repos.remove(repo);
    var ignored = new ArrayList<>(data.ignored());
    ignored.add(new IgnoredRepository(repo.name(), repo.repos()));
    if (write) {
      store.saveAll(new RepositoryData(repos, ignored));
    }
    return "Moved to ignored: " + name;
  }

  public String track(String name, boolean write) {
    var data = store.findAll();
    var ignored = new ArrayList<>(data.ignored());
    var match = ignored.stream().filter(ig -> ig.name().equals(name)).findFirst();
    if (match.isEmpty()) {
      return "Not found in ignored repositories: " + name;
    }
    var ig = match.get();
    ignored.remove(ig);
    var repo = new Repository(ig.name(), ig.repos(), null, null, List.of(), null, null, null, null);
    var forgeData = discoverOne(ig.name());
    if (forgeData != null) {
      repo = repo.mergeForgeData(forgeData);
    }
    var repos = new ArrayList<>(data.repos());
    repos.add(repo);
    if (write) {
      store.saveAll(new RepositoryData(repos, ignored));
    }
    return "Now tracking: " + name;
  }

  public SyncData sync(boolean write) {
    var stored = store.findAll();
    var forgeErrors = new ArrayList<String>();
    var discovered = discoverAll(forgeErrors);

    var syncData = processSyncData(stored.repos(), stored.ignored(), discovered, forgeErrors);

    if (write) {
      store.saveAll(new RepositoryData(syncData.repos(), syncData.ignored()));
    }

    return syncData;
  }

  private Repository discoverOne(String name) {
    Repository merged = null;
    for (var port : forgePorts) {
      try {
        for (var r : port.discoverAll()) {
          if (r.name().equals(name)) {
            merged = merged == null ? r : merged.mergeForgeData(r);
          }
        }
      } catch (Exception e) {
        // skip failing forges during track
      }
    }
    return merged;
  }

  private List<Repository> discoverAll(List<String> errors) {
    var result = new ArrayList<Repository>();
    for (var port : forgePorts) {
      try {
        result.addAll(port.discoverAll());
      } catch (Exception e) {
        var msg = "Failed to get data from forge " + port.forgeType() + ": " + e.getMessage();
        log.error(msg, e);
        errors.add(msg);
      }
    }
    return result;
  }

  static SyncData processSyncData(
      List<Repository> storedRepos,
      List<IgnoredRepository> existingIgnored,
      List<Repository> discovered,
      List<String> forgeErrors) {
    var logs = new ArrayList<String>();

    logLine(
        logs,
        "Before sync: %d tracked repos, %d ignored",
        storedRepos.size(),
        existingIgnored.size());
    for (var r : storedRepos) {
      logLine(logs, "  tracked: %s (%s)", r.name(), r.repos());
    }
    for (var ig : existingIgnored) {
      logLine(logs, "  ignored: %s (%s)", ig.name(), ig.repos());
    }

    // Pre-merge discovered repos by name (same name from different forges -> one entry)
    var discoveredByName = new LinkedHashMap<String, Repository>();
    for (var d : discovered) {
      var existing = discoveredByName.get(d.name());
      discoveredByName.put(d.name(), existing == null ? d : existing.mergeForgeData(d));
    }

    var repoMap = new LinkedHashMap<String, Repository>();
    for (var r : storedRepos) {
      repoMap.put(r.name(), r);
    }

    var ignoredKeys = new HashSet<String>();
    for (var ig : existingIgnored) {
      ignoredKeys.add(ig.name());
    }

    int updated = 0;
    var newlyIgnored = new ArrayList<IgnoredRepository>();
    var newRegularNames = new ArrayList<String>();
    var newIgnoredNames = new ArrayList<String>();

    for (var d : discoveredByName.values()) {
      var stored = repoMap.get(d.name());
      if (stored != null) {
        var merged = stored.mergeForgeData(d);
        if (!Objects.equals(merged.lastBumpDate(), stored.lastBumpDate())) {
          updated++;
          if (stored.lastBumpDate() == null) {
            newRegularNames.add(d.name());
          }
        }
        repoMap.put(d.name(), merged);
      } else if (!ignoredKeys.contains(d.name())) {
        newlyIgnored.add(new IgnoredRepository(d.name(), d.repos()));
        newIgnoredNames.add(d.name());
        ignoredKeys.add(d.name());
      }
    }

    var allIgnored = new ArrayList<>(existingIgnored);
    allIgnored.addAll(newlyIgnored);

    var repos = new ArrayList<>(repoMap.values());
    logLine(logs, "After sync: %d tracked repos, %d ignored", repos.size(), allIgnored.size());
    for (var r : repos) {
      logLine(logs, "  tracked: %s (%s, last update: %s)", r.name(), r.repos(), r.lastBumpDate());
    }
    for (var ig : allIgnored) {
      logLine(logs, "  ignored: %s (%s)", ig.name(), ig.repos());
    }

    return new SyncData(
        repos,
        allIgnored,
        new SyncResult(
            updated,
            storedRepos.size(),
            newlyIgnored.size(),
            allIgnored.size(),
            List.copyOf(newRegularNames),
            List.copyOf(newIgnoredNames),
            List.copyOf(forgeErrors)),
        List.copyOf(logs));
  }

  private static void logLine(List<String> logs, String format, Object... args) {
    var message = String.format(format, args);
    log.info(message);
    logs.add(message);
  }
}
