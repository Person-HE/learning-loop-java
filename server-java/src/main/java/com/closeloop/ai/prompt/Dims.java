package com.closeloop.ai.prompt;

import java.util.List;

/** 评分维度定义（机制规格第十一章）与缺口六标签 */
public final class Dims {

    private Dims() {}

    public record Dim(String name, int max, int weight, String desc) {}

    public static final List<String> GAP_LABELS = List.of("概念混淆", "因果链断裂", "边界缺失", "术语不准", "表达卡顿", "空白");

    public static final List<Dim> NORMAL = List.of(
            new Dim("完整性", 20, 0, "该讲的关键点是否都覆盖（对照标准要点清单）"),
            new Dim("正确性", 25, 0, "有无事实/原理错误"),
            new Dim("因果链", 20, 0, "是否“A→所以B→所以C”式推演，还是背结论"),
            new Dim("结构表达", 15, 0, "总分总/分点/逻辑顺序，能否让人听懂"),
            new Dim("术语准确", 10, 0, "专业名词是否用对（如回表、扇出、写屏障）"),
            new Dim("反例边界", 10, 0, "是否主动提到边界/反例/代价")
    );

    public static final List<Dim> ALGO = List.of(
            new Dim("正确性", 40, 0, "代码逻辑是否正确（注意空指针、越界等）"),
            new Dim("复杂度", 20, 0, "时间/空间复杂度分析是否正确，是否提到更优解"),
            new Dim("边界处理", 20, 0, "空输入/单元素/溢出/特殊输入是否考虑"),
            new Dim("思路表达", 20, 0, "能否讲清“为什么选这个解法+怎么一步步想出来”")
    );

    public static final List<Dim> PROJECT = List.of(
            new Dim("技术基础掌握", 10, 25, "简历涉及的技术栈原理是否讲得清（8+优秀，6-7良好，5及以下需加强）"),
            new Dim("项目表述能力", 10, 20, "STAR法则运用、量化数据、技术深度（是否“我”做了什么，做到了什么程度）"),
            new Dim("算法解题能力", 10, 15, "最优解、时间复杂度、思路清晰度"),
            new Dim("系统设计能力", 10, 15, "全面性、可行性、扩展性、trade-off 意识"),
            new Dim("沟通表达能力", 10, 15, "流畅度、逻辑性、专业性"),
            new Dim("学习能力与潜力", 10, 10, "技术视野、自驱力、成长速度")
    );

    public static final List<Dim> RESUME = List.of(
            new Dim("技术栈匹配度", 25, 0, "Java后端方向：与目标岗位高度匹配且体现深度理解（22-25 高匹配 / 18-21 匹配 / 14-17 部分匹配 / 10-13 低 / 0-9 严重不匹配）"),
            new Dim("项目经验质量", 25, 0, "技术亮点8+量化成果8+技术选型5+业务理解4"),
            new Dim("技术深度体现", 20, 0, "加分：深入理解XX原理、优化性能从A到B；减分：只写熟悉XX无深度；严重减分：写了但答不上来"),
            new Dim("STAR法则运用", 15, 0, "13-15 每个项目有S-T-A-R结构和量化结果 / 10-12 大部分结构化 / 7-9 部分 / 0-6 混乱或过于简单"),
            new Dim("简历真实性", 10, 0, "是否有过度包装风险"),
            new Dim("格式排版", 5, 0, "排版是否专业、简洁、重点突出、不超过2页")
    );

    /** 普通/算法评分维度描述（name(max分)：desc） */
    public static String normalDesc(List<Dim> dims) {
        StringBuilder sb = new StringBuilder();
        for (Dim d : dims) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(d.name()).append('(').append(d.max()).append("分)：").append(d.desc());
        }
        return sb.toString();
    }

    /** 项目面试维度描述（name（权重w%，1-10分）：desc） */
    public static String projectDesc(List<Dim> dims) {
        StringBuilder sb = new StringBuilder();
        for (Dim d : dims) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(d.name()).append("（权重").append(d.weight()).append("%，1-10分）：").append(d.desc());
        }
        return sb.toString();
    }

    /** 简历评分维度描述 */
    public static String resumeDesc(List<Dim> dims) {
        return normalDesc(dims);
    }
}
