package com.repoguard.reporting;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.repoguard.analysis.AnalysisModels.*;
import org.springframework.stereotype.Component;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class PdfReport {
    public byte[] render(Scan scan, String locale) {
        boolean de = locale.equals("de");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 42, 42, 42, 42);
        PdfWriter writer = PdfWriter.getInstance(document, bytes);
        writer.setPdfVersion(PdfWriter.VERSION_1_7);
        document.addTitle("RepoGuard – " + (de ? "Analysebericht" : "Analysis report"));
        document.addAuthor("RepoGuard"); document.open();
        Font body = font(9), heading = font(17);
        document.add(new Paragraph("RepoGuard", heading));
        paragraph(document, de ? "Statische Analyse – kein Build, keine Ausführung. Kein Nachweis von Sicherheit oder Malwarefreiheit." :
                "Static analysis – no build, no execution. This does not prove security or malware freedom.", body);
        paragraph(document, scan.source() + "\n" + scan.createdAt() + "\nID: " + scan.id() + "\nStatus: " + scan.status(), body);
        if (!scan.status().equals("COMPLETED")) paragraph(document, de ? "ACHTUNG: Analyse eingeschränkt oder nicht abgeschlossen. Prüfabdeckung und Fehler beachten." :
                "WARNING: Analysis is limited or incomplete. Review coverage and errors.", body);
        if (scan.failureMessage() != null) paragraph(document, scan.failureCode() + ": " + scan.failureMessage(), body);
        if (scan.result() != null) {
            Result result = scan.result();
            paragraph(document, "SHA-256: " + result.archiveSha256() + "\nRules: " + result.rulesVersion() + "\nClamAV: " + result.malwareVersion(), body);
            paragraph(document, de ? "Prüfabdeckung" : "Scanner coverage", heading);
            for (Coverage check : result.coverage()) paragraph(document,
                    check.status() + " · " + check.tool() + " · " + (check.language() == null ? "General" : check.language())
                            + " · " + check.check() + "\n" + check.reason(), body);
            paragraph(document, "Findings (" + result.findings().size() + ")", heading);
            for (Finding finding : result.findings()) {
                paragraph(document, finding.severity() + " · " + finding.category() + " · " + finding.ruleId() + "\n" + finding.title(), font(11));
                paragraph(document, finding.path() + (finding.line() > 0 ? ":" + finding.line() : "") + " · " + finding.language() + " · " + finding.tool(), body);
                paragraph(document, finding.explanation() + "\n" + (de ? "Abhilfe: " : "Remediation: ") + finding.remediation(), body);
                if (finding.snippet() != null) paragraph(document, (de ? "Maskierter Ausschnitt, Zeilen " : "Masked snippet, lines ")
                        + finding.snippet().startLine() + "–" + finding.snippet().endLine() + "\n" + finding.snippet().text(), body);
            }
        }
        document.close(); return bytes.toByteArray();
    }
    private static void paragraph(Document document, String text, Font font) {
        Paragraph paragraph = new Paragraph(text, font); paragraph.setSpacingAfter(10); document.add(paragraph);
    }
    private static Font font(float size) {
        String path = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf";
        try { if (Files.exists(Path.of(path))) return new Font(BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED), size); }
        catch (Exception ignored) { }
        return new Font(Font.HELVETICA, size);
    }
}
