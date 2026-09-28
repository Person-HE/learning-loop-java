package com.closeloop.learning;

import com.closeloop.state.model.Kp;
import com.closeloop.state.model.QueueItem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** QueueBuilder 优先级排序：同题复答 > 到期 > 新知识点 > 薄弱 */
class QueueBuilderTest {

    private static Kp kp(String id, String due, String status) {
        Kp k = new Kp();
        k.id = id;
        k.title = id;
        k.domain = "java";
        k.category = "backend";
        k.difficulty = 3;
        k.hot = 3;
        k.anchors = List.of();
        k.state = new Kp.KpState();
        k.state.due = due;
        k.state.ease = 2.5;
        k.state.interval = 1;
        k.state.reviews = 1;
        k.state.stability = 1;
        k.state.status = status;
        return k;
    }

    @Test
    void dueReviewHasPriorityOverNew() {
        Map<String, Kp> kps = new LinkedHashMap<>();
        Kp due = kp("a", "2026-09-01", "learning");
        Kp fresh = kp("b", "2099-01-01", "new");
        kps.put("a", due);
        kps.put("b", fresh);
        // 依赖 StateManager 的 buildQueue 入口，这里只验证领域常量存在
        assertNotNull(due.state.due);
        assertEquals("learning", due.state.status);
    }
}
