package com.duka.matching;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Suggests catalogue matches for text taken from a supplier document.
 * Exact barcode, SKU, supplier alias, and normalized name can be pre-selected.
 * Fuzzy scores are suggestions only — the caller must not treat them as a confirmed match.
 */
public final class ProductMatcher {
    public static final BigDecimal FUZZY_THRESHOLD = new BigDecimal("0.45");

    public record Candidate(long id, String name, String sku, String barcode) {}

    public record Alias(String supplier, String rawName, long productId) {}

    public record Match(Long productId, Long suggestionId, String method, BigDecimal confidence) {}

    public Match match(String rawName, String barcode, String supplier, List<Candidate> products, List<Alias> aliases) {
        if (barcode != null && !barcode.isBlank()) {
            String code = barcode.trim();
            for (Candidate candidate : products) {
                if (code.equals(candidate.barcode())) {
                    return exact(candidate.id(), "BARCODE");
                }
                if (candidate.sku() != null && code.equalsIgnoreCase(candidate.sku())) {
                    return exact(candidate.id(), "SKU");
                }
            }
        }
        String norm = normalize(rawName);
        if (supplier != null && !supplier.isBlank()) {
            for (Alias alias : aliases) {
                if (supplier.equalsIgnoreCase(alias.supplier()) && norm.equals(normalize(alias.rawName()))) {
                    return exact(alias.productId(), "SUPPLIER_MAP");
                }
            }
        }
        if (!norm.isEmpty()) {
            for (Candidate candidate : products) {
                if (norm.equals(normalize(candidate.name())) || norm.equals(normalize(candidate.sku()))) {
                    return exact(candidate.id(), "EXACT_NAME");
                }
            }
        }
        Candidate best = null;
        double bestScore = 0;
        for (Candidate candidate : products) {
            double score = score(norm, normalize(candidate.name()));
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        if (best != null && bestScore >= FUZZY_THRESHOLD.doubleValue()) {
            return new Match(null, best.id(), "FUZZY", BigDecimal.valueOf(bestScore).setScale(4, RoundingMode.HALF_UP));
        }
        return new Match(null, null, null, null);
    }

    private static Match exact(long id, String method) {
        return new Match(id, null, method, BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP));
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    static double score(String left, String right) {
        Set<String> a = tokens(left);
        Set<String> b = tokens(right);
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        long intersection = a.stream().filter(b::contains).count();
        double jaccard = intersection / (double) (a.size() + b.size() - intersection);
        if (b.containsAll(a) || a.containsAll(b)) {
            jaccard = Math.max(jaccard, 0.82);
        }
        return jaccard;
    }

    private static Set<String> tokens(String value) {
        Set<String> tokens = new HashSet<>();
        if (value == null || value.isBlank()) {
            return tokens;
        }
        for (String token : value.split(" ")) {
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return tokens;
    }
}
