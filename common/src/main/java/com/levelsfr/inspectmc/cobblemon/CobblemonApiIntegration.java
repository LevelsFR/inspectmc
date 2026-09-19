package com.levelsfr.inspectmc.cobblemon;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.ourstory.oscobblemon.entity.PokemonEntities;
import com.ourstory.oscobblemon.pokedex.PokedexQueries;
import com.ourstory.oscobblemon.pokemon.PokemonAbilities;
import com.ourstory.oscobblemon.pokemon.PokemonAbilityKind;
import com.ourstory.oscobblemon.pokemon.PokemonIdentity;
import com.ourstory.oscobblemon.pokemon.PokemonIdentitySnapshot;
import com.ourstory.oscobblemon.pokemon.PokemonIVs;
import com.ourstory.oscobblemon.pokemon.PokemonLabels;
import com.ourstory.oscobblemon.pokemon.PokemonMatcher;
import com.ourstory.oscobblemon.pokemon.PokemonSize;
import com.ourstory.oscobblemon.pokemon.PokemonTypes;
import com.ourstory.oscobblemon.storage.PokemonStorage;
import com.levelsfr.inspectmc.util.TextUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** API-bearing integration; load only after optional dependency checks. */
final class CobblemonApiIntegration {
    private CobblemonApiIntegration() {
    }

    static List<Component> inspectEntity(Entity entity) {
        if (!(entity instanceof PokemonEntity pokemonEntity)) {
            return List.of(Component.literal("The selected entity is not a Cobblemon Pokémon.")
                    .withStyle(ChatFormatting.RED));
        }

        Pokemon pokemon = PokemonEntities.pokemon(pokemonEntity);
        PokemonIdentitySnapshot identity = PokemonEntities.identity(pokemonEntity);
        List<Component> lines = new ArrayList<>();
        lines.add(TextUtil.section("Cobblemon"));
        lines.add(TextUtil.line("Species", identity.speciesId()));
        lines.add(TextUtil.line("Form", identity.standardForm() ? "<standard>" : identity.formId()));
        lines.add(TextUtil.line("Pokémon UUID", identity.uuid()));
        lines.add(TextUtil.boolLine("Shiny", identity.shiny()));
        lines.add(TextUtil.boolLine("Alpha", identity.alpha()));
        lines.add(TextUtil.listLine("Types", PokemonTypes.typeNames(pokemon)));
        lines.add(TextUtil.listLine("Aspects", PokemonIdentity.aspects(pokemon)));
        lines.add(TextUtil.listLine("Labels", PokemonLabels.labels(pokemon)));
        lines.add(TextUtil.listLine("Marks", PokemonIdentity.markIds(pokemon).stream()
                .map(Object::toString).toList()));

        PokemonAbilityKind abilityKind = PokemonAbilities.kind(pokemon);
        lines.add(TextUtil.line("Ability category", abilityKind));
        lines.add(TextUtil.boolLine("Can have hidden ability", PokemonAbilities.canHaveHiddenAbility(pokemon)));
        lines.add(TextUtil.line("Size category", PokemonSize.category(pokemon)));
        lines.add(TextUtil.line("Size translation key", PokemonSize.translationKey(pokemon)));
        lines.add(TextUtil.line("Scale modifier", format(PokemonSize.scaleModifier(pokemon))));
        lines.add(TextUtil.line("Effective scale", format(PokemonSize.effectiveScale(pokemon))));

        lines.add(TextUtil.section("Entity state"));
        lines.add(TextUtil.line("Owner UUID", PokemonEntities.ownerId(pokemonEntity).map(UUID::toString).orElse("<none>")));
        lines.add(TextUtil.boolLine("Battling", PokemonEntities.isBattling(pokemonEntity)));
        lines.add(TextUtil.boolLine("Busy", PokemonEntities.isBusy(pokemonEntity)));
        lines.add(TextUtil.boolLine("Evolving", PokemonEntities.isEvolving(pokemonEntity)));
        lines.add(TextUtil.boolLine("Alive and present", PokemonEntities.isAliveAndPresent(pokemonEntity)));

        lines.add(TextUtil.section("IVs"));
        lines.add(TextUtil.line("Natural total", PokemonIVs.naturalTotal(pokemon)));
        lines.add(TextUtil.line("Effective total", PokemonIVs.effectiveTotal(pokemon)));
        lines.add(TextUtil.line("Natural perfect", PokemonIVs.perfectNaturalCount(pokemon)));
        lines.add(TextUtil.line("Effective perfect", PokemonIVs.perfectEffectiveCount(pokemon)));
        lines.add(TextUtil.listLine("Natural values", formatStats(PokemonIVs.naturalSnapshot(pokemon))));
        lines.add(TextUtil.listLine("Effective values", formatStats(PokemonIVs.effectiveSnapshot(pokemon))));
        lines.add(TextUtil.actionRow(
                com.levelsfr.inspectmc.util.TextUtil.copyAction("[Copy species]", identity.speciesId(),
                        "Copy the canonical Cobblemon species ID"),
                com.levelsfr.inspectmc.util.TextUtil.copyAction("[Copy Pokémon UUID]", identity.uuid().toString(),
                        "Copy the stable Pokémon UUID")
        ));
        return List.copyOf(lines);
    }

