# QWEN.md — NekoSystem Development Guide

## Project Overview

NekoSystem builds **NekoPlugin**, a modular quality-of-life and gameplay plugin for Paper/Folia Minecraft servers.

- **Language:** Java 21
- **Build tool:** Gradle Wrapper (`./gradlew`)
- **Server API:** Paper `1.21.11` via paperweight userdev
- **Test framework:** JUnit 5; Mockito is available
- **Plugin entry point:** `com.nyarutoru.nekoplugin.NekoPlugin`
- **Plugin descriptor:** `src/main/resources/paper-plugin.yml`
- **Folia support:** declared with `folia-supported: true`

Treat `build.gradle`, `paper-plugin.yml`, and current source code as authoritative. `CLAUDE.md` refers to Paper `1.21.1`, but the active build and plugin descriptor target `1.21.11`.

## Architecture

The code is organized as a modular monolith under `src/main/java/com/nyarutoru/nekoplugin/`:

- `NekoPlugin.java` initializes shared managers, registers features, enables them, and performs shutdown.
- `core/` contains feature lifecycle and database infrastructure.
  - `FeatureManager` registers and enables/disables modules.
  - `AbstractFeature` tracks listeners and handles common cleanup.
  - `DatabaseManager` manages SQLite-backed persistence.
- `api/` contains reusable systems such as GUI, recipes, active tools, and vein-mining abstractions.
- `features/` contains independent gameplay modules, normally structured around a small `*Feature` lifecycle class and supporting listeners, data, recipes, GUI, or configuration classes.
- `utils/` contains shared scheduling, text/component, item, location, performance, and block-position helpers.

`NekoPlugin.java` is the authoritative list of runtime-enabled features. A feature package existing in the tree does not mean the feature is active.

### Currently Registered Features

- **Drawer:** bulk single-item storage, upgrades, GUI/hopper interaction, and persistence.
- **Ore Excavation:** shift-activated vein mining through the shared active-tool API.
- **Sand Excavation:** mass mining for sand and gravel with shovels.
- **Hammer:** craftable tiered hammers with 3×3 mining.
- **Player:** AFK behavior, automatic item replenishment, and crop harvesting/replanting.
- **Server:** server-wide recipes and utilities, TPS display, messages, concrete conversion, specialized mining, ladder extension, anvil repair, and lag warnings.
- **Woodcutting:** stonecutter recipes for converting logs into wood products.
- **TreeFeller:** tree detection and felling with tool matching, effects, and optional animation.

The Graves implementation exists under `features/graves/` but is not registered and is therefore inactive. Do not enable it without also resolving its command registration: its command classes expect commands that are not declared in `paper-plugin.yml`.

## Building and Testing

Use the checked-in Gradle Wrapper and Java 21:

```bash
./gradlew test
./gradlew build
./gradlew clean build
```

- `./gradlew test` runs the JUnit 5 suite.
- `./gradlew build` is the same build used by GitHub Actions.
- Build artifacts are written to `build/libs/`.
- CI configuration is in `.github/workflows/gradle.yml`.

`build.sh` invokes a system-installed `gradle` executable. Prefer `./gradlew` because it pins the project-compatible Gradle version and does not require a separate installation.

There is no repository-provided development-server or run-server task. To test the plugin in Minecraft, build the JAR and install it into a compatible Paper/Folia server manually.

## Development Conventions

### Feature Lifecycle

- Implement features by extending `AbstractFeature`.
- Give each feature a stable lowercase ID; use underscores for multiword IDs.
- Register listeners through `registerListener(listener, plugin)` so they are automatically unregistered.
- Put additional shutdown work in `cleanup()`. If overriding `onDisable()`, call `super.onDisable()`.
- Register a new active feature in `NekoPlugin.onEnable()`.
- Do not rely on feature registration order as startup order; `FeatureManager` stores features in a `ConcurrentHashMap`.
- A failure in one feature should remain isolated and must not prevent unrelated features from loading.

### Paper and Folia Safety

