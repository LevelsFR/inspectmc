package com.levelsfr.inspectmc.cobblemon;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.levelsfr.inspectmc.util.TextUtil;
import com.levelsfr.inspectmc.util.IdentifierCompat;
import com.levelsfr.inspectmc.util.MinecraftCompat;
import com.levelsfr.inspectmc.InspectMC;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Reads Cobblemon spawn data without linking Cobblemon classes. */
public final class CobblemonSpawnInspector {
    private static final String POOL_DIRECTORY = "spawn_pool_world";
    private static final String PRESET_DIRECTORY = "spawn_detail_presets";
    private static final int CHAT_RESULT_LIMIT = 10;

    private static volatile ResourceManager cachedManager;
    private static volatile Snapshot cachedSnapshot;

    private CobblemonSpawnInspector() {
    }

    public static Snapshot snapshot(MinecraftServer server) {
        ResourceManager manager = server.getResourceManager();
        Snapshot current = cachedSnapshot;
        if (manager == cachedManager && current != null) {
            return current;
        }
        synchronized (CobblemonSpawnInspector.class) {
            if (manager != cachedManager || cachedSnapshot == null) {
                cachedSnapshot = load(server, manager);
                cachedManager = manager;
            }
            return cachedSnapshot;
        }
    }

    public static List<String> pokemonSuggestions(MinecraftServer server) {
        return snapshot(server).spawns().stream().map(SpawnEntry::species).distinct().sorted().toList();
    }

    public static List<Component> validationChat(MinecraftServer server) {
        Snapshot snapshot = snapshot(server);
        if (!snapshot.available()) {
            return unavailable();
        }
        long errors = snapshot.issues().stream().filter(issue -> issue.severity() == Severity.ERROR).count();
        long warnings = snapshot.issues().stream().filter(issue -> issue.severity() == Severity.WARNING).count();
        List<Component> lines = new ArrayList<>();
        lines.add(TextUtil.section("Loaded spawn resources"));
        lines.add(TextUtil.line("Pool files", snapshot.poolFiles()));
        lines.add(TextUtil.line("Spawn entries", snapshot.spawns().size()));
        lines.add(TextUtil.line("Pokemon", snapshot.speciesCount()));
        lines.add(TextUtil.line("Presets", snapshot.presets().size()));
        lines.add(statusLine("Errors", errors, errors == 0));
        lines.add(statusLine("Warnings", warnings, warnings == 0));

        if (!snapshot.issues().isEmpty()) {
            lines.add(TextUtil.section("Problems to review"));
            snapshot.issues().stream().limit(CHAT_RESULT_LIMIT).forEach(issue ->
                    lines.add(issueComponent(issue)));
            if (snapshot.issues().size() > CHAT_RESULT_LIMIT) {
                lines.add(TextUtil.hint((snapshot.issues().size() - CHAT_RESULT_LIMIT)
                        + " more issue(s). Export the report to see every occurrence."));
            }
        } else {
            lines.add(Component.literal("✓ No unresolved spawn resource problem found.")
                    .withStyle(ChatFormatting.GREEN));
        }
        lines.add(TextUtil.actionRow(
                TextUtil.suggestAction("[Excel report]", "/inspectmc dump spawns excel",
                        "Create a readable workbook with problems, entries, conditions and presets"),
                TextUtil.suggestAction("[JSON for bots]", "/inspectmc dump spawns json",
                        "Create a stable machine-readable report")));
        lines.add(TextUtil.hint("Results describe resources currently loaded by the server after /reload."));
        return List.copyOf(lines);
    }

    public static List<Component> explainChat(ServerPlayer player, String query) {
        return explainChat(player, query, 1);
    }

    public static List<Component> explainChat(ServerPlayer player, String query, int page) {
        Snapshot snapshot = snapshot(MinecraftCompat.server(player));
        if (!snapshot.available()) {
            return unavailable();
        }
        String normalized = normalizeSpecies(query);
        List<SpawnEntry> matches = snapshot.spawns().stream()
                .filter(entry -> entry.species().equals(normalized)
                        || entry.pokemon().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))
                .toList();
        if (matches.isEmpty()) {
            return List.of(
                    Component.literal("No loaded spawn entry matches '" + query + "'.")
                            .withStyle(ChatFormatting.RED),
                    TextUtil.suggestAction("[List possible spawns here]", "/inspectmc spawn here",
                            "Inspect spawn entries compatible with the current position"));
        }

