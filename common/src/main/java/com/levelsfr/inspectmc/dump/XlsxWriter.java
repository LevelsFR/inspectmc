package com.levelsfr.inspectmc.dump;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Minimal dependency-free OOXML workbook writer for readable InspectMC exports. */
final class XlsxWriter {
    private XlsxWriter() {
    }

    static byte[] write(List<Sheet> sheets) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            entry(zip, "[Content_Types].xml", contentTypes(sheets.size()));
            entry(zip, "_rels/.rels", rootRelationships());
            entry(zip, "xl/workbook.xml", workbook(sheets));
            entry(zip, "xl/_rels/workbook.xml.rels", workbookRelationships(sheets.size()));
            entry(zip, "xl/styles.xml", styles());
            for (int index = 0; index < sheets.size(); index++) {
                entry(zip, "xl/worksheets/sheet" + (index + 1) + ".xml", sheetXml(sheets.get(index)));
            }
        }
        return output.toByteArray();
    }

    private static void entry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String contentTypes(int sheetCount) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
                .append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
                .append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
                .append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
                .append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
        for (int index = 1; index <= sheetCount; index++) {
            xml.append("<Override PartName=\"/xl/worksheets/sheet").append(index)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        return xml.append("</Types>").toString();
    }

    private static String rootRelationships() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "</Relationships>";
    }

    private static String workbook(List<Sheet> sheets) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>");
        for (int index = 0; index < sheets.size(); index++) {
            xml.append("<sheet name=\"").append(xml(sheets.get(index).name())).append("\" sheetId=\"")
                    .append(index + 1).append("\" r:id=\"rId").append(index + 1).append("\"/>");
        }
        return xml.append("</sheets></workbook>").toString();
    }

    private static String workbookRelationships(int sheetCount) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for (int index = 1; index <= sheetCount; index++) {
            xml.append("<Relationship Id=\"rId").append(index)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet")
                    .append(index).append(".xml\"/>");
        }
        xml.append("<Relationship Id=\"rId").append(sheetCount + 1)
                .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
        return xml.append("</Relationships>").toString();
    }

    private static String styles() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Aptos\"/></font><font><b/><color rgb=\"FFFFFFFF\"/><sz val=\"11\"/><name val=\"Aptos Display\"/></font></fonts>"
                + "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF087E8B\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>"
                + "<borders count=\"2\"><border/><border><left style=\"thin\"><color rgb=\"FFD9E2E3\"/></left><right style=\"thin\"><color rgb=\"FFD9E2E3\"/></right><top style=\"thin\"><color rgb=\"FFD9E2E3\"/></top><bottom style=\"thin\"><color rgb=\"FFD9E2E3\"/></bottom></border></borders>"
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                + "<cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\"><alignment vertical=\"top\" wrapText=\"1\"/></xf><xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"><alignment vertical=\"center\"/></xf></cellXfs>"
                + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
                + "</styleSheet>";
    }

    private static String sheetXml(Sheet sheet) {
        int columns = sheet.headers().size();
        int lastRow = sheet.rows().size() + 1;
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
                .append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>")
                .append("<cols>");
        for (int index = 0; index < columns; index++) {
            double width = index < sheet.widths().size() ? sheet.widths().get(index) : 22.0D;
            xml.append("<col min=\"").append(index + 1).append("\" max=\"").append(index + 1)
                    .append("\" width=\"").append(width).append("\" customWidth=\"1\"/>");
        }
        xml.append("</cols><sheetData>");
        appendRow(xml, 1, sheet.headers(), true);
        for (int index = 0; index < sheet.rows().size(); index++) {
            appendRow(xml, index + 2, sheet.rows().get(index), false);
        }
        xml.append("</sheetData>");
        if (columns > 0 && lastRow >= 1) {
            xml.append("<autoFilter ref=\"A1:").append(columnName(columns)).append(lastRow).append("\"/>");
        }
        return xml.append("</worksheet>").toString();
    }

    private static void appendRow(StringBuilder xml, int rowNumber, List<String> values, boolean header) {
        xml.append("<row r=\"").append(rowNumber).append("\">");
        for (int index = 0; index < values.size(); index++) {
            xml.append("<c r=\"").append(columnName(index + 1)).append(rowNumber)
                    .append("\" t=\"inlineStr\" s=\"").append(header ? 1 : 0)
                    .append("\"><is><t xml:space=\"preserve\">").append(xml(values.get(index)))
                    .append("</t></is></c>");
        }
        xml.append("</row>");
    }

    private static String columnName(int number) {
        StringBuilder name = new StringBuilder();
        int value = number;
        while (value > 0) {
            value--;
            name.insert(0, (char) ('A' + value % 26));
            value /= 26;
        }
        return name.toString();
    }

    private static String xml(String value) {
        StringBuilder clean = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character >= 0x20 || character == '\n' || character == '\r' || character == '\t') {
                clean.append(character);
            }
        }
        return clean.toString().replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

    record Sheet(String name, List<String> headers, List<List<String>> rows, List<Double> widths) {
        Sheet {
            if (name.length() > 31) {
                name = name.substring(0, 31);
            }
        }
    }
}
