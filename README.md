# InspectMC

InspectMC is an in-game development and inspection toolkit for Minecraft modpack creators, datapack authors and mod developers.

## Target

- Minecraft 1.21.1 only (strict version check)
- Java 21
- Fabric
- NeoForge

## Commands

- `/inspectmc hand`
- `/inspectmc inspect held [details]`
- `/inspectmc inspect block [distance|target [distance]|id <block_id> [distance]]`
- `/inspectmc inspect blockentity [distance]`
- `/inspectmc inspect entity [distance|look [distance]|target <selector>|id <entity_type> [distance]]`
- `/inspectmc inspect biome [details]`
- `/inspectmc inspect player [player]`
- `/inspectmc search <name or namespace>`
- `/inspectmc search page <page> <name or namespace>`
- `/inspectmc locate block <block_id> [radius]`
- `/inspectmc locate entity <entity_type> [radius] [limit]`
- `/inspectmc locate blockentity <block_entity_type> [radius] [limit]`
- `/inspectmc loaded chunks [map_radius_chunks]`
- `/inspectmc loaded entities [page]`
- `/inspectmc loaded blockentities`
- `/inspectmc loaded dimensions`
- `/inspectmc stats blocks [radius]`
- `/inspectmc stats biomes [radius] [step]`
- `/inspectmc spawn validate`
- `/inspectmc spawn here [page]`
- `/inspectmc spawn explain <pokemon> [page]`
- `/inspectmc cobblemon status`
- `/inspectmc cobblemon entity [look [distance]|target <selector>]`
- `/inspectmc cobblemon player [player]`
- `/inspectmc cobblemon find <property_query>`
- `/inspectmc cobblemon find player <player> <property_query>`
- `/inspectmc dump biomes [excel|csv|json]`
- `/inspectmc dump registries [json|csv|excel]`
- `/inspectmc dump registry <registry_id> [json|csv|excel]`
- `/inspectmc dump tags <registry_id> [json|csv|excel]`
- `/inspectmc dump recipes [json|csv|excel]`
- `/inspectmc dump spawns [json|csv|excel]`
- `/inspectmc dump report`
- `/inspectmc pack <mods|datapacks|namespaces|registries> [page]`

Dump files are written under `inspectmc/dumps` in the game or server working directory. Every export keeps a timestamped archive and atomically updates a stable `*-latest.*` alias for scripts and Discord bots.

`/inspectmc dump biomes` defaults to a real Excel `.xlsx` workbook and accepts `biomes-with-tags` as an alias. The workbook opens on an explanatory `Overview` sheet, followed by a filterable `Biomes` summary and a normalized `Biome Tags` sheet with one biome/tag relation per row. Headers are styled, columns are sized and the first row remains visible while scrolling.

CSV remains the stable RFC 4180 machine format. JSON exports use schema version 2, UTC timestamps and common Minecraft, InspectMC and loader metadata. IDs, tags, relations and recipes are sorted deterministically. Recipe exports now include their type, group, result, quantity and accepted ingredient alternatives.

`/inspectmc dump report` creates a ZIP containing `manifest.json`, a complete `report.xlsx`, normalized CSV files for mods, registries, entries, tags, biomes and recipes, plus detailed recipe JSON.

## Server configuration

InspectMC creates `config/inspectmc.json` in the game or server working directory when a configuration-aware command is first used. Changes are read when the server starts. Values outside their documented bounds fall back to the defaults below.

Cloth Config is an optional client-side dependency. When installed, the same settings are available through `/inspectmc-config`, Mod Menu on Fabric, or the NeoForge mod configuration button. In an integrated world, saving the screen updates the logical server configuration. On a dedicated server, the server administrator must edit the server's `config/inspectmc.json`; the client GUI cannot write to that remote filesystem. InspectMC remains fully usable without Cloth Config.

| Option | Default | Purpose |
|---|---:|---|
| `operatorPermissionLevel` | `2` | Vanilla permission level required for world, runtime and export diagnostics. |
| `defaultBlockDistance` | `20` | Distance used when block or block-entity inspection omits a distance. |
| `defaultEntityDistance` | `20` | Distance used when entity inspection omits a distance. |
| `defaultLocateResults` | `8` | Exact positions shown by locate commands when no limit is supplied. |
| `searchPageSize` | `30` | Registry search results shown per page. |
| `listPageSize` | `50` | Mods, datapacks, namespaces and runtime registries shown per page. |
| `loadedEntityPageSize` | `25` | Loaded entity types shown per page. |
| `maxDumpArchives` | `0` | Maximum timestamped archives kept for each export; `0` keeps all archives. Stable `*-latest.*` aliases are not counted. |

