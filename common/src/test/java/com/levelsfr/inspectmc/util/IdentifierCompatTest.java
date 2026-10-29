package com.levelsfr.inspectmc.util;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IdentifierCompatTest {
    @Test
    void nativeIdentifierArgumentsPreserveNamespacesAndDefaultNamespace() throws Exception {
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        AtomicReference<String> parsed = new AtomicReference<>();
        dispatcher.register(LiteralArgumentBuilder.<Object>literal("registry")
                .then(RequiredArgumentBuilder.argument("id", IdentifierCompat.argumentType())
                        .executes(context -> {
                            parsed.set(IdentifierCompat.argument(context, "id"));
                            return 1;
                        })));

        assertEquals(1, dispatcher.execute("registry cobblemon:pokemon", new Object()));
        assertEquals("cobblemon:pokemon", parsed.get());
        assertEquals(1, dispatcher.execute("registry item", new Object()));
        assertEquals("minecraft:item", parsed.get());
    }
}
