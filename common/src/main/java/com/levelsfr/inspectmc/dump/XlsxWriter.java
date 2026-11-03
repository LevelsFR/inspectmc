package com.levelsfr.inspectmc.dump;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Minimal dependency-free OOXML workbook writer for readable InspectMC exports. */
final class XlsxWriter {
    private static final int MAX_EXCEL_CELL_CHARACTERS = 32_767;
    private static final String CELL_TRUNCATION_NOTICE =
            "[Text shortened for Excel; see the JSON/CSV export for the full value]\n";

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
        xml.append("</sheets><definedNames>");
        for (int index = 0; index < sheets.size(); index++) {
            String name = sheets.get(index).name().replace("'", "''");
            xml.append("<definedName name=\"_xlnm.Print_Titles\" localSheetId=\"").append(index)
                    .append("\">'").append(xml(name)).append("'!$1:$1</definedName>");
        }
        return xml.append("</definedNames></workbook>").toString();
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
                + "<fonts count=\"3\"><font><sz val=\"11\"/><name val=\"Aptos\"/></font><font><b/><color rgb=\"FFFFFFFF\"/><sz val=\"11\"/><name val=\"Aptos Display\"/></font><font><b/><color rgb=\"FF075E66\"/><sz val=\"11\"/><name val=\"Aptos\"/></font></fonts>"
                + "<fills count=\"4\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF087E8B\"/><bgColor indexed=\"64\"/></patternFill></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFF0F6F6\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>"
                + "<borders count=\"2\"><border/><border><left style=\"thin\"><color rgb=\"FFD9E2E3\"/></left><right style=\"thin\"><color rgb=\"FFD9E2E3\"/></right><top style=\"thin\"><color rgb=\"FFD9E2E3\"/></top><bottom style=\"thin\"><color rgb=\"FFD9E2E3\"/></bottom></border></borders>"
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                + "<cellXfs count=\"4\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\"><alignment vertical=\"top\" wrapText=\"1\"/></xf><xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"><alignment horizontal=\"center\" vertical=\"center\" wrapText=\"1\"/></xf><xf numFmtId=\"0\" fontId=\"0\" fillId=\"3\" borderId=\"1\" xfId=\"0\" applyFill=\"1\"><alignment vertical=\"top\" wrapText=\"1\"/></xf><xf numFmtId=\"0\" fontId=\"2\" fillId=\"3\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"><alignment vertical=\"top\" wrapText=\"1\"/></xf></cellXfs>"
                + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
                + "</styleSheet>";
    }

    private static String sheetXml(Sheet sheet) {
        int columns = sheet.headers().size();
        int lastRow = sheet.rows().size() + 1;
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
                .append("<dimension ref=\"A1:").append(columnName(columns)).append(lastRow).append("\"/>")
                .append("<sheetViews><sheetView workbookViewId=\"0\" showGridLines=\"0\" zoomScale=\"90\"><pane ");
        if (sheet.frozenColumns() > 0) {
            xml.append("xSplit=\"").append(sheet.frozenColumns()).append("\" ");
        }
        xml.append("ySplit=\"1\" topLeftCell=\"").append(columnName(sheet.frozenColumns() + 1))
                .append("2\" activePane=\"").append(sheet.frozenColumns() > 0 ? "bottomRight" : "bottomLeft")
                .append("\" state=\"frozen\"/></sheetView></sheetViews><sheetFormatPr defaultRowHeight=\"19\"/><cols>");
        for (int index = 0; index < columns; index++) {
            double width = index < sheet.widths().size() ? sheet.widths().get(index) : 22.0D;
            xml.append("<col min=\"").append(index + 1).append("\" max=\"").append(index + 1)
                    .append("\" width=\"").append(width).append("\" customWidth=\"1\"/>");
        }
        xml.append("</cols><sheetData>");
        appendRow(xml, 1, sheet.headers(), true, sheet, 0);
        for (int index = 0; index < sheet.rows().size(); index++) {
            appendRow(xml, index + 2, sheet.rows().get(index), false, sheet, index);
        }
        xml.append("</sheetData>");
        if (sheet.autoFilter()) {
            xml.append("<autoFilter ref=\"A1:").append(columnName(columns)).append(lastRow).append("\"/>");
        }
        xml.append("<printOptions gridLines=\"false\" headings=\"false\"/>")
                .append("<pageMargins left=\"0.35\" right=\"0.35\" top=\"0.5\" bottom=\"0.5\" header=\"0.2\" footer=\"0.2\"/>")
                .append("<pageSetup orientation=\"landscape\" paperSize=\"9\"/>")
                .append("<headerFooter><oddHeader>&amp;C&amp;A</oddHeader><oddFooter>&amp;RPage &amp;P of &amp;N</oddFooter></headerFooter>");
        return xml.append("</worksheet>").toString();
    }

    private static void appendRow(StringBuilder xml, int rowNumber, List<String> values, boolean header,
                                  Sheet sheet, int dataRowIndex) {
        int height = header ? 28 : rowHeight(values, sheet);
        xml.append("<row r=\"").append(rowNumber).append("\" ht=\"").append(height)
                .append("\" customHeight=\"1\">");
        for (int index = 0; index < values.size(); index++) {
            int style = header ? 1
                    : sheet.name().equals("Overview") && index == 0 ? 3
                    : dataRowIndex % 2 == 1 ? 2 : 0;
            double width = index < sheet.widths().size() ? sheet.widths().get(index) : 22.0D;
            String value = excelCellText(values.get(index), width);
            xml.append("<c r=\"").append(columnName(index + 1)).append(rowNumber)
                    .append("\" t=\"inlineStr\" s=\"").append(style)
                    .append("\"><is><t xml:space=\"preserve\">").append(xml(value))
                    .append("</t></is></c>");
        }
        xml.append("</row>");
    }

    private static int rowHeight(List<String> values, Sheet sheet) {
        int lines = 1;
        for (int index = 0; index < values.size(); index++) {
            double width = index < sheet.widths().size() ? sheet.widths().get(index) : 22.0D;
            int charactersPerLine = Math.max(8, (int) Math.floor(width - 2.0D));
            int cellLines = 0;
            for (String line : excelCellText(values.get(index), width).split("\\R", -1)) {
                int length = line.codePointCount(0, line.length());
                cellLines += Math.max(1, (length + charactersPerLine - 1) / charactersPerLine);
            }
            lines = Math.max(lines, cellLines);
        }
        return Math.min(409, Math.max(19, lines * 15 + 4));
    }

    private static String excelCellText(String value, double width) {
        int charactersPerLine = Math.max(8, (int) Math.floor(width - 2.0D));
        int previewLimit = Math.min(MAX_EXCEL_CELL_CHARACTERS - CELL_TRUNCATION_NOTICE.length(),
                charactersPerLine * 12);
        if (value.length() <= previewLimit) {
            return value;
        }

        int end = previewLimit;
        if (Character.isHighSurrogate(value.charAt(end - 1)) && Character.isLowSurrogate(value.charAt(end))) {
            end--;
        }
        return CELL_TRUNCATION_NOTICE + value.substring(0, end);
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

    record Sheet(String name, List<String> headers, List<List<String>> rows, List<Double> widths,
                 int frozenColumns, boolean autoFilter) {
        Sheet(String name, List<String> headers, List<List<String>> rows, List<Double> widths) {
            this(name, headers, rows, widths, 1, true);
        }

        Sheet {
            if (name.length() > 31) {
                name = name.substring(0, 31);
            }
            if (headers.isEmpty() || frozenColumns < 0 || frozenColumns > headers.size()) {
                throw new IllegalArgumentException("Invalid worksheet columns");
            }
        }
    }
}
