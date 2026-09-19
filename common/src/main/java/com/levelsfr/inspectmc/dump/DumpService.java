package com.levelsfr.inspectmc.dump;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.levelsfr.inspectmc.InspectMC;
import com.levelsfr.inspectmc.cobblemon.CobblemonApiPresence;
import com.levelsfr.inspectmc.cobblemon.CobblemonSpawnInspector;
import com.levelsfr.inspectmc.config.InspectMCConfig;
import com.levelsfr.inspectmc.platform.ModDescriptor;
import com.levelsfr.inspectmc.util.IdentifierCompat;
import com.levelsfr.inspectmc.util.MinecraftCompat;
import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.biome.Biome;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class DumpService {
    private static final int SCHEMA_VERSION = 2;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static volatile Map<String, ModDescriptor> modsById;

    private DumpService() {
    }

    public static DumpResult dumpRegistryList(MinecraftServer server) throws IOException {
        List<String> ids = registryIds(server);
        JsonObject root = base("registries");
        root.addProperty("registry_count", ids.size());
        JsonArray registries = new JsonArray();
        ids.forEach(id -> registries.add(resourceObject(id)));
        root.add("registries", registries);
        return writeJson(server, "registries", root);
    }

    public static DumpResult dumpRegistryListCsv(MinecraftServer server) throws IOException {
        return writeText(server, "registries", "csv",
                csv(List.of("registry_id", "namespace", "path"), registryRows(server)));
    }

    public static DumpResult dumpRegistryListXlsx(MinecraftServer server) throws IOException {
        List<List<String>> rows = registryRows(server);
        byte[] workbook = XlsxWriter.write(List.of(
                overviewSheet("Registry list", "Runtime registries available to InspectMC", rows.size()),
                new XlsxWriter.Sheet("Registries", List.of("Registry ID", "Namespace", "Path"), rows,
                        List.of(46.0D, 22.0D, 42.0D))));
        return writeBytes(server, "registries", "xlsx", workbook);
    }

    public static DumpResult dumpRegistry(MinecraftServer server, String registryId) throws IOException {
        Optional<? extends Registry<?>> registryOptional = findRegistry(server.registryAccess(), registryId);
        if (registryOptional.isEmpty()) {
            return DumpResult.notFound("Unknown runtime registry: " + registryId);
        }
        List<String> entries = sortedKeys(registryOptional.get());
        JsonObject root = base("registry");
        root.addProperty("registry", registryId.toString());
        root.addProperty("entry_count", entries.size());
        JsonArray array = new JsonArray();
        entries.forEach(id -> array.add(resourceObject(id)));
        root.add("entries", array);
        return writeJson(server, safeName("registry_" + registryId), root);
    }

    public static DumpResult dumpRegistryCsv(MinecraftServer server, String registryId) throws IOException {
        Optional<? extends Registry<?>> registryOptional = findRegistry(server.registryAccess(), registryId);
        if (registryOptional.isEmpty()) {
            return DumpResult.notFound("Unknown runtime registry: " + registryId);
        }
        List<List<String>> rows = resourceRows(sortedKeys(registryOptional.get()));
        return writeText(server, safeName("registry_" + registryId), "csv",
                csv(List.of("entry_id", "namespace", "path", "provided_by"), rows));
    }

    public static DumpResult dumpRegistryXlsx(MinecraftServer server, String registryId) throws IOException {
        Optional<? extends Registry<?>> registryOptional = findRegistry(server.registryAccess(), registryId);
        if (registryOptional.isEmpty()) {
            return DumpResult.notFound("Unknown runtime registry: " + registryId);
        }
        List<List<String>> rows = resourceRows(sortedKeys(registryOptional.get()));
        byte[] workbook = XlsxWriter.write(List.of(
                overviewSheet("Registry export", "Runtime entries from " + registryId, rows.size()),
                new XlsxWriter.Sheet("Entries", List.of("Entry ID", "Namespace", "Path", "Provided by"),
                        rows, List.of(42.0D, 22.0D, 40.0D, 34.0D))));
        return writeBytes(server, safeName("registry_" + registryId), "xlsx", workbook);
    }

    public static DumpResult dumpTags(MinecraftServer server, String registryId) throws IOException {
        Optional<? extends Registry<?>> registryOptional = findRegistry(server.registryAccess(), registryId);
        if (registryOptional.isEmpty()) {
            return DumpResult.notFound("Unknown runtime registry: " + registryId);
        }
        List<TagEntry> entries = tagEntries(registryOptional.get());
        JsonObject root = base("tags");
        root.addProperty("registry", registryId.toString());
        root.addProperty("tag_count", entries.size());
        root.addProperty("relation_count", entries.stream().mapToInt(entry -> entry.values().size()).sum());
        JsonArray tags = new JsonArray();
        for (TagEntry entry : entries) {
            JsonObject tag = new JsonObject();
            tag.addProperty("id", entry.id());
            tag.addProperty("value_count", entry.values().size());
            JsonArray values = new JsonArray();
            entry.values().forEach(values::add);
            tag.add("values", values);
            tags.add(tag);
        }
        root.add("tags", tags);
        return writeJson(server, safeName("tags_" + registryId), root);
    }

    public static DumpResult dumpTagsCsv(MinecraftServer server, String registryId) throws IOException {
        Optional<? extends Registry<?>> registryOptional = findRegistry(server.registryAccess(), registryId);
        if (registryOptional.isEmpty()) {
            return DumpResult.notFound("Unknown runtime registry: " + registryId);
        }
        List<List<String>> rows = tagRelationRows(registryId, tagEntries(registryOptional.get()));
        return writeText(server, safeName("tags_" + registryId), "csv",
                csv(List.of("registry_id", "tag_id", "value_id"), rows));
    }

    public static DumpResult dumpTagsXlsx(MinecraftServer server, String registryId) throws IOException {
        Optional<? extends Registry<?>> registryOptional = findRegistry(server.registryAccess(), registryId);
        if (registryOptional.isEmpty()) {
            return DumpResult.notFound("Unknown runtime registry: " + registryId);
        }
        List<List<String>> rows = tagRelationRows(registryId, tagEntries(registryOptional.get()));
        byte[] workbook = XlsxWriter.write(List.of(
                overviewSheet("Tag export", "One row per tag/value relation in " + registryId, rows.size()),
                new XlsxWriter.Sheet("Tags", List.of("Registry ID", "Tag ID", "Value ID"), rows,
                        List.of(38.0D, 46.0D, 46.0D))));
        return writeBytes(server, safeName("tags_" + registryId), "xlsx", workbook);
    }

    public static DumpResult dumpRecipes(MinecraftServer server) throws IOException {
        List<RecipeEntry> recipes = recipeEntries(server);
        return writeJson(server, "recipes", recipesJson(recipes));
    }

    public static DumpResult dumpRecipesCsv(MinecraftServer server) throws IOException {
        List<RecipeEntry> recipes = recipeEntries(server);
        return writeText(server, "recipes", "csv", csv(recipeHeaders(false), recipeRows(recipes)));
    }

    public static DumpResult dumpRecipesXlsx(MinecraftServer server) throws IOException {
        List<RecipeEntry> recipes = recipeEntries(server);
        byte[] workbook = XlsxWriter.write(List.of(
                overviewSheet("Recipe export", "Readable summary of runtime recipes and accepted ingredients", recipes.size()),
                new XlsxWriter.Sheet("Recipes", recipeHeaders(true), recipeRows(recipes),
                        List.of(46.0D, 20.0D, 32.0D, 32.0D, 24.0D, 42.0D, 14.0D, 26.0D, 70.0D, 70.0D))));
        return writeBytes(server, "recipes", "xlsx", workbook);
    }

    public static DumpResult dumpCobblemonSpawnsJson(MinecraftServer server) throws IOException {
        if (!CobblemonApiPresence.isCobblemonInstalled()) {
            return DumpResult.notFound("Cobblemon is not installed");
        }
        CobblemonSpawnInspector.Snapshot snapshot = CobblemonSpawnInspector.snapshot(server);
        if (!snapshot.available()) {
            return DumpResult.notFound("No loaded Cobblemon world spawn pools were found");
        }
        return writeJson(server, "cobblemon_spawns", cobblemonSpawnsJson(snapshot));
    }

    /** Normalized one-row-per-spawn CSV intended for scripts and Discord bots. */
    public static DumpResult dumpCobblemonSpawnsCsv(MinecraftServer server) throws IOException {
        if (!CobblemonApiPresence.isCobblemonInstalled()) {
            return DumpResult.notFound("Cobblemon is not installed");
        }
        CobblemonSpawnInspector.Snapshot snapshot = CobblemonSpawnInspector.snapshot(server);
        if (!snapshot.available()) {
            return DumpResult.notFound("No loaded Cobblemon world spawn pools were found");
        }
        return writeText(server, "cobblemon_spawns", "csv", csv(spawnHeaders(false), spawnRows(snapshot)));
    }

    /** Human-facing Cobblemon audit with normalized, filterable sheets. */
    public static DumpResult dumpCobblemonSpawnsXlsx(MinecraftServer server) throws IOException {
        if (!CobblemonApiPresence.isCobblemonInstalled()) {
            return DumpResult.notFound("Cobblemon is not installed");
        }
        CobblemonSpawnInspector.Snapshot snapshot = CobblemonSpawnInspector.snapshot(server);
        if (!snapshot.available()) {
            return DumpResult.notFound("No loaded Cobblemon world spawn pools were found");
        }
        long errors = snapshot.issues().stream()
                .filter(issue -> issue.severity() == CobblemonSpawnInspector.Severity.ERROR).count();
        long warnings = snapshot.issues().size() - errors;
        List<List<String>> overview = new ArrayList<>(overviewSheet("Cobblemon spawn audit",
                "Loaded world spawn pools, presets, conditions and unresolved references", snapshot.spawns().size()).rows());
        overview.add(List.of("Pool files", Integer.toString(snapshot.poolFiles())));
        overview.add(List.of("Pokemon", Integer.toString(snapshot.speciesCount())));
        overview.add(List.of("Presets", Integer.toString(snapshot.presets().size())));
        overview.add(List.of("Errors", Long.toString(errors)));
        overview.add(List.of("Warnings", Long.toString(warnings)));
        overview.add(List.of("Interpretation", "Warnings can be intentional; inspect duplicate IDs and optional mod references before changing a datapack."));

        List<XlsxWriter.Sheet> sheets = List.of(
                new XlsxWriter.Sheet("Overview", List.of("Field", "Value"), List.copyOf(overview),
                        List.of(28.0D, 100.0D)),
                new XlsxWriter.Sheet("Problems", List.of("Severity", "Code", "Affected resource", "Details"),
                        spawnIssueRows(snapshot), List.of(14.0D, 30.0D, 64.0D, 100.0D)),
                new XlsxWriter.Sheet("Spawn Entries", spawnHeaders(true), spawnRows(snapshot),
                        List.of(28.0D, 34.0D, 24.0D, 12.0D, 18.0D, 14.0D, 60.0D, 18.0D,
                                18.0D, 45.0D, 35.0D, 35.0D, 58.0D, 32.0D, 14.0D, 68.0D, 68.0D)),
                new XlsxWriter.Sheet("Conditions",
                        List.of("Spawn ID", "Pokemon", "Kind", "Condition", "Value", "Source resource"),
                        spawnConditionRows(snapshot), List.of(32.0D, 28.0D, 18.0D, 30.0D, 90.0D, 64.0D)),
                new XlsxWriter.Sheet("Presets",
                        List.of("Preset ID", "Condition", "Anticondition", "Full JSON"),
                        spawnPresetRows(snapshot), List.of(42.0D, 80.0D, 80.0D, 110.0D)),
                new XlsxWriter.Sheet("Buckets", List.of("Bucket", "Entries", "Pokemon"),
                        spawnBucketRows(snapshot), List.of(24.0D, 16.0D, 16.0D)),
                new XlsxWriter.Sheet("Dependencies",
                        List.of("Spawn ID", "Pokemon", "Rule", "Mod ID", "Currently loaded", "Source resource"),
                        spawnDependencyRows(snapshot), List.of(32.0D, 28.0D, 28.0D, 28.0D, 18.0D, 64.0D)),
                new XlsxWriter.Sheet("Source Packs", List.of("Pack", "Pool entries", "Pokemon"),
                        spawnPackRows(snapshot), List.of(50.0D, 18.0D, 16.0D)));
        return writeBytes(server, "cobblemon_spawns", "xlsx", XlsxWriter.write(sheets));
    }

    /** RFC 4180 CSV intended for scripts and bots. */
    public static DumpResult dumpBiomesWithTagsCsv(MinecraftServer server) throws IOException {
        List<BiomeTagEntry> biomes = biomeTags(server);
        return writeText(server, "biomes_tags", "csv", csv(biomeHeaders(false), biomeRows(biomes)));
    }

    public static DumpResult dumpBiomesWithTagsJson(MinecraftServer server) throws IOException {
        List<BiomeTagEntry> biomes = biomeTags(server);
        JsonObject root = base("biomes_with_tags");
        root.addProperty("registry", IdentifierCompat.resourceKeyId(Registries.BIOME));
        root.addProperty("biome_count", biomes.size());
        root.addProperty("relation_count", biomes.stream().mapToInt(entry -> entry.tags().size()).sum());
        JsonArray array = new JsonArray();
        for (BiomeTagEntry biome : biomes) {
            JsonObject value = resourceObject(biome.id());
            value.addProperty("tag_count", biome.tags().size());
            JsonArray tags = new JsonArray();
            biome.tags().forEach(tags::add);
            value.add("tags", tags);
            array.add(value);
        }
        root.add("biomes", array);
        return writeJson(server, "biomes_tags", root);
    }

    /** Human-facing workbook: opens with an overview, then normalized/filterable sheets. */
    public static DumpResult dumpBiomesWithTagsXlsx(MinecraftServer server) throws IOException {
        List<BiomeTagEntry> biomes = biomeTags(server);
        List<List<String>> relations = biomeTagRows(biomes);
        byte[] workbook = XlsxWriter.write(List.of(
                overviewSheet("Biome export", "Use 'Biomes' for the summary and 'Biome Tags' for filtering tags", biomes.size()),
                new XlsxWriter.Sheet("Biomes", biomeHeaders(true), biomeRows(biomes),
                        List.of(44.0D, 22.0D, 38.0D, 34.0D, 14.0D, 68.0D)),
                new XlsxWriter.Sheet("Biome Tags", List.of("Biome ID", "Tag ID"), relations,
                        List.of(46.0D, 52.0D))));
        return writeBytes(server, "biomes_tags", "xlsx", workbook);
    }

    public static DumpResult dumpReport(MinecraftServer server) throws IOException {
        List<BiomeTagEntry> biomes = biomeTags(server);
        List<RecipeEntry> recipes = recipeEntries(server);
        CobblemonSpawnInspector.Snapshot spawns = CobblemonApiPresence.isCobblemonInstalled()
                ? CobblemonSpawnInspector.snapshot(server) : null;
        boolean cobblemonSpawnsAvailable = spawns != null && spawns.available();
        List<List<String>> mods = modRows();
        List<List<String>> registries = registryRows(server);
        List<List<String>> registryEntries = allRegistryEntryRows(server);
        List<List<String>> tags = allTagRows(server);
        List<List<String>> datapacks = datapackRows(server);
        List<List<String>> dimensions = dimensionRows(server);
        List<List<String>> loadedEntities = loadedEntityRows(server);

        List<XlsxWriter.Sheet> sheets = new ArrayList<>();
        sheets.add(overviewSheet("InspectMC report", "Complete runtime snapshot for humans, scripts and Discord bots", registryEntries.size()));
        sheets.addAll(List.of(new XlsxWriter.Sheet("Mods", List.of("Mod ID", "Name", "Version"), mods,
                        List.of(28.0D, 42.0D, 24.0D)),
                new XlsxWriter.Sheet("Datapacks", List.of("Pack ID"), datapacks,
                        List.of(64.0D)),
                new XlsxWriter.Sheet("Dimensions", List.of("Dimension", "Players", "Loaded entities", "Forced chunks"), dimensions,
                        List.of(42.0D, 14.0D, 20.0D, 18.0D)),
                new XlsxWriter.Sheet("Loaded Entities", List.of("Dimension", "Entity type", "Count"), loadedEntities,
                        List.of(42.0D, 42.0D, 14.0D)),
                new XlsxWriter.Sheet("Registries", List.of("Registry ID", "Namespace", "Path"), registries,
                        List.of(46.0D, 22.0D, 42.0D)),
                new XlsxWriter.Sheet("Registry Entries",
                        List.of("Registry ID", "Entry ID", "Namespace", "Path", "Provided by"), registryEntries,
                        List.of(46.0D, 48.0D, 22.0D, 44.0D, 34.0D)),
                new XlsxWriter.Sheet("Tags", List.of("Registry ID", "Tag ID", "Value ID"), tags,
                        List.of(46.0D, 48.0D, 48.0D)),
                new XlsxWriter.Sheet("Biomes", biomeHeaders(true), biomeRows(biomes),
                        List.of(44.0D, 22.0D, 38.0D, 34.0D, 14.0D, 68.0D)),
                new XlsxWriter.Sheet("Biome Tags", List.of("Biome ID", "Tag ID"), biomeTagRows(biomes),
                        List.of(46.0D, 52.0D)),
                new XlsxWriter.Sheet("Recipes", recipeHeaders(true), recipeRows(recipes),
                        List.of(46.0D, 20.0D, 32.0D, 32.0D, 24.0D, 42.0D, 14.0D, 26.0D, 70.0D, 70.0D))));
        if (cobblemonSpawnsAvailable) {
            sheets.add(new XlsxWriter.Sheet("Spawn Problems",
                    List.of("Severity", "Code", "Affected resource", "Details"),
                    spawnIssueRows(spawns), List.of(14.0D, 30.0D, 64.0D, 100.0D)));
            sheets.add(new XlsxWriter.Sheet("Cobblemon Spawns", spawnHeaders(true), spawnRows(spawns),
                    List.of(28.0D, 34.0D, 24.0D, 12.0D, 18.0D, 14.0D, 60.0D, 18.0D,
                            18.0D, 45.0D, 35.0D, 35.0D, 58.0D, 32.0D, 14.0D, 68.0D, 68.0D)));
            sheets.add(new XlsxWriter.Sheet("Spawn Conditions",
                    List.of("Spawn ID", "Pokemon", "Kind", "Condition", "Value", "Source resource"),
                    spawnConditionRows(spawns), List.of(32.0D, 28.0D, 18.0D, 30.0D, 90.0D, 64.0D)));
        }

        List<String> files = new ArrayList<>(List.of("manifest.json", "report.xlsx", "mods.csv", "datapacks.csv",
                "dimensions.csv", "loaded_entities.csv", "registries.csv", "registry_entries.csv", "tags.csv",
                "biomes.csv", "biome_tags.csv", "recipes.csv", "recipes.json"));
        if (cobblemonSpawnsAvailable) {
            files.add("cobblemon_spawns.csv");
            files.add("cobblemon_spawns.json");
        }
        JsonObject manifest = base("report");
        JsonObject summary = new JsonObject();
        summary.addProperty("mods", mods.size());
        summary.addProperty("datapacks", datapacks.size());
        summary.addProperty("dimensions", dimensions.size());
        summary.addProperty("loaded_entity_rows", loadedEntities.size());
        summary.addProperty("registries", registries.size());
        summary.addProperty("registry_entries", registryEntries.size());
        summary.addProperty("tag_relations", tags.size());
        summary.addProperty("biomes", biomes.size());
        summary.addProperty("biome_tag_relations", biomeTagRows(biomes).size());
        summary.addProperty("recipes", recipes.size());
        if (CobblemonApiPresence.isCobblemonInstalled()) {
            summary.addProperty("cobblemon_spawn_pools_available", cobblemonSpawnsAvailable);
        }
        if (cobblemonSpawnsAvailable) {
            summary.addProperty("cobblemon_spawn_entries", spawns.spawns().size());
            summary.addProperty("cobblemon_spawn_issues", spawns.issues().size());
        }
        manifest.add("summary", summary);
        JsonArray fileArray = new JsonArray();
        files.forEach(fileArray::add);
        manifest.add("files", fileArray);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            zipText(zip, "manifest.json", GSON.toJson(manifest));
            zipBytes(zip, "report.xlsx", XlsxWriter.write(sheets));
            zipText(zip, "mods.csv", csv(List.of("mod_id", "name", "version"), mods));
            zipText(zip, "datapacks.csv", csv(List.of("pack_id"), datapacks));
            zipText(zip, "dimensions.csv", csv(List.of("dimension", "players", "loaded_entities", "forced_chunks"), dimensions));
            zipText(zip, "loaded_entities.csv", csv(List.of("dimension", "entity_type", "count"), loadedEntities));
            zipText(zip, "registries.csv", csv(List.of("registry_id", "namespace", "path"), registries));
            zipText(zip, "registry_entries.csv",
                    csv(List.of("registry_id", "entry_id", "namespace", "path", "provided_by"), registryEntries));
            zipText(zip, "tags.csv", csv(List.of("registry_id", "tag_id", "value_id"), tags));
            zipText(zip, "biomes.csv", csv(biomeHeaders(false), biomeRows(biomes)));
            zipText(zip, "biome_tags.csv", csv(List.of("biome_id", "tag_id"), biomeTagRows(biomes)));
            zipText(zip, "recipes.csv", csv(recipeHeaders(false), recipeRows(recipes)));
            zipText(zip, "recipes.json", GSON.toJson(recipesJson(recipes)));
            if (cobblemonSpawnsAvailable) {
                zipText(zip, "cobblemon_spawns.csv", csv(spawnHeaders(false), spawnRows(spawns)));
                zipText(zip, "cobblemon_spawns.json", GSON.toJson(cobblemonSpawnsJson(spawns)));
            }
        }
        return writeBytes(server, "inspectmc_report", "zip", bytes.toByteArray());
    }

    public static List<String> registryIds(MinecraftServer server) {
        List<String> ids = new ArrayList<>();
        server.registryAccess().registries().forEach(entry -> ids.add(IdentifierCompat.resourceKeyId(entry.key())));
        ids.sort(String::compareTo);
        return ids;
    }

    private static JsonObject recipesJson(List<RecipeEntry> recipes) {
        JsonObject root = base("recipes");
        root.addProperty("recipe_count", recipes.size());
        JsonArray array = new JsonArray();
        for (RecipeEntry recipe : recipes) {
            JsonObject value = new JsonObject();
            value.addProperty("id", recipe.id().toString());
            value.addProperty("namespace", IdentifierCompat.namespace(recipe.id()));
            value.addProperty("provided_by", recipe.providedBy());
            value.addProperty("type", recipe.type());
            value.addProperty("group", recipe.group());
            JsonObject result = new JsonObject();
            result.addProperty("id", recipe.resultId());
            result.addProperty("count", recipe.resultCount());
            result.addProperty("components", recipe.resultComponents());
            value.add("result", result);
            JsonArray ingredients = new JsonArray();
            for (List<String> alternatives : recipe.ingredients()) {
                JsonArray ingredient = new JsonArray();
                alternatives.forEach(ingredient::add);
                ingredients.add(ingredient);
            }
            value.addProperty("ingredient_count", recipe.ingredients().size());
            value.add("ingredients", ingredients);
            array.add(value);
        }
        root.add("recipes", array);
        return root;
    }

    private static JsonObject cobblemonSpawnsJson(CobblemonSpawnInspector.Snapshot snapshot) {
        JsonObject root = base("cobblemon_spawns");
        root.addProperty("pool_file_count", snapshot.poolFiles());
        root.addProperty("spawn_count", snapshot.spawns().size());
        root.addProperty("pokemon_count", snapshot.speciesCount());
        root.addProperty("preset_count", snapshot.presets().size());
        JsonArray issues = new JsonArray();
        for (CobblemonSpawnInspector.Issue issue : snapshot.issues()) {
            JsonObject value = new JsonObject();
            value.addProperty("severity", issue.severity().name().toLowerCase(java.util.Locale.ROOT));
            value.addProperty("code", issue.code());
            value.addProperty("location", issue.location());
            value.addProperty("detail", issue.detail());
            issues.add(value);
        }
        root.add("issues", issues);

        JsonArray presets = new JsonArray();
        snapshot.presets().entrySet().stream().sorted(Map.Entry.comparingByKey(
                        Comparator.naturalOrder()))
                .forEach(entry -> {
                    JsonObject value = new JsonObject();
                    value.addProperty("id", entry.getKey().toString());
                    value.add("data", entry.getValue());
                    presets.add(value);
                });
        root.add("presets", presets);

        JsonArray spawns = new JsonArray();
        for (CobblemonSpawnInspector.SpawnEntry entry : snapshot.spawns()) {
            JsonObject value = new JsonObject();
            value.addProperty("id", entry.id());
            value.addProperty("pokemon", entry.pokemon());
            value.addProperty("species", entry.species());
            value.addProperty("bucket", entry.bucket());
            value.addProperty("weight", entry.weight());
            value.addProperty("enabled", entry.enabled());
            value.addProperty("type", entry.type());
            value.addProperty("context", entry.context());
            value.addProperty("source", entry.source().toString());
            value.addProperty("source_pack", entry.sourcePack());
            value.addProperty("source_index", entry.sourceIndex());
            JsonArray presetIds = new JsonArray();
            entry.presets().forEach(presetIds::add);
            value.add("presets", presetIds);
            JsonArray neededInstalledMods = new JsonArray();
            entry.neededInstalledMods().forEach(neededInstalledMods::add);
            value.add("needed_installed_mods", neededInstalledMods);
            JsonArray neededUninstalledMods = new JsonArray();
            entry.neededUninstalledMods().forEach(neededUninstalledMods::add);
            value.add("needed_uninstalled_mods", neededUninstalledMods);
            if (entry.raw().has("weightMultiplier")) {
                value.add("weight_multiplier", entry.raw().get("weightMultiplier"));
            }
            value.add("condition", entry.condition());
            value.add("anticondition", entry.anticondition());
            value.add("raw", entry.raw());
            spawns.add(value);
        }
        root.add("spawns", spawns);
        return root;
    }

    private static List<String> spawnHeaders(boolean humanReadable) {
        return humanReadable
                ? List.of("Spawn ID", "Pokemon definition", "Species", "Enabled", "Bucket", "Base weight",
                "Weight multiplier", "Type", "Context", "Presets", "Required mods", "Must be absent",
                "Source resource", "Source pack", "Entry #", "Condition", "Anticondition")
                : List.of("spawn_id", "pokemon", "species", "enabled", "bucket", "base_weight",
                "weight_multiplier_json", "type", "context", "presets", "needed_installed_mods",
                "needed_uninstalled_mods", "source_resource", "source_pack", "source_index",
                "condition_json", "anticondition_json");
    }

    private static List<List<String>> spawnRows(CobblemonSpawnInspector.Snapshot snapshot) {
        return snapshot.spawns().stream().map(entry -> List.of(
                entry.id(), entry.pokemon(), entry.species(), Boolean.toString(entry.enabled()), entry.bucket(),
                Double.toString(entry.weight()), entry.raw().has("weightMultiplier")
                        ? GSON.toJson(entry.raw().get("weightMultiplier")) : "",
                entry.type(), entry.context(), String.join(" | ", entry.presets()),
                String.join(" | ", entry.neededInstalledMods()), String.join(" | ", entry.neededUninstalledMods()),
                entry.source().toString(), entry.sourcePack(), Integer.toString(entry.sourceIndex()),
                GSON.toJson(entry.condition()), GSON.toJson(entry.anticondition()))).toList();
    }

    private static List<List<String>> spawnIssueRows(CobblemonSpawnInspector.Snapshot snapshot) {
        if (snapshot.issues().isEmpty()) {
            return List.of(List.of("OK", "none", "", "No unresolved reference or malformed entry found"));
        }
        return snapshot.issues().stream().map(issue -> List.of(issue.severity().name(), issue.code(),
                issue.location(), issue.detail())).toList();
    }

    private static List<List<String>> spawnConditionRows(CobblemonSpawnInspector.Snapshot snapshot) {
        List<List<String>> rows = new ArrayList<>();
        for (CobblemonSpawnInspector.SpawnEntry entry : snapshot.spawns()) {
            appendConditionRows(rows, entry, "condition", entry.condition());
            appendConditionRows(rows, entry, "anticondition", entry.anticondition());
            if (entry.condition().size() == 0 && entry.anticondition().size() == 0) {
                rows.add(List.of(entry.id(), entry.species(), "none", "", "", entry.source().toString()));
            }
        }
        return List.copyOf(rows);
    }

    private static void appendConditionRows(List<List<String>> rows, CobblemonSpawnInspector.SpawnEntry entry,
                                            String kind, JsonObject condition) {
        condition.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(value -> rows.add(List.of(
                entry.id(), entry.species(), kind, value.getKey(), GSON.toJson(value.getValue()),
                entry.source().toString())));
    }

    private static List<List<String>> spawnPresetRows(CobblemonSpawnInspector.Snapshot snapshot) {
        return snapshot.presets().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> List.of(entry.getKey().toString(),
                        entry.getValue().has("condition") ? GSON.toJson(entry.getValue().get("condition")) : "",
                        entry.getValue().has("anticondition") ? GSON.toJson(entry.getValue().get("anticondition")) : "",
                        GSON.toJson(entry.getValue()))).toList();
    }

    private static List<List<String>> spawnBucketRows(CobblemonSpawnInspector.Snapshot snapshot) {
        Map<String, List<CobblemonSpawnInspector.SpawnEntry>> grouped = snapshot.spawns().stream()
                .collect(java.util.stream.Collectors.groupingBy(CobblemonSpawnInspector.SpawnEntry::bucket,
                        TreeMap::new, java.util.stream.Collectors.toList()));
        return grouped.entrySet().stream().map(entry -> List.of(entry.getKey(),
                Integer.toString(entry.getValue().size()), Long.toString(entry.getValue().stream()
                        .map(CobblemonSpawnInspector.SpawnEntry::species).distinct().count()))).toList();
    }

    private static List<List<String>> spawnPackRows(CobblemonSpawnInspector.Snapshot snapshot) {
        Map<String, List<CobblemonSpawnInspector.SpawnEntry>> grouped = snapshot.spawns().stream()
                .collect(java.util.stream.Collectors.groupingBy(CobblemonSpawnInspector.SpawnEntry::sourcePack,
                        TreeMap::new, java.util.stream.Collectors.toList()));
        return grouped.entrySet().stream().map(entry -> List.of(entry.getKey(),
                Integer.toString(entry.getValue().size()), Long.toString(entry.getValue().stream()
                        .map(CobblemonSpawnInspector.SpawnEntry::species).distinct().count()))).toList();
    }

    private static List<List<String>> spawnDependencyRows(CobblemonSpawnInspector.Snapshot snapshot) {
        java.util.Set<String> loaded = InspectMC.platform().loadedMods().stream().map(ModDescriptor::id)
                .collect(java.util.stream.Collectors.toSet());
        List<List<String>> rows = new ArrayList<>();
        for (CobblemonSpawnInspector.SpawnEntry entry : snapshot.spawns()) {
            entry.neededInstalledMods().forEach(mod -> rows.add(List.of(entry.id(), entry.species(),
                    "must be installed", mod, Boolean.toString(loaded.contains(mod)), entry.source().toString())));
            entry.neededUninstalledMods().forEach(mod -> rows.add(List.of(entry.id(), entry.species(),
                    "must be absent", mod, Boolean.toString(loaded.contains(mod)), entry.source().toString())));
        }
        if (rows.isEmpty()) {
            rows.add(List.of("", "", "none", "", "", "No explicit mod dependency in loaded pools"));
        }
        return List.copyOf(rows);
    }

    private static List<RecipeEntry> recipeEntries(MinecraftServer server) {
        List<RecipeEntry> entries = new ArrayList<>();
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            Object recipe = holder.value();
            Object type = MinecraftCompat.call(recipe, "getType", "type");
            String typeId = type == null ? "<unknown>" : IdentifierCompat.value(BuiltInRegistries.RECIPE_TYPE.getKey((net.minecraft.world.item.crafting.RecipeType<?>) type));
            Object resultValue = MinecraftCompat.call(recipe, "getResultItem", server.registryAccess());
            if (resultValue == null) resultValue = MinecraftCompat.call(recipe, "resultItem");
            ItemStack result = resultValue instanceof ItemStack stack ? stack : ItemStack.EMPTY;
            List<List<String>> ingredients = new ArrayList<>();
            Object ingredientValue = MinecraftCompat.call(recipe, "getIngredients", "ingredients");
            if (ingredientValue instanceof Iterable<?> iterable) for (Object ingredient : iterable) {
                Object empty = MinecraftCompat.call(ingredient, "isEmpty", "isEmptyIngredient");
                if (Boolean.TRUE.equals(empty)) {
                    continue;
                }
                try {
                    Object itemsValue = MinecraftCompat.call(ingredient, "getItems", "items");
                    ItemStack[] items = itemsValue instanceof ItemStack[] stacks ? stacks : new ItemStack[0];
                    List<String> alternatives = Arrays.stream(items)
                            .filter(stack -> !stack.isEmpty())
                            .map(stack -> IdentifierCompat.value(BuiltInRegistries.ITEM.getKey(stack.getItem())))
                            .distinct().sorted().toList();
                    ingredients.add(alternatives.isEmpty() ? List.of("<custom ingredient>") : alternatives);
                } catch (RuntimeException exception) {
                    ingredients.add(List.of("<custom ingredient>"));
                }
            }
            String resultId = result.isEmpty() ? "" : IdentifierCompat.value(BuiltInRegistries.ITEM.getKey(result.getItem()));
            Object group = MinecraftCompat.call(recipe, "getGroup", "group");
            String recipeId = IdentifierCompat.resourceKeyId(holder.id());
            entries.add(new RecipeEntry(recipeId, ownerFor(recipeId),
                    typeId, group == null ? "" : group.toString(), resultId,
                    result.isEmpty() ? 0 : result.getCount(),
                    result.isEmpty() ? "" : result.getComponents().toString(), List.copyOf(ingredients)));
        }
        entries.sort(Comparator.comparing(RecipeEntry::id));
        return List.copyOf(entries);
    }

    private static List<BiomeTagEntry> biomeTags(MinecraftServer server) {
        Registry<Biome> registry = IdentifierCompat.registry(server.registryAccess(), Registries.BIOME);
        return registry.keySet().stream()
                .map(IdentifierCompat::value)
                .sorted()
                .map(id -> new BiomeTagEntry(id, List.of()))
                .toList();
    }

    private static List<TagEntry> tagEntries(Registry<?> registry) {
        List<TagEntry> entries = new ArrayList<>();
        Object tags = MinecraftCompat.call(registry, "getTags", "tags");
        if (tags instanceof java.util.stream.Stream<?> stream) {
            stream.forEach(pair -> {
                Object key = MinecraftCompat.call(pair, "getFirst", "key", "name");
                Object valuesObject = MinecraftCompat.call(pair, "getSecond", "values", "elements");
                List<String> values = new ArrayList<>();
                if (valuesObject instanceof Iterable<?> iterable) {
                    for (Object holder : iterable) {
                        Object holderKey = MinecraftCompat.call(holder, "unwrapKey", "key");
                        if (holderKey instanceof java.util.Optional<?> optional) {
                            optional.ifPresent(value -> values.add(IdentifierCompat.resourceKeyId(value)));
                        } else if (holderKey != null) {
                            values.add(IdentifierCompat.resourceKeyId(holderKey));
                        }
                    }
                }
                values.sort(String::compareTo);
                if (key != null) entries.add(new TagEntry(IdentifierCompat.resourceKeyId(key), List.copyOf(values)));
            });
        }
        entries.sort(Comparator.comparing(TagEntry::id));
        return List.copyOf(entries);
    }

    private static List<String> sortedKeys(Registry<?> registry) {
        return registry.keySet().stream().map(IdentifierCompat::value).sorted().toList();
    }

    private static List<List<String>> resourceRows(List<String> ids) {
        return ids.stream().map(id -> List.of(id, IdentifierCompat.namespace(id), IdentifierCompat.path(id), ownerFor(id))).toList();
    }

    private static List<String> biomeHeaders(boolean humanReadable) {
        return humanReadable
                ? List.of("Biome ID", "Namespace", "Path", "Provided by", "Tag count", "Tags (summary)")
                : List.of("biome_id", "namespace", "path", "provided_by", "tag_count", "tags");
    }

    private static List<List<String>> biomeRows(List<BiomeTagEntry> biomes) {
        return biomes.stream().map(biome -> List.of(
                biome.id(), IdentifierCompat.namespace(biome.id()), IdentifierCompat.path(biome.id()), ownerFor(biome.id()),
                Integer.toString(biome.tags().size()), String.join(" | ", biome.tags()))).toList();
    }

    private static List<List<String>> biomeTagRows(List<BiomeTagEntry> biomes) {
        List<List<String>> rows = new ArrayList<>();
        for (BiomeTagEntry biome : biomes) {
            biome.tags().forEach(tag -> rows.add(List.of(biome.id().toString(), tag)));
        }
        return List.copyOf(rows);
    }

    private static List<List<String>> tagRelationRows(String registryId, List<TagEntry> entries) {
        List<List<String>> rows = new ArrayList<>();
        for (TagEntry entry : entries) {
            if (entry.values().isEmpty()) {
                rows.add(List.of(registryId.toString(), entry.id(), ""));
            } else {
                entry.values().forEach(value -> rows.add(List.of(registryId.toString(), entry.id(), value)));
            }
        }
        return List.copyOf(rows);
    }

    private static List<String> recipeHeaders(boolean humanReadable) {
        return humanReadable
                ? List.of("Recipe ID", "Namespace", "Provided by", "Type", "Group", "Result ID",
                "Result count", "Result components", "Ingredient count", "Accepted ingredients")
                : List.of("recipe_id", "namespace", "provided_by", "type", "group", "result_id",
                "result_count", "result_components", "ingredient_count", "ingredients");
    }

    private static List<List<String>> recipeRows(List<RecipeEntry> recipes) {
        return recipes.stream().map(recipe -> List.of(
                recipe.id(), IdentifierCompat.namespace(recipe.id()), recipe.providedBy(), recipe.type(),
                recipe.group(), recipe.resultId(), Integer.toString(recipe.resultCount()),
                recipe.resultComponents(),
                Integer.toString(recipe.ingredients().size()), recipe.ingredients().stream()
                        .map(alternatives -> String.join(" | ", alternatives))
                        .collect(java.util.stream.Collectors.joining(" + ")))).toList();
    }

    private static List<List<String>> modRows() {
        return InspectMC.platform().loadedMods().stream().sorted(Comparator.comparing(ModDescriptor::id))
                .map(mod -> List.of(mod.id(), mod.name(), mod.version())).toList();
    }

    private static List<List<String>> registryRows(MinecraftServer server) {
        return registryIds(server).stream().map(id -> List.of(id, IdentifierCompat.namespace(id), IdentifierCompat.path(id))).toList();
    }

    private static List<List<String>> datapackRows(MinecraftServer server) {
        return server.getPackRepository().getSelectedIds().stream()
                .sorted(String::compareToIgnoreCase)
                .map(List::of)
                .toList();
    }

    private static List<List<String>> dimensionRows(MinecraftServer server) {
        List<List<String>> rows = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            int loadedEntities = 0;
            for (Entity ignored : level.getAllEntities()) {
                loadedEntities++;
            }
            rows.add(List.of(IdentifierCompat.resourceKeyId(level.dimension()),
                    Integer.toString(level.players().size()),
                    Integer.toString(loadedEntities),
                    Integer.toString(MinecraftCompat.forcedChunks(level).size())));
        }
        rows.sort(Comparator.comparing(row -> row.get(0)));
        return List.copyOf(rows);
    }

    private static List<List<String>> loadedEntityRows(MinecraftServer server) {
        Map<String, Map<String, Integer>> counts = new TreeMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            Map<String, Integer> dimensionCounts = counts.computeIfAbsent(
                    IdentifierCompat.resourceKeyId(level.dimension()), ignored -> new TreeMap<>());
            for (Entity entity : level.getAllEntities()) {
                String entityId = IdentifierCompat.value(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
                dimensionCounts.merge(entityId, 1, Integer::sum);
            }
        }
        List<List<String>> rows = new ArrayList<>();
        counts.forEach((dimension, entities) -> entities.forEach((entity, count) ->
                rows.add(List.of(dimension, entity, Integer.toString(count)))));
        return List.copyOf(rows);
    }

    private static List<List<String>> allRegistryEntryRows(MinecraftServer server) {
        List<List<String>> rows = new ArrayList<>();
        server.registryAccess().registries()
                .sorted(Comparator.comparing(entry -> IdentifierCompat.resourceKeyId(entry.key())))
                .forEach(entry -> sortedKeys(entry.value()).forEach(id -> rows.add(List.of(
                        IdentifierCompat.resourceKeyId(entry.key()), id, IdentifierCompat.namespace(id), IdentifierCompat.path(id), ownerFor(id)))));
        return List.copyOf(rows);
    }

    private static List<List<String>> allTagRows(MinecraftServer server) {
        List<List<String>> rows = new ArrayList<>();
        server.registryAccess().registries()
                .sorted(Comparator.comparing(entry -> IdentifierCompat.resourceKeyId(entry.key())))
                .forEach(entry -> rows.addAll(tagRelationRows(IdentifierCompat.resourceKeyId(entry.key()), tagEntries(entry.value()))));
        return List.copyOf(rows);
    }

    private static XlsxWriter.Sheet overviewSheet(String export, String description, int entries) {
        List<List<String>> rows = List.of(
                List.of("Export", export), List.of("Description", description),
                List.of("Generated (UTC)", Instant.now().toString()),
                List.of("Minecraft", MinecraftCompat.versionName()),
                List.of("InspectMC", inspectMcVersion()), List.of("Loader", InspectMC.platform().loaderName()),
                List.of("Schema version", Integer.toString(SCHEMA_VERSION)),
                List.of("Rows / entries", Integer.toString(entries)),
                List.of("Tip", "Use the filters in row 1. The header stays visible while scrolling."));
        return new XlsxWriter.Sheet("Overview", List.of("Field", "Value"), rows, List.of(26.0D, 90.0D));
    }

    private static JsonObject base(String type) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", "inspectmc." + type);
        root.addProperty("schema_version", SCHEMA_VERSION);
        root.addProperty("tool", InspectMC.MOD_NAME);
        root.addProperty("type", type);
        root.addProperty("generated_at", Instant.now().toString());
        root.addProperty("minecraft_version", MinecraftCompat.versionName());
        root.addProperty("inspectmc_version", inspectMcVersion());
        root.addProperty("loader", InspectMC.platform().loaderName());
        return root;
    }

    private static JsonObject resourceObject(String id) {
        JsonObject value = new JsonObject();
        value.addProperty("id", id);
        value.addProperty("namespace", IdentifierCompat.namespace(id));
        value.addProperty("path", IdentifierCompat.path(id));
        value.addProperty("provided_by", ownerFor(id));
        return value;
    }

    private static String ownerFor(String id) {
        if (IdentifierCompat.namespace(id).equals("minecraft")) {
            return "Minecraft";
        }
        ModDescriptor mod = loadedModsById().get(IdentifierCompat.namespace(id));
        return mod == null ? IdentifierCompat.namespace(id) : mod.name() + " (" + mod.id() + ")";
    }

    private static String inspectMcVersion() {
        ModDescriptor mod = loadedModsById().get(InspectMC.MOD_ID);
        return mod == null ? "unknown" : mod.version();
    }

    private static Map<String, ModDescriptor> loadedModsById() {
        Map<String, ModDescriptor> cached = modsById;
        if (cached != null) {
            return cached;
        }
        synchronized (DumpService.class) {
            if (modsById == null) {
                modsById = InspectMC.platform().loadedMods().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                        ModDescriptor::id, mod -> mod, (first, ignored) -> first));
            }
            return modsById;
        }
    }

    private static Optional<? extends Registry<?>> findRegistry(RegistryAccess access, String id) {
        return access.registries().filter(entry -> IdentifierCompat.resourceKeyId(entry.key()).equals(id))
                .map(RegistryAccess.RegistryEntry::value).findFirst();
    }

    private static String csv(List<String> headers, List<List<String>> rows) {
        StringBuilder result = new StringBuilder();
        appendCsvRow(result, headers);
        rows.forEach(row -> appendCsvRow(result, row));
        return result.toString();
    }

    private static void appendCsvRow(StringBuilder result, List<String> values) {
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                result.append(',');
            }
            result.append(csvField(values.get(index)));
        }
        result.append("\r\n");
    }

    private static String csvField(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static DumpResult writeJson(MinecraftServer server, String baseName, JsonObject json) throws IOException {
        return writeText(server, baseName, "json", GSON.toJson(json));
    }

    private static DumpResult writeText(MinecraftServer server, String baseName, String extension, String content)
            throws IOException {
        return writeBytes(server, baseName, extension, content.getBytes(StandardCharsets.UTF_8));
    }

    private static DumpResult writeBytes(MinecraftServer server, String baseName, String extension, byte[] content)
            throws IOException {
        Path directory = server.getServerDirectory().resolve("inspectmc").resolve("dumps");
        Files.createDirectories(directory);
        String timestamp = FILE_TIME.format(LocalDateTime.now());
        Path archive = directory.resolve(baseName + "-" + timestamp + "." + extension);
        int collision = 2;
        while (Files.exists(archive)) {
            archive = directory.resolve(baseName + "-" + timestamp + "-" + collision++ + "." + extension);
        }
        Files.write(archive, content, StandardOpenOption.CREATE_NEW);

        Path latest = directory.resolve(baseName + "-latest." + extension);
        try {
            writeLatest(directory, latest, content);
        } catch (IOException exception) {
            InspectMC.LOGGER.warn("Dump written to {}, but latest alias {} could not be updated", archive, latest, exception);
            latest = null;
        }
        cleanupArchives(directory, baseName, extension, InspectMCConfig.forServer(server).maxDumpArchives());
        return DumpResult.success(archive.toAbsolutePath().normalize(),
                latest == null ? null : latest.toAbsolutePath().normalize());
    }

    private static void cleanupArchives(Path directory, String baseName, String extension, int maximum) {
        if (maximum <= 0) {
            return;
        }
        String prefix = baseName + "-";
        String suffix = "." + extension;
        try (var paths = Files.list(directory)) {
            List<Path> archives = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.startsWith(prefix) && name.endsWith(suffix)
                                && !name.equals(baseName + "-latest." + extension);
                    })
                    .sorted(Comparator.comparingLong(DumpService::lastModified).reversed())
                    .toList();
            for (Path archive : archives.stream().skip(maximum).toList()) {
                Files.deleteIfExists(archive);
            }
        } catch (IOException exception) {
            InspectMC.LOGGER.warn("Could not apply dump archive retention in {}", directory, exception);
        }
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException exception) {
            return Long.MIN_VALUE;
        }
    }

    private static void writeLatest(Path directory, Path latest, byte[] content) throws IOException {
        Path temporary = directory.resolve("." + latest.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            Files.write(temporary, content, StandardOpenOption.CREATE_NEW);
            try {
                Files.move(temporary, latest, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, latest, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void zipText(ZipOutputStream zip, String name, String content) throws IOException {
        zipBytes(zip, name, content.getBytes(StandardCharsets.UTF_8));
    }

    private static void zipBytes(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }

    private static String safeName(String input) {
        return input.replace(':', '_').replace('/', '_').replace('\\', '_');
    }

    private record BiomeTagEntry(String id, List<String> tags) {
    }

    private record TagEntry(String id, List<String> values) {
    }

    private record RecipeEntry(String id, String providedBy, String type, String group,
                               String resultId, int resultCount, String resultComponents,
                               List<List<String>> ingredients) {
    }

    public record DumpResult(boolean success, String message, Path path, Path latestPath) {
        public static DumpResult success(Path path, Path latestPath) {
            return new DumpResult(true, "Dump written", path, latestPath);
        }

        public static DumpResult notFound(String message) {
            return new DumpResult(false, message, null, null);
        }
    }
}
