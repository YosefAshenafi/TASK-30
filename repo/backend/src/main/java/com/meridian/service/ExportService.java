package com.meridian.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;

@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);
    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneOffset.UTC);

    @Value("${meridian.backup.path:/var/meridian/backups}")
    private String exportBasePath;

    public Path exportCsv(List<Map<String, Object>> data, String reportType) {
        String timestamp = TIMESTAMP_FMT.format(Instant.now());
        String filename = reportType + "_" + timestamp + ".csv";

        Path exportDir = Path.of(exportBasePath, "exports");
        try {
            Files.createDirectories(exportDir);
        } catch (IOException e) {
            log.warn("Could not create export directory {}, using temp dir", exportDir);
            exportDir = Path.of(System.getProperty("java.io.tmpdir"), "meridian-exports");
            try {
                Files.createDirectories(exportDir);
            } catch (IOException ex) {
                throw new RuntimeException("Failed to create export directory", ex);
            }
        }

        Path outputPath = exportDir.resolve(filename);

        Set<String> headers = new LinkedHashSet<>();
        if (!data.isEmpty()) {
            headers.addAll(data.get(0).keySet());
        }

        try (FileWriter writer = new FileWriter(outputPath.toFile());
             CSVPrinter printer = new CSVPrinter(writer,
                     CSVFormat.DEFAULT.builder()
                             .setHeader(headers.toArray(new String[0]))
                             .build())) {

            for (Map<String, Object> row : data) {
                List<Object> values = new ArrayList<>();
                for (String header : headers) {
                    Object value = row.get(header);
                    values.add(value != null ? value.toString() : "");
                }
                printer.printRecord(values);
            }

        } catch (IOException e) {
            log.error("Failed to write CSV export file {}", outputPath, e);
            throw new RuntimeException("CSV export failed: " + e.getMessage(), e);
        }

        log.info("CSV export written to {}", outputPath);
        return outputPath;
    }

    public Path exportPdf(List<Map<String, Object>> data, String reportType) {
        String timestamp = TIMESTAMP_FMT.format(Instant.now());
        String filename = reportType + "_" + timestamp + ".pdf";

        Path exportDir = Path.of(exportBasePath, "exports");
        try {
            Files.createDirectories(exportDir);
        } catch (IOException e) {
            log.warn("Could not create export directory {}, using temp dir", exportDir);
            exportDir = Path.of(System.getProperty("java.io.tmpdir"), "meridian-exports");
            try {
                Files.createDirectories(exportDir);
            } catch (IOException ex) {
                throw new RuntimeException("Failed to create export directory", ex);
            }
        }

        Path outputPath = exportDir.resolve(filename);

        Set<String> headers = new LinkedHashSet<>();
        if (!data.isEmpty()) {
            headers.addAll(data.get(0).keySet());
        }

        try (PdfWriter pdfWriter = new PdfWriter(outputPath.toFile());
             PdfDocument pdfDocument = new PdfDocument(pdfWriter);
             Document document = new Document(pdfDocument)) {

            document.add(new Paragraph(reportType.replace("_", " ").toUpperCase())
                    .setBold()
                    .setFontSize(16));
            document.add(new Paragraph("Generated: " + Instant.now().toString())
                    .setFontSize(10));

            if (!data.isEmpty()) {
                Table table = new Table(headers.size());
                table.setWidth(com.itextpdf.layout.properties.UnitValue.createPercentValue(100));

                for (String header : headers) {
                    table.addHeaderCell(new Cell().add(new Paragraph(header).setBold()));
                }

                for (Map<String, Object> row : data) {
                    for (String header : headers) {
                        Object value = row.get(header);
                        table.addCell(new Cell().add(new Paragraph(value != null ? value.toString() : "")));
                    }
                }

                document.add(table);
            } else {
                document.add(new Paragraph("No data available for this report."));
            }

        } catch (IOException e) {
            log.error("Failed to write PDF export file {}", outputPath, e);
            throw new RuntimeException("PDF export failed: " + e.getMessage(), e);
        }

        log.info("PDF export written to {}", outputPath);
        return outputPath;
    }
}
