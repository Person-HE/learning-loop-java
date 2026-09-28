import React from 'react';
import { SectionTitle, Tag } from '../fmt.jsx';

function FlowSteps() {
  const steps = [
    ['① 调度', '到期卡+新知识点+薄弱补强'],
    ['② AI 出题', '每知识点 3~5 题'],
    ['③ 作答', '先答后看 · 出声'],
    ['④ AI 评分', '六维打分+等级+缺口'],
    ['⑤ 对照', '标准答案+卡壳标记'],
    ['⑥ 记录', 'SM-2+双因子调度']
  ];
  return (
    <div className="flow">
      {steps.map((s, i) => (
        <React.Fragment key={i}>
          <div className="flow-node">
            <b>{s[0]}</b>
            <span>{s[1]}</span>
          </div>
          {i < steps.length - 1 && <div className="flow-arrow">→</div>}
        </React.Fragment>
      ))}
    </div>
  );
}

export default function Spec() {
  return (
    <div className="view">
      <div className="view-head">
        <div>
          <h1>机制说明</h1>
          <div className="view-sub">本系统严格实现《学习闭环系统_机制与功能规格_v2.md》</div>
        </div>
      </div>

      <div className="card">
        <SectionTitle>每日闭环（六步）</SectionTitle>
        <FlowSteps />
        <p className="dim">
          调度器每天挑「3 张到期复习 + 2 个新知识点（含 1 薄弱补强）」（上限 5 题，最小模式 1 题）→ AI 出题 → 你作答 → AI 六维评分 →
          AI 标准答案 → 缺口入库 → 差则跳转知识库原文锚点精读 → 遗忘曲线决定下次见面时间。
        </p>
        <p className="dim" style={{ marginTop: 8 }}>
          新知识点出题域优先级：<b>网络编程 / 云原生 / 计算机组成原理 / 分布式与微服务 排后</b>（其余 Java 域、AI、算法优先出题，仅当无其他新知识点时才轮到这 4 个域）。
        </p>
      </div>

      <div className="card">
        <SectionTitle>双因子记忆模型</SectionTitle>
        <div className="spec-rows">
          <div className="spec-row"><b>记忆强度 S</b><span>记得多牢（天）。初始 1 天；答得越好乘得越多（优秀×2.5 / 良好×1.8 / 及格×1.2 / 不及格×0.7 / 空白×0.3）。</span></div>
          <div className="spec-row"><b>可提取性 R</b><span>R = e^(−Δt/S)，R 降到 0.9（预测 10% 概率想不起来）即触发复习。</span></div>
          <div className="spec-row"><b>间隔序列</b><span>良好以上：1 → 6 → interval×ease（ease∈[1.3,2.8]，答错回 1 天）。</span></div>
          <div className="spec-row"><b>已掌握</b><span>interval ≥ 30 天且 q ≥ 4（优秀/良好）。</span></div>
        </div>
      </div>

      <div className="card">
        <SectionTitle>AI 评分六维（普通题）与分级动作</SectionTitle>
        <div className="spec-table">
          {[['完整性', 20], ['正确性', 25], ['因果链', 20], ['结构表达', 15], ['术语准确', 10], ['反例边界', 10]].map(([n, m]) => (
            <div className="st-row" key={n}><span>{n}</span><b>{m}分</b></div>
          ))}
        </div>
        <div className="grade-table">
          {[['优秀 ≥90', '间隔正常拉长，一周后横向拓展题'], ['良好 75~89', '间隔正常，3 天后追问变体'], ['及格 60~74', '缺口「讲不清机制」，间隔减半，明天补题'], ['不及格 40~59', '缺口+间隔=1 天+跳转锚点精读+明天重答'], ['空白 <40', '同上 + 触发「先学后答」模式']].map(([g, a]) => (
            <div className="gt-row" key={g}><Tag text={g} color="#1d4ed8" bg="#e8effc" /><span>{a}</span></div>
          ))}
        </div>
      </div>

      <div className="card">
        <SectionTitle>六类缺口标签 → 复习动作</SectionTitle>
        <div className="grade-table">
          {[['概念混淆', '跳转概念锚点 + 概念对比题'], ['因果链断裂', '跳转真相层 + T2 原理推演题'], ['边界缺失', '跳转反例层 + T3 反例题'], ['术语不准', '跳转术语表 + T1 概念题'], ['表达卡顿', '不出新题，重答原题（出声）'], ['空白', '触发先学后答模式']].map(([g, a]) => (
            <div className="gt-row" key={g}><b>{g}</b><span>{a}</span></div>
          ))}
        </div>
      </div>

      <div className="card">
        <SectionTitle>防退化三铁律（写进首页，每次打开可见）</SectionTitle>
        <ol className="iron-list">
          <li><b>14 天冻结期</b>：自第一天使用起 14 天内禁止改系统规则/数据/代码；想改的记进备忘，第 15 天再动。</li>
          <li><b>漏卡无惩罚</b>：漏一天，队列自动压缩到 5 题，绝不积累还债焦虑。</li>
          <li><b>连续漏 3 天触发最小模式</b>：每日 1 题（2 分钟），恢复连续 3 天后升回标准模式。</li>
        </ol>
        <div className="dim">三个主指标（唯一常驻）：连续天数 / 已消灭缺口 / 口述完成率（目标 ≥80%，低于 60% 提示「你在用写字代替说话」）。</div>
      </div>

      <div className="card">
        <SectionTitle>三大类差异化</SectionTitle>
        <div className="spec-rows">
          <div className="spec-row"><b>Java 后端</b><span>跟随学习路线路径一（计组→OS→网络→Java→JVM→并发→MySQL→Redis→MQ→Spring→分布式→系统设计）；出题风格：原理推演 + 场景排查 + 追问连环。</span></div>
          <div className="spec-row"><b>AI Agents</b><span>链式「从零到落地」，每章结尾必带「Java 后端怎么集成」；特有架构选择题与协议题。</span></div>
          <div className="spec-row"><b>数据结构与算法</b><span>每题 = 思路推导 + 手写代码 + 复杂度 + 边界反例 + 变体追问；四维评分（正确性40/复杂度20/边界20/思路20）；与底层知识交叉出题。</span></div>
        </div>
      </div>

      <div className="card">
        <SectionTitle>项目部分（本次新增）</SectionTitle>
        <div className="spec-rows">
          <div className="spec-row"><b>数据来源</b><span>简历_何宏鑫_Java后端开发实习生.html（知缘Flow情绪社区平台）+ 《面试官Skills.md》面试官提示词（严格参考）。</span></div>
          <div className="spec-row"><b>项目面试</b><span>按简历切面（多级缓存/事件驱动与降级/LLM可靠性/SQL索引优化/横切与安全/整体）AI 深挖 3~5 题，六维面试官评估（技术基础25/项目表述20/算法15/系统设计15/沟通15/潜力10），输出 STAR 满分表达 + 通过率预测。</span></div>
          <div className="spec-row"><b>简历评分</b><span>按 Skills 评分模型：技术栈匹配25 + 项目质量25 + 深度20 + STAR15 + 真实性10 + 排版5。</span></div>
          <div className="spec-row"><b>记忆联动</b><span>项目切面同样走 SM-2 遗忘曲线，答不好的切面会出现在「项目复习」列表中。</span></div>
        </div>
      </div>
    </div>
  );
}
