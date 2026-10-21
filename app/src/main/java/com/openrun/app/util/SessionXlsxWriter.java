package com.openrun.app.util;

import android.content.Context;

import com.openrun.app.R;
import com.openrun.app.data.PointEntity;
import com.openrun.app.data.SessionEntity;
import com.openrun.core.model.ActivityType;
import com.openrun.core.stats.GeoMath;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes one activity as an Office Open XML workbook (.xlsx).
 *
 * <p>The workbook has a summary sheet and a track-points sheet. It is built
 * directly with {@link ZipOutputStream}, so the app does not need a large
 * spreadsheet dependency. The generated file opens in Excel, LibreOffice and
 * Google Sheets.</p>
 */
public final class SessionXlsxWriter {
    private static final String XML_DECLARATION =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";
    private static final String WORKSHEET_NS =
            "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);

    private static final int STYLE_DEFAULT = 0;
    private static final int STYLE_HEADER = 1;
    private static final int STYLE_TITLE = 2;
    private static final int STYLE_LABEL = 3;

    private SessionXlsxWriter() {
    }

    public static void write(Context context,
                             OutputStream output,
                             SessionEntity session,
                             List<PointEntity> points) throws IOException {
        if (context == null || output == null || session == null) {
            throw new IllegalArgumentException("Context, output and session are required");
        }
        List<PointEntity> safePoints = points == null ? Collections.emptyList() : points;
        String summaryName = context.getString(R.string.export_sheet_summary);
        String pointsName = context.getString(R.string.export_sheet_points);

        try (ZipOutputStream zip = new ZipOutputStream(new NonClosingOutputStream(output))) {
            zip.setLevel(Deflater.DEFAULT_COMPRESSION);
            writeEntry(zip, "[Content_Types].xml", contentTypes());
            writeEntry(zip, "_rels/.rels", packageRelationships());
            writeEntry(zip, "xl/workbook.xml", workbook(summaryName, pointsName));
            writeEntry(zip, "xl/_rels/workbook.xml.rels", workbookRelationships());
            writeEntry(zip, "xl/styles.xml", styles());
            writeEntry(
                    zip,
                    "xl/worksheets/sheet1.xml",
                    summarySheet(context, session, safePoints)
            );
            zip.putNextEntry(new ZipEntry("xl/worksheets/sheet2.xml"));
            writePointsSheet(zip, context, safePoints);
            zip.closeEntry();
        }
    }

