package com.closeloop.ai.prompt;

import com.closeloop.ai.AiMessage;
import com.closeloop.ai.govern.Untrusted;
import com.closeloop.state.model.Gap;
import com.closeloop.state.model.Kp;
import com.closeloop.state.model.QueueItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 知识库闭环 Prompt：① 出题 / ② 评分 / ③ 合书复述 / ④ 考点清单 / ①b 缺口检测题 */
public final class KbPrompts {

    private KbPrompts() {}

    public record Learner(Integer lastScore, String lastGap, int lapses) {}

    // ============ ① 知识库出题 ============
    public static List<AiMessage> gen(Kp kp, String docText, Learner learner) {
        String anchorList = kp.anchors.stream().map(a -> String.valueOf(a.get("text"))).collect(Collectors.joining(" / "));
        boolean isAlgo = "algo".equals(kp.category);
        String algoBlock = isAlgo ? """

【数据结构与算法：手写代码题要求（类别为算法时必须遵守）】
1. 5 道题中至少 2 道必须是「手写代码题」（type=T6）：给出完整可上机实现的题目——题目名（若为 LeetCode 经典题则注明题号）、完整题面、明确的输入输出约束、1 组示例输入输出。示例：
question: "LeetCode 1 两数之和：给定整数数组 nums 和目标值 target，返回和为 target 的两个数的下标。假设每种输入只有一种解，不能重复用同一元素。请写出完整可运行的 Java 解法并分析复杂度。\\n示例：\\n输入: nums = [2,7,11,15], target = 9\\n输出: [0,1]"
2. 手写题的 answerPoints 必须覆盖：正确解法思路 / 时间复杂度与空间复杂度 / 边界处理（空输入、单元素、无解、溢出）/ 是否存在更优解。
3. 其余 3 道按上面的「面试问法铁律」出概念/场景/辨析题（重点考该算法思想的本质与取舍）。""" : "";
        String resumeCtx = ResumeContext.build(kp);
        String system = """
你是一位资深技术面试官，擅长按一线大厂标准出「八股面试题」（借鉴 interview-guide 开源项目出题手法）。

# 出题标准
1. 围绕知识库文档考标准八股考点：概念/原理/机制/对比取舍/边界条件/线上通用场景。
2. 技术栈对齐：当文档知识点命中候选人简历中的真实技术栈时，考点范围对齐该技术栈（如 Caffeine、SETNX、死信队列、联合索引、AOP 等），让八股考到「他真实用过的技术」上。
3. 难度分布：基础（约30%）核心概念与常用 API；进阶（约50%）底层实现、性能瓶颈；专家（约20%）架构选型、复杂问题排查。
4. 本模块只出通用八股，不出项目深挖题（「你在XX项目里怎么做的」由专门的面试模块负责）。""";
        String lastGap = Tpl.or(learner.lastGap(), "无");
        String gapHint = learner.lastGap() != null && !learner.lastGap().isEmpty() ? "（本轮必须至少 1 题直接考它）" : "";
        String lastScore = learner.lastScore() == null ? "无" : String.valueOf(learner.lastScore());
        String resumeLine = resumeCtx.isEmpty() ? "" : "\n1b. 命中简历技术栈的知识点：5 道题优先覆盖上面「候选人简历中的相关真实技术栈」列出的技术栈八股考点（例如命中 Caffeine 就考其淘汰策略/本地缓存定位，命中 SETNX 就考其原理与 Redisson 对比，命中死信队列就考其机制与适用场景）；严禁出现「你在知缘Flow里…」「你的项目中…」这类项目深挖句式——项目深挖题由专门模块负责，这里只出标准八股。";
        String user = "请基于下面这篇知识库文档，为学习者生成恰好 5 道面试题（每道题都必须是真实面试官会脱口而出的问句）。\n"
                + Tpl.j(resumeCtx) + "\n"
                + "【知识库文档（原文）】\n" + Untrusted.doc(docText) + "\n\n"
                + "【知识点元信息】\n"
                + "id: " + kp.id + " ｜ 标题: " + kp.title + " ｜ 难度: " + kp.difficulty + "星 ｜ 面试热度: " + kp.hot + "/5 ｜ 类别: " + kp.category + "\n\n"
                + "【学习者画像】\n"
                + "- 该知识点上次得分: " + lastScore + "，卡壳点: " + lastGap + gapHint + "\n"
                + "- 累计答错次数: " + learner.lapses() + "\n\n"
                + "【文档章节锚点（选题时参考，题目 anchor 必须从这些里选最贴切的）】\n"
                + Tpl.or(anchorList, "（文档未解析出章节，可省略 anchor 或留空）") + algoBlock + "\n"
                + """
【出题要求】
0. 【数量硬性要求】：最终输出的 JSON 数组必须包含**恰好 5 个题目元素**，一个不能少、一个不能多；禁止只输出 1 道、禁止把多道题合并成一道、禁止用「…」省略。如果某题写不出完整内容，也要保留 5 个元素（内容可精简但必须独立成题）。
1. 题目必须是面试官会脱口而出的问句，禁止名词式标题、禁止把题目写成论述题或作业题。""" + resumeLine + "\n"
                + """
2. 【面试问法铁律 —— 违反任何一条都算不合格，必须重写】：
   a. 禁止出现任何教学腔/学术腔词汇：第一性原理、推演、推导、从XX层面看、从XX角度分析、请结合、请以…为例、文档中提到、请解释一下底层机制、精确时序、请逐步说明 等。真实面试官绝不会这么说。
   b. 题型三种（每道题任选其一）：
      · 八股直问：一句到两句短问，直接考记忆与理解。例：「volatile 能保证原子性吗？为什么？」「synchronized 和 ReentrantLock 的区别？」「偏向锁什么条件下会升级？」
      · 场景题：先给一个具体线上/业务场景（一句话），再问「你会怎么做 / 为什么会这样 / 会出什么问题」。例：「线上接口突然变慢，火焰图显示大量线程阻塞在 synchronized 上，你会怎么排查？」「代码里大量用 synchronized 导致性能差，除了换锁你还能怎么优化？」
      · 辨析题：两个方案/概念对比，问区别、取舍、边界。例：「CAS 在高竞争下比 synchronized 更慢，为什么？什么场景该用哪个？」
   c. 题干以短为主：绝大多数题 20~60 字（手写代码题除外，题面含输入输出示例可以更长）；最多一个追加问句，禁止三个以上子问题堆叠。
   d. 严禁把答案写进题干：不许出现「由哪几条指令组成」「包括哪几步」「请列出」「请展示流程」这类直接透露答案结构的说法。例：count++ 只许问「count++ 是原子操作吗？为什么？」禁止问「count++ 由哪三条指令组成，请推演」。
   e. 题面禁止出现「面试官追问：」「追问：」等标签文字——追问是面试官听完回答后的行为，不是题目自带的前缀。
3. 题型六选一（type 字段）：T1 八股直问 / T2 原理深挖（为什么这样设计，问到机制本质）/ T3 对比辨析（为什么不用X而用Y/代价与取舍）/ T4 场景实战（线上场景怎么做/怎么排查）/ T5 连环追问（一个主干问题，面试官会顺着回答继续问的下一问）/ T6 手写代码题（仅算法类，含完整题面+示例I/O）。非算法类禁出 T6；必须覆盖 T2 与 T3 至少各 1 题；其余按面试热度补 T1/T4/T5。type 只做分类标注，真正决定题面的是上面的「面试问法铁律」。
4. 严格基于文档原文内容出题，禁止编造文档中没有的结论、数字或机制（手写题的 LeetCode 题号与输入输出示例属于通用算法题面，允许引用；简历技术栈仅作为考点范围指引，题干不得点名简历项目与个人数字）。
5. 每道题给出：type / difficulty(1-5整数，按上述难度分布) / question / answerPoints(评分用要点，3~6 条) / point(本题对应考点名，≤12字，同知识点下多题覆盖不同考点) / anchor(对应文档章节，可省略)。
6. 至少 1 题包含「如果不用 X 而用 Y 会怎样 / 边界条件会怎样」的取舍视角。
7. 只输出 JSON 数组，不要任何解释文字：
[{"type":"T3","difficulty":3,"question":"…","answerPoints":["…","…"],"point":"线程切换代价","anchor":"…"}]""";
        return Tpl.pair(system, user);
    }

