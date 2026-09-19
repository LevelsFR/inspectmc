# InspectMC changelog

## 1.0.0 (release candidate)

Release candidate for Minecraft 1.21.1 on Fabric and NeoForge.

### New

- Add `/inspectmc hand` compact summary and `/inspectmc hand full` with clickable item IDs, item tags, data components and block tags for the held item.
- Add server-local configuration for permission level, inspection defaults, pagination and dump archive retention.
- Add an optional Cloth Config screen for editing the server-local settings in an integrated world.
- Add paginated registry, pack and loaded-entity results with clickable navigation.
- Add an in-game loaded-dimensions summary with players, entities and forced chunks.
- Add active datapacks, dimension summaries and loaded-entity summaries to the complete report.
- Add item-result components to recipe exports.

### Changes

- Expand registry search to block entities, fluids, particles and sound events.
- Show container contents and living-entity effects/equipment in targeted inspections.

### Inspection and diagnostics

- Inspect held items, blocks, block entities, entities, players and biomes.
- Search runtime registries and identify the mod providing an entry.
- Locate multiple nearby entities and block entities with compact chunk maps.
- Inspect loaded chunks, entities and block entities.
- Collect block and biome statistics.

### Datapacks and exports

- List mods, datapacks, namespaces and runtime registries.
- Export registries, tags, recipes and biome/tag relations as JSON, CSV or Excel.
- Produce a complete ZIP report with stable `*-latest` aliases for scripts and bots.
- Open local exports and their folder through secured chat actions.

### Optional Cobblemon support

- Validate loaded world spawn pools, presets and referenced resources.
- Explain spawn conditions at the player's current position.
- Browse compatible and potentially compatible spawns without flooding chat.
- Export a normalized Cobblemon audit as JSON, CSV or Excel.
- Add optional OS Cobblemon Library diagnostics without making Cobblemon required.
- Inspect targeted Pokémon identity, forms, aspects, marks, types, abilities, size, state and IVs.
- Summarize player Party, PC and Pokédex progress.
- Search owned Pokémon with native Cobblemon property queries.

### User experience

- Copy useful IDs and values directly from chat.
- Hover values for context and prefill commands through clickable actions.
- Keep technical NBT available without printing large raw payloads by default.
