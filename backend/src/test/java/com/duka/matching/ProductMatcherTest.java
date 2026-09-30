package com.duka.matching;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProductMatcherTest {
    private final ProductMatcher matcher = new ProductMatcher();
    private final List<ProductMatcher.Candidate> products = List.of(
            new ProductMatcher.Candidate(1, "Milk 500ml", "DAI-001", "6161100000001"),
            new ProductMatcher.Candidate(2, "Blue Band Margarine 500g", "GRO-100", "6161100000099")
    );

    @Test
    void matchesExactBarcodeSkuAndNormalizedName() {
        assertEquals(1L, matcher.match("anything", "6161100000001", null, products, List.of()).productId());
        assertEquals("BARCODE", matcher.match("anything", "6161100000001", null, products, List.of()).method());
        assertEquals(1L, matcher.match("ignored", "DAI-001", null, products, List.of()).productId());
        assertEquals(1L, matcher.match("milk   500ML", null, null, products, List.of()).productId());
        assertEquals("EXACT_NAME", matcher.match("milk   500ML", null, null, products, List.of()).method());
    }

    @Test
    void usesSupplierAliasBeforeFuzzyGuess() {
        var aliases = List.of(new ProductMatcher.Alias("ABC Wholesalers", "BB 500g", 2L));
        var match = matcher.match("BB 500g", null, "ABC Wholesalers", products, aliases);
        assertEquals(2L, match.productId());
        assertEquals("SUPPLIER_MAP", match.method());
    }

    @Test
    void fuzzyMatchIsOnlyASuggestion() {
        var match = matcher.match("Blue Band 500g", null, null, products, List.of());
        assertNull(match.productId());
        assertEquals(2L, match.suggestionId());
        assertEquals("FUZZY", match.method());
    }
}