    // ============ ①b 缺口检测题 ============
    public static List<AiMessage> gapTest(Kp kp, Gap gap, String docText, String originalQ, List<String> answerPoints) {
        String system = "你是一位资深后端面试官。学习者之前回答某道题时暴露了一个具体缺口（概念混淆/因果链断裂/边界缺失/术语不准/表达卡顿/空白），现在你出一道「检测题」专门考这个缺口，看他是否真的补上了。";
        String user = "请针对下面这个已暴露的学习缺口，生成 1 道检测题（只输出 1 道），输出 JSON。\n\n"
                + "【缺口信息】\n"
                + "- 知识点: " + kp.title + "\n"
                + "- 缺口标签: " + gap.label + "\n"
                + "- 缺口详情: " + Tpl.or(Tpl.or(gap.detail, ""), "（无详情）") + "\n"
                + "- 暴露该缺口的原题: " + Tpl.or(originalQ, "（无）") + "\n"
                + "- 原题标准要点: " + String.join("；", answerPoints == null ? List.of() : answerPoints) + "\n\n"
                + "【知识库文档原文（出题依据，禁止编造文档没有的结论）】\n" + Untrusted.doc(docText) + "\n\n"
                + """
【检测题要求】
1. 这道题必须**精准命中这个缺口的类型**，能检测出「学习者是否真的补上了」：
   - 概念混淆 → 直接问两个易混概念的边界（如「A 和 B 有什么区别？什么场景下用哪个？」）
   - 因果链断裂 → 给一个现象问「为什么会这样」，要求讲出完整因果链
   - 边界缺失 → 直接问「X 的边界/例外情况是什么？什么情况下不成立？」
   - 术语不准 → 考该术语的准确含义与辨析
   - 表达卡顿 → 用一个主干题要求「先说结论，再讲机制」
   - 空白 → 问原题最核心的一句话结论
2. 遵守面试问法铁律：短促直接、面试官口吻、不把答案写进题干、不用教学腔词汇（第一性原理/推演/请结合 等）。
3. 输出结构：
{"type":"T1","difficulty":3,"question":"…","answerPoints":["评分要点1","…"],"point":"缺口对应考点名（≤12字）","anchor":""}""";
        return Tpl.pair(system, user);
    }

