package com.closeloop.web;

import com.closeloop.service.QuestionService;
import com.closeloop.service.StateViewService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 今日队列与出题路由 */
@RestController
@RequestMapping("/api")
public class QueueController {

    private final StateViewService view;
    private final QuestionService questions;

    public QueueController(StateViewService view, QuestionService questions) {
        this.view = view;
        this.questions = questions;
    }

    /** 组装今日队列（不生成题目） */
    @PostMapping("/queue/build")
    public Map<String, Object> build() {
        return view.buildQueue();
    }

    /** 今日选题计划（只读：锁定卡 + 各大类候选 + 模块池 + 薄弱建议） */
    @GetMapping("/today/plan")
    public Map<String, Object> plan() {
        return view.buildPlan();
    }

    /** 确认选题并写回今日队列（锁定不砍，picks 按序补到 maxQ） */
    @PostMapping("/today/confirm")
    public Map<String, Object> confirm(@RequestBody(required = false) Map<String, Object> body) {
        return view.confirmPlan(Req.strList(body, "picks"));
    }

    /** 预生成队列所有题目（并行，限并发 2） */
    @PostMapping("/queue/prepare")
    public Map<String, Object> prepare() {
        return view.prepareQueue(questions);
    }

    /** 单知识点重新出题（地图/曲线直接开始） */
    @PostMapping("/questions/gen")
    public Map<String, Object> gen(@RequestBody(required = false) Map<String, Object> body) {
        return questions.genDirect(Req.str(body, "kpId", null));
    }
}
