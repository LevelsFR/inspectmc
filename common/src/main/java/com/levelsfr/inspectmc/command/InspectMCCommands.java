package com.levelsfr.inspectmc.command;

import com.levelsfr.inspectmc.cobblemon.CobblemonSpawnInspector;
import com.levelsfr.inspectmc.cobblemon.CobblemonApiPresence;
import com.levelsfr.inspectmc.cobblemon.CobblemonOptionalBridge;
import com.levelsfr.inspectmc.config.InspectMCConfig;
import com.levelsfr.inspectmc.dump.DumpService;
import com.levelsfr.inspectmc.inspection.BiomeInspector;
import com.levelsfr.inspectmc.inspection.BlockEntityInspector;
import com.levelsfr.inspectmc.inspection.BlockInspector;
import com.levelsfr.inspectmc.inspection.EntityInspector;
import com.levelsfr.inspectmc.inspection.ItemInspector;
import com.levelsfr.inspectmc.inspection.PlayerInspector;
import com.levelsfr.inspectmc.inspection.RegistrySearch;
import com.levelsfr.inspectmc.locate.WorldLocator;
import com.levelsfr.inspectmc.output.CommandOutput;
import com.levelsfr.inspectmc.output.ChunkMapRenderer;
import com.levelsfr.inspectmc.pack.PackInspector;
import com.levelsfr.inspectmc.runtime.LoadedInspector;
import com.levelsfr.inspectmc.stats.WorldStats;
import com.levelsfr.inspectmc.util.TextUtil;
import com.levelsfr.inspectmc.util.IdentifierCompat;
import com.levelsfr.inspectmc.util.MinecraftCompat;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class InspectMCCommands {
    private InspectMCCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("inspectmc")
                .executes(ctx -> help(ctx.getSource()))
                .then(hand())
                .then(inspect(buildContext))
                .then(search())
                .then(locate(buildContext))
                .then(loaded())
                .then(stats());
        if (CobblemonApiPresence.isCobblemonInstalled()) {
            root.then(spawn()).then(cobblemon());
        }
        root.then(dump()).then(pack());
        dispatcher.register(root);
    }

    private static boolean hasOperatorPermission(CommandSourceStack source) {
        return MinecraftCompat.hasPermission(source, InspectMCConfig.forServer(source.getServer()).operatorPermissionLevel());
    }

    private static int defaultBlockDistance(CommandSourceStack source) {
        return InspectMCConfig.forServer(source.getServer()).defaultBlockDistance();
    }

    private static int defaultEntityDistance(CommandSourceStack source) {
        return InspectMCConfig.forServer(source.getServer()).defaultEntityDistance();
    }

    private static int defaultLocateResults(CommandSourceStack source) {
        return InspectMCConfig.forServer(source.getServer()).defaultLocateResults();
    }

    private static LiteralArgumentBuilder<CommandSourceStack> search() {
        LiteralArgumentBuilder<CommandSourceStack> search = Commands.literal("search");
        search.then(Commands.argument("query", StringArgumentType.greedyString())
                .executes(ctx -> CommandOutput.send(ctx.getSource(), "Registry search",
                        RegistrySearch.search(ctx.getSource().getServer(),
                                StringArgumentType.getString(ctx, "query")))));
        search.then(Commands.literal("page")
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .then(Commands.argument("query", StringArgumentType.greedyString())
                                .executes(ctx -> CommandOutput.send(ctx.getSource(), "Registry search",
                                        RegistrySearch.search(ctx.getSource().getServer(),
                                                StringArgumentType.getString(ctx, "query"),
                                                IntegerArgumentType.getInteger(ctx, "page")))))));
        return search;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> hand() {
        return Commands.literal("hand")
                .executes(ctx -> inspectHand(ctx.getSource(), false))
                .then(Commands.literal("full")
                        .executes(ctx -> inspectHand(ctx.getSource(), true)));
    }

    private static int inspectHand(CommandSourceStack source, boolean full)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return CommandOutput.send(source, "Item in main hand",
                full ? ItemInspector.inspectHand(player.getMainHandItem())
                        : ItemInspector.inspectHandSummary(player.getMainHandItem()));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> inspect(CommandBuildContext buildContext) {
        return Commands.literal("inspect")
                .then(Commands.literal("held")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            return CommandOutput.send(ctx.getSource(), "Held item", ItemInspector.inspect(player.getMainHandItem()));
                        })
                        .then(Commands.literal("details").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            return CommandOutput.send(ctx.getSource(), "Held item details", ItemInspector.inspectDetails(player.getMainHandItem()));
                        })))
                .then(Commands.literal("block")
                        .executes(ctx -> inspectBlockTarget(ctx.getSource(), defaultBlockDistance(ctx.getSource())))
                        .then(Commands.argument("distance", IntegerArgumentType.integer(1, 128))
                                .executes(ctx -> inspectBlockTarget(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "distance"))))
                        .then(Commands.literal("target")
                                .executes(ctx -> inspectBlockTarget(ctx.getSource(), defaultBlockDistance(ctx.getSource())))
                                .then(Commands.argument("distance", IntegerArgumentType.integer(1, 128))
                                        .executes(ctx -> inspectBlockTarget(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "distance")))))
                        .then(Commands.literal("id")
                                .then(Commands.argument("block_id", ResourceArgument.resource(buildContext, Registries.BLOCK))
                                        .executes(ctx -> inspectBlockType(ctx.getSource(),
                                                ResourceArgument.getResource(ctx, "block_id", Registries.BLOCK)))
                                        .then(Commands.argument("distance", IntegerArgumentType.integer(1, 64))
                                                .requires(InspectMCCommands::hasOperatorPermission)
                                                .executes(ctx -> inspectBlockInWorld(
                                                        ctx.getSource(),
                                                        ResourceArgument.getResource(ctx, "block_id", Registries.BLOCK),
                                                        IntegerArgumentType.getInteger(ctx, "distance")))))))
                .then(Commands.literal("entity").requires(InspectMCCommands::hasOperatorPermission)
                        .executes(ctx -> inspectLookedAtEntity(ctx.getSource(), defaultEntityDistance(ctx.getSource())))
                        .then(Commands.argument("distance", IntegerArgumentType.integer(1, 128))
                                .executes(ctx -> inspectLookedAtEntity(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "distance"))))
                        .then(Commands.literal("look")
                                .executes(ctx -> inspectLookedAtEntity(ctx.getSource(), defaultEntityDistance(ctx.getSource())))
                                .then(Commands.argument("distance", IntegerArgumentType.integer(1, 128))
                                        .executes(ctx -> inspectLookedAtEntity(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "distance")))))
                        .then(Commands.literal("target")
                                .requires(InspectMCCommands::hasOperatorPermission)
                                .then(Commands.argument("selector", EntityArgument.entity())
                                        .executes(ctx -> inspectRuntimeEntity(ctx.getSource(), EntityArgument.getEntity(ctx, "selector")))))
                        .then(Commands.literal("id")
                                .then(Commands.argument("entity_type", ResourceArgument.resource(buildContext, Registries.ENTITY_TYPE))
                                        .executes(ctx -> inspectEntityType(ctx.getSource(),
                                                ResourceArgument.getResource(ctx, "entity_type", Registries.ENTITY_TYPE)))
                                        .then(Commands.argument("distance", IntegerArgumentType.integer(1, 512))
                                                .requires(InspectMCCommands::hasOperatorPermission)
                                                .executes(ctx -> inspectEntityTypeInWorld(
                                                        ctx.getSource(),
                                                        ResourceArgument.getResource(ctx, "entity_type", Registries.ENTITY_TYPE),
                                                        IntegerArgumentType.getInteger(ctx, "distance")))))))
                .then(Commands.literal("blockentity").requires(InspectMCCommands::hasOperatorPermission)
                        .executes(ctx -> inspectBlockEntityTarget(ctx.getSource(), defaultBlockDistance(ctx.getSource())))
                        .then(Commands.argument("distance", IntegerArgumentType.integer(1, 128))
                                .executes(ctx -> inspectBlockEntityTarget(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "distance")))))
                .then(Commands.literal("biome")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            return CommandOutput.send(ctx.getSource(), "Biome", BiomeInspector.inspect(MinecraftCompat.serverLevel(player), player.blockPosition()));
                        })
                        .then(Commands.literal("details").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            return CommandOutput.send(ctx.getSource(), "Biome details",
                                    BiomeInspector.inspectDetails(MinecraftCompat.serverLevel(player), player.blockPosition()));
                        })))
                .then(Commands.literal("player")
                        .executes(ctx -> inspectPlayer(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(InspectMCCommands::hasOperatorPermission)
                                .executes(ctx -> inspectPlayer(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> locate(CommandBuildContext buildContext) {
        return Commands.literal("locate").requires(InspectMCCommands::hasOperatorPermission)
                .then(Commands.literal("block")
                        .then(Commands.argument("block_id", ResourceArgument.resource(buildContext, Registries.BLOCK))
                                .executes(ctx -> locateBlock(ctx.getSource(), ResourceArgument.getResource(ctx, "block_id", Registries.BLOCK), 32))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 64))
                                        .executes(ctx -> locateBlock(ctx.getSource(), ResourceArgument.getResource(ctx, "block_id", Registries.BLOCK), IntegerArgumentType.getInteger(ctx, "radius"))))))
                .then(Commands.literal("entity")
                        .then(Commands.argument("entity_type", ResourceArgument.resource(buildContext, Registries.ENTITY_TYPE))
                                .executes(ctx -> locateEntity(ctx.getSource(), ResourceArgument.getResource(ctx, "entity_type", Registries.ENTITY_TYPE), 64, defaultLocateResults(ctx.getSource())))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 512))
                                        .executes(ctx -> locateEntity(ctx.getSource(), ResourceArgument.getResource(ctx, "entity_type", Registries.ENTITY_TYPE), IntegerArgumentType.getInteger(ctx, "radius"), defaultLocateResults(ctx.getSource())))
                                        .then(Commands.argument("limit", IntegerArgumentType.integer(1, 20))
                                                .executes(ctx -> locateEntity(ctx.getSource(),
                                                        ResourceArgument.getResource(ctx, "entity_type", Registries.ENTITY_TYPE),
                                                        IntegerArgumentType.getInteger(ctx, "radius"),
                                                        IntegerArgumentType.getInteger(ctx, "limit")))))))
                .then(Commands.literal("blockentity")
                        .then(Commands.argument("block_entity_type", ResourceArgument.resource(buildContext, Registries.BLOCK_ENTITY_TYPE))
                                .executes(ctx -> locateBlockEntity(ctx.getSource(), ResourceArgument.getResource(ctx, "block_entity_type", Registries.BLOCK_ENTITY_TYPE), 64, defaultLocateResults(ctx.getSource())))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 512))
                                        .executes(ctx -> locateBlockEntity(ctx.getSource(), ResourceArgument.getResource(ctx, "block_entity_type", Registries.BLOCK_ENTITY_TYPE), IntegerArgumentType.getInteger(ctx, "radius"), defaultLocateResults(ctx.getSource())))
                                        .then(Commands.argument("limit", IntegerArgumentType.integer(1, 20))
                                                .executes(ctx -> locateBlockEntity(ctx.getSource(),
                                                        ResourceArgument.getResource(ctx, "block_entity_type", Registries.BLOCK_ENTITY_TYPE),
                                                        IntegerArgumentType.getInteger(ctx, "radius"),
                                                        IntegerArgumentType.getInteger(ctx, "limit")))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> loaded() {
        return Commands.literal("loaded").requires(InspectMCCommands::hasOperatorPermission)
                .then(Commands.literal("entities").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return CommandOutput.send(ctx.getSource(), "Loaded entities", LoadedInspector.entities(MinecraftCompat.serverLevel(player)));
                }).then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return CommandOutput.send(ctx.getSource(), "Loaded entities", LoadedInspector.entities(MinecraftCompat.serverLevel(player),
                            IntegerArgumentType.getInteger(ctx, "page")));
                })))
                .then(Commands.literal("dimensions").executes(ctx -> CommandOutput.send(ctx.getSource(),
                        "Loaded dimensions", LoadedInspector.dimensions(ctx.getSource().getServer()))))
                .then(Commands.literal("chunks").executes(ctx -> {
                    return loadedChunks(ctx.getSource(), -1);
                }).then(Commands.argument("map_radius_chunks", IntegerArgumentType.integer(1, 64))
                        .executes(ctx -> loadedChunks(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "map_radius_chunks")))))
                .then(Commands.literal("blockentities").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    LoadedInspector.LoadedChunkSnapshot snapshot = LoadedInspector.chunks(MinecraftCompat.serverLevel(player));
                    return CommandOutput.send(ctx.getSource(), "Loaded block entities", List.of(
                            TextUtil.line("Dimension", IdentifierCompat.resourceKeyId(MinecraftCompat.serverLevel(player).dimension())),
                            TextUtil.line("Block entities in observed chunks", snapshot.blockEntities())
                    ));
                }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> stats() {
        return Commands.literal("stats").requires(InspectMCCommands::hasOperatorPermission)
                .then(Commands.literal("blocks")
                        .executes(ctx -> statsBlocks(ctx.getSource(), 8))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 24))
                                .executes(ctx -> statsBlocks(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius")))))
                .then(Commands.literal("biomes")
                        .executes(ctx -> statsBiomes(ctx.getSource(), 128, 16))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(16, 1024))
                                .executes(ctx -> statsBiomes(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius"), 16))
                                .then(Commands.argument("step", IntegerArgumentType.integer(4, 64))
                                        .executes(ctx -> statsBiomes(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius"), IntegerArgumentType.getInteger(ctx, "step"))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> spawn() {
        return Commands.literal("spawn").requires(InspectMCCommands::hasOperatorPermission)
                .then(Commands.literal("validate")
                        .executes(ctx -> CommandOutput.send(ctx.getSource(), "Cobblemon spawn audit",
                                CobblemonSpawnInspector.validationChat(ctx.getSource().getServer()))))
                .then(Commands.literal("here")
                        .executes(ctx -> CommandOutput.send(ctx.getSource(), "Cobblemon spawns here",
                                CobblemonSpawnInspector.hereChat(ctx.getSource().getPlayerOrException(), 1)))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> CommandOutput.send(ctx.getSource(), "Cobblemon spawns here",
                                        CobblemonSpawnInspector.hereChat(ctx.getSource().getPlayerOrException(),
                                                IntegerArgumentType.getInteger(ctx, "page"))))))
                .then(Commands.literal("explain")
                        .then(Commands.argument("pokemon", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    CobblemonSpawnInspector.pokemonSuggestions(ctx.getSource().getServer())
                                            .forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> CommandOutput.send(ctx.getSource(), "Cobblemon spawn explanation",
                                        CobblemonSpawnInspector.explainChat(ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "pokemon"))))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(ctx -> CommandOutput.send(ctx.getSource(), "Cobblemon spawn explanation",
                                                CobblemonSpawnInspector.explainChat(
                                                        ctx.getSource().getPlayerOrException(),
                                                        StringArgumentType.getString(ctx, "pokemon"),
                                                        IntegerArgumentType.getInteger(ctx, "page")))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> dump() {
        LiteralArgumentBuilder<CommandSourceStack> dump = Commands.literal("dump").requires(InspectMCCommands::hasOperatorPermission)
                .then(biomeDump("biomes"))
                .then(biomeDump("biomes-with-tags"))
                .then(Commands.literal("report")
                        .executes(ctx -> runDump(ctx.getSource(), () -> DumpService.dumpReport(ctx.getSource().getServer()))))
                .then(Commands.literal("registries")
                        .executes(ctx -> runDump(ctx.getSource(), () -> DumpService.dumpRegistryList(ctx.getSource().getServer())))
                        .then(Commands.literal("json").executes(ctx -> runDump(ctx.getSource(), () -> DumpService.dumpRegistryList(ctx.getSource().getServer()))))
                        .then(Commands.literal("csv").executes(ctx -> runDump(ctx.getSource(), () -> DumpService.dumpRegistryListCsv(ctx.getSource().getServer()))))
                        .then(Commands.literal("excel").executes(ctx -> runDump(ctx.getSource(), () -> DumpService.dumpRegistryListXlsx(ctx.getSource().getServer())))))
                .then(Commands.literal("registry")
                        .then(Commands.argument("registry", IdentifierCompat.argumentType())
                                .suggests((ctx, builder) -> {
                                    DumpService.registryIds(ctx.getSource().getServer()).forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> runDump(ctx.getSource(), () -> DumpService.dumpRegistry(ctx.getSource().getServer(), IdentifierCompat.argument(ctx, "registry"))))
                                .then(Commands.literal("json").executes(ctx -> runDump(ctx.getSource(), () -> DumpService.dumpRegistry(ctx.getSource().getServer(), IdentifierCompat.argument(ctx, "registry")))))
                                .then(Commands.literal("csv").executes(ctx -> runDump(ctx.getSource(), () -> DumpService.dumpRegistryCsv(ctx.getSource().getServer(), IdentifierCompat.argument(ctx, "registry")))))
                                .then(Commands.literal("excel").executes(ctx -> runDump(ctx.getSource(), () -> DumpService.dumpRegistryXlsx(ctx.getSource().getServer(), IdentifierCompat.argument(ctx, "registry")))))))
                .then(Commands.literal("tags")
                        .then(Commands.argument("registry", IdentifierCompat.argumentType())
                                .suggests((ctx, builder) -> {
                                    DumpService.registryIds(ctx.getSource().getServer()).forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> runDump(ctx.getSource(), () -> DumpService.dumpTags(ctx.getSource().getServer(), IdentifierCompat.argument(ctx, "registry"))))
                                .then(Commands.literal("json").executes(ctx -> runDump(ctx.getSource(), () -> DumpService.dumpTags(ctx.getSource().getServer(), IdentifierCompat.argument(ctx, "registry")))))
                                .then(Commands.literal("csv").executes(ctx -> runDump(ctx.getSource(), () -> DumpService.dumpTagsCsv(ctx.getSource().getServer(), IdentifierCompat.argument(ctx, "registry")))))
                                .then(Commands.literal("excel").executes(ctx -> runDump(ctx.getSource(), () -> DumpService.dumpTagsXlsx(ctx.getSource().getServer(), IdentifierCompat.argument(ctx, "registry")))))))
                .then(Commands.literal("recipes")
                        .executes(ctx -> runDump(ctx.getSource(), () -> DumpService.dumpRecipes(ctx.getSource().getServer())))
                        .then(Commands.literal("json").executes(ctx -> runDump(ctx.getSource(), () -> DumpService.dumpRecipes(ctx.getSource().getServer()))))
                        .then(Commands.literal("csv").executes(ctx -> runDump(ctx.getSource(), () -> DumpService.dumpRecipesCsv(ctx.getSource().getServer()))))
                        .then(Commands.literal("excel").executes(ctx -> runDump(ctx.getSource(), () -> DumpService.dumpRecipesXlsx(ctx.getSource().getServer())))));
        if (CobblemonApiPresence.isCobblemonInstalled()) {
            dump.then(Commands.literal("spawns")
                        .executes(ctx -> runDump(ctx.getSource(), () -> DumpService.dumpCobblemonSpawnsXlsx(ctx.getSource().getServer())))
                        .then(Commands.literal("excel").executes(ctx -> runDump(ctx.getSource(), () -> DumpService.dumpCobblemonSpawnsXlsx(ctx.getSource().getServer()))))
                        .then(Commands.literal("csv").executes(ctx -> runDump(ctx.getSource(), () -> DumpService.dumpCobblemonSpawnsCsv(ctx.getSource().getServer()))))
                        .then(Commands.literal("json").executes(ctx -> runDump(ctx.getSource(), () -> DumpService.dumpCobblemonSpawnsJson(ctx.getSource().getServer())))));
        }
        return dump;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> biomeDump(String literal) {
        return Commands.literal(literal)
                .executes(ctx -> runDump(ctx.getSource(), () -> DumpService.dumpBiomesWithTagsXlsx(ctx.getSource().getServer())))
                .then(Commands.literal("excel")
                        .executes(ctx -> runDump(ctx.getSource(), () -> DumpService.dumpBiomesWithTagsXlsx(ctx.getSource().getServer()))))
                .then(Commands.literal("csv")
                        .executes(ctx -> runDump(ctx.getSource(), () -> DumpService.dumpBiomesWithTagsCsv(ctx.getSource().getServer()))))
                .then(Commands.literal("json")
                        .executes(ctx -> runDump(ctx.getSource(), () -> DumpService.dumpBiomesWithTagsJson(ctx.getSource().getServer()))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> pack() {
        return Commands.literal("pack").requires(InspectMCCommands::hasOperatorPermission)
                .then(Commands.literal("mods")
                        .executes(ctx -> CommandOutput.send(ctx.getSource(), "Loaded mods",
                                PackInspector.mods(ctx.getSource().getServer())))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> CommandOutput.send(ctx.getSource(), "Loaded mods",
                                        PackInspector.mods(ctx.getSource().getServer(),
                                                IntegerArgumentType.getInteger(ctx, "page"))))))
                .then(Commands.literal("datapacks")
                        .executes(ctx -> CommandOutput.send(ctx.getSource(), "Data packs", PackInspector.datapacks(ctx.getSource())))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> CommandOutput.send(ctx.getSource(), "Data packs",
                                        PackInspector.datapacks(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "page"))))))
                .then(Commands.literal("namespaces")
                        .executes(ctx -> CommandOutput.send(ctx.getSource(), "Runtime namespaces", PackInspector.namespaces(ctx.getSource())))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> CommandOutput.send(ctx.getSource(), "Runtime namespaces",
                                        PackInspector.namespaces(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "page"))))))
                .then(Commands.literal("registries")
                        .executes(ctx -> packRegistries(ctx.getSource(), 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> packRegistries(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "page")))));
    }

    private static int packRegistries(CommandSourceStack source, int requestedPage) {
        List<String> registryIds = DumpService.registryIds(source.getServer());
        int pageSize = InspectMCConfig.forServer(source.getServer()).listPageSize();
        int pages = Math.max(1, (registryIds.size() + pageSize - 1) / pageSize);
        int page = Math.min(Math.max(requestedPage, 1), pages);
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, registryIds.size());
        List<Component> lines = new ArrayList<>();
        lines.add(TextUtil.section("Runtime registries"));
        registryIds.subList(start, end).stream()
                .map(id -> TextUtil.resourceLine("Registry", id, "root registry"))
                .forEach(lines::add);
        if (pages > 1) {
            lines.add(TextUtil.hint("Showing registries " + (start + 1) + "–" + end + " out of " + registryIds.size()
                    + ". Use /inspectmc dump registries for the complete list."));
        }
        lines.add(TextUtil.actionRow(TextUtil.copyAction("[Copy all registry IDs]",
                registryIds.stream().collect(java.util.stream.Collectors.joining(", ")),
                "Copy every runtime registry ID")));
        CommandOutput.addPaging(lines, page, pages,
                "/inspectmc pack registries " + (page - 1),
                "/inspectmc pack registries " + (page + 1));
        return CommandOutput.send(source, "Runtime registries", lines);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> cobblemon() {
        LiteralArgumentBuilder<CommandSourceStack> cobblemon = Commands.literal("cobblemon")
                .then(Commands.literal("status")
                        .executes(ctx -> CommandOutput.send(ctx.getSource(), "Cobblemon integration", List.of(
                                TextUtil.section("Optional integration"),
                                TextUtil.boolLine("Available", CobblemonOptionalBridge.isAvailable()),
                                TextUtil.line("Status", CobblemonApiPresence.availabilityDescription()),
                                TextUtil.hint("InspectMC remains usable without Cobblemon or OS Cobblemon Library.")
                        ))));
        if (!CobblemonApiPresence.isAvailable()) {
            return cobblemon;
        }
        return cobblemon.then(Commands.literal("entity").requires(InspectMCCommands::hasOperatorPermission)
                        .executes(ctx -> inspectLookedAtCobblemon(ctx.getSource(), defaultEntityDistance(ctx.getSource())))
                        .then(Commands.literal("look")
                                .executes(ctx -> inspectLookedAtCobblemon(ctx.getSource(), defaultEntityDistance(ctx.getSource())))
                                .then(Commands.argument("distance", IntegerArgumentType.integer(1, 128))
                                        .executes(ctx -> inspectLookedAtCobblemon(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "distance")))))
                        .then(Commands.literal("target")
                                .then(Commands.argument("selector", EntityArgument.entity())
                                        .executes(ctx -> inspectCobblemonEntity(ctx.getSource(),
                                                EntityArgument.getEntity(ctx, "selector"))))))
                .then(Commands.literal("player")
                        .executes(ctx -> inspectCobblemonPlayer(ctx.getSource(),
                                ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(InspectMCCommands::hasOperatorPermission)
                                .executes(ctx -> inspectCobblemonPlayer(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("find")
                        .then(Commands.argument("query", StringArgumentType.greedyString())
                                .executes(ctx -> findCobblemon(ctx.getSource(),
                                        ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "query"))))
                        .then(Commands.literal("player")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .requires(InspectMCCommands::hasOperatorPermission)
                                        .then(Commands.argument("query", StringArgumentType.greedyString())
                                                .executes(ctx -> findCobblemon(ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                         StringArgumentType.getString(ctx, "query")))))));
    }

    private static int inspectBlockTarget(CommandSourceStack source, int distance) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        HitResult hit = player.pick(distance, 1.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return CommandOutput.failWithActions(source, "No block found in sight within " + distance + " blocks",
                    TextUtil.actionRow(
                            TextUtil.suggestAction("[Try 32]", "/inspectmc inspect block target 32", "Retry with 32 blocks"),
                            TextUtil.suggestAction("[Try 64]", "/inspectmc inspect block target 64", "Retry with 64 blocks")
                    ));
        }
        return CommandOutput.send(source, "Targeted block", BlockInspector.inspect(MinecraftCompat.serverLevel(player), blockHit.getBlockPos()));
    }

    private static int inspectBlockEntityTarget(CommandSourceStack source, int distance)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        HitResult hit = player.pick(distance, 1.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return CommandOutput.failWithActions(source,
                    "No block found in sight within " + distance + " blocks",
                    TextUtil.actionRow(
                            TextUtil.suggestAction("[Try 32]", "/inspectmc inspect blockentity 32",
                                    "Retry with 32 blocks"),
                            TextUtil.suggestAction("[Try 64]", "/inspectmc inspect blockentity 64",
                                    "Retry with 64 blocks")
                    ));
        }

        net.minecraft.world.level.block.entity.BlockEntity blockEntity =
                MinecraftCompat.serverLevel(player).getBlockEntity(blockHit.getBlockPos());
        if (blockEntity == null) {
            return CommandOutput.failWithActions(source, "The targeted block has no block entity",
                    TextUtil.actionRow(TextUtil.suggestAction("[Inspect block]", "/inspectmc inspect block target",
                            "Inspect the regular block instead")));
        }
        return CommandOutput.send(source, "Targeted block entity",
                BlockEntityInspector.inspect(MinecraftCompat.serverLevel(player), blockEntity));
    }

    private static int inspectBlockType(CommandSourceStack source, Holder.Reference<Block> block) {
        return CommandOutput.send(source, "Registered block", BlockInspector.inspect(block));
    }

    private static int inspectBlockInWorld(CommandSourceStack source, Holder.Reference<Block> block, int distance)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        String id = IdentifierCompat.resourceKeyId(block.key());
        return WorldLocator.block(MinecraftCompat.serverLevel(player), player.blockPosition(), block.value(), distance)
                .map(pos -> CommandOutput.send(source, "World block: " + id,
                        BlockInspector.inspect(MinecraftCompat.serverLevel(player), pos)))
                .orElseGet(() -> CommandOutput.failWithActions(source,
                        id + " not found in loaded chunks within distance " + distance,
                        TextUtil.actionRow(
                                TextUtil.suggestAction("[Try 32]", "/inspectmc inspect block id " + id + " 32", "Search within 32 blocks"),
                                TextUtil.suggestAction("[Try 64]", "/inspectmc inspect block id " + id + " 64", "Search within 64 blocks")
                        )));
    }

    private static int inspectLookedAtEntity(CommandSourceStack source, int distance) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Entity entity = EntityInspector.findLookedAt(player, distance);
        if (entity == null) {
            return CommandOutput.failWithActions(source, "No entity found in sight within " + distance + " blocks",
                    TextUtil.actionRow(
                            TextUtil.suggestAction("[Try 32]", "/inspectmc inspect entity look 32", "Retry with 32 blocks"),
                            TextUtil.suggestAction("[Try 64]", "/inspectmc inspect entity look 64", "Retry with 64 blocks")
                    ));
        }
        return inspectRuntimeEntity(source, entity);
    }

    private static int inspectLookedAtCobblemon(CommandSourceStack source, int distance)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Entity entity = EntityInspector.findLookedAt(player, distance);
        if (entity == null) {
            return CommandOutput.failWithActions(source,
                    "No entity found in sight within " + distance + " blocks",
                    TextUtil.actionRow(TextUtil.suggestAction("[Try 32]",
                            "/inspectmc cobblemon entity look 32", "Retry with 32 blocks")));
        }
        return inspectCobblemonEntity(source, entity);
    }

    private static int inspectCobblemonEntity(CommandSourceStack source, Entity entity) {
        if (!CobblemonOptionalBridge.isAvailable()) {
            return CommandOutput.fail(source, CobblemonApiPresence.availabilityDescription()
                    + ". Install both optional mods to inspect Cobblemon data.");
        }
        if (!CobblemonOptionalBridge.isPokemonEntity(entity)) {
            return CommandOutput.failWithActions(source, "The selected entity is not a Cobblemon Pokémon",
                    TextUtil.actionRow(TextUtil.suggestAction("[Inspect generic entity]",
                            "/inspectmc inspect entity target " + entity.getId(),
                            "Show the regular Minecraft entity diagnostic")));
        }
        return CommandOutput.send(source, "Cobblemon Pokémon", CobblemonOptionalBridge.inspectEntity(entity));
    }

    private static int inspectCobblemonPlayer(CommandSourceStack source, ServerPlayer player) {
        return CommandOutput.send(source, "Cobblemon player diagnostics",
                CobblemonOptionalBridge.inspectPlayer(player));
    }

    private static int findCobblemon(CommandSourceStack source, ServerPlayer player, String query) {
        return CommandOutput.send(source, "Cobblemon storage search",
                CobblemonOptionalBridge.findOwned(player, query));
    }

    private static int inspectRuntimeEntity(CommandSourceStack source, Entity entity) {
        return CommandOutput.send(source, "Runtime entity", EntityInspector.inspect(entity));
    }

    private static int inspectEntityType(CommandSourceStack source, Holder.Reference<EntityType<?>> entityType) {
        return CommandOutput.send(source, "Registered entity type", EntityInspector.inspectType(entityType));
    }

    private static int inspectEntityTypeInWorld(CommandSourceStack source, Holder.Reference<EntityType<?>> entityType, int distance)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        String id = IdentifierCompat.resourceKeyId(entityType.key());
        return WorldLocator.entity(MinecraftCompat.serverLevel(player), player.blockPosition(), entityType.value(), distance)
                .map(entity -> CommandOutput.send(source, "Runtime entity: " + id, EntityInspector.inspect(entity)))
                .orElseGet(() -> CommandOutput.failWithActions(source, id + " not found within distance " + distance,
                        TextUtil.actionRow(
                                TextUtil.suggestAction("[Try 64]", "/inspectmc inspect entity id " + id + " 64", "Search within 64 blocks"),
                                TextUtil.suggestAction("[Try 128]", "/inspectmc inspect entity id " + id + " 128", "Search within 128 blocks")
                        )));
    }

    private static int inspectPlayer(CommandSourceStack source, ServerPlayer player) {
        return CommandOutput.send(source, "Player: " + MinecraftCompat.playerName(player), PlayerInspector.inspect(player));
    }

    private static int locateBlock(CommandSourceStack source, Holder.Reference<Block> block, int radius) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        String id = IdentifierCompat.resourceKeyId(block.key());
        return WorldLocator.block(MinecraftCompat.serverLevel(player), player.blockPosition(), block.value(), radius)
                .map(pos -> CommandOutput.send(source, "Block located", List.of(
                        TextUtil.resourceLine("ID", id, "block"),
                        TextUtil.positionLine("Position", pos, IdentifierCompat.resourceKeyId(MinecraftCompat.serverLevel(player).dimension())))))
                .orElseGet(() -> CommandOutput.fail(source, id + " not found in loaded chunks within radius " + radius));
    }

    private static int locateEntity(CommandSourceStack source, Holder.Reference<EntityType<?>> entityType, int radius, int limit) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        String id = IdentifierCompat.resourceKeyId(entityType.key());
        List<Entity> matches = WorldLocator.entities(MinecraftCompat.serverLevel(player), player.blockPosition(), entityType.value(), radius);
        if (matches.isEmpty()) {
            return CommandOutput.failWithActions(source, id + " not found within radius " + radius,
                    TextUtil.actionRow(
                            TextUtil.suggestAction("[Try 128]", "/inspectmc locate entity " + id + " 128", "Search within 128 blocks"),
                            TextUtil.suggestAction("[Try 256]", "/inspectmc locate entity " + id + " 256", "Search within 256 blocks")
                    ));
        }

        int shown = Math.min(limit, matches.size());
        List<Component> lines = new ArrayList<>();
        lines.add(TextUtil.section("Summary"));
        lines.add(TextUtil.resourceLine("Entity type", id, "entity_type"));
        lines.add(TextUtil.line("Radius", radius));
        lines.add(TextUtil.line("Matches", matches.size()));
        lines.add(TextUtil.line("Exact positions shown", shown));
        lines.add(TextUtil.section("Nearest entities"));
        for (int index = 0; index < shown; index++) {
            Entity entity = matches.get(index);
            double distance = Math.sqrt(entity.distanceToSqr(
                    player.getX(), player.getY(), player.getZ()));
            String label = "#" + (index + 1) + " " + entity.getName().getString() + " • "
                    + String.format(Locale.ROOT, "%.1f blocks", distance);
            lines.add(TextUtil.positionLine(label, entity.getX(), entity.getY(), entity.getZ(),
                    IdentifierCompat.resourceKeyId(entity.level().dimension())));
        }
        if (matches.size() > shown) {
            lines.add(TextUtil.hint((matches.size() - shown) + " additional matches are grouped on the map."));
            lines.add(TextUtil.actionRow(TextUtil.suggestAction("[Show up to 20]",
                    "/inspectmc locate entity " + id + " " + radius + " 20",
                    "Rerun while showing up to 20 exact positions")));
        }
        lines.addAll(ChunkMapRenderer.entities(player.blockPosition(), matches, radius,
                IdentifierCompat.resourceKeyId(MinecraftCompat.serverLevel(player).dimension())));
        return CommandOutput.send(source, "Entities located", lines);
    }

    private static int locateBlockEntity(CommandSourceStack source, Holder.Reference<BlockEntityType<?>> blockEntityType, int radius, int limit) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        String id = IdentifierCompat.resourceKeyId(blockEntityType.key());
        List<net.minecraft.core.BlockPos> matches = WorldLocator.blockEntities(
                MinecraftCompat.serverLevel(player), player.blockPosition(), blockEntityType.value(), radius);
        if (matches.isEmpty()) {
            return CommandOutput.fail(source, id + " not found in loaded chunks within radius " + radius);
        }

        int shown = Math.min(limit, matches.size());
        List<Component> lines = new ArrayList<>();
        lines.add(TextUtil.section("Summary"));
        lines.add(TextUtil.resourceLine("Block entity type", id, "block_entity_type"));
        lines.add(TextUtil.line("Radius", radius));
        lines.add(TextUtil.line("Matches", matches.size()));
        lines.add(TextUtil.section("Nearest block entities"));
        for (int index = 0; index < shown; index++) {
            net.minecraft.core.BlockPos pos = matches.get(index);
            double distance = Math.sqrt(pos.distSqr(player.blockPosition()));
            lines.add(TextUtil.positionLine("#" + (index + 1) + " • "
                            + String.format(Locale.ROOT, "%.1f blocks", distance),
                    pos, IdentifierCompat.resourceKeyId(MinecraftCompat.serverLevel(player).dimension())));
        }
        if (matches.size() > shown) {
            lines.add(TextUtil.hint((matches.size() - shown) + " additional matches are grouped on the map."));
            lines.add(TextUtil.actionRow(TextUtil.suggestAction("[Show up to 20]",
                    "/inspectmc locate blockentity " + id + " " + radius + " 20",
                    "Rerun while showing up to 20 exact positions")));
        }
        lines.addAll(ChunkMapRenderer.blockEntities(player.blockPosition(), matches, radius,
                IdentifierCompat.resourceKeyId(MinecraftCompat.serverLevel(player).dimension())));
        return CommandOutput.send(source, "Block entities located", lines);
    }

    private static int statsBlocks(CommandSourceStack source, int radius) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return CommandOutput.send(source, "Block statistics", WorldStats.blocks(MinecraftCompat.serverLevel(player), player.blockPosition(), radius));
    }

    private static int loadedChunks(CommandSourceStack source, int requestedMapRadius)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        int mapRadius = requestedMapRadius > 0
                ? requestedMapRadius
                : MinecraftCompat.serverLevel(player).getServer().getPlayerList().getViewDistance();
        LoadedInspector.LoadedChunkSnapshot snapshot = LoadedInspector.chunks(MinecraftCompat.serverLevel(player));
        return CommandOutput.send(source, "Loaded chunks",
                snapshot.toLines(MinecraftCompat.serverLevel(player), player.blockPosition(), mapRadius));
    }

    private static int statsBiomes(CommandSourceStack source, int radius, int step) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return CommandOutput.send(source, "Biome statistics", WorldStats.biomes(MinecraftCompat.serverLevel(player), player.blockPosition(), radius, step));
    }

    private static int runDump(CommandSourceStack source, DumpAction action) {
        try {
            DumpService.DumpResult result = action.run();
            if (!result.success()) {
                return CommandOutput.fail(source, result.message());
            }
            return CommandOutput.send(source, "Dump complete",
                    CommandOutput.dumpResult(result.path(), result.latestPath(), source.getServer().isDedicatedServer()));
        } catch (IOException exception) {
            return CommandOutput.fail(source, "Failed to write dump: " + exception.getMessage());
        }
    }

    private static int help(CommandSourceStack source) {
        List<Component> lines = new ArrayList<>(List.of(
                TextUtil.hint("Click a syntax to prefill it."),
                TextUtil.commandLine("/inspectmc hand [full]", "/inspectmc hand"),
                TextUtil.commandLine("/inspectmc inspect held [details]", "/inspectmc inspect held"),
                TextUtil.commandLine("/inspectmc inspect block [distance|target [distance]|id <block_id> [distance]]",
                        "/inspectmc inspect block target 20"),
                TextUtil.commandLine("/inspectmc inspect blockentity [distance]",
                        "/inspectmc inspect blockentity 20"),
                TextUtil.commandLine("/inspectmc inspect entity [distance|look [distance]|target <selector>|id <entity_type> [distance]]",
                        "/inspectmc inspect entity look 20"),
                TextUtil.commandLine("/inspectmc inspect biome [details]", "/inspectmc inspect biome"),
                TextUtil.commandLine("/inspectmc inspect player [player]", "/inspectmc inspect player"),
                TextUtil.commandLine("/inspectmc search <query> | /inspectmc search page <page> <query>", "/inspectmc search copper"),
                TextUtil.hint("Held item, targeted block, current biome, self inspection and registry search work without operator rights. NBT, world and export tools use the configured operator permission level."),
                TextUtil.commandLine("/inspectmc locate block <block_id> [radius]",
                        "/inspectmc locate block minecraft:stone 32"),
                TextUtil.commandLine("/inspectmc locate entity <entity_type> [radius] [limit]",
                        "/inspectmc locate entity minecraft:cow 64 8"),
                TextUtil.commandLine("/inspectmc locate blockentity <block_entity_type> [radius] [limit]",
                        "/inspectmc locate blockentity minecraft:chest 64 8"),
                TextUtil.commandLine("/inspectmc loaded chunks [map_radius_chunks]", "/inspectmc loaded chunks"),
                TextUtil.commandLine("/inspectmc loaded <entities|blockentities|dimensions>", "/inspectmc loaded entities"),
                TextUtil.commandLine("/inspectmc stats blocks [radius]", "/inspectmc stats blocks 8"),
                TextUtil.commandLine("/inspectmc stats biomes [radius] [step]", "/inspectmc stats biomes 128 16")
        ));
        if (CobblemonApiPresence.isCobblemonInstalled()) {
            lines.add(TextUtil.commandLine("/inspectmc spawn <validate|here [page]|explain <pokemon> [page]>",
                    "/inspectmc spawn validate"));
            if (CobblemonApiPresence.isAvailable()) {
                lines.add(TextUtil.commandLine("/inspectmc cobblemon <status|entity|player|find>",
                        "/inspectmc cobblemon status"));
                lines.add(TextUtil.hint("Cobblemon diagnostics require operator rights for entity and other-player inspection; player diagnostics expose server-side data for the selected player."));
            } else {
                lines.add(TextUtil.commandLine("/inspectmc cobblemon status", "/inspectmc cobblemon status"));
                lines.add(TextUtil.hint("Cobblemon is installed, but the optional OS Cobblemon Library integration is unavailable; API diagnostics are hidden."));
            }
        }
        lines.addAll(List.of(
                TextUtil.commandLine("/inspectmc dump biomes [excel|csv|json]", "/inspectmc dump biomes"),
                TextUtil.commandLine("/inspectmc dump <registry|tags> <registry_id> [json|csv|excel]",
                        "/inspectmc dump tags minecraft:item excel"),
                TextUtil.commandLine("/inspectmc dump recipes [json|csv|excel]", "/inspectmc dump recipes excel")
        ));
        if (CobblemonApiPresence.isCobblemonInstalled()) {
            lines.add(TextUtil.commandLine("/inspectmc dump spawns [json|csv|excel]", "/inspectmc dump spawns excel"));
        }
        lines.addAll(List.of(
                TextUtil.commandLine("/inspectmc dump registries [json|csv|excel]",
                        "/inspectmc dump registries"),
                TextUtil.commandLine("/inspectmc dump report", "/inspectmc dump report"),
                TextUtil.commandLine("/inspectmc pack <mods|datapacks|namespaces|registries> [page]", "/inspectmc pack mods")
        ));
        return CommandOutput.send(source, "Modpack development toolkit", lines);
    }

    @FunctionalInterface
    private interface DumpAction {
        DumpService.DumpResult run() throws IOException;
    }
}