Long registry, pack and loaded-entity results are paginated with clickable previous/next actions. The complete values remain available through the copy actions and the dump commands.

The report now also includes active datapacks, loaded dimensions, forced-chunk counts, a per-dimension entity summary and item-result data components in recipe exports.

## Optional Cobblemon spawn audit

InspectMC detects Cobblemon world spawn pools through the datapack resources currently loaded by the server; Cobblemon is not a required dependency. After `/reload`, `/inspectmc spawn validate` checks malformed entries, duplicate spawn IDs, missing presets and unresolved biome, dimension or block references. Warnings remain warnings because another loaded pack or an optional mod can intentionally affect a reference.

`/inspectmc spawn explain <pokemon>` evaluates the current biome, dimension, height, light, sky visibility, weather and moon phase, and clearly marks conditions that require a dynamic Cobblemon check. `/inspectmc spawn here [page]` provides a compact, paginated view of compatible and potentially compatible entries without flooding chat.

`/inspectmc dump spawns` defaults to an Excel audit with Overview, Problems, Spawn Entries, Conditions, Presets, Buckets, Dependencies and Source Packs sheets. CSV provides one stable row per spawn entry and JSON preserves normalized metadata plus each original loaded definition for bots and advanced tooling.

## Optional Cobblemon API diagnostics

When both Cobblemon 1.8.1–1.8.x and OS Cobblemon Library 1.1.x are installed, InspectMC enables an optional runtime integration. `/inspectmc inspect entity` automatically adds species, form, shiny/Alpha state, aspects, labels, marks, types, ability category, size, owner, battle state and IV diagnostics for a targeted Cobblemon Pokémon. The dedicated `/inspectmc cobblemon entity` command exposes the same view directly.

`/inspectmc cobblemon player [player]` summarizes Party, PC and Pokédex progress. `/inspectmc cobblemon find <property_query>` searches the executing player's owned Pokémon with Cobblemon's native property syntax, for example `species=pikachu shiny=true`; operators can use `find player <player> <property_query>` for another player. `/inspectmc cobblemon status` reports whether the optional integration is available.

The API integration is read-only and remains isolated from the base mod. InspectMC still loads and retains all non-Cobblemon features when either optional dependency is absent.

For source builds that compile the optional integration, publish OS Cobblemon Library 1.1.0 to Maven Local first with its `scripts/publish-local.ps1` helper. The released InspectMC JARs do not bundle Cobblemon or OS Cobblemon Library; they only declare both as optional loader suggestions/dependencies.

## Release information

- Changes: [CHANGELOG.md](CHANGELOG.md)
- License: [LICENSE](LICENSE)
- Use the regular loader JAR from `fabric/build/libs` or `neoforge/build/libs`; files containing `sources` or `dev-shadow` are development artifacts.

## Interactive chat output

- Click displayed values and registry IDs to copy them.
- `/inspectmc hand` provides a compact KubeJS-style main-hand summary with a clickable item ID; `/inspectmc hand full` adds item and block tags, components and detailed copy actions.
- Hover registry IDs to see their registry and namespace.
- Positions include a `[TP]` action that prefills a teleport command.
- Long tag and registry lists are summarized and remain copyable in full.
- Boolean values use readable colored status markers.
- Raw entity NBT is hidden from the default output and available through `[Copy NBT]`.
- `/inspectmc inspect held details` provides an explicit technical item view without flooding the default result.
- `/inspectmc inspect biome` is compact by default; `details` adds ambient effects, spawns and world-generation data.
- Block entities expose inventory contents, occupancy and copyable NBT without printing raw data into chat.
- Living entities expose active effects and equipped items when present.
- Inspected registry entries identify their providing mod when the namespace matches a loaded mod ID.
- `/inspectmc search` finds partial IDs across items, blocks, block entities, fluids, particles, sounds, entity types and runtime biomes.
- Help syntaxes and retry actions can be clicked to prefill commands.
- Local dump results provide `[Open file]` and `[Open folder]` actions in addition to path copying.
- Entity and block-entity location results include multiple nearest positions and a compact chunk map.

InspectMC intentionally does not require Architectury API at runtime.

## Permissions

Looking at blocks, inspecting the held item/current biome/self, and registry search are available to regular players. Block-entity and entity NBT, another player, world searches, loaded-state diagnostics, statistics, pack information and dumps require the configured vanilla permission level.
