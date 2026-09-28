package com.closeloop.ai.prompt;

import com.closeloop.ai.AiMessage;
import com.closeloop.knowledge.LcDataService.LcProblem;
import com.closeloop.state.model.LcExplain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 力扣模块 Prompt：⑨ 零基础精讲 / ⑩ 复习出题 / ⑪ 手搓代码评审 / ⑫ 侧边 AI 答疑 */
public final class LcPrompts {

    private LcPrompts() {}

    // ============ ⑨ LC 题精讲 ============
    public static List<AiMessage> explain(LcProblem p) {
        String system = "你是国内顶尖算法面试官兼教学老师，最擅长把一道算法题讲给零基础小白听懂。你的铁律：绝不跳跃思路，绝不直接给结论，每一个细小思考都必须给出\"为什么会想到这一步\"，让读者能复现你的思考过程而不是背答案。";
        String user = "请为下面这道力扣题写一份面向【零基础小白】的完整精讲，只输出 JSON。\n\n"
                + "【题目】\n"
                + "力扣 " + p.no + ". " + p.title + "（" + p.dName() + "）\n"
                + "题面：" + Tpl.j(p.q) + "\n"
                + "示例：" + toJson(p.ex) + "\n"
                + "约束：" + Tpl.j(p.c) + "\n"
                + "算法标签：" + Tpl.j(p.category) + " / " + Tpl.j(p.algo) + "\n\n"
                + """
【输出要求】（严格按此结构）
1. "point"：一句话点出本题核心思想（如"哈希表空间换时间"）。
2. "thinking"：数组，元素是 {"t":"这一步在做什么","w":"为什么能想到这一步/背后的直觉"}。
   - 必须从"先读懂题目要什么"开始，逐步推进：暴力解法 → 暴力慢在哪 → 关键洞察怎么来的 → 数据结构/算法为什么选它 → 如何把思路变成代码 → 复杂度 → 边界。
   - 这是最重要的部分：每个细小思考都必须有"怎么想出来的"，不准跳步、不准只给结论。
   - 8~12 步。
3. "solutions"：数组，至少 2 个解法，每个是 {"name","idea","code","time","space","note"}。
   - code 必须是完整可运行的 Java 代码（含 import、方法签名）。
   - 第一个是最优解；按难度给暴力解做对照。
4. "animation"：动画步骤数据，格式 {"type":"数组/栈/链表/树/DP表格/哈希表 之一","frames":[{"desc":"这一步在做什么","...":"可选状态字段（数组用 nums/i/j/cur/max 等；栈用 stack；链表用 nodes/cur/prev；DP 用表格值）"}]}。
   - 5~10 帧，每帧 desc 用大白话解释这一步发生了什么、为什么。
5. "answerPoints"：标准答案要点清单（4~6 条，面试官踩点用）。
6. "edge"：边界情况与坑（3~5 条）。
7. "tips"：面试答题技巧（3~4 条）。

只输出 JSON，不要任何解释文字。""";
        return Tpl.pair(system, user);
    }

    // ============ ⑩ LC 复习出题 ============
    public static List<AiMessage> review(LcProblem p, String learner) {
        String system = "你是国内一线大厂算法面试官。你在面试中考察候选人对算法题的掌握程度，问法短促直接、像真实面试，绝不把答案写进题干，绝不使用教学腔词汇（第一性原理、推演、请结合 等）。";
        String user = "候选人在复习这道算法题，请按真实面试官口吻出 2 道考察题，只输出 JSON 数组。\n\n"
                + "【题目背景】\n"
                + "力扣 " + p.no + ". " + p.title + "（" + Tpl.j(p.category) + " / " + Tpl.j(p.algo) + "）\n"
                + "题面：" + Tpl.j(p.q) + "\n\n"
                + "【候选人上次情况】\n" + Tpl.or(learner, "首次复习") + "\n\n"
                + "【出题铁律】\n"
                + "1. 第 1 题 T2/T3：针对本题核心思想深挖或辨析，如\"为什么用X不用Y\"\"复杂度为什么是这样\"\"换一个输入会怎样\"。不重复题面。\n"
                + "2. 第 2 题 T6：出一道本题的变体手写题，必须包含完整题面+输入输出示例（可改约束/改条件/改数据结构），必须让候选人动手写代码。\n"
                + "3. 每题含 point（≤12 字考点）、difficulty（1-5）、type、answerPoints（标准踩点 3-6 条）、anchor（本题对应 LeetCode 题号，如 \"lc-" + p.no + "\"）。\n"
                + "4. 每道题只输出 question 字段，绝不在题干里出现答案。\n\n"
                + "输出 JSON 数组：[{\"type\":\"T2\",\"difficulty\":3,\"point\":\"…\",\"question\":\"…\",\"answerPoints\":[\"…\"],\"anchor\":\"lc-" + p.no + "\"}, …]";
        return Tpl.pair(system, user);
    }