    private static String summarySheet(Context context,
                                       SessionEntity session,
                                       List<PointEntity> points) {
        ActivityType type = ActivityType.fromKey(session.type);
        String typeLabel = context.getString(
                type == ActivityType.WALK ? R.string.activity_walk : R.string.activity_run
        );
        long start = session.startedAt;
        long end = session.endedAt != null
                ? session.endedAt
                : start + session.movingMs + session.pausedMs;
        long total = Math.max(0, end - start);
        double pace = GeoMath.paceSecPerKm(session.distanceM, session.movingMs);

        StringBuilder rows = new StringBuilder(4096);
        appendRow(
                rows,
                1,
                new Object[]{
                        context.getString(R.string.app_name) + " · " + typeLabel,
                        null,
                        null
                },
                new int[]{STYLE_TITLE, STYLE_DEFAULT, STYLE_DEFAULT}
        );
        appendRow(
                rows,
                3,
                new Object[]{
                        context.getString(R.string.export_metric_metric),
                        context.getString(R.string.export_metric_value),
                        context.getString(R.string.export_metric_display)
                },
                new int[]{STYLE_HEADER, STYLE_HEADER, STYLE_HEADER}
        );

        int row = 4;
        row = appendSummaryRow(
                rows,
                row,
                context.getString(R.string.export_metric_activity),
                typeLabel,
                null
        );
        row = appendSummaryRow(
                rows,
                row,
                context.getString(R.string.export_metric_id),
                session.id,
                null
        );
        row = appendSummaryRow(
                rows,
                row,
                context.getString(R.string.export_metric_start),
                start,
                Formatters.dateTime(context, start)
        );
        row = appendSummaryRow(
                rows,
                row,
                context.getString(R.string.export_metric_end),
                end,
                Formatters.dateTime(context, end)
        );
        row = appendSummaryRow(
                rows,
                row,
                context.getString(R.string.export_metric_distance),
                session.distanceM,
                Formatters.distance(context, session.distanceM)
        );
        row = appendSummaryRow(
                rows,
                row,
                context.getString(R.string.export_metric_moving),
                session.movingMs / 1000.0d,
                Formatters.duration(session.movingMs)
        );
        row = appendSummaryRow(
                rows,
                row,
                context.getString(R.string.export_metric_total),
                total / 1000.0d,
                Formatters.duration(total)
        );
        row = appendSummaryRow(
                rows,
                row,
                context.getString(R.string.export_metric_pace),
                pace,
                Formatters.pace(context, pace)
        );
        row = appendSummaryRow(
                rows,
                row,
                context.getString(R.string.export_metric_elevation_gain),
                session.elevationGainM,
                Formatters.elevation(context, session.elevationGainM)
        );
        row = appendSummaryRow(
                rows,
                row,
                context.getString(R.string.export_metric_elevation_loss),
                session.elevationLossM,
                Formatters.elevation(context, session.elevationLossM)
        );
        row = appendSummaryRow(
                rows,
                row,
                context.getString(R.string.label_track_points),
                points.size(),
                null
        );
        row++;
        appendSummaryRow(
                rows,
                row,
                context.getString(R.string.export_report_generated_by),
                null,
                null
        );

        int lastRow = row;
        return XML_DECLARATION
                + "<worksheet xmlns=\"" + WORKSHEET_NS + "\">"
                + "<dimension ref=\"A1:C" + lastRow + "\"/>"
                + "<sheetViews><sheetView workbookViewId=\"0\"/></sheetViews>"
                + "<sheetFormatPr defaultRowHeight=\"15\"/>"
                + "<cols>"
                + "<col min=\"1\" max=\"1\" width=\"32\" customWidth=\"1\"/>"
                + "<col min=\"2\" max=\"2\" width=\"38\" customWidth=\"1\"/>"
                + "<col min=\"3\" max=\"3\" width=\"34\" customWidth=\"1\"/>"
                + "</cols>"
                + "<sheetData>" + rows + "</sheetData>"
                + "<mergeCells count=\"1\"><mergeCell ref=\"A1:C1\"/></mergeCells>"
                + "</worksheet>";
    }

    private static int appendSummaryRow(StringBuilder rows,
                                        int row,
                                        String label,
                                        Object rawValue,
                                        String displayValue) {
        appendRow(
                rows,
                row,
                new Object[]{label, rawValue, displayValue},
                new int[]{STYLE_LABEL, STYLE_DEFAULT, STYLE_DEFAULT}
        );
        return row + 1;
    }

