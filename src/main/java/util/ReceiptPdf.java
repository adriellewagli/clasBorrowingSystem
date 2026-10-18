package util;

import model.Receipt;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes a Receipt as a one-page A4 PDF using only the JDK (no extra libraries).
 * Uses the built-in Helvetica fonts, so the peso sign is printed as "PHP".
 */
public final class ReceiptPdf {

    private static final double PAGE_W = 595, PAGE_H = 842;
    private static final double MARGIN = 56;
    private static final double LABEL_X = MARGIN;
    private static final double VALUE_X = 230;
    private static final double RIGHT_X = PAGE_W - MARGIN;
    private static final double VALUE_W = RIGHT_X - VALUE_X;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM dd, yyyy");

    // Helvetica advance widths (1/1000 em) for ASCII 32..126
    private static final int[] REG = {
            278, 278, 355, 556, 556, 889, 667, 191, 333, 333, 389, 584, 278, 333, 278, 278,
            556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 278, 278, 584, 584, 584, 556,
            1015, 667, 667, 722, 722, 667, 611, 778, 722, 278, 500, 667, 556, 833, 722, 778,
            667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 278, 278, 278, 469, 556,
            333, 556, 556, 500, 556, 556, 278, 556, 556, 222, 222, 500, 222, 833, 556, 556,
            556, 556, 333, 500, 278, 556, 500, 722, 500, 500, 500, 334, 260, 334, 584};
    // Helvetica-Bold
    private static final int[] BOLD = {
            278, 333, 474, 556, 556, 889, 722, 238, 333, 333, 389, 584, 278, 333, 278, 278,
            556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 333, 333, 584, 584, 584, 611,
            975, 722, 722, 722, 722, 667, 611, 778, 722, 278, 556, 722, 611, 833, 722, 778,
            667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 333, 278, 333, 584, 556,
            333, 556, 611, 556, 611, 556, 333, 611, 611, 278, 278, 556, 278, 889, 611, 611,
            611, 611, 389, 556, 333, 611, 556, 778, 556, 556, 500, 389, 280, 389, 584};

    private final StringBuilder page = new StringBuilder();
    private double y = PAGE_H;

    private ReceiptPdf() {}

    public static void write(Receipt r, Path file) throws IOException {
        Files.write(file, new ReceiptPdf().build(r));
    }

    // ------------------------------------------------------------------ layout

    private byte[] build(Receipt r) throws IOException {
        // Header band
        fillRect(0, PAGE_H - 110, PAGE_W, 110, 0.145, 0.388, 0.922);
        text("CLAS Borrowing System", MARGIN, PAGE_H - 44, 11, true, 0.80, 0.87, 0.99);
        text("Digital Receipt", MARGIN, PAGE_H - 74, 24, true, 1, 1, 1);
        String no = r.receiptNumber();
        text(no, RIGHT_X - width(no, 18, true), PAGE_H - 52, 18, true, 1, 1, 1);
        String st = clean(r.status());
        text(st, RIGHT_X - width(st, 11, true), PAGE_H - 74, 11, true, 0.80, 0.87, 0.99);

        y = PAGE_H - 150;

        section("TRANSACTION");
        row("Date borrowed", date(r.dateBorrowed()));
        row("Due date", date(r.dueDate()));
        if (r.returnedDate() != null) row("Returned on", date(r.returnedDate()));

        section("BORROWER");
        row("Name", r.borrowerName());
        row("Type", capitalize(r.borrowerType()));
        row("ID number", r.borrowerIdNumber());
        row("Program / Department", r.programOrDept());

        section("EQUIPMENT");
        row("Item", r.itemName());
        row("Category", r.category());
        row("Serial number", r.serialNumber());
        row("Condition when released", r.initialCondition());

        section("CHARGES");
        moneyRow("Borrowing fee", r.borrowingFee());
        if (r.lateDays() > 0) {
            moneyRow("Late penalty (" + r.lateDays() + (r.lateDays() == 1 ? " day" : " days")
                    + " x " + money(r.latePenaltyPerDay()) + ")", r.lateFee());
        }
        y -= 4;
        line(LABEL_X, y + 8, RIGHT_X, y + 8, 0.80, 0.84, 0.88);
        y -= 12;
        text("TOTAL", LABEL_X, y, 13, true, 0.06, 0.09, 0.16);
        String total = money(r.totalDue());
        text(total, RIGHT_X - width(total, 15, true), y - 1, 15, true, 0.145, 0.388, 0.922);
        y -= 30;

        section("HANDLED BY");
        row("Requested by", r.requestedBy());
        row("Processed by", r.processedBy());

        String footer = "System-generated digital receipt  -  "
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm"));
        line(MARGIN, 64, RIGHT_X, 64, 0.88, 0.91, 0.94);
        text(footer, MARGIN, 46, 9, false, 0.45, 0.50, 0.58);

        return assemble();
    }

    private void section(String title) {
        y -= 8;
        line(LABEL_X, y + 6, RIGHT_X, y + 6, 0.88, 0.91, 0.94);
        y -= 14;
        text(title, LABEL_X, y, 9.5, true, 0.39, 0.45, 0.55);
        y -= 20;
    }

