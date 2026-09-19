package com.levelsfr.inspectmc.client;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DumpOpenUtilTest {
    @Test
    void commandEncodesNormalizedAbsolutePathWithoutSpaces() {
        Path input = Path.of("inspectmc", "dumps", "report with spaces.xlsx");
        String command = DumpOpenUtil.command("file", input);

        assertTrue(command.startsWith("/inspectmc-open file "));
        String token = command.substring(command.lastIndexOf(' ') + 1);
        assertFalse(token.contains(" "));
        assertFalse(token.contains("="));
        String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
        assertEquals(input.toAbsolutePath().normalize().toString(), decoded);
    }
}
