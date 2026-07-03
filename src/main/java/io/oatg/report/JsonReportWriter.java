package io.oatg.report;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.oatg.OatgException;

import java.io.IOException;
import java.nio.file.Path;

/** Writes the machine-readable {@code report.json}. */
public final class JsonReportWriter {

    private final ObjectMapper mapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public Path write(ReportModel model, Path outDir) {
        Path file = outDir.resolve("report.json");
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), model);
        } catch (IOException e) {
            throw new OatgException("Could not write " + file + ": " + e.getMessage(), e);
        }
        return file;
    }
}
