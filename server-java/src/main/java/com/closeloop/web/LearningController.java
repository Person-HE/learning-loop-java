package com.closeloop.web;

import com.closeloop.service.CurveService;
import com.closeloop.service.GapService;
import com.closeloop.service.GradingService;
import com.closeloop.service.KpPlanService;
import com.closeloop.service.RestateService;
import com.closeloop.service.WeeklyService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 学习闭环路由：作答评分 / 缺口 / 考点地图 / 合书复述 / 遗忘曲线 / 周报 */
@RestController
@RequestMapping("/api")
public class LearningController {

    private final GradingService grading;
    private final GapService gaps;
    private final KpPlanService kpPlan;
    private final RestateService restate;
    private final CurveService curve;
    private final WeeklyService weekly;

    public LearningController(GradingService grading, GapService gaps, KpPlanService kpPlan,
                              RestateService restate, CurveService curve, WeeklyService weekly) {
        this.grading = grading;
        this.gaps = gaps;
        this.kpPlan = kpPlan;
        this.restate = restate;
        this.curve = curve;
        this.weekly = weekly;
    }

    /** 提交答案 → AI 面试官诊断 + 学习闭环回写 */
    @PostMapping("/answer/score")
    public Map<String, Object> answerScore(@RequestBody(required = false) Map<String, Object> body) {
        return grading.score(
                Req.str(body, "kpId", null),
                Req.str(body, "questionId", null),
                Req.str(body, "answerText", ""),
                Req.str(body, "code", ""),
                Req.str(body, "think", ""),
                Req.bool(body, "spoken"),
                Req.orNull(body, "stallMark"));
    }

    /** 学习舱合书复述评分（只数覆盖 + 漏点提示） */
    @PostMapping("/restate/score")
    public Map<String, Object> restateScore(@RequestBody(required = false) Map<String, Object> body) {
        return restate.score(Req.str(body, "kpId", null), Req.str(body, "questionId", null), Req.str(body, "answerText", ""));
    }

    /** 考点清单 + 记忆卡（懒生成缓存） */
    @GetMapping("/kp/points")
    public Object kpPoints(@RequestParam("kpId") String kpId,
                           @RequestParam(value = "fresh", required = false) String fresh) {
        return kpPlan.points(kpId, "1".equals(fresh));
    }

    @GetMapping("/gaps")
    public List<?> gapsList() {
        return gaps.list();
    }

    /** 缺口检测题：专门考该缺口，答 ≥75 自动消灭 */
    @PostMapping("/gaps/{id}/test")
    public Map<String, Object> gapTest(@PathVariable("id") String id) {
        return gaps.generateTest(id);
    }

    @PostMapping("/gaps/{id}/resolve")
    public Map<String, Object> gapResolve(@PathVariable("id") String id) {
        return gaps.resolve(id);
    }

    /** 遗忘曲线：单知识点双序列 */
    @GetMapping("/curve")
    public Map<String, Object> curveOf(@RequestParam("kpId") String kpId) {
        return curve.curve(kpId);
    }

    @GetMapping("/curve/summary")
    public Map<String, Object> curveSummary() {
        return curve.summary();
    }

    /** AI 教练周报 */
    @PostMapping("/weekly")
    public Map<String, Object> weekly() {
        return weekly.weekly();
    }
}