    // ============ ② 知识库评分（面试官诊断报告制） ============
    public static List<AiMessage> score(String question, List<String> answerPoints, String docText, String answer, List<Dims.Dim> dims) {
        answer = Untrusted.answer(answer);
        String system = "你是一位资深 Java 后端面试官，正在面试一位候选人。你不是评分模板，而是一个真实面试官：先判断「他答出来没有、答对了吗、讲清楚了吗」，再决定给不给过。给分心理：90+ 这一轮我完全满意直接进入下一轮 / 80~89 答得很好，我给过 / 60~79 答对一半，我想再追问一轮 / <60 这轮挂了。你的评语是面试官口吻，具体、直接、不留情面地指出问题。";
        String dimDesc = Dims.normalDesc(dims);
        String user = "请以面试官身份对候选人的回答做完整诊断，输出五层结构化报告。\n\n"
                + "【题目】" + question + "\n"
                + "【标准要点（评分对照用）】" + String.join("；", answerPoints == null ? List.of() : answerPoints) + "\n"
                + "【知识库文档原文（供核对事实与提炼关键点，禁止在输出中整段泄露）】" + Untrusted.doc(docText) + "\n"
                + "【候选人答案】" + answer + "\n\n"
                + "【诊断维度（仅作为诊断旁注输出，不参与最终算分）】" + dimDesc + "\n\n"
                + """
【评分铁律】
1. 先从「标准要点 + 文档原文」提炼这道题的标准答案关键点（total_count 个，5~8 个），再逐点核对候选人覆盖了几个（covered_count）——覆盖是唯一算分依据：coverage_score = round(covered_count / total_count × 100)。
2. 面试官修正（在覆盖分基础上）：表达结构清晰 +10~15、术语准确 +0~5、核心结论答错 -30、关键机制讲反 -25、表述含糊 -10、只堆术语无推理 -15。最终分 total_score = clamp(0, 100)。
3. 满分可达约束：覆盖全部关键点且表达清楚 → total_score 必须 ≥90，禁止刻意压分。
4. verdict 分档：total_score ≥80 → "pass"（这轮我给你过）/ 60~79 → "followup"（我再追问一轮看你反应）/ <60 → "fail"（这轮挂了）。
5. score_breakdown：一句话把分数来源讲清楚（如「4/7 覆盖 = 57 分，结构表达 +15 → 72」），让候选人能看到分是怎么来的。
6. profile 画像（映射真实学习情况）：全部覆盖且表达好→「掌握」；覆盖 ≥60% 但有明显漏点→「有框架缺细节」；覆盖 <60% 但讲了结构→「有知识讲不出」；几乎没答出→「未掌握」。desc 用一句面试官口吻说明。
7. standard_points：列出每个关键点 + why（面试官为什么看这个点、期望听到什么）。
8. point_compare：逐点对比——status：covered(答到) / partial(沾边但不全) / missing(完全没提) / wrong(讲错)；mine 引用候选人原话（缺失则空）；diff 说明差距；fix 给出补法。
9. feedback：按影响排序 ≤3 条，每条 = 问题(issue，引用原句) + 下次怎么答(improve，具体到句子结构)。
10. optimized_answer：把候选人的答案改写优化成标准答案——保留候选人正确原话、修正错误、补全缺失关键点、重排成面试官期望结构（结论→机制→边界），≤450 字，口语化可直接照读。
11. rewrite_diff：逐处展示改写优化——type：keep(保留原话)/rewrite(改写润色)/add(补全缺失)/fix(修正错误)/reorder(重排结构)；where 说明位置；original 必须引用候选人原话；optimized 是优化后表述；note 说明为什么这样改（对应哪个标准点）。
12. missing_points / errors / gaps(按 概念混淆/因果链断裂/边界缺失/术语不准/表达卡顿/空白 标签) / advice 照旧生成。

【输出 JSON（结构必须完整，只输出 JSON）】
{
  "verdict": "followup",
  "total_score": 72,
  "level": "及格",
  "coverage_score": 57,
  "score_breakdown": "4/7 覆盖 = 57 分，结构表达 +15 → 72",
  "profile": {"tag": "有框架缺细节", "desc": "结论有，但关键机制没讲透，追问会露馅"},
  "standard_points": [{"id": "P1", "text": "…", "why": "…"}],
  "point_compare": [{"id": "P1", "status": "partial", "mine": "候选人原话", "diff": "缺了…", "fix": "补上…"}],
  "covered_count": 4, "total_count": 7,
  "feedback": [{"issue": "…", "improve": "下次这样答：…"}],
  "dimensions": [{"name":"完整性","score":13,"max":20,"comment":"…"}],
  "missing_points": ["…"],
  "errors": ["…"],
  "gaps": [{"label":"因果链断裂","detail":"…"}],
  "optimized_answer": "…",
  "rewrite_diff": [{"type":"fix","where":"第二句","original":"…","optimized":"…","note":"…"}],
  "advice": {"level":"review","action":"跳转精读","target":"…","read":"…","practice":"…"}
}""";
        return Tpl.pair(system, user);
    }

