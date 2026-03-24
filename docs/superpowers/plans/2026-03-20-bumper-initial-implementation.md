# Bumper Initial Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a ports-adapters CLI that tracks git repository dependency-update dates, reads/writes to Confluence (fake), and fetches from GitHub/Gerrit (fake), with `list` and `sync` commands.

**Architecture:** Single Maven module with strict package conventions: `domain`, `port`, `application`, `adapter`, `cli`. ArchUnit enforces that inner layers never import outer layers. All external systems accessed through port interfaces; fake implementations activated via Spring profile `fake`.

**Tech Stack:** Java 25, Spring Boot 4, Spring Shell 4, ArchUnit 1.3.0, JUnit 5, AssertJ

---

## File Map

| File | Action | Responsibility |
|---|---|---|
| `pom.xml` | Modify | Java 25, add ArchUnit |
| `src/main/resources/application.properties` | Modify | Activate `fake` profile by default |
| `BumperApplication.java` | Modify | Remove HelloCommands wiring |
| `commands/HelloCommands.java` | Delete | Replaced by new CLI commands |
| `domain/ForgeType.java` | Create | Enum: GITHUB, GERRIT |
| `domain/Repository.java` | Create | Immutable record with Map.copyOf |
| `domain/SyncResult.java` | Create | updated/total counts |
| `port/RepositoryStore.java` | Create | findAll / saveAll interface |
| `port/ForgePort.java` | Create | supports / fetchLastDependencyUpdate interface |
| `application/RepositoryService.java` | Create | list() and sync() business logic |
| `adapter/confluence/FakeConfluenceRepositoryStore.java` | Create | In-memory store, hardcoded initial data |
| `adapter/github/FakeGitHubForgeAdapter.java` | Create | Returns fixed date for GITHUB repos |
| `adapter/gerrit/FakeGerritForgeAdapter.java` | Create | Returns fixed date for GERRIT repos |
| `cli/ListCommand.java` | Create | `list` shell command |
| `cli/SyncCommand.java` | Create | `sync` shell command |
| `ArchitectureTest.java` | Create | ArchUnit layer rules |
| `BumperApplicationTests.java` | Modify | Smoke tests with fake profile |
| `domain/RepositoryTest.java` | Create | Map.copyOf immutability tests |
| `application/RepositoryServiceTest.java` | Create | Unit tests, hand-rolled fakes, no Spring |

All Java source files live under `src/main/java/com/example/bumper/` and test files under `src/test/java/com/example/bumper/`.

---

## Task 1: Project Setup

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.properties`

- [ ] **Step 1: Update Java version and add ArchUnit to pom.xml**

Replace `<java.version>21</java.version>` with `<java.version>25</java.version>`.

Add to `<dependencies>`:
```xml
<dependency>
    <groupId>com.tngtech.archunit</groupId>
    <artifactId>archunit-junit5</artifactId>
    <version>1.3.0</version>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: Activate fake profile by default**

In `src/main/resources/application.properties`, add:
```properties
spring.profiles.active=fake
```

- [ ] **Step 3: Verify project compiles**

