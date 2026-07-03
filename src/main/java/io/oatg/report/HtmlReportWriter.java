package io.oatg.report;

import io.oatg.OatgException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Renders a self-contained {@code report.html} from the bundled template. */
public final class HtmlReportWriter {

    private static final String TEMPLATE = "/report-template.html";

    public Path write(ReportModel model, Path outDir) {
        String html = loadTemplate()
                .replace("{{SPEC_TITLE}}", escape(model.specTitle()))
                .replace("{{SPEC_LOCATION}}", escape(model.specLocation()))
                .replace("{{BASE_URL}}", escape(model.baseUrl() != null ? model.baseUrl() : "—"))
                .replace("{{SEED}}", String.valueOf(model.seed()))
                .replace("{{TIMESTAMP}}", escape(model.timestamp()))
                .replace("{{TOTAL}}", String.valueOf(model.totals().total()))
                .replace("{{PASSED}}", String.valueOf(model.totals().passed()))
                .replace("{{FAILED}}", String.valueOf(model.totals().failed()))
                .replace("{{ERRORS}}", String.valueOf(model.totals().errors()))
                .replace("{{SKIPPED}}", String.valueOf(model.totals().skipped()))
                .replace("{{ROWS}}", rows(model));
        Path file = outDir.resolve("report.html");
        try {
            Files.writeString(file, html, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new OatgException("Could not write " + file + ": " + e.getMessage(), e);
        }
        return file;
    }

    private String rows(ReportModel model) {
        StringBuilder sb = new StringBuilder();
        for (ReportModel.Entry entry : model.entries()) {
            sb.append("    <tr class=\"").append(entry.verdict()).append("\">")
              .append("<td class=\"v-").append(entry.verdict()).append("\">").append(entry.verdict()).append("</td>")
              .append("<td>").append(escape(entry.method())).append("</td>")
              .append("<td><code>").append(escape(entry.path())).append("</code></td>")
              .append("<td>").append(escape(entry.operationId())).append("</td>")
              .append("<td>").append(entry.status() != null ? entry.status() : "—").append("</td>")
              .append("<td>").append(entry.latencyMs() != null ? entry.latencyMs() + " ms" : "—").append("</td>")
              .append("<td>").append(escape(entry.reason() != null ? entry.reason() : "")).append("</td>")
              .append("</tr>\n");
        }
        return sb.toString();
    }

    private String loadTemplate() {
        try (InputStream in = HtmlReportWriter.class.getResourceAsStream(TEMPLATE)) {
            if (in == null) {
                throw new OatgException("Report template missing from classpath: " + TEMPLATE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new OatgException("Could not read report template: " + e.getMessage(), e);
        }
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
