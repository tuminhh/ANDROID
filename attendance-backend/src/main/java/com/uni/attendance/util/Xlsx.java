package com.uni.attendance.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Bộ ghi file .xlsx tối giản, không cần thư viện ngoài: một trang tính, ô chữ hoặc số, vài kiểu định dạng.
 * Chữ được ghi dạng inline string nên nội dung như "=1+1" chỉ là văn bản, Excel không tính như công thức.
 */
public final class Xlsx {
    /** Chỉ số kiểu khớp với cellXfs trong styles.xml bên dưới. */
    public enum Style { NORMAL(0), TITLE(1), HEADER(2), PRESENT(3), ABSENT(4), CELL(5);
        final int index;
        Style(int index) { this.index = index; }
    }

    private final String sheetName;
    private final List<String> rows = new ArrayList<>();
    private int[] widths = new int[0];
    private int rowNumber = 0;

    public Xlsx(String sheetName) {
        this.sheetName = safeSheetName(sheetName);
    }

    /** Độ rộng cột theo số ký tự, theo thứ tự từ cột A. */
    public Xlsx widths(int... widths) {
        this.widths = widths;
        return this;
    }

    /** Thêm một dòng với cùng một kiểu cho mọi ô. Giá trị là Number thì ghi số, còn lại ghi chữ. */
    public Xlsx row(Style style, Object... values) {
        Style[] styles = new Style[values.length];
        java.util.Arrays.fill(styles, style);
        return row(styles, values);
    }

    /** Thêm một dòng với kiểu riêng cho từng ô. */
    public Xlsx row(Style[] styles, Object... values) {
        rowNumber++;
        StringBuilder sb = new StringBuilder();
        sb.append("<row r=\"").append(rowNumber).append("\">");
        for (int i = 0; i < values.length; i++) {
            Object v = values[i];
            if (v == null || (v instanceof String s && s.isEmpty() && styles[i] == Style.NORMAL)) {
                continue;
            }
            String ref = column(i) + rowNumber;
            sb.append("<c r=\"").append(ref).append("\" s=\"").append(styles[i].index).append('"');
            if (v instanceof Number n) {
                sb.append("><v>").append(n).append("</v></c>");
            } else {
                sb.append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                        .append(escape(String.valueOf(v))).append("</t></is></c>");
            }
        }
        sb.append("</row>");
        rows.add(sb.toString());
        return this;
    }

    public Xlsx blank() {
        rowNumber++;
        return this;
    }

    public byte[] build() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
                put(zip, "[Content_Types].xml", CONTENT_TYPES);
                put(zip, "_rels/.rels", ROOT_RELS);
                put(zip, "xl/workbook.xml", workbook());
                put(zip, "xl/_rels/workbook.xml.rels", WORKBOOK_RELS);
                put(zip, "xl/styles.xml", STYLES);
                put(zip, "xl/worksheets/sheet1.xml", sheet());
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String sheet() {
        StringBuilder sb = new StringBuilder(XML_HEADER);
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        if (widths.length > 0) {
            sb.append("<cols>");
            for (int i = 0; i < widths.length; i++) {
                sb.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1)
                        .append("\" width=\"").append(widths[i]).append("\" customWidth=\"1\"/>");
            }
            sb.append("</cols>");
        }
        sb.append("<sheetData>");
        rows.forEach(sb::append);
        sb.append("</sheetData></worksheet>");
        return sb.toString();
    }

    private String workbook() {
        return XML_HEADER
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<sheets><sheet name=\"" + escape(sheetName) + "\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>";
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    /** 0 -> A, 25 -> Z, 26 -> AA ... */
    static String column(int index) {
        StringBuilder sb = new StringBuilder();
        for (int n = index; n >= 0; n = n / 26 - 1) {
            sb.insert(0, (char) ('A' + n % 26));
        }
        return sb.toString();
    }

    /** Tên trang tính: tối đa 31 ký tự và không chứa : \ / ? * [ ] */
    private static String safeSheetName(String name) {
        String cleaned = name == null ? "" : name.replaceAll("[:\\\\/?*\\[\\]]", " ").trim();
        if (cleaned.isEmpty()) cleaned = "Điểm danh";
        return cleaned.length() > 31 ? cleaned.substring(0, 31) : cleaned;
    }

    /** Escape XML và bỏ ký tự điều khiển không hợp lệ trong XML 1.0. */
    static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                default -> {
                    boolean ok = c == '\t' || c == '\n' || c == '\r' || (c >= 0x20 && c != 0xFFFE && c != 0xFFFF);
                    if (ok) sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    private static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";

    private static final String CONTENT_TYPES = XML_HEADER
            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
            + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
            + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
            + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
            + "</Types>";

    private static final String ROOT_RELS = XML_HEADER
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
            + "</Relationships>";

    private static final String WORKBOOK_RELS = XML_HEADER
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
            + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
            + "</Relationships>";

    // fonts: 0 thường, 1 đậm cỡ 14, 2 đậm chữ trắng. fills: 2 xanh đậm, 3 xanh lá nhạt, 4 đỏ nhạt.
    private static final String STYLES = XML_HEADER
            + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
            + "<fonts count=\"3\">"
            + "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
            + "<font><b/><sz val=\"14\"/><name val=\"Calibri\"/></font>"
            + "<font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font>"
            + "</fonts>"
            + "<fills count=\"5\">"
            + "<fill><patternFill patternType=\"none\"/></fill>"
            + "<fill><patternFill patternType=\"gray125\"/></fill>"
            + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF1F4E79\"/><bgColor indexed=\"64\"/></patternFill></fill>"
            + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFE2F0D9\"/><bgColor indexed=\"64\"/></patternFill></fill>"
            + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFF8D7DA\"/><bgColor indexed=\"64\"/></patternFill></fill>"
            + "</fills>"
            + "<borders count=\"2\">"
            + "<border><left/><right/><top/><bottom/><diagonal/></border>"
            + "<border><left style=\"thin\"><color rgb=\"FFBFBFBF\"/></left><right style=\"thin\"><color rgb=\"FFBFBFBF\"/></right>"
            + "<top style=\"thin\"><color rgb=\"FFBFBFBF\"/></top><bottom style=\"thin\"><color rgb=\"FFBFBFBF\"/></bottom><diagonal/></border>"
            + "</borders>"
            + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
            + "<cellXfs count=\"6\">"
            + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
            + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>"
            + "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"2\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\" wrapText=\"1\"/></xf>"
            + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"3\" borderId=\"1\" xfId=\"0\" applyFill=\"1\" applyBorder=\"1\"/>"
            + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"4\" borderId=\"1\" xfId=\"0\" applyFill=\"1\" applyBorder=\"1\"/>"
            + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyBorder=\"1\"/>"
            + "</cellXfs>"
            + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
            + "</styleSheet>";
}
