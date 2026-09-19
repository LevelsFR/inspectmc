package com.levelsfr.inspectmc.dump;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XlsxWriterTest {
    @Test
    void writesACompleteReadableWorkbook() throws Exception {
        byte[] workbook = XlsxWriter.write(List.of(
                new XlsxWriter.Sheet("Overview", List.of("Field", "Value"),
                        List.of(List.of("Export", "InspectMC & friends")), List.of(24.0D, 60.0D)),
                new XlsxWriter.Sheet("Entries", List.of("ID", "Notes"),
                        List.of(List.of("minecraft:stone", "<safe>\u0001")), List.of(40.0D, 50.0D))));

        Map<String, String> entries = unzip(workbook);
        assertTrue(entries.containsKey("[Content_Types].xml"));
        assertTrue(entries.containsKey("xl/workbook.xml"));
        assertTrue(entries.containsKey("xl/styles.xml"));
        assertTrue(entries.containsKey("xl/worksheets/sheet1.xml"));
        assertTrue(entries.containsKey("xl/worksheets/sheet2.xml"));
        assertTrue(entries.get("xl/workbook.xml").contains("name=\"Overview\""));
        assertTrue(entries.get("xl/workbook.xml").contains("name=\"Entries\""));
        assertTrue(entries.get("xl/worksheets/sheet1.xml").contains("InspectMC &amp; friends"));
        assertTrue(entries.get("xl/worksheets/sheet2.xml").contains("&lt;safe&gt;"));
        assertTrue(entries.get("xl/worksheets/sheet2.xml").contains("autoFilter"));
        assertTrue(entries.get("xl/worksheets/sheet2.xml").contains("state=\"frozen\""));
    }

    @Test
    void limitsExcelSheetNamesToThirtyOneCharacters() throws Exception {
        String longName = "This sheet name is intentionally much too long";
        byte[] workbook = XlsxWriter.write(List.of(new XlsxWriter.Sheet(
                longName, List.of("Value"), List.of(List.of("ok")), List.of(20.0D))));

        String workbookXml = unzip(workbook).get("xl/workbook.xml");
        String expected = longName.substring(0, 31);
        assertTrue(workbookXml.contains("name=\"" + expected + "\""));
        assertEquals(31, expected.length());
    }

    private static Map<String, String> unzip(byte[] bytes) throws Exception {
        Map<String, String> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }
}