    private void row(String label, String value) {
        List<String> lines = wrap(blank(value), VALUE_W, 11, true);
        text(clean(label), LABEL_X, y, 10.5, false, 0.39, 0.45, 0.55);
        for (String l : lines) {
            text(l, VALUE_X, y, 11, true, 0.06, 0.09, 0.16);
            y -= 15;
        }
        y -= 3;
    }

    private void moneyRow(String label, double amount) {
        text(clean(label), LABEL_X, y, 10.5, false, 0.39, 0.45, 0.55);
        String v = money(amount);
        text(v, RIGHT_X - width(v, 11, true), y, 11, true, 0.06, 0.09, 0.16);
        y -= 18;
    }

    // ------------------------------------------------------------------ text helpers

    private static String blank(String v) {
        return v == null || v.isBlank() ? "-" : v;
    }

    private static String date(LocalDate d) {
        return d == null ? null : d.format(DATE_FMT);
    }

    private static String money(double amount) {
        return String.format("PHP %,.2f", amount);
    }

    private static String capitalize(String s) {
        if (s == null || s.isBlank()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    /** Keep only characters the built-in WinAnsi font can print. */
    private static String clean(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c >= 32 && c <= 126) sb.append(c);
            else if (c >= 160 && c <= 255) sb.append(c);
            else if (c == '\u20B1') sb.append("PHP ");
            else sb.append('?');
        }
        return sb.toString();
    }

    private static double width(String s, double size, boolean bold) {
        int[] table = bold ? BOLD : REG;
        double w = 0;
        for (char c : clean(s).toCharArray()) {
            w += (c >= 32 && c <= 126) ? table[c - 32] : 556;
        }
        return w * size / 1000.0;
    }

    /** Word-wraps to maxWidth; very long words are split so nothing runs off the page. */
    private static List<String> wrap(String text, double maxWidth, double size, boolean bold) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : clean(text).split("\\s+")) {
            while (width(word, size, bold) > maxWidth) {
                int cut = word.length();
                while (cut > 1 && width(word.substring(0, cut), size, bold) > maxWidth) cut--;
                if (cur.length() > 0) { out.add(cur.toString()); cur.setLength(0); }
                out.add(word.substring(0, cut));
                word = word.substring(cut);
            }
            String attempt = cur.length() == 0 ? word : cur + " " + word;
            if (width(attempt, size, bold) <= maxWidth) {
                cur.setLength(0);
                cur.append(attempt);
            } else {
                out.add(cur.toString());
                cur.setLength(0);
                cur.append(word);
            }
        }
        if (cur.length() > 0 || out.isEmpty()) out.add(cur.toString());
        return out;
    }

    // ------------------------------------------------------------------ PDF drawing operators

    private static String num(double d) {
        return String.format(java.util.Locale.ROOT, "%.2f", d);
    }

    private void text(String s, double x, double yy, double size, boolean bold, double r, double g, double b) {
        page.append(num(r)).append(' ').append(num(g)).append(' ').append(num(b)).append(" rg\n")
            .append("BT /").append(bold ? "F2" : "F1").append(' ').append(num(size)).append(" Tf ")
            .append(num(x)).append(' ').append(num(yy)).append(" Td (")
            .append(escape(clean(s))).append(") Tj ET\n");
    }

    private void fillRect(double x, double yy, double w, double h, double r, double g, double b) {
        page.append(num(r)).append(' ').append(num(g)).append(' ').append(num(b)).append(" rg\n")
            .append(num(x)).append(' ').append(num(yy)).append(' ').append(num(w)).append(' ').append(num(h)).append(" re f\n");
    }

    private void line(double x1, double y1, double x2, double y2, double r, double g, double b) {
        page.append(num(r)).append(' ').append(num(g)).append(' ').append(num(b)).append(" RG 0.8 w\n")
            .append(num(x1)).append(' ').append(num(y1)).append(" m ")
            .append(num(x2)).append(' ').append(num(y2)).append(" l S\n");
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    // ------------------------------------------------------------------ file structure

    private byte[] assemble() throws IOException {
        byte[] content = page.toString().getBytes(StandardCharsets.ISO_8859_1);

        List<byte[]> objects = new ArrayList<>();
        objects.add(ascii("<< /Type /Catalog /Pages 2 0 R >>"));
        objects.add(ascii("<< /Type /Pages /Kids [3 0 R] /Count 1 >>"));
        objects.add(ascii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + (int) PAGE_W + " " + (int) PAGE_H + "] "
                + "/Resources << /Font << /F1 5 0 R /F2 6 0 R >> >> /Contents 4 0 R >>"));

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        stream.write(ascii("<< /Length " + content.length + " >>\nstream\n"));
        stream.write(content);
        stream.write(ascii("\nendstream"));
        objects.add(stream.toByteArray());

        objects.add(ascii("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"));
        objects.add(ascii("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>"));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(ascii("%PDF-1.4\n"));
        int[] offsets = new int[objects.size()];
        for (int i = 0; i < objects.size(); i++) {
            offsets[i] = out.size();
            out.write(ascii((i + 1) + " 0 obj\n"));
            out.write(objects.get(i));
            out.write(ascii("\nendobj\n"));
        }
        int xref = out.size();
        StringBuilder x = new StringBuilder("xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n");
        for (int off : offsets) x.append(String.format("%010d 00000 n \n", off));
        x.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\nstartxref\n")
         .append(xref).append("\n%%EOF\n");
        out.write(ascii(x.toString()));
        return out.toByteArray();
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }
}