```bash
mvn verify -q
```
Expected: BUILD SUCCESS (existing `contextLoads` test may fail because `HelloCommands` is still referenced — that's OK, we fix it in Task 2)

---

## Task 2: Remove HelloCommands scaffold

**Files:**
- Modify: `src/main/java/com/example/bumper/BumperApplication.java`
- Delete: `src/main/java/com/example/bumper/commands/HelloCommands.java`

- [ ] **Step 1: Remove HelloCommands from BumperApplication**

Replace the entire file content:
```java
package com.volvo.bumper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BumperApplication {

    public static void main(String[] args) {
        SpringApplication.run(BumperApplication.class, args);
    }
}
```

- [ ] **Step 2: Delete HelloCommands.java**

```bash
rm src/main/java/com/example/bumper/commands/HelloCommands.java
rmdir src/main/java/com/example/bumper/commands
```

- [ ] **Step 3: Verify build and context loads**

```bash
mvn verify -q
```
Expected: BUILD SUCCESS, `contextLoads` test passes. (Context load may fail because no `RepositoryStore` bean exists yet — that's fine, this will be fixed in later tasks. If it fails, that is expected and is tracked.)

- [ ] **Step 4: Commit**

```bash
git add pom.xml src/main/resources/application.properties src/main/java/com/example/bumper/BumperApplication.java
git rm src/main/java/com/example/bumper/commands/HelloCommands.java
git commit -m "chore: project setup — Java 25, ArchUnit, remove HelloCommands scaffold"
```

---

## Task 3: Domain Model

**Files:**
- Create: `src/main/java/com/example/bumper/domain/ForgeType.java`
- Create: `src/main/java/com/example/bumper/domain/Repository.java`
- Create: `src/main/java/com/example/bumper/domain/SyncResult.java`
- Create: `src/test/java/com/example/bumper/domain/RepositoryTest.java`

- [ ] **Step 1: Write failing tests for Repository**

Create `src/test/java/com/example/bumper/domain/RepositoryTest.java`:
```java
package com.volvo.bumper.domain;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class RepositoryTest {

    @Test
    void extra_isDefensivelyCopied() {
        var mutableMap = new HashMap<String, String>();
        mutableMap.put("key", "value");
        var repo = new Repository("name", ForgeType.GITHUB, "https://github.com/org/repo",
                null, "team-a", "", mutableMap);

        mutableMap.put("other", "injected");

        assertThat(repo.extra()).doesNotContainKey("other");
    }

    @Test
    void extra_isUnmodifiable() {
        var repo = new Repository("name", ForgeType.GITHUB, "https://github.com/org/repo",
                null, "team-a", "", Map.of("k", "v"));

        assertThatThrownBy(() -> repo.extra().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
```

- [ ] **Step 2: Run tests — expect compilation failure**

```bash
mvn test -pl . -Dtest=RepositoryTest -q 2>&1 | tail -5
```
Expected: compilation error — `Repository`, `ForgeType` not found.

- [ ] **Step 3: Create ForgeType enum**

Create `src/main/java/com/example/bumper/domain/ForgeType.java`:
```java
package com.volvo.bumper.domain;

public enum ForgeType {
    GITHUB,
    GERRIT
}
```

- [ ] **Step 4: Create Repository record**

Create `src/main/java/com/example/bumper/domain/Repository.java`:
```java
package com.volvo.bumper.domain;

import java.time.LocalDate;
import java.util.Map;

public record Repository(
        String name,
        ForgeType forge,
        String forgeUrl,
        LocalDate lastDependencyUpdate,
        String team,
        String comments,
        Map<String, String> extra
) {
    public Repository {
        extra = Map.copyOf(extra);
    }
}
```

- [ ] **Step 5: Create SyncResult record**

Create `src/main/java/com/example/bumper/domain/SyncResult.java`:
```java
package com.volvo.bumper.domain;

public record SyncResult(int updated, int total) {}
```

- [ ] **Step 6: Run tests — expect pass**

```bash
mvn test -Dtest=RepositoryTest -q
```
Expected: BUILD SUCCESS, 2 tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/example/bumper/domain/ src/test/java/com/example/bumper/domain/
git commit -m "feat: domain model — Repository, ForgeType, SyncResult"
```

---

## Task 4: Port Interfaces

**Files:**
- Create: `src/main/java/com/example/bumper/port/RepositoryStore.java`
- Create: `src/main/java/com/example/bumper/port/ForgePort.java`

No tests: these are interfaces with no logic.

- [ ] **Step 1: Create RepositoryStore**

Create `src/main/java/com/example/bumper/port/RepositoryStore.java`:
```java
package com.volvo.bumper.port;

import com.volvo.bumper.domain.Repository;
import java.util.List;

public interface RepositoryStore {
    List<Repository> findAll();
    void saveAll(List<Repository> repos);
}
```

- [ ] **Step 2: Create ForgePort**

Create `src/main/java/com/example/bumper/port/ForgePort.java`:
```java
package com.volvo.bumper.port;

import com.volvo.bumper.domain.ForgeType;
import com.volvo.bumper.domain.Repository;
import java.time.LocalDate;
import java.util.Optional;

public interface ForgePort {
    boolean supports(ForgeType forge);
    Optional<LocalDate> fetchLastDependencyUpdate(Repository repo);
}
```

- [ ] **Step 3: Verify compilation**

```bash
mvn compile -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/bumper/port/
git commit -m "feat: port interfaces — RepositoryStore, ForgePort"
```

---

## Task 5: RepositoryService

**Files:**
- Create: `src/main/java/com/example/bumper/application/RepositoryService.java`
- Create: `src/test/java/com/example/bumper/application/RepositoryServiceTest.java`

- [ ] **Step 1: Write failing unit tests**

Create `src/test/java/com/example/bumper/application/RepositoryServiceTest.java`:
```java
package com.volvo.bumper.application;

import com.volvo.bumper.domain.ForgeType;
import com.volvo.bumper.domain.Repository;
import com.volvo.bumper.domain.SyncResult;
import com.volvo.bumper.port.ForgePort;
import com.volvo.bumper.port.RepositoryStore;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RepositoryServiceTest {

    // Hand-rolled fakes — no Mockito, no Spring

    static class InMemoryStore implements RepositoryStore {
        private List<Repository> repos;

        InMemoryStore(List<Repository> initial) {
            this.repos = new ArrayList<>(initial);
        }

        @Override
        public List<Repository> findAll() {
            return List.copyOf(repos);
        }

        @Override
        public void saveAll(List<Repository> repos) {
            this.repos = new ArrayList<>(repos);
        }
    }

    static class FixedDateForgePort implements ForgePort {
        private final ForgeType supported;
        private final LocalDate date;

        FixedDateForgePort(ForgeType supported, LocalDate date) {
            this.supported = supported;
            this.date = date;
        }

        @Override
        public boolean supports(ForgeType forge) {
            return forge == supported;
        }

        @Override
        public Optional<LocalDate> fetchLastDependencyUpdate(Repository repo) {
            return Optional.of(date);
        }
    }

    static Repository githubRepo(String name) {
        return new Repository(name, ForgeType.GITHUB, "https://github.com/org/" + name,
                null, "team", "", Map.of());
    }

    // --- list ---

    @Test
    void list_returnsAllReposFromStore() {
        var repo = githubRepo("myrepo");
        var service = new RepositoryService(new InMemoryStore(List.of(repo)), List.of());

        assertThat(service.list()).containsExactly(repo);
    }

    // --- sync ---

    @Test
    void sync_updatesLastDependencyUpdate_andCountsChange() {
        var newDate = LocalDate.of(2026, 1, 15);
        var store = new InMemoryStore(List.of(githubRepo("myrepo")));
        var service = new RepositoryService(store,
                List.of(new FixedDateForgePort(ForgeType.GITHUB, newDate)));

        SyncResult result = service.sync();

        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.total()).isEqualTo(1);
        assertThat(store.findAll().get(0).lastDependencyUpdate()).isEqualTo(newDate);
    }

    @Test
    void sync_doesNotCount_whenDateUnchanged() {
        var date = LocalDate.of(2026, 1, 15);
        var repo = new Repository("repo", ForgeType.GITHUB, "https://github.com/org/repo",
                date, "team", "", Map.of());
        var store = new InMemoryStore(List.of(repo));
        var service = new RepositoryService(store,
                List.of(new FixedDateForgePort(ForgeType.GITHUB, date)));

        SyncResult result = service.sync();

        assertThat(result.updated()).isEqualTo(0);
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void sync_skipsRepo_whenNoForgePortMatches() {
        var repo = new Repository("repo", ForgeType.GERRIT, "https://gerrit.example.com/repo",
                null, "team", "", Map.of());
        var store = new InMemoryStore(List.of(repo));
        // only a GITHUB port — GERRIT repo gets skipped
        var service = new RepositoryService(store,
                List.of(new FixedDateForgePort(ForgeType.GITHUB, LocalDate.now())));

        SyncResult result = service.sync();

        assertThat(result.updated()).isEqualTo(0);
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void sync_doesNotCount_whenForgeReturnsEmpty() {
        var store = new InMemoryStore(List.of(githubRepo("myrepo")));
        ForgePort emptyPort = new ForgePort() {
            @Override public boolean supports(ForgeType forge) { return forge == ForgeType.GITHUB; }
            @Override public Optional<LocalDate> fetchLastDependencyUpdate(Repository r) {
                return Optional.empty();
            }
        };
        var service = new RepositoryService(store, List.of(emptyPort));

        SyncResult result = service.sync();

        assertThat(result.updated()).isEqualTo(0);
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void sync_savesAllRepos_includingUnchanged() {
        var date = LocalDate.of(2026, 1, 15);
        var github = githubRepo("github-repo");
        var gerrit = new Repository("gerrit-repo", ForgeType.GERRIT, "https://gerrit.example.com/repo",
                null, "team", "", Map.of());
        var store = new InMemoryStore(List.of(github, gerrit));
        // only GITHUB port — gerrit repo unchanged, github repo updated
        var service = new RepositoryService(store,
                List.of(new FixedDateForgePort(ForgeType.GITHUB, date)));

        service.sync();

        var saved = store.findAll();
        assertThat(saved).hasSize(2);
        assertThat(saved.stream().filter(r -> r.name().equals("github-repo"))
                .findFirst().orElseThrow().lastDependencyUpdate()).isEqualTo(date);
        assertThat(saved.stream().filter(r -> r.name().equals("gerrit-repo"))
                .findFirst().orElseThrow().lastDependencyUpdate()).isNull();
    }
}
```

- [ ] **Step 2: Run tests — expect compilation failure**

```bash
mvn test -Dtest=RepositoryServiceTest -q 2>&1 | tail -5
```
Expected: compilation error — `RepositoryService` not found.

- [ ] **Step 3: Implement RepositoryService**

Create `src/main/java/com/example/bumper/application/RepositoryService.java`:
```java
package com.volvo.bumper.application;

import com.volvo.bumper.domain.Repository;
import com.volvo.bumper.domain.SyncResult;
import com.volvo.bumper.port.ForgePort;
import com.volvo.bumper.port.RepositoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class RepositoryService {

    private static final Logger log = LoggerFactory.getLogger(RepositoryService.class);

    private final RepositoryStore store;
    private final List<ForgePort> forgePorts;

    public RepositoryService(RepositoryStore store, List<ForgePort> forgePorts) {
        this.store = store;
        this.forgePorts = forgePorts;
    }

    public List<Repository> list() {
        return store.findAll();
    }

    public SyncResult sync() {
        var repos = store.findAll();
        int updated = 0;

        var result = new java.util.ArrayList<Repository>(repos.size());
        for (var repo : repos) {
            var port = forgePorts.stream()
                    .filter(p -> p.supports(repo.forge()))
                    .findFirst();

            if (port.isEmpty()) {
                log.warn("No ForgePort found for forge type {} (repo: {})", repo.forge(), repo.name());
                result.add(repo);
                continue;
            }

            var fetched = port.get().fetchLastDependencyUpdate(repo);
            if (fetched.isPresent() && !fetched.get().equals(repo.lastDependencyUpdate())) {
                result.add(new Repository(
                        repo.name(), repo.forge(), repo.forgeUrl(),
                        fetched.get(), repo.team(), repo.comments(), repo.extra()));
                updated++;
            } else {
                result.add(repo);
            }
        }

        store.saveAll(result);
        return new SyncResult(updated, repos.size());
    }
}
```

- [ ] **Step 4: Run tests — expect pass**

```bash
mvn test -Dtest=RepositoryServiceTest -q
```
Expected: BUILD SUCCESS, 6 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/bumper/application/ src/test/java/com/example/bumper/application/
git commit -m "feat: RepositoryService with list and sync logic"
```

---

## Task 6: Fake Adapters

**Files:**
- Create: `src/main/java/com/example/bumper/adapter/confluence/FakeConfluenceRepositoryStore.java`
- Create: `src/main/java/com/example/bumper/adapter/github/FakeGitHubForgeAdapter.java`
- Create: `src/main/java/com/example/bumper/adapter/gerrit/FakeGerritForgeAdapter.java`

No isolated unit tests: behavior is covered by the integration smoke test in Task 9.

- [ ] **Step 1: Create FakeConfluenceRepositoryStore**

Create `src/main/java/com/example/bumper/adapter/confluence/FakeConfluenceRepositoryStore.java`:
```java
package com.volvo.bumper.adapter.confluence;

import com.volvo.bumper.domain.ForgeType;
import com.volvo.bumper.domain.Repository;
import com.volvo.bumper.port.RepositoryStore;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Profile("fake")
public class FakeConfluenceRepositoryStore implements RepositoryStore {

    private List<Repository> repos = new ArrayList<>(List.of(
            new Repository("platform-core", ForgeType.GITHUB,
                    "https://github.com/example/platform-core",
                    null, "platform-team", "Core platform library", Map.of()),
            new Repository("auth-service", ForgeType.GERRIT,
                    "https://gerrit.example.com/auth-service",
                    null, "security-team", "Authentication service", Map.of("criticality", "high"))
    ));

    @Override
    public List<Repository> findAll() {
        return List.copyOf(repos);
    }

    @Override
    public void saveAll(List<Repository> repos) {
        this.repos = new ArrayList<>(repos);
    }
}
```

- [ ] **Step 2: Create FakeGitHubForgeAdapter**

Create `src/main/java/com/example/bumper/adapter/github/FakeGitHubForgeAdapter.java`:
```java
package com.volvo.bumper.adapter.github;

import com.volvo.bumper.domain.ForgeType;
import com.volvo.bumper.domain.Repository;
import com.volvo.bumper.port.ForgePort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

@Component
@Profile("fake")
public class FakeGitHubForgeAdapter implements ForgePort {

    private static final LocalDate FIXED_DATE = LocalDate.of(2026, 2, 14);

    @Override
    public boolean supports(ForgeType forge) {
        return forge == ForgeType.GITHUB;
    }

    @Override
    public Optional<LocalDate> fetchLastDependencyUpdate(Repository repo) {
        return Optional.of(FIXED_DATE);
    }
}
```

- [ ] **Step 3: Create FakeGerritForgeAdapter**

Create `src/main/java/com/example/bumper/adapter/gerrit/FakeGerritForgeAdapter.java`:
```java
package com.volvo.bumper.adapter.gerrit;

import com.volvo.bumper.domain.ForgeType;
import com.volvo.bumper.domain.Repository;
import com.volvo.bumper.port.ForgePort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

@Component
@Profile("fake")
public class FakeGerritForgeAdapter implements ForgePort {

    private static final LocalDate FIXED_DATE = LocalDate.of(2026, 1, 20);

    @Override
    public boolean supports(ForgeType forge) {
        return forge == ForgeType.GERRIT;
    }

    @Override
    public Optional<LocalDate> fetchLastDependencyUpdate(Repository repo) {
        return Optional.of(FIXED_DATE);
    }
}
```

- [ ] **Step 4: Verify compilation**

```bash
mvn compile -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/bumper/adapter/
git commit -m "feat: fake adapters for Confluence, GitHub, Gerrit (profile: fake)"
```

---

## Task 7: CLI Commands

**Files:**
- Create: `src/main/java/com/example/bumper/cli/ListCommand.java`
- Create: `src/main/java/com/example/bumper/cli/SyncCommand.java`

- [ ] **Step 1: Create ListCommand**

Create `src/main/java/com/example/bumper/cli/ListCommand.java`:
```java
package com.volvo.bumper.cli;

import com.volvo.bumper.application.RepositoryService;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.stereotype.Component;

@Component
public class ListCommand {

    private final RepositoryService service;

    public ListCommand(RepositoryService service) {
        this.service = service;
    }

    @Command(name = "list", description = "List all tracked repositories")
    public String list() {
        var repos = service.list();
        if (repos.isEmpty()) {
            return "No repositories tracked.";
        }
        var sb = new StringBuilder();
        for (var repo : repos) {
            sb.append(String.format("%s  [%s]  %s  team:%s  updated:%s%n",
                    repo.name(),
                    repo.forge(),
                    repo.forgeUrl(),
                    repo.team(),
                    repo.lastDependencyUpdate() != null ? repo.lastDependencyUpdate() : "never"));
        }
        return sb.toString().stripTrailing();
    }
}
```

- [ ] **Step 2: Create SyncCommand**

Create `src/main/java/com/example/bumper/cli/SyncCommand.java`:
```java
package com.volvo.bumper.cli;

import com.volvo.bumper.application.RepositoryService;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.stereotype.Component;

@Component
public class SyncCommand {

    private final RepositoryService service;

    public SyncCommand(RepositoryService service) {
        this.service = service;
    }

    @Command(name = "sync", description = "Sync repository data from forges and update Confluence")
    public String sync() {
        var result = service.sync();
        return String.format("Sync complete: %d/%d repos updated", result.updated(), result.total());
    }
}
```

- [ ] **Step 3: Verify build and existing tests pass**

```bash
mvn verify -q
```
Expected: BUILD SUCCESS. (`contextLoads` test may still fail without a valid Spring context — expected until Task 9.)

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/bumper/cli/
git commit -m "feat: list and sync CLI commands"
```

---

## Task 8: ArchUnit Tests

**Files:**
- Create: `src/test/java/com/example/bumper/ArchitectureTest.java`

- [ ] **Step 1: Create ArchitectureTest**

Create `src/test/java/com/example/bumper/ArchitectureTest.java`:
```java
package com.volvo.bumper;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.volvo.bumper", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    // domain must not depend on port, application, adapter, cli, or any framework
    @ArchTest
    static final ArchRule domain_must_not_depend_on_other_layers =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..port..", "..application..", "..adapter..", "..cli..");

    @ArchTest
    static final ArchRule domain_must_not_use_spring =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("org.springframework..");

    // application must not depend on adapter or cli
    @ArchTest
    static final ArchRule application_must_not_depend_on_adapter_or_cli =
            noClasses().that().resideInAPackage("..application..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..adapter..", "..cli..");

    // cli must not depend on adapter directly
    @ArchTest
    static final ArchRule cli_must_not_depend_on_adapter =
            noClasses().that().resideInAPackage("..cli..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..adapter..");

    // port must not depend on application, adapter, or cli
    @ArchTest
    static final ArchRule port_must_not_depend_on_application_adapter_or_cli =
            noClasses().that().resideInAPackage("..port..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..application..", "..adapter..", "..cli..");
}
```

- [ ] **Step 2: Run ArchUnit tests**

```bash
mvn test -Dtest=ArchitectureTest -q
```
Expected: BUILD SUCCESS, 5 rules pass. If any rule fails, the error message names the violating class and dependency — fix that class before continuing.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/example/bumper/ArchitectureTest.java
git commit -m "test: ArchUnit layer dependency rules"
```

---

## Task 9: Integration Smoke Tests

**Files:**
- Modify: `src/test/java/com/example/bumper/BumperApplicationTests.java`

- [ ] **Step 1: Replace BumperApplicationTests with smoke tests**

Replace the entire file:
```java
package com.volvo.bumper;

import com.volvo.bumper.application.RepositoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("fake")
// Fresh Spring context per test: FakeConfluenceRepositoryStore is stateful (singleton)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class BumperApplicationTests {

    @Autowired
    RepositoryService service;

    @Test
    void contextLoads() {
        // Spring context starts successfully with fake profile
    }

    @Test
    void list_returnsFakeRepos() {
        var repos = service.list();
        assertThat(repos).isNotEmpty();
        // fake store has platform-core (GITHUB) and auth-service (GERRIT)
        assertThat(repos).anyMatch(r -> r.name().equals("platform-core"));
        assertThat(repos).anyMatch(r -> r.name().equals("auth-service"));
    }

    @Test
    void sync_updatesAndReturnsSummary() {
        var result = service.sync();
        // both repos start with null lastDependencyUpdate, so both get updated
        assertThat(result.total()).isEqualTo(2);
        assertThat(result.updated()).isEqualTo(2);
    }

    @Test
    void sync_thenList_reflectsUpdatedDates() {
        service.sync();
        var repos = service.list();
        assertThat(repos).allMatch(r -> r.lastDependencyUpdate() != null);
    }
}
```

- [ ] **Step 2: Run all tests**

```bash
mvn verify -q
```
Expected: BUILD SUCCESS, all tests pass (2 domain + 6 service + 5 arch + 4 smoke = 17 tests).

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/example/bumper/BumperApplicationTests.java
git commit -m "test: integration smoke tests with fake profile"
```

---

## Verification

After all tasks complete, run the full suite one final time:

```bash
mvn verify
```

Expected output includes lines like:
```
Tests run: 17, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

To manually test the CLI:
```bash
mvn spring-boot:run
```
Then at the shell prompt:
```
shell:> list
shell:> sync
shell:> list
```
The second `list` should show updated dates.
