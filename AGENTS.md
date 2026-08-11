# AGENTS.md — NekoSystem / NekoPlugin

Paper/Folia plugin. Published name `NekoPlugin`, entry `com.nyarutoru.nekoplugin.NekoPlugin`, descriptor `src/main/resources/paper-plugin.yml` (`api-version: 26.2`, `folia-supported: true`).

## Commands

```bash
./gradlew test                          # full suite (passed/skipped/failed logged)
./gradlew test --tests 'com.nyarutoru.nekoplugin.features.treefeller.TreeFellerValidationTest'  # single test
./gradlew build                         # compile + test + jar + resource-pack + bedrock-pack + geyser-mappings -> build/libs/
./gradlew clean build                   # diagnosing stale output
./gradlew buildResourcePack             # -> build/libs/NekoPlugin-ResourcePack.zip (pack.mcmeta + assets/nekoplugin)
./gradlew buildBedrockPack              # -> build/libs/NekoPlugin-BedrockPack.mcpack (Bedrock textures)
./gradlew buildGeyserMappings           # -> build/libs/NekoPlugin-GeyserMappings.json + geyser/nekoplugin.json
```
Prefer `./gradlew` over `build.sh` (needs system `gradle`). `build` already depends on the three pack/mapping tasks. No dev server checked in — copy jar to Paper/Folia `plugins/` to test. CI `/.github/workflows/gradle.yml`: JDK 25, `./gradlew build`, auto-release `build/libs/*` as `v<run_number>` on push to `main`. `install.sh` is Termux-only (glibc-runner + Claude) — not needed for plugin build.

## Stack

- Java 25 (toolchain fallback if host <25), UTF-8, `-Xlint:all` — see `build.gradle:32-48`
- Gradle Wrapper 9.0.0 + Paperweight `2.0.0-beta.21` + `paperDevBundle("26.2.build.+")` — no runtime deps
- JUnit 5.10.2 + Mockito 5.11.0; SQLite via `core/DatabaseManager` (WAL, per-feature DB `plugins/NekoPlugin/database/<feature>/<feature>.db`, 5s reconnect cooldown)

## Layout

- `src/main/java/com/nyarutoru/nekoplugin/NekoPlugin.java` — lifecycle (see below)
- `core/` — `Feature`, `AbstractFeature`, `FeatureManager`, `DatabaseManager`, `admin/` (`AdminState`, `AdminConfigStore`→`admin.yml`, `NekoCommand`), `settings/` (`SettingRegistry`, `SettingDescriptor`)
- `api/` — `gui/` (`BaseGUI`, `GUIManager`, `AnvilTextInputGUI`), `recipe/` (`CustomRecipe`, `RecipeAPI`), `tool/` (`AbstractVeinMiner`, `ActiveToolAPI`)
- `features/` — `carry`, `curse` (AquaCurse→`aqua-curse.yml`, not `AdminState`), `drawer`, `graves`, `hammer`, `magnet`, `oreexcavation`, `player`, `server`, `tool` (SandExcavation), `treefeller`, `villageroptimize`, `woodcutting` (13 total)
- `utils/` — `SchedulerUtils`, `ComponentUtils`, `ItemUtils`, `BlockPos`, `LocationUtils`, `ServerPerformanceUtils`
- `src/main/resources/paper-plugin.yml` — metadata + permissions (`nekoplugin.grave.use`/`.admin`) only; `/neko` registered in code
- `src/main/resources/assets/nekoplugin/` + `pack.mcmeta` — Java resource pack; `geyser/nekoplugin.json` — committed copy of Geyser mappings (generated, don't hand-edit)
- `src/test/java` mirrors `src/main/java`

## Architecture

**Lifecycle `NekoPlugin.java:37-114`:** `onEnable` = `AdminConfigStore.load()` → `DatabaseManager.initialize()` → `FeatureManager.initialize()` → `GUIManager.initialize()` → `registerFeature` ×13 → `registerSettings` each (AquaCurse skips — uses its own YAML) → `registerCommand("neko",…)` → `FeatureManager.enableDesired(adminState::desiredEnabled)` → `ActiveToolListener`. `onDisable` reverse: `FeatureManager.shutdown()` → `ActiveToolAPI.shutdown()` → `RecipeAPI.clear()` → `AdminConfigStore.flush()` → `DatabaseManager.shutdown()`.

**Feature system:** extend `core/AbstractFeature` (`id`, `name`, `registerListener` auto-unregisters, `ownTask` tracks `SchedulerUtils.TaskHandle`, override `cleanup()` not `onDisable`). `FeatureManager` is singleton `LinkedHashMap`, `synchronized`, `TransitionResult(CHANGED|ALREADY_IN_STATE|NOT_FOUND|FAILED)`, `enableDesired(Predicate)` / `enableAll` / `disableAll` reverse-order. Adding a feature: extend `AbstractFeature`, implement `registerSettings` if needed, register instance in `NekoPlugin.onEnable` before `enableDesired`.

**Admin/settings:** `AdminState` thread-safe maps `desiredFeatures` (default true) + `settingValues`; `AdminConfigStore` coalesced async writes to `admin.yml` + sync `flush()` on disable; `SettingRegistry` groups by `featureId` with duplicate-key check.

**Check before new abstraction:** `api/gui`, `api/recipe`, `api/tool`, `utils/*` — reuse existing.

## Gotchas

- **Scheduling/Folia:** never mutate world/block/entity/inventory/player off main/region thread. Use `SchedulerUtils` (`runAtEntity`/`runAtLocation`/`runGlobal`/`runAsync` + `*Later`/`*Timer` → `TaskHandle`) not `BukkitScheduler`. Folia detection via `RegionizedServer` class; shutdown returns dummy cancelled handle.
- **DB:** use `DatabaseManager.getConnection(featureName)` / `createTable`, not raw connections. AquaCurse bypasses DB (flat `aqua-curse.yml`).
- **Config:** `paper-plugin.yml` is metadata-only; TreeFeller etc. hardcode config in Java — inspect impl before assuming YAML/reload.
- **Paper API:** version is `build.gradle` + `paper-plugin.yml` (`26.2`). Verify unfamiliar APIs via `https://jd.papermc.io/paper/26.2/` + WebSearch; prefer Adventure `Component`/`MiniMessage` over legacy codes. `QWEN.md`/`PLANS.md` are stale/historical — trust executable sources.
- **Tests:** pure domain validation, avoid Bukkit enum init; events/scheduler/registry/world need manual Paper/Folia verification. Always run focused test → `./gradlew test` → `./gradlew build`.
- **Style:** Java 25, 4-space, braces same line, `com.nyarutoru.nekoplugin`, `getLogger()` not `System.out`.
- **Guardrails:** never edit `.git/`, `build/`, `.gradle/`, `logs/latest.log`, IDE metadata (`.idea/`, `.vscode/`, `.settings/`, `.classpath`, `.project`); don't commit secrets/DBs/worlds; don't add deps until Paper API proven insufficient; never block main/region thread.

## Maintenance

- **Always update this file** — any change to commands, stack versions, feature list, `NekoPlugin.java` lifecycle, `paper-plugin.yml`, or a new gotcha must patch `AGENTS.md` in the same commit.
