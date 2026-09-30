package com.duka.service;

import com.duka.config.DukaProperties;
import com.duka.domain.*;
import com.duka.matching.ProductMatcher;
import com.duka.repo.*;
import com.duka.security.UserPrincipal;
import com.duka.web.ApiException;
import com.duka.web.Dto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExtractionService {
    private final DukaProperties properties;
    private final StoredDocumentRepository documents;
    private final ReceiptExtractionRepository extractions;
    private final ProductRepository products;
    private final SupplierProductMapRepository maps;
    private final StockReceiptService stockReceipts;
    private final AuditService audit;
    private final ObjectMapper json;
    private final ProductMatcher matcher = new ProductMatcher();

    @Transactional
    public Dto.ExtractionView ingest(MultipartFile file, String supplierName, String invoiceNumber, UserPrincipal user) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(400, "Choose a file to upload");
        }
        String original = file.getOriginalFilename() == null ? "upload" : Path.of(file.getOriginalFilename()).getFileName().toString();
        String lower = original.toLowerCase();
        if (!(lower.endsWith(".pdf") || lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp") || lower.endsWith(".txt") || lower.endsWith(".csv"))) {
            throw new ApiException(400, "Upload a PDF, text file, or image of the supplier invoice");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new ApiException(400, "Could not read the uploaded file");
        }
        Path dir = Path.of(properties.getStorage().getPath());
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(UUID.randomUUID() + "-" + original.replaceAll("[^A-Za-z0-9._-]", "_"));
            Files.write(target, bytes);
            StoredDocument document = new StoredDocument();
            document.setOriginalName(original);
            document.setContentType(file.getContentType());
            document.setStoragePath(target.toString());
            document.setUploadedBy(user.getId());
            documents.save(document);

            String text = extractText(original, bytes);
            List<ParsedLine> parsed = parse(original, text);
            ReceiptExtraction extraction = new ReceiptExtraction();
            extraction.setDocumentId(document.getId());
            extraction.setSupplierName(supplierName);
            extraction.setInvoiceNumber(invoiceNumber);
            extraction.setStatus(ReceiptStatus.REVIEW);
            if (parsed.isEmpty()) {
                extraction.setNotes(emptyNote(text));
            }
            List<ProductMatcher.Candidate> candidates = candidates();
            List<ProductMatcher.Alias> aliases = aliases();
            for (ParsedLine parsedLine : parsed) {
                ExtractionLine line = new ExtractionLine();
                line.setExtraction(extraction);
                line.setRawName(parsedLine.name());
                line.setQuantity(parsedLine.qty());
                line.setUnitCost(parsedLine.cost());
                line.setBarcode(parsedLine.barcode());
                ProductMatcher.Match match = matcher.match(parsedLine.name(), parsedLine.barcode(), supplierName, candidates, aliases);
                line.setMatchedProductId(match.productId());
                line.setSuggestedProductId(match.suggestionId());
                line.setMatchMethod(match.method());
                line.setConfidence(match.confidence());
                extraction.getLines().add(line);
            }
            ReceiptExtraction saved = extractions.save(extraction);
            audit.log(user.getName(), "uploaded a supplier document", original);
            return toView(saved);
        } catch (IOException ex) {
            throw new ApiException(500, "Could not store the document");
        }
    }

    @Transactional(readOnly = true)
    public List<Dto.ExtractionView> list(String status) {
        List<ReceiptExtraction> rows = status == null || status.isBlank()
                ? extractions.findAll()
                : extractions.findByStatusOrderByCreatedAtDesc(ReceiptStatus.valueOf(status.toUpperCase()));
        return rows.stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public Dto.ExtractionView get(long id) {
        return toView(load(id));
    }

    @Transactional
    public Dto.ExtractionView update(long id, Dto.ExtractionUpdate update) {
        ReceiptExtraction extraction = load(id);
        if (extraction.getStatus() == ReceiptStatus.APPROVED || extraction.getStatus() == ReceiptStatus.REJECTED) {
            throw new ApiException(409, "This document is already closed");
        }
        if (update.supplierName() != null) {
            extraction.setSupplierName(update.supplierName());
        }
        if (update.invoiceNumber() != null) {
            extraction.setInvoiceNumber(update.invoiceNumber());
        }
        if (update.lines() != null) {
            for (Dto.LineEdit edit : update.lines()) {
                if (edit.id() == null) {
                    ExtractionLine line = new ExtractionLine();
                    line.setExtraction(extraction);
                    applyEdit(line, edit);
                    extraction.getLines().add(line);
                } else {
                    extraction.getLines().stream().filter(line -> line.getId().equals(edit.id())).findFirst()
                            .ifPresent(line -> applyEdit(line, edit));
                }
            }
        }
        return toView(extraction);
    }

    @Transactional
    public Dto.ExtractionView approve(long id, UserPrincipal user) {
        ReceiptExtraction extraction = load(id);
        if (extraction.getStatus() == ReceiptStatus.APPROVED) {
            return toView(extraction);
        }
        if (extraction.getStatus() == ReceiptStatus.REJECTED) {
            throw new ApiException(409, "This document was rejected");
        }
        List<ExtractionLine> active = extraction.getLines().stream().filter(line -> !line.isRemoved()).toList();
        if (active.isEmpty()) {
            throw new ApiException(400, "Add at least one line before approving");
        }
        List<StockReceiptService.Line> lines = new ArrayList<>();
        for (ExtractionLine line : active) {
            if (line.getMatchedProductId() == null) {
                throw new ApiException(400, "Match a catalogue product for \"" + line.getRawName() + "\" before approving");
            }
            lines.add(new StockReceiptService.Line(line.getMatchedProductId(), line.getQuantity(), line.getUnitCost()));
            rememberAlias(extraction.getSupplierName(), line);
        }
        StockReceipt receipt = stockReceipts.approveNew(extraction.getSupplierName(), extraction.getInvoiceNumber(),
                extraction.getDocumentId(), lines, user);
        extraction.setStatus(ReceiptStatus.APPROVED);
        extraction.setStockReceiptId(receipt.getId());
        extraction.setReviewedBy(user.getId());
        extraction.setReviewedAt(Instant.now());
        audit.log(user.getName(), "approved imported receipt #" + extraction.getId(), extraction.getInvoiceNumber());
        return toView(extraction);
    }

    @Transactional
    public Dto.ExtractionView reject(long id, UserPrincipal user) {
        ReceiptExtraction extraction = load(id);
        if (extraction.getStatus() == ReceiptStatus.APPROVED) {
            throw new ApiException(409, "An approved document cannot be rejected");
        }
        extraction.setStatus(ReceiptStatus.REJECTED);
        extraction.setReviewedBy(user.getId());
        extraction.setReviewedAt(Instant.now());
        audit.log(user.getName(), "rejected imported receipt #" + extraction.getId(), "");
        return toView(extraction);
    }

    private void rememberAlias(String supplier, ExtractionLine line) {
        if (supplier == null || supplier.isBlank() || line.getRawName() == null) {
            return;
        }
        maps.findBySupplierNameIgnoreCaseAndRawNameIgnoreCase(supplier, line.getRawName()).ifPresentOrElse(existing -> {
            existing.setProductId(line.getMatchedProductId());
        }, () -> {
            SupplierProductMap map = new SupplierProductMap();
            map.setSupplierName(supplier.trim());
            map.setRawName(line.getRawName().trim());
            map.setProductId(line.getMatchedProductId());
            maps.save(map);
        });
    }

    private void applyEdit(ExtractionLine line, Dto.LineEdit edit) {
        if (edit.rawName() != null && !edit.rawName().isBlank()) {
            line.setRawName(edit.rawName().trim());
        } else if (line.getRawName() == null) {
            line.setRawName("Item");
        }
        if (edit.quantity() != null) {
            if (edit.quantity() <= 0) {
                throw new ApiException(400, "quantity must be positive");
            }
            line.setQuantity(edit.quantity());
        } else if (line.getQuantity() <= 0) {
            line.setQuantity(1);
        }
        if (edit.unitCost() != null) {
            if (edit.unitCost().signum() < 0) {
                throw new ApiException(400, "cost cannot be negative");
            }
            line.setUnitCost(Money.money(edit.unitCost()));
        } else if (line.getUnitCost() == null) {
            line.setUnitCost(BigDecimal.ZERO);
        }
        if (edit.barcode() != null) {
            line.setBarcode(edit.barcode());
        }
        if (edit.matchedProductId() != null) {
            if (!products.existsById(edit.matchedProductId())) {
                throw new ApiException(404, "Product not found");
            }
            line.setMatchedProductId(edit.matchedProductId());
            line.setMatchMethod("MANUAL");
        }
        if (edit.removed() != null) {
            line.setRemoved(edit.removed());
        }
    }

    private ReceiptExtraction load(long id) {
        return extractions.findById(id).orElseThrow(() -> new ApiException(404, "Receipt review not found"));
    }

    private Dto.ExtractionView toView(ReceiptExtraction extraction) {
        List<Dto.LineView> lines = extraction.getLines().stream().map(line -> new Dto.LineView(
                line.getId(),
                line.getRawName(),
                line.getQuantity(),
                line.getUnitCost(),
                line.getBarcode(),
                line.getMatchedProductId(),
                nameOf(line.getMatchedProductId()),
                line.getSuggestedProductId(),
                nameOf(line.getSuggestedProductId()),
                line.getMatchMethod(),
                line.getConfidence(),
                line.isRemoved()
        )).toList();
        return new Dto.ExtractionView(extraction.getId(), extraction.getDocumentId(), extraction.getSupplierName(),
                extraction.getInvoiceNumber(), extraction.getStatus().name(), extraction.getStockReceiptId(),
                extraction.getReviewedBy(), extraction.getReviewedAt(), extraction.getNotes(), extraction.getCreatedAt(), lines);
    }

    private String nameOf(Long id) {
        if (id == null) {
            return null;
        }
        return products.findById(id).map(Product::getName).orElse(null);
    }

    private List<ProductMatcher.Candidate> candidates() {
        return products.findAll().stream()
                .map(product -> new ProductMatcher.Candidate(product.getId(), product.getName(), product.getSku(), product.getBarcode()))
                .toList();
    }

    private List<ProductMatcher.Alias> aliases() {
        return maps.findAll().stream()
                .map(map -> new ProductMatcher.Alias(map.getSupplierName(), map.getRawName(), map.getProductId()))
                .toList();
    }

    private String extractText(String filename, byte[] bytes) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".csv") || lower.endsWith(".txt") || lower.endsWith(".json")) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        if (lower.endsWith(".pdf")) {
            return run(List.of("pdftotext", "-", "-"), bytes);
        }
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp")) {
            return runTesseract(bytes, lower.substring(lower.lastIndexOf('.') + 1));
        }
        return "";
    }

    private String runTesseract(byte[] bytes, String extension) {
        try {
            Path temp = Files.createTempFile("duka-ocr-", "." + extension);
            Files.write(temp, bytes);
            try {
                return run(List.of("tesseract", temp.toString(), "stdout"), new byte[0]);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException ex) {
            return "";
        }
    }

    private String run(List<String> command, byte[] stdin) {
        try {
            Process process = new ProcessBuilder(command).start();
            if (stdin.length > 0) {
                process.getOutputStream().write(stdin);
            }
            process.getOutputStream().close();
            byte[] output = process.getInputStream().readAllBytes();
            int code = process.waitFor();
            if (code != 0) {
                return "";
            }
            return new String(output, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "";
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return "";
        }
    }

    private List<ParsedLine> parse(String filename, String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String trimmed = text.strip();
        if (filename.toLowerCase(Locale.ROOT).endsWith(".json") || trimmed.startsWith("[")) {
            return parseJson(trimmed);
        }
        return parseCsv(trimmed);
    }

    private List<ParsedLine> parseJson(String text) {
        try {
            JsonNode node = json.readTree(text);
            if (!node.isArray()) {
                return List.of();
            }
            List<ParsedLine> lines = new ArrayList<>();
            for (JsonNode row : node) {
                String name = row.path("name").asText("");
                int qty = row.path("qty").asInt(row.path("quantity").asInt(0));
                BigDecimal cost = row.has("unit_cost") ? row.get("unit_cost").decimalValue() : row.path("cost").decimalValue();
                String barcode = row.path("barcode").asText(null);
                if (!name.isBlank() && qty > 0) {
                    lines.add(new ParsedLine(name, qty, cost, barcode));
                }
            }
            return lines;
        } catch (IOException ex) {
            return List.of();
        }
    }

    private List<ParsedLine> parseCsv(String text) {
        String[] rows = text.split("\\R");
        if (rows.length == 0) {
            return List.of();
        }
        String header = rows[0].toLowerCase(Locale.ROOT);
        int start = header.contains("name") && (header.contains("qty") || header.contains("quantity")) ? 1 : 0;
        if (start == 0 && !looksNumericRow(rows[0])) {
            return List.of();
        }
        List<ParsedLine> lines = new ArrayList<>();
        for (int i = start; i < rows.length; i++) {
            if (rows[i].isBlank()) {
                continue;
            }
            String[] cols = rows[i].split(",");
            if (cols.length < 3) {
                continue;
            }
            try {
                String name = cols[0].trim();
                int qty = Integer.parseInt(cols[1].trim());
                BigDecimal cost = new BigDecimal(cols[2].trim());
                String barcode = cols.length > 3 && !cols[3].isBlank() ? cols[3].trim() : null;
                if (!name.isBlank() && qty > 0) {
                    lines.add(new ParsedLine(name, qty, cost, barcode));
                }
            } catch (NumberFormatException ignored) {
                // skip a line the parser cannot trust
            }
        }
        return lines;
    }

    private static boolean looksNumericRow(String row) {
        String[] cols = row.split(",");
        if (cols.length < 3) {
            return false;
        }
        try {
            Integer.parseInt(cols[1].trim());
            new BigDecimal(cols[2].trim());
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static String emptyNote(String text) {
        String prefix = "No structured lines were extracted. Inventory has not changed. Enter the lines, match products, then approve.";
        if (text == null || text.isBlank()) {
            return prefix + " OCR is unavailable on this machine (tesseract is not installed), so a photo needs to be typed in.";
        }
        String snippet = text.length() > 1500 ? text.substring(0, 1500) : text;
        return prefix + "\n\nExtracted text:\n" + snippet;
    }

    private record ParsedLine(String name, int qty, BigDecimal cost, String barcode) {}
}
