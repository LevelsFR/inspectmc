package com.levelsfr.inspectmc.client.config;

import com.levelsfr.inspectmc.config.InspectMCConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;

public final class InspectMCClothConfigScreen {
    private InspectMCClothConfigScreen() {
    }

    public static Screen create(Screen parent) {
        Path gameDirectory = Minecraft.getInstance().gameDirectory.toPath();
        InspectMCConfig config = InspectMCConfig.forDirectory(gameDirectory);
        config.normalize();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("InspectMC configuration"))
                .setDoesConfirmSave(true)
                .setShouldTabsSmoothScroll(true)
                .setShouldListSmoothScroll(true);
        ConfigEntryBuilder entries = builder.entryBuilder();

        addAccessCategory(builder, entries, config);
        addInspectionCategory(builder, entries, config);
        addOutputCategory(builder, entries, config);

        builder.setSavingRunnable(() -> config.save(gameDirectory));
        return builder.build();
    }

    private static void addAccessCategory(ConfigBuilder builder, ConfigEntryBuilder entries, InspectMCConfig config) {
        ConfigCategory category = builder.getOrCreateCategory(Component.literal("Access"));
        category.setDescription(new Component[]{Component.literal(
                "These settings affect the logical server. On a dedicated server, edit its config file directly.")});
        category.addEntry(entries.startIntSlider(Component.literal("Operator permission level"),
                        config.operatorPermissionLevel, 0, 4)
                .setDefaultValue(2)
                .setSaveConsumer(value -> config.operatorPermissionLevel = value)
                .build());
    }

    private static void addInspectionCategory(ConfigBuilder builder, ConfigEntryBuilder entries, InspectMCConfig config) {
        ConfigCategory category = builder.getOrCreateCategory(Component.literal("Inspection defaults"));
        category.addEntry(entries.startIntSlider(Component.literal("Default block distance"),
                        config.defaultBlockDistance, 1, 128)
                .setDefaultValue(20)
                .setSaveConsumer(value -> config.defaultBlockDistance = value)
                .build());
        category.addEntry(entries.startIntSlider(Component.literal("Default entity distance"),
                        config.defaultEntityDistance, 1, 128)
                .setDefaultValue(20)
                .setSaveConsumer(value -> config.defaultEntityDistance = value)
                .build());
        category.addEntry(entries.startIntSlider(Component.literal("Default locate results"),
                        config.defaultLocateResults, 1, 20)
                .setDefaultValue(8)
                .setSaveConsumer(value -> config.defaultLocateResults = value)
                .build());
    }

    private static void addOutputCategory(ConfigBuilder builder, ConfigEntryBuilder entries, InspectMCConfig config) {
        ConfigCategory category = builder.getOrCreateCategory(Component.literal("Output"));
        category.addEntry(entries.startIntSlider(Component.literal("Search results per page"),
                        config.searchPageSize, 1, 100)
                .setDefaultValue(30)
                .setSaveConsumer(value -> config.searchPageSize = value)
                .build());
        category.addEntry(entries.startIntSlider(Component.literal("List results per page"),
                        config.listPageSize, 1, 100)
                .setDefaultValue(50)
                .setSaveConsumer(value -> config.listPageSize = value)
                .build());
        category.addEntry(entries.startIntSlider(Component.literal("Loaded entity types per page"),
                        config.loadedEntityPageSize, 1, 100)
                .setDefaultValue(25)
                .setSaveConsumer(value -> config.loadedEntityPageSize = value)
                .build());
        category.addEntry(entries.startIntSlider(Component.literal("Maximum dump archives"),
                        config.maxDumpArchives, 0, 500)
                .setDefaultValue(0)
                .setSaveConsumer(value -> config.maxDumpArchives = value)
                .build());
    }
}
