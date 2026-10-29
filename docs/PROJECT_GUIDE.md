# InspectMC — project guide

In-game inspection and export tools for modpack creators, datapack authors and developers.

Source and publication snapshot: **2026-10-29**. This guide describes the checked-out source; a download page can contain an earlier release.

## Status and official links

- [Source repository](https://github.com/LevelsFR/inspectmc/tree/main) (public).
- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/inspectmc) — published project verified under LevelsFR.
- Modrinth: no matching project found in the public LevelsFR project list checked on 2026-10-21.

## Features in the current source

- Inspect held items, blocks, block entities, entities, players and biomes.
- Search registries, locate nearby content and inspect loaded runtime data.
- Optional Cobblemon entity, player and spawn-pool diagnostics.
- Deterministic JSON, CSV and Excel exports with timestamped archives.
- Combined ZIP reports containing registry, recipe, biome, tag, mod and datapack information.

## Compatibility and dependencies

| Component | Value |
| --- | --- |
| Source version | `1.0.2` |
| Minecraft baseline | `1.21.1` |
| JDK | `21` |
| Active branch | `main` |

| Loader | Dependency | Declared constraint | Requirement |
| --- | --- | --- | --- |
| Fabric | `fabricloader` | `>=0.19.3` | required |
| Fabric | `minecraft` | `=1.21.1` | required |
| Fabric | `java` | `>=21` | required |
| Fabric | `fabric-api` | `*` | required |
| Fabric | `cloth-config` | `*` | suggests |
| Fabric | `modmenu` | `*` | suggests |
| Fabric | `cobblemon` | `>=1.8.2 <1.8.5` | suggests |
| Fabric | `os_cobblemon_library` | `>=1.5.2 <2.0.0` | suggests |
| NeoForge | `neoforge` | `[21.1,)` | required; BOTH |
| NeoForge | `minecraft` | `[1.21.1]` | required; BOTH |
| NeoForge | `cloth_config` | `[15.0.140,)` | optional; CLIENT |
| NeoForge | `cobblemon` | `[1.8.2,1.8.5)` | optional; BOTH |
| NeoForge | `os_cobblemon_library` | `[1.5.2,2.0.0)` | optional; BOTH |

The table reflects loader metadata. Build-time version pins are in [gradle.properties](../gradle.properties). A dependency used only for compilation or optional integration is not automatically required for every installation.

### Additional configured source targets

| Source directory | Minecraft | JDK | Version properties |
| --- | --- | --- | --- |
| `26.2` | `26.2` | `see target build` | [versions/26.2/gradle.properties](../versions/26.2/gradle.properties) |
| `26.3` | `26.3` | `see target build` | [versions/26.3/gradle.properties](../versions/26.3/gradle.properties) |

A configured source target is not a statement that a matching public release or runtime qualification exists.

## Installation and first use

1. Choose a released JAR matching both the Minecraft version and loader, when this project has a release page.
2. Install the required dependencies declared for that loader. A source-only development version may require locally built dependencies.
3. Use a separate development instance for unreleased builds. Keep client-side integrations on the correct side and follow the main README for server installation.
4. Start the game/server once to create configuration, then use the project settings or documented commands.

### Command entry points

- `/inspectmc hand`
- `/inspectmc inspect biome`
- `/inspectmc spawn validate`
- `/inspectmc dump report`

Commands with arguments have contextual completion in game. Permission checks and the complete argument tree are defined in the linked command sources below.

## Command implementation reference

These are literal builder nodes found in each source file, not independent full command syntaxes. Read the builder tree for nesting, arguments and permissions.

| Source | Declared command nodes |
| --- | --- |
| [common/src/main/java/com/levelsfr/inspectmc/command/InspectMCCommands.java](../common/src/main/java/com/levelsfr/inspectmc/command/InspectMCCommands.java) | `inspectmc`, `search`, `page`, `hand`, `full`, `inspect`, `held`, `details`, `block`, `target`, `id`, `entity`, `look`, `blockentity`, `biome`, `player`, `locate`, `loaded`, `entities`, `dimensions`, `chunks`, `blockentities`, `stats`, `blocks`, `biomes`, `spawn`, `validate`, `here`, `explain`, `dump` |

## Configuration and saved data

| Configuration implementation | File names or path literals |
| --- | --- |
| [common/src/main/java/com/levelsfr/inspectmc/config/InspectMCConfig.java](../common/src/main/java/com/levelsfr/inspectmc/config/InspectMCConfig.java) | `inspectmc.json` |

