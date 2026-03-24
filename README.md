# bumper

A CLI tool that tracks **dependency-bump activity** across a team's repositories.

It reads a list of projects from a Confluence page, discovers their associated repositories in Gerrit and GitHub, scans commit histories for dependency-bump commits, and displays the results in a formatted terminal table.

## Requirements

- Java 25
- Maven 3.x (to build)
- SSH access to Gerrit with an appropriate key

## Building

```bash
mvn package
```

This produces a fat JAR at `target/bumper-0.1.0.jar`.

> Integration tests (tagged `@Tag("integration")`, files matching `*IT.java`) are excluded by default. Run them with:
> ```bash
> mvn test -Pintegration
> ```

## Configuration

All configuration is read from **environment variables**. A `.env` file in the working directory is also supported (environment variables take precedence).

| Variable | Description |
|---|---|
| `GERRIT_SSH_USER` | SSH username for the Gerrit server |
| `GERRIT_SSH_KEY` | Path to the SSH private key for Gerrit |
| `GITHUB_TOKEN` | GitHub Personal Access Token (Bearer) |
| `CONFLUENCE_URL` | Confluence base URL (e.g. `https://company.atlassian.net/wiki`) |
| `CONFLUENCE_USERNAME` | Confluence user email |
| `CONFLUENCE_API_TOKEN` | Confluence API token |
| `CONFLUENCE_PAGE_ID_READ` | Numeric ID of the Confluence page to read the project table from |
| `CONFLUENCE_PAGE_ID_WRITE` | Numeric ID of the Confluence page to write results back to (may differ from the read page) |

See `.env.example` for a template.

## Usage

Run the JAR to enter an interactive shell:

```bash
java -jar target/bumper-0.1.0.jar
```

All commands support `--write` to persist changes (default is dry-run). Most destructive operations print a `[DRY RUN]` prefix and a reminder to pass `--write` to apply.

---

### `list`

Show all currently tracked and ignored repositories.

```
list
```

---

### `sync`

Pull fresh data from all configured forges (Gerrit, GitHub) and update the repository store.

- Repositories already in the tracked list get their bump data refreshed.
- Newly discovered repositories that are not yet tracked are automatically added to the **ignored** list for review.

```
sync [--write] [-v]
```

| Option | Description |
|---|---|
| `--write` | Persist the updated data to the store |
| `-v`, `--verbose` | Print a detailed log of what changed |

---

### `track`

Move a project from the ignored list to the tracked list. Bump data is refreshed from the forges at the same time.

```
track <name> [--write]
```

---

### `ignore`

Move a project from the tracked list to the ignored list.

```
ignore <name> [--write]
```

---

## Repository Discovery

When `sync` runs, it asks each configured forge for its list of relevant repositories. Relevance is defined by a filter baked into each adapter:

**Gerrit** — lists all projects under the `vgt/connectivity/` namespace that grant access to the `VGCS-ConnectivityServices` ACL group. This is equivalent to running `gerrit ls-projects -p vgt/connectivity/ --has-acl-for VGCS-ConnectivityServices` over SSH.

**GitHub** — lists all repositories that belong to the `vgcs-cos` team in the `VolvoGroup-Internal` organisation. Only repos explicitly assigned to that team are included.

Repositories discovered for the first time are automatically placed on the **ignored** list, giving you a chance to review them before they appear in tracking. Use `track <name>` to promote a repository to the tracked list.

## Bump Detection

A commit is classified as a dependency-bump commit if its subject line matches any of the following patterns (case-insensitive):

- Contains the words `bump`, `upgrade`, `renovate`, or `dependabot`
- Conventional commit scope matching `deps` or `deps-dev` — e.g. `chore(deps): ...`, `build(deps-dev): ...`
- Contains `update` followed by a dependency-related keyword — e.g. `update dependencies`, `update packages`, `upgrade lockfile`

This covers automated tools like **Renovate** and **Dependabot** as well as manual bump commits.

## Architecture

The project follows a **hexagonal / ports-and-adapters** layout:

```
CLI commands (list / sync / track / ignore)
    └── RepositoryService (application logic)
            ├── RepositoryStore port  →  Confluence adapter (reads/writes project table)
            └── ForgePort(s)          →  Gerrit adapter + GitHub adapter (discovers repos & bump data)
```

All I/O sits behind port interfaces so the core logic is independently testable. The `sync` command merges data from all forges — when the same project appears in both Gerrit and GitHub, the most recent bump date wins.