    private static void writePointsSheet(ZipOutputStream zip,
                                         Context context,
                                         List<PointEntity> points) throws IOException {
        int lastRow = Math.max(1, points.size() + 1);
        StringBuilder prefix = new StringBuilder(2048);
        prefix.append(XML_DECLARATION)
                .append("<worksheet xmlns=\"").append(WORKSHEET_NS).append("\">")
                .append("<dimension ref=\"A1:I").append(lastRow).append("\"/>")
                .append("<sheetViews><sheetView workbookViewId=\"0\">")
                .append("<pane ySplit=\"1\" topLeftCell=\"A2\" ")
                .append("activePane=\"bottomLeft\" state=\"frozen\"/>")
                .append("</sheetView></sheetViews>")
                .append("<sheetFormatPr defaultRowHeight=\"15\"/>")
                .append("<cols>")
                .append("<col min=\"1\" max=\"1\" width=\"7\" customWidth=\"1\"/>")
                .append("<col min=\"2\" max=\"2\" width=\"27\" customWidth=\"1\"/>")
                .append("<col min=\"3\" max=\"3\" width=\"14\" customWidth=\"1\"/>")
                .append("<col min=\"4\" max=\"5\" width=\"14\" customWidth=\"1\"/>")
                .append("<col min=\"6\" max=\"9\" width=\"13\" customWidth=\"1\"/>")
                .append("</cols><sheetData>");
        write(zip, prefix.toString());

        StringBuilder row = new StringBuilder(512);
        appendRow(
                row,
                1,
                new Object[]{
                        context.getString(R.string.export_col_sequence),
                        context.getString(R.string.export_col_timestamp),
                        context.getString(R.string.export_col_elapsed),
                        context.getString(R.string.export_col_latitude),
                        context.getString(R.string.export_col_longitude),
                        context.getString(R.string.export_col_altitude),
                        context.getString(R.string.export_col_accuracy),
                        context.getString(R.string.export_col_speed),
                        context.getString(R.string.export_col_bearing)
                },
                new int[]{
                        STYLE_HEADER, STYLE_HEADER, STYLE_HEADER,
                        STYLE_HEADER, STYLE_HEADER, STYLE_HEADER,
                        STYLE_HEADER, STYLE_HEADER, STYLE_HEADER
                }
        );
        write(zip, row.toString());
        row.setLength(0);

        int rowNumber = 2;
        for (PointEntity point : points) {
            if (point == null) {
                continue;
            }
            appendRow(
                    row,
                    rowNumber++,
                    new Object[]{
                            point.seq > 0 ? point.seq : rowNumber - 2,
                            timestamp(point.timestamp),
                            point.elapsedMs / 1000.0d,
                            point.lat,
                            point.lon,
                            point.altitude,
                            point.accuracy,
                            point.speed,
                            point.bearing
                    },
                    null
            );
            write(zip, row.toString());
            row.setLength(0);
        }
        write(zip, "</sheetData></worksheet>");
    }

    private static void appendRow(StringBuilder out,
                                  int rowNumber,
                                  Object[] values,
                                  int[] styles) {
        out.append("<row r=\"").append(rowNumber).append("\">");
        for (int i = 0; i < values.length; i++) {
            Object value = values[i];
            if (value == null) {
                continue;
            }
            int style = styles != null && i < styles.length
                    ? styles[i]
                    : STYLE_DEFAULT;
            String reference = columnName(i + 1) + rowNumber;
            if (value instanceof Number) {
                String numeric = numberValue((Number) value);
                if (numeric == null) {
                    continue;
                }
                out.append("<c r=\"").append(reference).append("\"");
                if (style != STYLE_DEFAULT) {
                    out.append(" s=\"").append(style).append("\"");
                }
                out.append("><v>").append(numeric).append("</v></c>");
            } else {
                out.append("<c r=\"").append(reference).append("\" t=\"inlineStr\"");
                if (style != STYLE_DEFAULT) {
                    out.append(" s=\"").append(style).append("\"");
                }
                out.append("><is><t xml:space=\"preserve\">")
                        .append(escapeXml(String.valueOf(value)))
                        .append("</t></is></c>");
            }
        }
        out.append("</row>");
    }

    private static String numberValue(Number value) {
        if (value instanceof Double || value instanceof Float) {
            double number = value.doubleValue();
            if (Double.isNaN(number) || Double.isInfinite(number)) {
                return null;
            }
            return BigDecimal.valueOf(number).stripTrailingZeros().toPlainString();
        }
        return value.toString();
    }

    private static String timestamp(long epochMs) {
        if (epochMs <= 0) {
            return null;
        }
        return TIMESTAMP_FORMAT.format(
                Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
        );
    }

    private static String columnName(int index) {
        StringBuilder name = new StringBuilder();
        int value = index;
        while (value > 0) {
            int remainder = (value - 1) % 26;
            name.insert(0, (char) ('A' + remainder));
            value = (value - 1) / 26;
        }
        return name.toString();
    }

