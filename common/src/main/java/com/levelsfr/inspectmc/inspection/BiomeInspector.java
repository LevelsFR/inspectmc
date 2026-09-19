package com.levelsfr.inspectmc.inspection;

import com.levelsfr.inspectmc.util.TextUtil;
import com.levelsfr.inspectmc.util.IdentifierCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.Music;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.GenerationStep;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

public final class BiomeInspector {
    private BiomeInspector() {
    }

    public static List<Component> inspect(ServerLevel level, BlockPos pos) {
        Holder<Biome> biomeHolder = level.getBiome(pos);
        Biome biome = biomeHolder.value();
        BiomeSpecialEffects effects = biome.getSpecialEffects();
        List<Component> lines = new ArrayList<>();
        String biomeId = biomeHolder.unwrapKey().map(IdentifierCompat::resourceKeyId).orElse("<direct>");
        List<String> tags = biomeHolder.tags().map(IdentifierCompat::resourceKeyId).toList();

        lines.add(TextUtil.section("Identity"));
        biomeHolder.unwrapKey().ifPresentOrElse(key -> {
                    lines.add(TextUtil.resourceLine("Registry ID", IdentifierCompat.resourceKeyId(key), "worldgen/biome"));
                    InspectionOrigin.append(lines, IdentifierCompat.resourceKeyId(key));
                },
                () -> lines.add(TextUtil.line("Registry ID", "<direct>")));
        lines.add(TextUtil.positionLine("Position", pos, IdentifierCompat.resourceKeyId(level.dimension())));
        lines.add(TextUtil.listLine("Tags", tags));

        lines.add(TextUtil.section("Climate"));
        lines.add(TextUtil.line("Temperature", biome.getBaseTemperature()));
        lines.add(TextUtil.line("Precipitation", biome.getPrecipitationAt(pos).getSerializedName()));

        lines.add(TextUtil.section("Colors"));
        lines.add(TextUtil.colorLine("Sky", effects.getSkyColor()));
        lines.add(TextUtil.colorLine("Fog", effects.getFogColor()));
        lines.add(TextUtil.colorLine("Water", effects.getWaterColor()));
        lines.add(TextUtil.colorLine("Grass", biome.getGrassColor(pos.getX(), pos.getZ())));
        lines.add(TextUtil.colorLine("Foliage", biome.getFoliageColor()));

        lines.add(TextUtil.actionRow(
                TextUtil.copyAction("[Copy ID]", biomeId, "Copy the biome ID"),
                TextUtil.copyAction("[Copy tags]", TextUtil.join(tags), "Copy all tags"),
                TextUtil.suggestAction("[Details]", "/inspectmc inspect biome details",
                        "Show ambient effects, mob spawning and world generation")
        ));
        return lines;
    }

    public static List<Component> inspectDetails(ServerLevel level, BlockPos pos) {
        Holder<Biome> biomeHolder = level.getBiome(pos);
        Biome biome = biomeHolder.value();
        BiomeSpecialEffects effects = biome.getSpecialEffects();
        List<Component> lines = new ArrayList<>();
        String biomeId = biomeHolder.unwrapKey().map(IdentifierCompat::resourceKeyId).orElse("<direct>");
        List<String> tags = biomeHolder.tags().map(IdentifierCompat::resourceKeyId).toList();

        lines.add(TextUtil.section("Identity"));
        biomeHolder.unwrapKey().ifPresentOrElse(
                key -> {
                    lines.add(TextUtil.resourceLine("Registry ID", IdentifierCompat.resourceKeyId(key), "worldgen/biome"));
                    InspectionOrigin.append(lines, IdentifierCompat.resourceKeyId(key));
                },
                () -> lines.add(TextUtil.line("Registry ID", "<direct>")));
        lines.add(TextUtil.positionLine("Position", pos, IdentifierCompat.resourceKeyId(level.dimension())));
        lines.add(TextUtil.resourceLine("Dimension", IdentifierCompat.resourceKeyId(level.dimension()), "dimension"));
        lines.add(TextUtil.listLine("Tags", tags));

        lines.add(TextUtil.section("Climate"));
        lines.add(TextUtil.line("  Base temperature", biome.getBaseTemperature()));
        lines.add(TextUtil.line("  Has precipitation", biome.hasPrecipitation()));
        lines.add(TextUtil.line("  Precipitation at position", biome.getPrecipitationAt(pos).getSerializedName()));

        lines.add(TextUtil.section("Visual effects"));
        lines.add(TextUtil.colorLine("  Sky color", effects.getSkyColor()));
        lines.add(TextUtil.colorLine("  Fog color", effects.getFogColor()));
        lines.add(TextUtil.colorLine("  Water color", effects.getWaterColor()));
        lines.add(TextUtil.colorLine("  Water fog color", effects.getWaterFogColor()));
        lines.add(TextUtil.colorLine("  Effective grass color", biome.getGrassColor(pos.getX(), pos.getZ())));
        effects.getGrassColorOverride().ifPresentOrElse(
                color -> lines.add(TextUtil.colorLine("  Grass color override", color)),
                () -> lines.add(TextUtil.line("  Grass color override", "<none>")));
        lines.add(TextUtil.line("  Grass color modifier", effects.getGrassColorModifier().getName()));
        lines.add(TextUtil.colorLine("  Effective foliage color", biome.getFoliageColor()));
        effects.getFoliageColorOverride().ifPresentOrElse(
                color -> lines.add(TextUtil.colorLine("  Foliage color override", color)),
                () -> lines.add(TextUtil.line("  Foliage color override", "<none>")));

        lines.add(TextUtil.section("Ambient effects"));
        effects.getAmbientParticleSettings().ifPresentOrElse(particle ->
                        lines.add(TextUtil.resourceLine("  Particle", IdentifierCompat.value(BuiltInRegistries.PARTICLE_TYPE.getKey(particle.getOptions().getType())), "particle_type")),
                () -> lines.add(TextUtil.line("  Particle", "<none>")));
        effects.getAmbientLoopSoundEvent().ifPresentOrElse(
                sound -> lines.add(TextUtil.resourceLine("  Loop sound", IdentifierCompat.value(sound.value().getLocation()), "sound_event")),
                () -> lines.add(TextUtil.line("  Loop sound", "<none>")));
        appendMood(lines, effects.getAmbientMoodSettings().orElse(null));
        appendAdditions(lines, effects.getAmbientAdditionsSettings().orElse(null));
        appendMusic(lines, effects.getBackgroundMusic().orElse(null));

        appendMobSpawns(lines, biome.getMobSettings());
        appendGeneration(lines, biome.getGenerationSettings());
        lines.add(TextUtil.section("Actions"));
        lines.add(TextUtil.actionRow(
                TextUtil.copyAction("[Copy ID]", biomeId, "Copy the biome ID"),
                TextUtil.copyAction("[Copy tags]", TextUtil.join(tags), "Copy all tags"),
                TextUtil.suggestAction("[Compact]", "/inspectmc inspect biome", "Return to the compact biome view")
        ));
        return lines;
    }

