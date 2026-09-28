package com.closeloop.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonExtractorTest {

    @Test
    void extractsPlainObject() {
        JsonNode n = JsonExtractor.extract("{\"a\":1}");
        assertEquals(1, n.path("a").asInt());
    }

    @Test
    void stripsCodeFence() {
        JsonNode n = JsonExtractor.extract("```json\n{\"a\":2}\n```");
        assertEquals(2, n.path("a").asInt());
    }

    @Test
    void extractsArrayFromProse() {
        JsonNode n = JsonExtractor.extract("结果如下：[{\"q\":\"x\"},{\"q\":\"y\"}] 以上");
        assertTrue(n.isArray());
        assertEquals(2, n.size());
    }

    @Test
    void repairsTrailingComma() {
        JsonNode n = JsonExtractor.extract("{\"a\":1,}");
        assertEquals(1, n.path("a").asInt());
    }

    @Test
    void rejectsGarbage() {
        assertThrows(FormatReject.class, () -> JsonExtractor.extract("没有 JSON"));
    }

    @Test
    void nestedStringsWithBraces() {
        JsonNode n = JsonExtractor.extract("{\"q\":\"code { int a = 1; }\", \"n\":3}");
        assertEquals(3, n.path("n").asInt());
        assertTrue(n.path("q").asText().contains("int a"));
    }
}