    static List<Component> inspectPlayer(ServerPlayer player) {
        List<Pokemon> party = PokemonStorage.partySnapshot(player);
        int owned = PokemonStorage.countOwned(player, value -> true);
        int pc = Math.max(0, owned - party.size());

        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Cobblemon player diagnostics")
                .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
        lines.add(TextUtil.section("Storage"));
        lines.add(TextUtil.line("Player", player.getGameProfile().getName()));
        lines.add(TextUtil.line("Party Pokémon", party.size()));
        lines.add(TextUtil.line("PC Pokémon", pc));
        lines.add(TextUtil.line("Owned Pokémon", owned));

        lines.add(TextUtil.section("Pokédex"));
        lines.add(TextUtil.line("Seen species", PokedexQueries.seenCount(player)));
        lines.add(TextUtil.line("Caught species", PokedexQueries.caughtCount(player)));
        lines.add(TextUtil.line("Seen progress", percent(PokedexQueries.seenPercent(player))));
        lines.add(TextUtil.line("Caught progress", percent(PokedexQueries.caughtPercent(player))));

        lines.add(TextUtil.section("Party"));
        if (party.isEmpty()) {
            lines.add(TextUtil.hint("The party is empty."));
        } else {
            for (int index = 0; index < party.size(); index++) {
                PokemonIdentitySnapshot identity = PokemonIdentity.snapshot(party.get(index));
                lines.add(TextUtil.line("Slot " + (index + 1), partyEntry(identity)));
            }
        }
        lines.add(TextUtil.hint("PC contents are summarized to keep the command safe for large storage boxes."));
        return List.copyOf(lines);
    }

    static List<Component> findOwned(ServerPlayer player, String query) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.isEmpty()) {
            return List.of(Component.literal("A Cobblemon property query is required, for example: species=pikachu shiny=true")
                    .withStyle(ChatFormatting.RED));
        }

        PokemonMatcher matcher = PokemonMatcher.fromProperties(normalized);
        List<Pokemon> matches = PokemonStorage.findAllOwned(player, matcher);
        List<Component> lines = new ArrayList<>();
        lines.add(TextUtil.section("Cobblemon storage search"));
        lines.add(TextUtil.line("Query", normalized));
        lines.add(TextUtil.line("Matches", matches.size()));

        if (matches.isEmpty()) {
            lines.add(TextUtil.hint("No owned Pokémon matched this query."));
            return List.copyOf(lines);
        }

        lines.add(TextUtil.section("Matches"));
        int shown = Math.min(20, matches.size());
        for (int index = 0; index < shown; index++) {
            Pokemon pokemon = matches.get(index);
            PokemonIdentitySnapshot identity = PokemonIdentity.snapshot(pokemon);
            String location = PokemonStorage.isInParty(player, identity.uuid())
                    ? "Party" : "PC";
            lines.add(TextUtil.line("#" + (index + 1) + " " + location, partyEntry(identity)));
        }
        if (matches.size() > shown) {
            lines.add(TextUtil.hint((matches.size() - shown)
                    + " additional matches were not displayed."));
        }
        return List.copyOf(lines);
    }

    private static List<String> formatStats(Map<?, Integer> stats) {
        return stats.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toList();
    }

    private static String partyEntry(PokemonIdentitySnapshot identity) {
        String form = identity.standardForm() ? "" : " form=" + identity.formId();
        return identity.speciesId() + form
                + " • " + (identity.shiny() ? "shiny" : "normal")
                + " • " + (identity.alpha() ? "alpha" : "regular")
                + " • " + identity.uuid();
    }

    private static String percent(float value) {
        return String.format(Locale.ROOT, "%.1f%%", value);
    }

    private static String format(float value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