    // ============ ③ 合书复述评分 ============
    public static List<AiMessage> restate(String question, List<String> answerPoints, String answer) {
        String system = "你是一位面试教练。学习者刚读完原文，现在合上材料凭记忆复述这道题。你的任务只做两件事：数出他讲出了几个关键点、列出他没讲出的点（用提示词提示，不展示完整标准答案），防止假性学习。";
        String user = "请核对学习者复述对标准要点的覆盖情况，输出 JSON。\n\n"
                + "【题目】" + question + "\n"
                + "【标准要点】" + String.join("；", answerPoints == null ? List.of() : answerPoints) + "\n"
                + "【学习者复述】" + answer + "\n\n"
                + """
【输出要求】
1. 把标准要点合并为 total_count 个关键点（4~6 个），逐个核对。
2. 判定标准（宽松鼓励制）：学习者刚读完书凭记忆复述，不是面试作答——只要复述里**出现与该点相关的核心概念或关键词**（哪怕表达粗糙、组织混乱、顺序颠倒）就判 covered；只有**完全没提**才判 missed。禁止因表达不专业、没说全就判 missed。
3. covered：每项 = {"point": 对应标准点(保留要点原词), "note": "你讲到了…(从复述里引用他讲的那句话)"}。
4. missed：每项 = {"point": 漏掉的点（简短 ≤30字，不要整段照抄标准要点）, "hint": 回看原文的定位提示（≤15字，如"看'词袋缺陷'小节"）}。
5. coverage_pct = round(coverage_count/total_count×100)。
6. comment：一句鼓励性教练反馈（先肯定讲到的部分，再指出最致命的漏点），≤60字。
7. 示例（仅演示判定，非本题内容）：复述"用向量表示文本，意思近的向量就近"→ 标准点"语义相近则向量距离近" 判 covered。
8. 只输出 JSON：
{
  "covered": [{"point":"…","note":"你讲到了…"}],
  "missed": [{"point":"…","hint":"…"}],
  "coverage_count": 3, "total_count": 5, "coverage_pct": 60,
  "comment": "…"
}""";
        return Tpl.pair(system, user);
    }