    // ============ ⑪ LC 纯手搓代码评审 ============
    public static List<AiMessage> codeScore(LcProblem p, LcExplain sol, String code) {
        String system = "你是一位资深算法面试官，正在线上面试候选人的手写代码环节（力扣模式）。候选人只提交代码，没有口头讲解。你要像真实面试官看白板代码一样：先判断代码能不能过样例、有没有隐藏 bug、复杂度对不对、边界守不守得住、写得好不好，再给出这一轮的通过裁决。给分心理：代码正确且高效 + 边界完备 → 90+ 直接过；思路对但有小瑕疵 → 70~89 过，指出问题；有明显错误或复杂度不达标 → <70 挂，必须说清错在哪。你的评语是面试官口吻，具体、直接。";
        String solPoints = sol == null || sol.answerPoints == null ? "" : String.join("；", sol.answerPoints);
        String user = "请以面试官身份评审候选人提交的这段代码，输出五层结构化报告（JSON）。\n\n"
                + "【题目】\n"
                + "力扣 " + p.no + ". " + p.title + "（" + p.dName() + "｜" + Tpl.j(p.category) + " / " + Tpl.j(p.algo) + "）\n"
                + "题面：" + Tpl.j(p.q) + "\n"
                + "示例：" + toJson(p.ex) + "\n"
                + "约束：" + Tpl.j(p.c) + "\n\n"
                + "【标准要点（评分对照）】\n" + Tpl.or(solPoints, "（无）") + "\n\n"
                + "【候选人提交的代码】\n" + Tpl.or(code, "（未提交代码）") + "\n\n"
                + """
【评分铁律】
1. 先从标准要点 + 题目本身提炼这道题必须满足的验收点（total_count 个，5~8 个）：正确解法核心 / 时间复杂度 / 空间复杂度 / 关键边界（空输入、单元素、无解、溢出、重复元素等）/ 是否最优解。逐点核对候选代码覆盖了几个（covered_count），coverage_score = round(covered_count / total_count × 100)。
2. 面试官修正（在覆盖分基础上）：代码完全正确可直接运行 +10~15；能通过全部示例 I/O +5；有明显逻辑错误/编译错误 -30；时间复杂度非最优 -20；空间非最优 -10；命名混乱/缩进差 -5；关键边界没处理 -10。最终 total_score = clamp(0, 100)。
3. 满分可达约束：代码正确、复杂度最优、边界完备 → total_score 必须 ≥90，禁止刻意压分。
4. verdict：≥80 "pass"（这轮代码我给你过）/ 60~79 "followup"（我看过，需再追问或重写）/ <60 "fail"（这轮挂了）。
5. level：≥90 优秀 / ≥75 良好 / ≥60 及格 / ≥40 不及格 / 否则 空白。
6. score_breakdown：一句话把分数来源讲清楚（如「5/7 验收点覆盖 = 71 分，代码正确可运行 +15 → 86」）。
7. profile 画像：全过且最优→「掌握」；思路对但有小瑕疵→「会做欠打磨」；有思路但实现有错→「有思路不会写」；完全写不出/明显错误→「未掌握」。desc 用面试官口吻一句话说明。
8. standard_points：每个验收点 + why（面试官为什么盯这个点）。
9. point_compare：逐点对比 status(covered/partial/missing/wrong)；mine 引用候选代码里的原句（缺失则空）；diff 说明差距；fix 给出补法（可给关键代码片段）。
10. feedback：按影响排序 ≤3 条，每条 issue（引用代码原句/指出问题）+ improve（具体怎么改）。
11. optimized_answer：给出本题最优解法完整 Java 代码（≤60 行，可直接运行），并在开头一句话点评候选代码与最优解的差距。
12. rewrite_diff：逐处展示优化——type(keep/rewrite/add/fix/reorder)；where 定位代码位置；original 引用候选代码原句；optimized 是优化后代码；note 说明为什么（对应哪个验收点）。≤8 处。
13. missing_points / errors / gaps(标签照旧) / advice(level/action/target/read/practice，target 留空由系统处理) 照旧生成。
14. dimensions（诊断旁注，不参与算分）：正确性(40)/时间复杂度(20)/空间复杂度(20)/代码规范(20)。

【输出 JSON（结构必须完整，只输出 JSON）】
{
  "verdict": "followup",
  "total_score": 72,
  "level": "及格",
  "coverage_score": 71,
  "score_breakdown": "5/7 验收点覆盖 = 71 分，代码正确可运行 +15 → 86",
  "profile": {"tag": "会做欠打磨", "desc": "思路对，但边界处理不完整"},
  "standard_points": [{"id":"P1","text":"…","why":"…"}],
  "point_compare": [{"id":"P1","status":"partial","mine":"候选代码原句","diff":"…","fix":"…"}],
  "covered_count": 5, "total_count": 7,
  "feedback": [{"issue":"…","improve":"下次这样写：…"}],
  "dimensions": [{"name":"正确性","score":30,"max":40,"comment":"…"}],
  "missing_points": ["…"],
  "errors": ["…"],
  "gaps": [{"label":"边界缺失","detail":"…"}],
  "optimized_answer": "…",
  "rewrite_diff": [{"type":"fix","where":"第3行","original":"…","optimized":"…","note":"…"}],
  "advice": {"level":"review","action":"回到 LC 精讲复习","target":"","read":"…","practice":"…"}
}""";
        return Tpl.pair(system, user);
    }

