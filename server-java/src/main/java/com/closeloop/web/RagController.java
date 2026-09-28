package com.closeloop.web;

import com.closeloop.application.rag.RagService;
import com.closeloop.infrastructure.ai.KnowNomicEmbeddingModel;
import com.closeloop.knowledge.KbService;
import com.closeloop.knowledge.KnowledgeBase;
import com.closeloop.state.model.Kp;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** RAG：Qdrant 向量检索 + 索引管道 */
@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagService rag;
    private final KbService kb;

    public RagController(RagService rag, KbService kb) {
        this.rag = rag;
        this.kb = kb;
    }

    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam("q") String q,
                                      @RequestParam(value = "topK", defaultValue = "6") int topK) {
        long t0 = System.nanoTime();
        List<Map<String, Object>> hits = new ArrayList<>();
        for (RagService.Hit h : rag.search(q, topK)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", h.id());
            m.put("docId", h.docId());
            m.put("heading", h.heading());
            m.put("score", h.score());
            String p = h.text();
            m.put("preview", p.length() > 160 ? p.substring(0, 160) : p);
            hits.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("q", q);
        out.put("ms", (System.nanoTime() - t0) / 1_000_000);
        out.put("model", KnowNomicEmbeddingModel.MODEL_ID);
        out.put("store", "qdrant");
        out.put("hits", hits);
        return out;
    }

    @PostMapping("/reindex")
    public Map<String, Object> reindex(@RequestParam(value = "limit", defaultValue = "0") int limit) {
        long t0 = System.nanoTime();
        KnowledgeBase kbAll = kb.getKb(false);
        int scanned = 0, indexed = 0, failed = 0;
        List<String> errors = new ArrayList<>();
        for (Kp kp : kbAll.kps) {
            if (limit > 0 && scanned >= limit) break;
            if (kp.isLc || kp.path == null || kp.path.isEmpty()) continue;
            scanned++;
            try {
                String md = kb.readDoc(kp);
                if (md == null || md.isBlank()) continue;
                String docId = kp.path.replace('\\', '/');
                RagService.IndexResult r = rag.indexDoc(docId, kp.id, kp.domain, kp.title, md);
                if (!r.skipped()) indexed++;
            } catch (Exception e) {
                failed++;
                if (errors.size() < 5) errors.add(kp.id + ": " + e.getMessage());
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scanned", scanned);
        out.put("indexed", indexed);
        out.put("failed", failed);
        out.put("store", "qdrant");
        out.put("ms", (System.nanoTime() - t0) / 1_000_000);
        out.put("errors", errors);
        return out;
    }
}