- Preserve Java 21 and the Paper version configured in `build.gradle`.
- Verify unfamiliar or version-sensitive Paper APIs against the matching Paper Javadocs before use. Do not rely on signatures from older versions.
- Use Adventure `Component`/MiniMessage APIs for player-facing text rather than legacy color codes.
- Use the plugin logger instead of `System.out.println`.
- Use existing scheduling abstractions such as `SchedulerUtils`; do not introduce raw scheduling that breaks Folia compatibility.
- Never mutate world, entity, inventory, or other server state from an unsafe asynchronous context.
- Avoid blocking operations on server threads. Move genuinely heavy work to an appropriate asynchronous path, then return world-state changes to the correct scheduler context.

### Code Organization and Style

- Keep feature-specific code inside its feature package; move code into `api/` or `utils/` only when it is genuinely shared.
- Follow existing package naming, class naming, formatting, and lifecycle patterns in neighboring code.
- Prefer existing helpers for components, items, locations, scheduling, performance tracking, and block positions over duplicate utilities.
- Keep configuration and constants centralized in the owning feature.
- Use `BlockPos` rather than mutable Bukkit `Location` objects for large traversal/visited sets when neighboring code follows that pattern.
- Preserve external behavior and persistence formats unless a migration is explicitly part of the task.
- Validate inputs at system boundaries and handle player disconnects, world unloads, tool breakage, and plugin shutdown where relevant.
- Add comments only for non-obvious invariants, compatibility constraints, or workarounds; favor clear names and structure for ordinary behavior.

### Persistence

- Database lifecycle is owned by `DatabaseManager`: initialize during plugin startup and shut down during plugin disable.
- Keep database operations and migrations compatible with existing stored data.
- Confirm runtime packaging before introducing or relying on JDBC/Gson functionality. The source references `org.sqlite.JDBC`, but the current `build.gradle` does not declare an explicit SQLite or Gson runtime dependency.

## Testing Practices

Tests live under `src/test/java/` and use JUnit 5.

- Add focused regression tests for changed pure logic.
- Follow the existing package layout and descriptive `test...` method naming.
- Prefer assertions about observable behavior over class-existence checks or unconditional assertions.
- Mockito is declared and may be used when it matches existing boundaries, but there is no MockBukkit-based integration harness.
- Bukkit/Paper lifecycle, scheduler, event, and live-world behavior is not comprehensively covered by the current suite. A passing unit suite is not proof that server integration works.
- For Paper-dependent changes, run the relevant unit tests and build, then state clearly when live-server verification was not performed.

Minimum verification for a normal code change:

```bash
./gradlew test
./gradlew build
```

For a small, isolated change, run the narrow relevant test first; run the full build before final delivery when shared APIs, feature lifecycle, resources, or packaging are affected.

## Resources and Commands

`src/main/resources/paper-plugin.yml` currently contains only plugin metadata and declares no commands or permissions.

When adding a command:

1. Add its declaration and permissions to `paper-plugin.yml`.
2. Register its executor/tab completer in the owning feature.
3. Test missing-command and permission behavior.
4. Keep command lifecycle consistent with feature enable/disable behavior.

`processResources` expands the Gradle `version` property only for a file named `plugin.yml`; the repository currently uses `paper-plugin.yml` with a literal version. If version substitution is needed, update the resource-processing rule deliberately rather than assuming expansion already occurs.

## Working Rules for Future Changes

- Read the owning feature, its listener/data classes, and relevant tests before editing.
- Search all callers before changing a shared API, feature ID, persistence schema, or configuration contract.
- Treat `PLANS.md` as historical/planning context, not a description of current behavior. Verify every claim against current source.
- Do not describe unregistered features as available at runtime.
- Do not edit generated output under `build/`, Gradle caches, IDE metadata, logs, or `.git/`.
- Keep changes scoped to the requested feature; do not perform unrelated refactors.
- Review the final diff for accidental generated files, debug output, deprecated APIs, unsafe scheduling, and missing resource declarations.
- Report the exact verification commands run and distinguish unit/build success from live Paper/Folia server testing.