    // ============ ⑫ LC AI 答疑（自然语言输出） ============
    public static List<AiMessage> ask(LcProblem p, LcExplain sol, List<Map<String, Object>> history) {
        String system = "你是一位耐心的算法老师，专门为正在刷力扣题的学习者答疑。你的目标：让学习者真正想通，而不是直接喂答案。回答规则：1) 先直接回答他的问题（结论先行）；2) 如果他在思路上卡住，用引导式提问/类比帮他想到关键洞察，但最终给出明确结论；3) 涉及代码时给关键片段（Java），不贴整段完整解法除非他明确要求；4) 语言口语化、分点清晰、控制在 400 字内；5) 不知道就直说，不编造。";
        String solText = "";
        if (sol != null) {
            StringBuilder th = new StringBuilder();
            for (int i = 0; i < Math.min(4, sol.thinking.size()); i++) {
                th.append(i > 0 ? "\n" : "").append("- ").append(String.valueOf(sol.thinking.get(i).get("t")));
            }
            List<String> sols = new ArrayList<>();
            for (Map<String, Object> s : sol.solutions) {
                sols.add(s.get("name") + "（" + s.get("time") + "/" + s.get("space") + "）");
            }
            solText = "\n【本题精讲（已有）】\n核心思想：" + Tpl.j(sol.point)
                    + "\n思考链摘要：" + th
                    + "\n解法：" + String.join("；", sols);
        }
        StringBuilder hist = new StringBuilder();
        int from = Math.max(0, (history == null ? 0 : history.size()) - 6);
        if (history != null) {
            for (int i = from; i < history.size(); i++) {
                Map<String, Object> msg = history.get(i);
                String who = "user".equals(msg.get("role")) ? "学习者" : "你";
                hist.append(i > from ? "\n" : "").append(who).append("：")
                        .append(Tpl.cut(String.valueOf(Tpl.or((String) msg.get("content"), "")), 400));
            }
        }
        String latest = history == null || history.isEmpty() ? "" : String.valueOf(history.get(history.size() - 1).get("content"));
        String user = "下面是一位学习者在刷这道力扣题时的疑问，请回答。\n"
                + "【题目】力扣 " + p.no + ". " + p.title + "（" + p.dName() + "｜" + Tpl.j(p.category) + " / " + Tpl.j(p.algo) + "）\n"
                + "题面：" + Tpl.j(p.q) + "\n"
                + "示例：" + toJson(p.ex) + "\n"
                + "约束：" + Tpl.j(p.c) + solText + "\n\n"
                + "【对话历史（最近几轮，role=user 是学习者提问，role=assistant 是你之前的回答）】\n"
                + (hist.length() == 0 ? "（无）" : hist) + "\n\n"
                + "【学习者最新提问】" + latest;
        return Tpl.pair(system, user);
    }

    static String toJson(Object v) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(v == null ? List.of() : v);
        } catch (Exception e) {
            return "[]";
        }
    }
}
