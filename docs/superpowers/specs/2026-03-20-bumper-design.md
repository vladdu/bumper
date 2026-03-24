# Bumper — Design Spec
_Date: 2026-03-20_

## Overview

Bumper is a Java 25 / Spring Boot 4 / Spring Shell 4 CLI that tracks git repositories (from GitHub or Gerrit), recording metadata and when their dependencies were last updated. Data is persisted to a Confluence page (table format) and fetched from git forges. Architecture follows ports-adapters (hexagonal), enforced by ArchUnit.

---

## Domain Model

Package: `com.volvo.bumper.domain`

```
Repository (immutable record)
  - name: String
  - forge: ForgeType
  - forgeUrl: String
  - lastDependencyUpdate: LocalDate  // nullable
  - team: String
  - comments: String
  - extra: Map<String, String>       // defensive copy via Map.copyOf in compact constructor

ForgeType (enum)
  - GITHUB
  - GERRIT

SyncResult (record)
  - updated: int   // repos whose lastDependencyUpdate changed
  - total: int     // total repos loaded from RepositoryStore (including skipped ones)
```

No framework annotations in this package. Pure Java.

---

## Ports

Package: `com.volvo.bumper.port`

```java
// Outbound: persistence
interface RepositoryStore {
    List<Repository> findAll();
    void saveAll(List<Repository> repos);
}

// Outbound: forge data
interface ForgePort {
    boolean supports(ForgeType forge);
    Optional<LocalDate> fetchLastDependencyUpdate(Repository repo);
}
```

---

## Application Service

Package: `com.volvo.bumper.application`

```java
class RepositoryService {
    List<Repository> list();
    SyncResult sync();
}
```

`sync` logic:
1. Load all repos from `RepositoryStore`
2. For each repo, find a `ForgePort` that `supports(repo.forge())`; if none found, skip the repo and log a warning
3. Call `fetchLastDependencyUpdate`; if `Optional.empty()`, leave `lastDependencyUpdate` unchanged and count as not-updated; if present and different from current value, update the record
4. Save all (including unchanged) back via `RepositoryStore`
5. Return `SyncResult` with count of repos whose date changed and total count

Depends on: `RepositoryStore`, `List<ForgePort>`. `RepositoryService` carries `@Service` for Spring wiring — the only framework annotation permitted in `application`. All forge-selection and sync logic remains in plain Java methods.

---

## Adapters

Package: `com.volvo.bumper.adapter`

### Fake adapters (Spring profile: `fake`)

| Class | Port | Behavior |
|---|---|---|
| `FakeConfluenceRepositoryStore` | `RepositoryStore` | Starts with hardcoded list; `saveAll` updates in-memory state (so `list` after `sync` reflects updated data) |
| `FakeGitHubForgeAdapter` | `ForgePort` | Supports `GITHUB`; returns fixed `LocalDate` |
| `FakeGerritForgeAdapter` | `ForgePort` | Supports `GERRIT`; returns fixed `LocalDate` |

All annotated `@Component @Profile("fake")`.

Real adapters (future): `@Profile("!fake")` or a dedicated named profile.

---

## CLI Commands

Package: `com.volvo.bumper.cli`

| Command | Description | Output |
|---|---|---|
| `list` | Calls `RepositoryService.list()`, prints table to console | One repo per line: `{name}  [{forge}]  {forgeUrl}  team:{team}  updated:{lastDependencyUpdate}` |
| `sync` | Calls `RepositoryService.sync()`, prints summary | "Sync complete: {updated}/{total} repos updated" |

Spring Shell `@Command` annotations only in this package.

---

## Package Layout

```
com.volvo.bumper
  domain/
    Repository.java
    ForgeType.java
    SyncResult.java
  port/
    RepositoryStore.java
    ForgePort.java
  application/
    RepositoryService.java
  adapter/
    confluence/
      FakeConfluenceRepositoryStore.java
    github/
      FakeGitHubForgeAdapter.java
    gerrit/
      FakeGerritForgeAdapter.java
  cli/
    ListCommand.java
    SyncCommand.java
  BumperApplication.java
```

---

## Architecture Rules (ArchUnit)

Enforced in a single test class `ArchitectureTest`.

Permitted dependencies (the intentional couplings):
- `port` may depend on `domain` (ports use domain types)
- `application` may depend on `port` and `domain`
- `adapter` may depend on `port` and `domain`
- `cli` may depend on `application` and `domain`

Forbidden dependencies (enforced by ArchUnit):
1. `domain` must not depend on `port`, `application`, `adapter`, `cli`, or any framework
2. `application` must not depend on `adapter` or `cli`
3. `cli` must not depend on `adapter` directly
4. `port` must not depend on `application`, `adapter`, or `cli`

Note: `cli` depends directly on `RepositoryService` (concrete class in `application`). There is no inbound port interface for the initial phase — this is a deliberate simplification. An inbound port can be introduced later if the CLI grows multiple entry points.

---

## Testing Strategy

- **Unit tests**: `RepositoryService` tested with hand-rolled fakes (no Spring context). Fast, pure domain logic coverage.
- **ArchUnit tests**: `ArchitectureTest` asserts all layer rules above.
- **Integration smoke tests**: Spring Shell test with `@ActiveProfiles("fake")`; verifies `list` produces at least one formatted line and `sync` produces the `"Sync complete: ..."` summary line.

---

## Out of Scope (initial phase)

- Real Confluence, GitHub, Gerrit adapters
- `add`, `remove`, `edit` commands
- Authentication / credential management
- Pagination of Confluence table
