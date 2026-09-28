package com.closeloop.web;

import com.closeloop.ai.AiClient;
import com.closeloop.service.StateViewService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 系统级路由：健康检查 / 状态快照 / 记录 / 设置 / 重置 / 知识库浏览 */
@RestController
@RequestMapping("/api")
public class SystemController {

    private final StateViewService view;
    private final AiClient ai;

    public SystemController(StateViewService view, AiClient ai) {
        this.view = view;
        this.ai = ai;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "ok", true,
                "ts", System.currentTimeMillis(),
                "aiReady", ai.hasKey(),
                "breaker", ai.breaker().currentState().name(),
                "service", "learning-loop-java"
        );
    }

    @GetMapping("/state")
    public Map<String, Object> state() {
        return view.publicState();
    }

    @GetMapping("/records")
    public Map<String, Object> records() {
        return view.records();
    }

    @PostMapping("/records/{id}/stall")
    public Map<String, Object> stall(@PathVariable("id") String id, @RequestBody(required = false) Map<String, Object> body) {
        Object mark = Req.orNull(body, "mark");
        return view.markStall(id, mark);
    }

    @PostMapping("/settings")
    public Map<String, Object> settings(@RequestBody(required = false) Map<String, Object> body) {
        return view.saveSettings(Req.map(body, "weights"), Req.str(body, "mode", null));
    }

    @PostMapping("/state/reset")
    public Map<String, Object> reset() {
        return view.reset();
    }

    @GetMapping("/kb")
    public Map<String, Object> kb(@RequestParam(value = "refresh", required = false) String refresh) {
        return view.kbView("1".equals(refresh));
    }

    @PostMapping("/kb/refresh")
    public Map<String, Object> kbRefresh() {
        return view.kbRefresh();
    }

    @GetMapping("/kb/doc")
    public Map<String, Object> kbDoc(@RequestParam("kpId") String kpId) {
        return view.kbDoc(kpId);
    }
}