### Selected source defaults

These values are field initializers from the current configuration classes. Nested sections, validation, migrations and server synchronization can affect the final effective value.

[common/src/main/java/com/levelsfr/inspectmc/config/InspectMCConfig.java](../common/src/main/java/com/levelsfr/inspectmc/config/InspectMCConfig.java)

- `operatorPermissionLevel = 2`
- `defaultBlockDistance = 20`
- `defaultEntityDistance = 20`
- `defaultLocateResults = 8`
- `searchPageSize = 30`
- `listPageSize = 50`
- `loadedEntityPageSize = 25`
- `maxDumpArchives = 0`

Keep world data and player data with the matching world backup. Use the mod-specific reload/configuration route documented in the main README; changing an arbitrary file does not imply live reloading. Do not copy development-world data into the source repository.

## Source map

| Directory | Responsibility |
| --- | --- |
| [common](../common) | Shared behavior and resources; loader-independent contracts where the architecture provides them. |
| [fabric](../fabric) | Fabric entrypoints, metadata and platform integration. |
| [neoforge](../neoforge) | NeoForge entrypoints, metadata and platform integration. |
| [versions](../versions) | Additional version-specific source/build targets. |
| [gradle](../gradle) | Committed wrapper and build configuration. |

Metadata: [fabric/src/main/resources/fabric.mod.json](../fabric/src/main/resources/fabric.mod.json), [neoforge/src/main/resources/META-INF/neoforge.mods.toml](../neoforge/src/main/resources/META-INF/neoforge.mods.toml).

| Package | Entry points to inspect |
| --- | --- |
| `com/levelsfr/inspectmc/client/config` | [common/src/main/java/com/levelsfr/inspectmc/client/config/ClothConfigCompat.java](../common/src/main/java/com/levelsfr/inspectmc/client/config/ClothConfigCompat.java), [common/src/main/java/com/levelsfr/inspectmc/client/config/ClothConfigUnavailableScreen.java](../common/src/main/java/com/levelsfr/inspectmc/client/config/ClothConfigUnavailableScreen.java), [common/src/main/java/com/levelsfr/inspectmc/client/config/InspectMCClothConfigScreen.java](../common/src/main/java/com/levelsfr/inspectmc/client/config/InspectMCClothConfigScreen.java) |
| `com/levelsfr/inspectmc/command` | [common/src/main/java/com/levelsfr/inspectmc/command/InspectMCCommands.java](../common/src/main/java/com/levelsfr/inspectmc/command/InspectMCCommands.java) |
| `com/levelsfr/inspectmc/config` | [common/src/main/java/com/levelsfr/inspectmc/config/InspectMCConfig.java](../common/src/main/java/com/levelsfr/inspectmc/config/InspectMCConfig.java) |

## Build from source

Run commands from the repository root using a matching JDK and the committed Gradle wrapper.

```powershell
.\gradlew.bat buildAllVersions
```

Loader JARs are normally written under the corresponding `build/libs` directory. Aggregate projects document their collected artifact layout in the main README or build helper.

The build pins OS Cobblemon Library `1.5.2`. Inspect the build repositories before compiling; projects that resolve it from Maven Local need the matching local publication. See the [library Maven Local guide](https://github.com/LevelsFR/OS-Cobblemon-Library/blob/levelsfr/oscl-1.5.2-loader-detection/docs/LOCAL_MAVEN.md).

## Documentation index

- [.github/pull_request_template.md](../.github/pull_request_template.md)
- [CHANGELOG.md](../CHANGELOG.md)
- [CONTRIBUTING.md](../CONTRIBUTING.md)
- [README.md](../README.md)
- [SECURITY.md](../SECURITY.md)

## Validation and reporting a problem

A source audit, successful compilation, automated checks and in-game validation are different forms of evidence. This documentation update alone does not establish runtime compatibility. Preserve the project-specific remaining release gates.

When reporting a problem, include:

- Exact mod version, Minecraft version, loader and dependency versions.
- Client, integrated server or dedicated-server context.
- Minimal reproduction steps and the relevant log/stack trace.
- Configuration or datapack excerpts required to reproduce it, with credentials and personal data removed.

Before a release, compile every affected loader and configured version, inspect packaged metadata/resources, and perform the relevant client and dedicated-server checks. Rendering, interactions, networking and persistence need real runtime validation.

## License and attribution

See [LICENSE](../LICENSE). Retain upstream notices and third-party asset attribution.