        int pages = Math.max(1, (matches.size() + CHAT_RESULT_LIMIT - 1) / CHAT_RESULT_LIMIT);
        int safePage = Math.max(1, Math.min(page, pages));
        int start = (safePage - 1) * CHAT_RESULT_LIMIT;
        int end = Math.min(matches.size(), start + CHAT_RESULT_LIMIT);
        List<Component> lines = new ArrayList<>();
        lines.add(TextUtil.section("Current position"));
        lines.add(TextUtil.resourceLine("Biome", player.level().getBiome(player.blockPosition())
                .unwrapKey().map(IdentifierCompat::resourceKeyId).orElse("minecraft:unknown"), "biome"));
        lines.add(TextUtil.resourceLine("Dimension", IdentifierCompat.resourceKeyId(player.level().dimension()), "dimension"));
        lines.add(TextUtil.positionLine("Position", player.blockPosition(), IdentifierCompat.resourceKeyId(player.level().dimension())));
        lines.add(TextUtil.section("Spawn checks for " + normalized));
        lines.add(TextUtil.line("Entries", matches.size()));
        lines.add(TextUtil.line("Page", safePage + " / " + pages));
        for (SpawnEntry entry : matches.subList(start, end)) {
            Evaluation evaluation = evaluate(snapshot, entry, MinecraftCompat.serverLevel(player), player.blockPosition());
            lines.add(entryHeader(entry, evaluation));
            evaluation.checks().stream().limit(8).forEach(check -> lines.add(checkComponent(check)));
            lines.add(TextUtil.actionRow(
                    TextUtil.copyAction("[Copy spawn ID]", entry.id(), "Copy this spawn definition ID"),
                    TextUtil.copyAction("[Copy source]", entry.source(),
                            "Copy the loaded datapack resource ID")));
        }
        if (pages > 1) {
            List<Component> paging = new ArrayList<>();
            if (safePage > 1) {
                paging.add(TextUtil.suggestAction("[Previous]",
                        "/inspectmc spawn explain " + normalized + " " + (safePage - 1),
                        "Show the previous spawn definitions"));
            }
            if (safePage < pages) {
                paging.add(TextUtil.suggestAction("[Next]",
                        "/inspectmc spawn explain " + normalized + " " + (safePage + 1),
                        "Show the next spawn definitions"));
            }
            lines.add(TextUtil.actionRow(paging.toArray(Component[]::new)));
        }
        lines.add(TextUtil.actionRow(TextUtil.suggestAction("[Export all spawns]",
                "/inspectmc dump spawns excel", "Export every loaded pool and condition")));
        return List.copyOf(lines);
    }

    public static List<Component> hereChat(ServerPlayer player, int page) {
        Snapshot snapshot = snapshot(MinecraftCompat.server(player));
        if (!snapshot.available()) {
            return unavailable();
        }
        List<EvaluatedSpawn> possible = snapshot.spawns().stream()
                .map(entry -> new EvaluatedSpawn(entry,
                        evaluate(snapshot, entry, MinecraftCompat.serverLevel(player), player.blockPosition())))
                .filter(value -> value.evaluation().status() != CheckStatus.FAIL)
                .sorted(Comparator
                        .comparing((EvaluatedSpawn value) -> value.evaluation().status() == CheckStatus.PASS ? 0 : 1)
                        .thenComparing((EvaluatedSpawn value) -> -value.evaluation().effectiveWeight())
                        .thenComparing(value -> value.entry().species()))
                .toList();
        int pages = Math.max(1, (possible.size() + CHAT_RESULT_LIMIT - 1) / CHAT_RESULT_LIMIT);
        int safePage = Math.max(1, Math.min(page, pages));
        int start = Math.min(possible.size(), (safePage - 1) * CHAT_RESULT_LIMIT);
        int end = Math.min(possible.size(), start + CHAT_RESULT_LIMIT);

        long definite = possible.stream().filter(value -> value.evaluation().status() == CheckStatus.PASS).count();
        List<Component> lines = new ArrayList<>();
        lines.add(TextUtil.section("Possible at this position"));
        lines.add(TextUtil.resourceLine("Biome", player.level().getBiome(player.blockPosition())
                .unwrapKey().map(IdentifierCompat::resourceKeyId).orElse("minecraft:unknown"), "biome"));
        lines.add(TextUtil.line("Definite with evaluated conditions", definite));
        lines.add(TextUtil.line("Possible including dynamic checks", possible.size()));
        lines.add(TextUtil.line("Page", safePage + " / " + pages));
        for (EvaluatedSpawn value : possible.subList(start, end)) {
            SpawnEntry entry = value.entry();
            lines.add(entryHeader(entry, value.evaluation()).copy()
                    .append(Component.literal(" "))
                    .append(TextUtil.suggestAction("[Why]", "/inspectmc spawn explain " + entry.species(),
                            "Explain every condition for " + entry.species())));
        }
        if (possible.isEmpty()) {
            lines.add(Component.literal("No compatible loaded entry found here.").withStyle(ChatFormatting.RED));
        }
        List<Component> actions = new ArrayList<>();
        if (safePage > 1) {
            actions.add(TextUtil.suggestAction("[Previous]", "/inspectmc spawn here " + (safePage - 1),
                    "Show the previous page"));
        }
        if (safePage < pages) {
            actions.add(TextUtil.suggestAction("[Next]", "/inspectmc spawn here " + (safePage + 1),
                    "Show the next page"));
        }
        actions.add(TextUtil.suggestAction("[Validate]", "/inspectmc spawn validate",
                "Audit all loaded spawn resources"));
        lines.add(TextUtil.actionRow(actions.toArray(Component[]::new)));
        lines.add(TextUtil.hint("Possible entries may still depend on structures, nearby blocks, fishing or other runtime events."));
        return List.copyOf(lines);
    }

    public static Evaluation evaluate(Snapshot snapshot, SpawnEntry entry, ServerLevel level, BlockPos pos) {
        List<Check> checks = new ArrayList<>();
        if (!entry.enabled()) {
            checks.add(new Check(CheckStatus.FAIL, "Enabled", "disabled by the pool or spawn entry"));
        }
        Set<String> loadedMods = InspectMC.platform().loadedMods().stream()
                .map(mod -> mod.id().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        for (String mod : entry.neededInstalledMods()) {
            boolean loaded = loadedMods.contains(mod.toLowerCase(Locale.ROOT));
            checks.add(new Check(loaded ? CheckStatus.PASS : CheckStatus.FAIL, "Required mod",
                    mod + (loaded ? " is loaded" : " is not loaded")));
        }
        for (String mod : entry.neededUninstalledMods()) {
            boolean absent = !loadedMods.contains(mod.toLowerCase(Locale.ROOT));
            checks.add(new Check(absent ? CheckStatus.PASS : CheckStatus.FAIL, "Incompatible mod",
                    mod + (absent ? " is absent" : " is loaded")));
        }
        for (String preset : entry.presets()) {
            JsonObject presetJson = snapshot.presets().get(resolvePreset(snapshot.presets(), preset));
            if (presetJson == null) {
                checks.add(new Check(CheckStatus.FAIL, "Preset", preset + " is unresolved"));
                continue;
            }
            if (presetJson.has("condition") && presetJson.get("condition").isJsonObject()) {
                evaluateCondition(presetJson.getAsJsonObject("condition"), false, level, pos, checks);
            }
            if (presetJson.has("anticondition") && presetJson.get("anticondition").isJsonObject()) {
                evaluateCondition(presetJson.getAsJsonObject("anticondition"), true, level, pos, checks);
            }
        }
        evaluateCondition(entry.condition(), false, level, pos, checks);
        evaluateCondition(entry.anticondition(), true, level, pos, checks);
        if (checks.isEmpty()) {
            checks.add(new Check(CheckStatus.PASS, "Conditions", "No location restriction"));
        }
        CheckStatus status = checks.stream().anyMatch(check -> check.status() == CheckStatus.FAIL)
                ? CheckStatus.FAIL
                : checks.stream().anyMatch(check -> check.status() == CheckStatus.UNKNOWN)
                ? CheckStatus.UNKNOWN : CheckStatus.PASS;
        WeightResult weight = evaluateWeight(entry, level, pos, checks);
        return new Evaluation(status, List.copyOf(checks), weight.value(), weight.uncertain());
    }

    private static Snapshot load(MinecraftServer server, ResourceManager manager) {
        Map<String, JsonObject> presets = new TreeMap<>();
        List<Issue> issues = new ArrayList<>();
        Map<String, Resource> presetResources = IdentifierCompat.listResources(manager, PRESET_DIRECTORY).entrySet().stream()
                .filter(entry -> IdentifierCompat.path(entry.getKey()).endsWith(".json"))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, second) -> first, LinkedHashMap::new));
        for (Map.Entry<String, Resource> resource : presetResources.entrySet()) {
            try {
                JsonObject json = readObject(resource.getValue());
                presets.put(logicalId(resource.getKey(), PRESET_DIRECTORY), json);
            } catch (Exception exception) {
                issues.add(new Issue(Severity.ERROR, "invalid_preset_json", resource.getKey(),
                        concise(exception)));
            }
        }

        List<SpawnEntry> spawns = new ArrayList<>();
        Map<String, Resource> poolResources = IdentifierCompat.listResources(manager, POOL_DIRECTORY).entrySet().stream()
                .filter(entry -> IdentifierCompat.path(entry.getKey()).endsWith(".json"))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, second) -> first, LinkedHashMap::new));
        for (Map.Entry<String, Resource> resource : poolResources.entrySet()) {
            try {
                JsonObject root = readObject(resource.getValue());
                boolean poolEnabled = bool(root, "enabled", true);
                List<String> poolNeededInstalledMods = strings(root.get("neededInstalledMods"));
                List<String> poolNeededUninstalledMods = strings(root.get("neededUninstalledMods"));
                JsonArray values = root.has("spawns") && root.get("spawns").isJsonArray()
                        ? root.getAsJsonArray("spawns") : null;
                if (values == null) {
                    issues.add(new Issue(Severity.ERROR, "missing_spawns_array", resource.getKey(),
                            "Root object has no spawns array"));
                    continue;
                }
                int index = 0;
                for (JsonElement value : values) {
                    index++;
                    if (!value.isJsonObject()) {
                        issues.add(new Issue(Severity.ERROR, "invalid_spawn", resource.getKey() + "#" + index,
                                "Spawn entry is not an object"));
                        continue;
                    }
                    SpawnEntry entry = parseSpawn(resource.getKey(), resource.getValue().sourcePackId(), index,
                            value.getAsJsonObject(), poolEnabled, poolNeededInstalledMods, poolNeededUninstalledMods);
                    spawns.add(entry);
                    if (entry.id().isBlank()) {
                        issues.add(new Issue(Severity.ERROR, "missing_spawn_id", entry.location(),
                                "Spawn entry has no id"));
                    }
                    if (entry.pokemon().isBlank()) {
                        issues.add(new Issue(Severity.ERROR, "missing_pokemon", entry.location(),
                                "Spawn entry has no pokemon value"));
                    }
                }
            } catch (Exception exception) {
                issues.add(new Issue(Severity.ERROR, "invalid_pool_json", resource.getKey(),
                        concise(exception)));
            }
        }
        spawns.sort(Comparator.comparing(SpawnEntry::species).thenComparing(SpawnEntry::id)
                .thenComparing(SpawnEntry::source));
        validate(server, presets, spawns, issues);
        issues.sort(Comparator.comparing(Issue::severity).thenComparing(Issue::code)
                .thenComparing(Issue::location));
        int speciesCount = (int) spawns.stream().map(SpawnEntry::species).filter(value -> !value.isBlank())
                .distinct().count();
        return new Snapshot(!poolResources.isEmpty(), poolResources.size(), speciesCount,
                List.copyOf(spawns), Map.copyOf(presets), List.copyOf(issues));
    }

    private static SpawnEntry parseSpawn(String source, String sourcePack, int index, JsonObject json,
                                         boolean poolEnabled, List<String> poolNeededInstalledMods,
                                         List<String> poolNeededUninstalledMods) {
        String id = string(json, "id", "");
        String pokemon = string(json, "pokemon", "");
        String species = normalizeSpecies(pokemon);
        List<String> presets = strings(json.get("presets"));
        List<String> neededInstalledMods = merge(poolNeededInstalledMods, strings(json.get("neededInstalledMods")));
        List<String> neededUninstalledMods = merge(poolNeededUninstalledMods, strings(json.get("neededUninstalledMods")));
        return new SpawnEntry(id, pokemon, species, string(json, "bucket", "unspecified"),
                number(json, "weight", 1.0D), string(json, "type", ""), string(json, "context", ""),
                presets, poolEnabled && bool(json, "enabled", true), neededInstalledMods, neededUninstalledMods,
                object(json, "condition"), object(json, "anticondition"),
                source, sourcePack, index, json.deepCopy());
    }

    private static WeightResult evaluateWeight(SpawnEntry entry, ServerLevel level, BlockPos pos, List<Check> checks) {
        double value = entry.weight();
        boolean uncertain = false;
        JsonElement raw = entry.raw().get("weightMultiplier");
        if (raw == null || raw.isJsonNull()) return new WeightResult(value, false);
        List<JsonObject> multipliers = new ArrayList<>();
        if (raw.isJsonObject()) {
            multipliers.add(raw.getAsJsonObject());
        } else if (raw.isJsonArray()) {
            raw.getAsJsonArray().forEach(element -> {
                if (element.isJsonObject()) multipliers.add(element.getAsJsonObject());
            });
        } else if (raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isNumber()) {
            double multiplier = raw.getAsDouble();
            checks.add(new Check(CheckStatus.PASS, "Weight multiplier", "always ×" + format(multiplier)));
            return new WeightResult(value * multiplier, false);
        }
        for (JsonObject multiplier : multipliers) {
            double factor = number(multiplier, "multiplier", 1.0D);
            List<Check> multiplierChecks = new ArrayList<>();
            evaluateCondition(object(multiplier, "condition"), false, level, pos, multiplierChecks);
            CheckStatus status = multiplierChecks.stream().anyMatch(check -> check.status() == CheckStatus.FAIL)
                    ? CheckStatus.FAIL : multiplierChecks.stream().anyMatch(check -> check.status() == CheckStatus.UNKNOWN)
                    ? CheckStatus.UNKNOWN : CheckStatus.PASS;
            if (status == CheckStatus.PASS) {
                value *= factor;
                checks.add(new Check(CheckStatus.PASS, "Weight multiplier", "active ×" + format(factor)));
            } else if (status == CheckStatus.FAIL) {
                checks.add(new Check(CheckStatus.PASS, "Weight multiplier", "inactive ×" + format(factor)));
            } else {
                uncertain = true;
                checks.add(new Check(CheckStatus.UNKNOWN, "Weight multiplier", "dynamic ×" + format(factor)));
            }
        }
        return new WeightResult(value, uncertain);
    }

    private static void validate(MinecraftServer server, Map<String, JsonObject> presets,
                                 List<SpawnEntry> spawns, List<Issue> issues) {
        Map<String, List<SpawnEntry>> ids = spawns.stream().filter(entry -> !entry.id().isBlank())
                .collect(Collectors.groupingBy(SpawnEntry::id, LinkedHashMap::new, Collectors.toList()));
        ids.forEach((id, entries) -> {
            if (entries.size() > 1) {
                issues.add(new Issue(Severity.WARNING, "duplicate_spawn_id", id,
                        entries.size() + " loaded entries use this ID: " + entries.stream()
                                .map(SpawnEntry::source).distinct().collect(Collectors.joining(", "))));
            }
        });

        Registry<Biome> biomeRegistry = IdentifierCompat.registry(server.registryAccess(), Registries.BIOME);
        Set<String> knownDimensions = new HashSet<>();
        server.getAllLevels().forEach(level -> knownDimensions.add(IdentifierCompat.resourceKeyId(level.dimension())));
        Set<String> checkedReferences = new HashSet<>();
        for (SpawnEntry entry : spawns) {
            for (String preset : entry.presets()) {
                if (resolvePreset(presets, preset) == null) {
                    issues.add(new Issue(Severity.ERROR, "missing_preset", entry.location(), preset));
                }
            }
            validateCondition(entry.condition(), entry.location(), biomeRegistry, knownDimensions,
                    checkedReferences, issues);
            validateCondition(entry.anticondition(), entry.location(), biomeRegistry, knownDimensions,
                    checkedReferences, issues);
        }
        presets.forEach((id, json) -> {
            validateCondition(object(json, "condition"), "preset " + id, biomeRegistry, knownDimensions,
                    checkedReferences, issues);
            validateCondition(object(json, "anticondition"), "preset " + id, biomeRegistry, knownDimensions,
                    checkedReferences, issues);
        });
    }

    private static void validateCondition(JsonObject condition, String location, Registry<Biome> biomes,
                                          Set<String> dimensions, Set<String> checked, List<Issue> issues) {
        for (String value : strings(condition.get("biomes"))) {
            String key = "biome|" + value;
            if (!checked.add(key)) continue;
            if (value.startsWith("#")) {
                String id = parseId(value.substring(1));
                if (id == null || !IdentifierCompat.registryHasTag(biomes, Registries.BIOME, id)) {
                    issues.add(new Issue(Severity.WARNING, "unresolved_biome_tag", location, value));
                }
            } else {
                String id = parseId(value);
                if (id == null || !IdentifierCompat.registryContains(biomes, id)) {
                    issues.add(new Issue(Severity.WARNING, "unresolved_biome", location, value));
                }
            }
        }
        for (String value : strings(condition.get("dimensions"))) {
            String key = "dimension|" + value;
            if (!checked.add(key)) continue;
            if (!value.startsWith("#") && !dimensions.contains(value)) {
                issues.add(new Issue(Severity.WARNING, "unresolved_dimension", location, value));
            }
        }
        validateBlocks(condition.get("neededBaseBlocks"), location, checked, issues);
        validateBlocks(condition.get("neededNearbyBlocks"), location, checked, issues);
    }

    private static void validateBlocks(JsonElement element, String location, Set<String> checked, List<Issue> issues) {
        for (String value : strings(element)) {
            String key = "block|" + value;
            if (!checked.add(key)) continue;
            String id = parseId(value.startsWith("#") ? value.substring(1) : value);
            boolean missing = id == null || (value.startsWith("#")
                    ? !IdentifierCompat.registryHasTag(BuiltInRegistries.BLOCK, Registries.BLOCK, id)
                    : !IdentifierCompat.registryContains(BuiltInRegistries.BLOCK, id));
            if (missing) {
                issues.add(new Issue(Severity.WARNING,
                        value.startsWith("#") ? "unresolved_block_tag" : "unresolved_block", location, value));
            }
        }
    }

    private static void evaluateCondition(JsonObject condition, boolean anti, ServerLevel level, BlockPos pos,
                                          List<Check> checks) {
        if (condition == null || condition.size() == 0) return;
        List<Check> local = new ArrayList<>();
        if (condition.has("biomes")) {
            List<String> values = strings(condition.get("biomes"));
            boolean match = values.stream().anyMatch(value -> biomeMatches(value, level, pos));
            local.add(new Check(match ? CheckStatus.PASS : CheckStatus.FAIL, "Biome",
                    (match ? "matches " : "requires ") + summarize(values)));
        }
        if (condition.has("dimensions")) {
            List<String> values = strings(condition.get("dimensions"));
            String current = IdentifierCompat.resourceKeyId(level.dimension());
            boolean match = values.contains(current);
            local.add(new Check(match ? CheckStatus.PASS : CheckStatus.FAIL, "Dimension",
                    match ? current : "requires " + summarize(values)));
        }
        compareMinimum(condition, "minY", pos.getY(), "Minimum Y", local);
        compareMaximum(condition, "maxY", pos.getY(), "Maximum Y", local);
        int skyLight = level.getBrightness(LightLayer.SKY, pos);
        compareMinimum(condition, "minSkyLight", skyLight, "Sky light minimum", local);
        compareMaximum(condition, "maxSkyLight", skyLight, "Sky light maximum", local);
        int light = level.getMaxLocalRawBrightness(pos);
        compareMinimum(condition, "minLight", light, "Light minimum", local);
        compareMaximum(condition, "maxLight", light, "Light maximum", local);
        compareBoolean(condition, "canSeeSky", level.canSeeSky(pos), "Can see sky", local);
        compareBoolean(condition, "isRaining", level.isRainingAt(pos), "Raining", local);
        compareBoolean(condition, "isThundering", level.isThundering(), "Thundering", local);
        if (condition.has("moonPhase") && condition.get("moonPhase").isJsonPrimitive()) {
            int required = condition.get("moonPhase").getAsInt();
            int actual = MinecraftCompat.moonPhase(level);
            local.add(new Check(required == actual ? CheckStatus.PASS : CheckStatus.FAIL,
                    "Moon phase", actual + " (required " + required + ")"));
        }
        Set<String> evaluated = Set.of("biomes", "dimensions", "minY", "maxY", "minSkyLight", "maxSkyLight",
                "minLight", "maxLight", "canSeeSky", "isRaining", "isThundering", "moonPhase");
        condition.keySet().stream().filter(key -> !evaluated.contains(key)).sorted().forEach(key ->
                local.add(new Check(CheckStatus.UNKNOWN, friendlyName(key), "dynamic: " + compact(condition.get(key)))));

        if (!anti) {
            checks.addAll(local);
            return;
        }
        boolean fullyMatches = !local.isEmpty() && local.stream().allMatch(check -> check.status() == CheckStatus.PASS);
        boolean definitelyDifferent = local.stream().anyMatch(check -> check.status() == CheckStatus.FAIL);
        checks.add(new Check(fullyMatches ? CheckStatus.FAIL : definitelyDifferent ? CheckStatus.PASS : CheckStatus.UNKNOWN,
                "Excluded when", fullyMatches ? "anticondition currently matches" : definitelyDifferent
                ? "anticondition does not match" : "dynamic anticondition: " + summarize(condition.keySet())));
    }

    private static boolean biomeMatches(String value, ServerLevel level, BlockPos pos) {
        Holder<Biome> biome = level.getBiome(pos);
        if (value.startsWith("#")) {
            String id = parseId(value.substring(1));
            return id != null && IdentifierCompat.holderHasTag(biome, Registries.BIOME, id);
        }
        String id = parseId(value);
        return id != null && biome.unwrapKey().map(key -> IdentifierCompat.resourceKeyId(key).equals(id)).orElse(false);
    }

    private static void compareMinimum(JsonObject json, String key, int actual, String label, List<Check> checks) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) return;
        double expected = json.get(key).getAsDouble();
        checks.add(new Check(actual >= expected ? CheckStatus.PASS : CheckStatus.FAIL, label,
                actual + " (required >= " + format(expected) + ")"));
    }

    private static void compareMaximum(JsonObject json, String key, int actual, String label, List<Check> checks) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) return;
        double expected = json.get(key).getAsDouble();
        checks.add(new Check(actual <= expected ? CheckStatus.PASS : CheckStatus.FAIL, label,
                actual + " (required <= " + format(expected) + ")"));
    }

    private static void compareBoolean(JsonObject json, String key, boolean actual, String label, List<Check> checks) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) return;
        boolean expected = json.get(key).getAsBoolean();
        checks.add(new Check(actual == expected ? CheckStatus.PASS : CheckStatus.FAIL, label,
                actual + " (required " + expected + ")"));
    }

    private static Component entryHeader(SpawnEntry entry, Evaluation evaluation) {
        ChatFormatting color = evaluation.status() == CheckStatus.PASS ? ChatFormatting.GREEN
                : evaluation.status() == CheckStatus.FAIL ? ChatFormatting.RED : ChatFormatting.YELLOW;
        String icon = evaluation.status() == CheckStatus.PASS ? "✓" : evaluation.status() == CheckStatus.FAIL ? "✕" : "?";
        String weight = format(evaluation.effectiveWeight())
                + (evaluation.weightUncertain() ? " (dynamic)" : "");
        return Component.literal(icon + " " + entry.species()).withStyle(color, ChatFormatting.BOLD)
                .append(Component.literal(" • " + entry.bucket() + " • weight " + weight
                        + " • " + entry.id()).withStyle(ChatFormatting.GRAY));
    }

    private static Component checkComponent(Check check) {
        ChatFormatting color = check.status() == CheckStatus.PASS ? ChatFormatting.GREEN
                : check.status() == CheckStatus.FAIL ? ChatFormatting.RED : ChatFormatting.YELLOW;
        String icon = check.status() == CheckStatus.PASS ? "  ✓ " : check.status() == CheckStatus.FAIL ? "  ✕ " : "  ? ";
        return Component.literal(icon + check.label() + ": ").withStyle(color)
                .append(Component.literal(check.detail()).withStyle(ChatFormatting.GRAY));
    }

    private static Component statusLine(String label, long count, boolean good) {
        return Component.literal(label + ": ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(Long.toString(count)).withStyle(good ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
    }

    private static Component issueComponent(Issue issue) {
        ChatFormatting color = issue.severity() == Severity.ERROR ? ChatFormatting.RED : ChatFormatting.YELLOW;
        String icon = issue.severity() == Severity.ERROR ? "✕ " : "⚠ ";
        return Component.literal(icon + issue.code() + " — ").withStyle(color)
                .append(TextUtil.copyAction(issue.location(), issue.location(), "Copy the affected resource or spawn ID"))
                .append(Component.literal(": " + TextUtil.abbreviate(issue.detail(), 240)).withStyle(ChatFormatting.GRAY));
    }

    private static List<Component> unavailable() {
        return List.of(
                Component.literal("No loaded Cobblemon world spawn pools were found.").withStyle(ChatFormatting.RED),
                TextUtil.hint("Install/load Cobblemon spawn datapacks, run /reload, then retry."));
    }

    private static JsonObject readObject(Resource resource) throws IOException {
        try (Reader reader = resource.openAsReader()) {
            JsonElement value = JsonParser.parseReader(reader);
            if (!value.isJsonObject()) throw new IOException("JSON root is not an object");
            return value.getAsJsonObject();
        }
    }

    private static String logicalId(String id, String directory) {
        String path = IdentifierCompat.path(id);
        String prefix = directory + "/";
        if (path.startsWith(prefix)) path = path.substring(prefix.length());
        if (path.endsWith(".json")) path = path.substring(0, path.length() - 5);
        return IdentifierCompat.value(IdentifierCompat.fromNamespaceAndPath(IdentifierCompat.namespace(id), path));
    }

    private static String resolvePreset(Map<String, JsonObject> presets, String input) {
        String exact = parseId(input);
        if (exact != null && presets.containsKey(exact)) return exact;
        if (!input.contains(":")) {
            String cobblemon = IdentifierCompat.value(IdentifierCompat.fromNamespaceAndPath("cobblemon", input));
            if (presets.containsKey(cobblemon)) return cobblemon;
            return presets.keySet().stream().filter(id -> IdentifierCompat.path(id).equals(input)).findFirst().orElse(null);
        }
        return null;
    }

    private static String normalizeSpecies(String pokemon) {
        String value = pokemon == null ? "" : pokemon.trim().toLowerCase(Locale.ROOT);
        int space = value.indexOf(' ');
        if (space >= 0) value = value.substring(0, space);
        int colon = value.indexOf(':');
        if (colon >= 0) value = value.substring(colon + 1);
        return value;
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonObject()
                ? parent.getAsJsonObject(key) : new JsonObject();
    }

    private static String string(JsonObject json, String key, String fallback) {
        try {
            return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : fallback;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static double number(JsonObject json, String key, double fallback) {
        try {
            return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsDouble() : fallback;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static boolean bool(JsonObject json, String key, boolean fallback) {
        try {
            return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsBoolean() : fallback;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static List<String> merge(List<String> first, List<String> second) {
        LinkedHashSet<String> values = new LinkedHashSet<>(first);
        values.addAll(second);
        return List.copyOf(values);
    }

    private static List<String> strings(JsonElement element) {
        if (element == null || element.isJsonNull()) return List.of();
        List<String> values = new ArrayList<>();
        if (element.isJsonArray()) {
            for (JsonElement value : element.getAsJsonArray()) {
                if (value.isJsonPrimitive()) values.add(value.getAsString());
            }
        } else if (element.isJsonPrimitive()) {
            values.add(element.getAsString());
        }
        return List.copyOf(values);
    }

    private static String parseId(String value) {
        try {
            Object id = IdentifierCompat.parse(value);
            return id == null ? null : IdentifierCompat.value(id);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static String compact(JsonElement value) {
        return TextUtil.abbreviate(value == null ? "null" : value.toString(), 120);
    }

    private static String summarize(Iterable<String> values) {
        List<String> list = new ArrayList<>();
        values.forEach(list::add);
        if (list.isEmpty()) return "<none>";
        String result = list.stream().limit(4).collect(Collectors.joining(", "));
        return list.size() > 4 ? result + ", … (" + list.size() + ")" : result;
    }

    private static String friendlyName(String key) {
        return key.replaceAll("([a-z])([A-Z])", "$1 $2");
    }

    private static String format(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.3f", value);
    }

    private static String concise(Exception exception) {
        String message = exception.getMessage();
        return exception.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    public enum Severity { ERROR, WARNING }
    public enum CheckStatus { PASS, FAIL, UNKNOWN }

    public record Issue(Severity severity, String code, String location, String detail) { }
    public record Check(CheckStatus status, String label, String detail) { }
    public record Evaluation(CheckStatus status, List<Check> checks, double effectiveWeight,
                             boolean weightUncertain) { }
    private record EvaluatedSpawn(SpawnEntry entry, Evaluation evaluation) { }
    private record WeightResult(double value, boolean uncertain) { }

    public record SpawnEntry(String id, String pokemon, String species, String bucket, double weight,
                             String type, String context, List<String> presets, boolean enabled,
                             List<String> neededInstalledMods, List<String> neededUninstalledMods,
                             JsonObject condition, JsonObject anticondition, String source, String sourcePack,
                             int sourceIndex, JsonObject raw) {
        public String location() {
            return source + "#" + sourceIndex + (id.isBlank() ? "" : " (" + id + ")");
        }
    }

    public record Snapshot(boolean available, int poolFiles, int speciesCount, List<SpawnEntry> spawns,
                           Map<String, JsonObject> presets, List<Issue> issues) { }
}
