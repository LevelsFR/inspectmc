package com.levelsfr.inspectmc.dump;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DumpServiceTest {
    @Test
    void invertsRuntimeBiomeTagRelationsDeterministically() {
        List<DumpService.BiomeTagEntry> result = DumpService.biomeTagsFromRelations(
                List.of("mod:crystal_valley", "minecraft:plains", "minecraft:the_void"),
                List.of(
                        new DumpService.TagEntry("minecraft:is_overworld",
                                List.of("minecraft:plains", "minecraft:plains")),
                        new DumpService.TagEntry("mod:featured", List.of("mod:crystal_valley", "unknown:biome")),
                        new DumpService.TagEntry("minecraft:is_overworld", List.of("minecraft:plains"))));

        assertEquals(List.of("minecraft:is_overworld"), result.get(0).tags());
        assertEquals(List.of(), result.get(1).tags());
        assertEquals(List.of("mod:featured"), result.get(2).tags());
        assertEquals("minecraft:plains", result.get(0).id());
    }
}