    private static String escapeXml(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '&':
                    escaped.append("&amp;");
                    break;
                case '<':
                    escaped.append("&lt;");
                    break;
                case '>':
                    escaped.append("&gt;");
                    break;
                case '"':
                    escaped.append("&quot;");
                    break;
                case '\'':
                    escaped.append("&apos;");
                    break;
                default:
                    if (character == '\t'
                            || character == '\n'
                            || character == '\r'
                            || character >= 0x20) {
                        escaped.append(character);
                    }
                    break;
            }
        }
        return escaped.toString();
    }

    private static String contentTypes() {
        return XML_DECLARATION
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" "
                + "ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\" "
                + "ContentType=\"application/vnd.openxmlformats-officedocument."
                + "spreadsheetml.sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet1.xml\" "
                + "ContentType=\"application/vnd.openxmlformats-officedocument."
                + "spreadsheetml.worksheet+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet2.xml\" "
                + "ContentType=\"application/vnd.openxmlformats-officedocument."
                + "spreadsheetml.worksheet+xml\"/>"
                + "<Override PartName=\"/xl/styles.xml\" "
                + "ContentType=\"application/vnd.openxmlformats-officedocument."
                + "spreadsheetml.styles+xml\"/>"
                + "</Types>";
    }

    private static String packageRelationships() {
        return XML_DECLARATION
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" "
                + "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/"
                + "relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "</Relationships>";
    }

    private static String workbook(String summaryName, String pointsName) {
        return XML_DECLARATION
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<bookViews><workbookView activeTab=\"0\"/></bookViews>"
                + "<sheets>"
                + "<sheet name=\"" + escapeXml(summaryName) + "\" sheetId=\"1\" r:id=\"rId1\"/>"
                + "<sheet name=\"" + escapeXml(pointsName) + "\" sheetId=\"2\" r:id=\"rId2\"/>"
                + "</sheets>"
                + "</workbook>";
    }

    private static String workbookRelationships() {
        return XML_DECLARATION
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" "
                + "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/"
                + "relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                + "<Relationship Id=\"rId2\" "
                + "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/"
                + "relationships/worksheet\" Target=\"worksheets/sheet2.xml\"/>"
                + "<Relationship Id=\"rId3\" "
                + "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/"
                + "relationships/styles\" Target=\"styles.xml\"/>"
                + "</Relationships>";
    }

    private static String styles() {
        return XML_DECLARATION
                + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<fonts count=\"3\">"
                + "<font><sz val=\"11\"/><name val=\"Calibri\"/><family val=\"2\"/>"
                + "<scheme val=\"minor\"/></font>"
                + "<font><b/><sz val=\"14\"/><name val=\"Calibri\"/><family val=\"2\"/>"
                + "<scheme val=\"minor\"/></font>"
                + "<font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/>"
                + "<name val=\"Calibri\"/><family val=\"2\"/><scheme val=\"minor\"/></font>"
                + "</fonts>"
                + "<fills count=\"3\">"
                + "<fill><patternFill patternType=\"none\"/></fill>"
                + "<fill><patternFill patternType=\"gray125\"/></fill>"
                + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF0B6E4F\"/>"
                + "<bgColor indexed=\"64\"/></patternFill></fill>"
                + "</fills>"
                + "<borders count=\"1\"><border><left/><right/><top/><bottom/>"
                + "<diagonal/></border></borders>"
                + "<cellStyleXfs count=\"1\">"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/>"
                + "</cellStyleXfs>"
                + "<cellXfs count=\"4\">"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                + "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"2\" borderId=\"0\" xfId=\"0\" "
                + "applyFont=\"1\" applyFill=\"1\"/>"
                + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" "
                + "applyFont=\"1\"/>"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" "
                + "applyFont=\"1\" applyAlignment=\"1\">"
                + "<alignment vertical=\"center\"/></xf>"
                + "</cellXfs>"
                + "<cellStyles count=\"1\">"
                + "<cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/>"
                + "</cellStyles>"
                + "<dxfs count=\"0\"/>"
                + "<tableStyles count=\"0\" defaultTableStyle=\"TableStyleMedium2\" "
                + "defaultPivotStyle=\"PivotStyleLight16\"/>"
                + "</styleSheet>";
    }

    private static void writeEntry(ZipOutputStream zip,
                                   String name,
                                   String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        write(zip, content);
        zip.closeEntry();
    }

    private static void write(ZipOutputStream zip, String content) throws IOException {
        zip.write(content.getBytes(StandardCharsets.UTF_8));
    }

    private static final class NonClosingOutputStream extends OutputStream {
        private final OutputStream delegate;

        NonClosingOutputStream(OutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public void write(int value) throws IOException {
            delegate.write(value);
        }

        @Override
        public void write(byte[] buffer, int offset, int length) throws IOException {
            delegate.write(buffer, offset, length);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.flush();
        }
    }
}