    private static void appendMood(List<Component> lines, Object mood) {
        if (mood == null) {
            lines.add(TextUtil.line("  Mood sound", "<none>"));
            return;
        }
        lines.add(TextUtil.line("  Mood settings", mood));
    }

    private static void appendAdditions(List<Component> lines, Object additions) {
        if (additions == null) {
            lines.add(TextUtil.line("  Additions sound", "<none>"));
            return;
        }
        lines.add(TextUtil.line("  Additions settings", additions));
    }

    private static void appendMusic(List<Component> lines, Music music) {
        if (music == null) {
            lines.add(TextUtil.line("  Music", "<none>"));
            return;
        }
        lines.add(TextUtil.resourceLine("  Music", IdentifierCompat.value(music.getEvent().value().getLocation()), "sound_event"));
        lines.add(TextUtil.line("    Delay", music.getMinDelay() + " - " + music.getMaxDelay() + " ticks"));
        lines.add(TextUtil.line("    Replaces current", music.replaceCurrentMusic()));
    }

    private static void appendMobSpawns(List<Component> lines, MobSpawnSettings settings) {
        lines.add(TextUtil.section("Mob spawning"));
        lines.add(TextUtil.line("  Creature probability", settings.getCreatureProbability()));
        int total = 0;
        int shown = 0;
        for (MobCategory category : MobCategory.values()) {
            List<MobSpawnSettings.SpawnerData> spawns = settings.getMobs(category).unwrap();
            if (spawns.isEmpty()) {
                continue;
            }
            total += spawns.size();
            lines.add(TextUtil.line("  " + category.getName(), spawns.size() + " entries"));
            for (MobSpawnSettings.SpawnerData spawn : spawns) {
                if (shown >= 20) {
                    continue;
                }
                shown++;
                lines.add(TextUtil.resourceLine("    Entity", IdentifierCompat.value(BuiltInRegistries.ENTITY_TYPE.getKey(spawn.type)), "entity_type"));
                lines.add(TextUtil.line("      Spawn", "weight " + spawn.getWeight().asInt()
                        + ", group " + spawn.minCount + "-" + spawn.maxCount));
            }
        }
        lines.add(TextUtil.line("  Total spawn entries", total));
        if (total > shown) {
            lines.add(TextUtil.hint("Showing " + shown + " spawn entries out of " + total + "."));
        }
    }

    private static void appendGeneration(List<Component> lines, BiomeGenerationSettings settings) {
        lines.add(TextUtil.section("World generation"));
        List<GenerationStep.Decoration> steps = List.of(GenerationStep.Decoration.values());
        List<? extends net.minecraft.core.HolderSet<?>> features = settings.features();
        int totalFeatures = 0;
        for (int index = 0; index < features.size(); index++) {
            int count = features.get(index).size();
            totalFeatures += count;
            if (count > 0) {
                String step = index < steps.size() ? steps.get(index).getName() : "step_" + index;
                lines.add(TextUtil.line("  " + step, count + " placed feature sets"));
            }
        }
        lines.add(TextUtil.line("  Total placed feature sets", totalFeatures));
        for (GenerationStep.Carving carving : GenerationStep.Carving.values()) {
            long count = StreamSupport.stream(settings.getCarvers(carving).spliterator(), false).count();
            lines.add(TextUtil.line("  " + carving.getName() + " carvers", count));
        }
        lines.add(TextUtil.line("  Flower features", settings.getFlowerFeatures().size()));
    }
}