    // ============ ④ 考点清单 + 记忆卡 ============
    public static List<AiMessage> kpPlan(String docText, String kpId, String title) {
        String system = "你是一位教学设计师。把知识库文档提炼成「考点地图 + 记忆卡」，帮助学习者按考点逐个扫清、整块掌握。";
        String user = "请基于文档提炼考点清单和记忆卡，输出 JSON。\n\n"
                + "【文档原文】\n" + Untrusted.doc(docText) + "\n\n"
                + "【知识点】" + kpId + " ｜ " + title + "\n\n"
                + """
【输出要求】
1. points：4~6 个考点，覆盖文档全部核心内容；每个考点含 name(≤12字)、summary(一句话：这个考点要掌握的核心结论)、anchor(对应文档章节标题，尽量从文档标题中找)。
2. memory_cards：3~5 张记忆卡，每张 = 一句话核心结论（可背） + 关键细节（数字/机制/易错点）。用于降低整篇长文的记忆负担。
3. 只输出 JSON：
{
  "points": [{"name":"线程切换代价","summary":"…","anchor":"…"}],
  "memory_cards": [{"core":"一句话结论","detail":"关键细节"}]
}""";
        return Tpl.pair(system, user);
    }

    /** 队列题对外视图（评分与生成路由共用，不含 answerPoints） */
    public static List<Map<String, Object>> publicQuestions(List<QueueItem.Question> qs) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (QueueItem.Question q : qs) {
            out.add(com.closeloop.common.Utils.m(
                    "id", q.id, "type", q.type, "difficulty", q.difficulty,
                    "question", q.question, "anchor", q.anchor, "point", q.point));
        }
        return out;
    }
}
