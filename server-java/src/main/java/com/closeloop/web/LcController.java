package com.closeloop.web;

import com.closeloop.service.LcService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 力扣 100 题路由：列表 / 详情 / AI 精讲 / 手搓代码入口 / 侧边答疑 */
@RestController
@RequestMapping("/api/lc")
public class LcController {

    private final LcService lc;

    public LcController(LcService lc) {
        this.lc = lc;
    }

    @GetMapping
    public Map<String, Object> list() {
        return lc.list();
    }

    @GetMapping("/{no}")
    public Map<String, Object> detail(@PathVariable("no") String no) {
        return lc.detail(no);
    }

    @PostMapping("/{no}/explain")
    public Map<String, Object> explain(@PathVariable("no") String no) {
        return Map.of("explain", lc.explain(no));
    }

    @PostMapping("/{no}/practice")
    public Map<String, Object> practice(@PathVariable("no") String no) {
        return lc.practice(no);
    }

    @PostMapping("/{no}/ask")
    public Map<String, Object> ask(@PathVariable("no") String no,
                                   @RequestBody(required = false) Map<String, Object> body) {
        List<Map<String, Object>> messages = Req.list(body, "messages");
        return lc.ask(no, messages);
    }
}
