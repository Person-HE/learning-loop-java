// 答题工作台 v3：出题 → 作答 → 面试官诊断报告（五层）→ 学习舱 → 同题遗忘调度
import React, { useEffect, useRef, useState } from 'react';
import { api } from '../api.js';
import DocPreview from './DocPreview.jsx';
import LearnCabin from './LearnCabin.jsx';
import { CATEGORY_META, TYPE_META, LEVEL_META, GAP_COLORS, stars, Tag, Spinner } from '../fmt.jsx';

const SCORE_STEPS = ['面试官正在看你的回答…', '正在对照标准要点…', '正在裁定是否给过…', '正在生成诊断报告与改写优化…'];

const VERDICT_META = {
  pass: { name: '这轮给过', color: '#059669', bg: '#e6f6ef' },
  followup: { name: '追问一轮', color: '#b45309', bg: '#fdf0df' },
  fail: { name: '这轮挂了', color: '#dc2626', bg: '#fde8e8' }
};
const PC_META = {
  covered: { name: '答到', color: '#059669', bg: '#e6f6ef' },
  partial: { name: '沾边不全', color: '#b45309', bg: '#fdf0df' },
  missing: { name: '没提', color: '#dc2626', bg: '#fde8e8' },
  wrong: { name: '讲错', color: '#9f1239', bg: '#fce7ef' }
};
const DIFF_META = {
  keep: { name: '✓ 保留', color: '#059669', bg: '#e6f6ef' },
  rewrite: { name: '✏️ 改写', color: '#2563eb', bg: '#e8effc' },
  add: { name: '➕ 补全', color: '#7c3aed', bg: '#f1eafe' },
  fix: { name: '❌ 修正', color: '#dc2626', bg: '#fde8e8' },
  reorder: { name: '🔀 重排', color: '#b45309', bg: '#fdf0df' }
};

function ScoreRing({ score, level }) {
  const r = 44;
  const c = 2 * Math.PI * r;
  const pct = Math.max(0, Math.min(100, score)) / 100;
  const color = (LEVEL_META[level] || LEVEL_META['及格']).color;
  return (
    <div className="ring-wrap">
      <svg viewBox="0 0 110 110" className="ring">
        <circle cx="55" cy="55" r={r} fill="none" stroke="#eef1f5" strokeWidth="10" />
        <circle
          cx="55" cy="55" r={r} fill="none"
          stroke={color} strokeWidth="10" strokeLinecap="round"
          strokeDasharray={`${c * pct} ${c}`}
          transform="rotate(-90 55 55)"
        />
      </svg>
      <div className="ring-num">
        <div className="ring-score" style={{ color }}>{score}</div>
        <div className="ring-level" style={{ color }}>{level}</div>
      </div>
    </div>
  );
}

function VerdictBadge({ verdict }) {
  const m = VERDICT_META[verdict] || VERDICT_META.followup;
  return <span className="verdict-badge" style={{ color: m.color, background: m.bg }}>{m.name}</span>;
}

export default function Workbench({ items: initItems, onDone, showToast, kpsMeta, onOpenLc }) {
  const [items, setItems] = useState(initItems);
  const [idx, setIdx] = useState(0);
  const [qIdx, setQIdx] = useState(0);
  const [phase, setPhase] = useState('ask'); // ask | scoring | result | error
  const [answer, setAnswer] = useState({ text: '', code: '', think: '' });
  const [spoken, setSpoken] = useState(false);
  const [stall, setStall] = useState(null);
  const [result, setResult] = useState(null);
  const [step, setStep] = useState(0);
  const [error, setError] = useState('');
  const [showOpt, setShowOpt] = useState(false);
  const [showStd, setShowStd] = useState(false);
  const [doneList, setDoneList] = useState([]);
  const [showEnd, setShowEnd] = useState(false);
  const [regenerating, setRegenerating] = useState(false);
  const [preview, setPreview] = useState(null); // {kpId, anchor} 知识库原文预览
  const [cabin, setCabin] = useState(false);     // 学习舱
  const [plan, setPlan] = useState(null);        // 考点地图/记忆卡
  const stepTimer = useRef(null);

  const item = items[idx];
  const kp = item && (item.kp || (kpsMeta && kpsMeta[item.kpId]));
  const q = item && item.questions && item.questions[qIdx];

  useEffect(() => {
    return () => { if (stepTimer.current) clearInterval(stepTimer.current); };
  }, []);

  useEffect(() => {
    if (phase === 'scoring') {
      setStep(0);
      stepTimer.current = setInterval(() => {
        setStep(s => Math.min(SCORE_STEPS.length - 1, s + 1));
      }, 900);
      return () => clearInterval(stepTimer.current);
    }
  }, [phase]);

  // 结果页加载考点地图（记忆卡/考点进度）
  useEffect(() => {
    if (phase === 'result' && item && !plan) {
      let alive = true;
      api.getKpPoints(item.kpId)
        .then(p => { if (alive) setPlan(p); })
        .catch(() => { /* 考点加载失败不阻塞 */ });
      return () => { alive = false; };
    }
  }, [phase, item, plan]);

  if (cabin && item && q) {
    return (
      <LearnCabin
        kpId={item.kpId}
        kpTitle={kp ? kp.title : ''}
        kp={kp}
        q={q}
        onExit={() => setCabin(false)}
        showToast={showToast}
      />
    );
  }

  if (showEnd) {
    const count = doneList.length;
    const avg = count ? Math.round(doneList.reduce((a, x) => a + x.total, 0) / count) : 0;
    return (
      <div className="workbench">
        <div className="wb-card done-card">
          <div className="done-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="#059669" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="M20 6L9 17l-5-5" /></svg>
          </div>
          <h2>今日闭环完成</h2>
          <div className="done-stats">
            <div><b>{count}</b><span>完成题数</span></div>
            <div><b>{avg}</b><span>平均分</span></div>
            <div><b>{doneList.filter(x => x.total < 60).length}</b><span>新增缺口</span></div>
            <div><b>{doneList.filter(x => x.total >= 75).length}</b><span>达标(≥75)</span></div>
          </div>
          <div className="done-tip">答得不好的题已生成缺口并写入「缺口库」，且该题会按遗忘曲线在 2 天内回到队列等你复答同一题。</div>
          <button className="btn btn-primary" onClick={onDone}>返回今日闭环</button>
        </div>
      </div>
    );
  }

  if (!item) {
    return (
      <div className="workbench">
        <div className="wb-card">
          <h2>队列为空</h2>
          <button className="btn btn-primary" onClick={onDone}>返回</button>
        </div>
      </div>
    );
  }

  const cat = CATEGORY_META[kp.category] || { name: kp.category, color: '#64748b' };
  const isAlgo = kp.category === 'algo';
  const typeMeta = TYPE_META[q ? q.type : 'T2'] || TYPE_META.T2;

  async function regenerate() {
    setRegenerating(true);
    try {
      const r = await api.genQuestions(item.kpId);
      setItems(prev => prev.map((it, i) => i === idx ? { ...it, questions: r.questions, genError: null } : it));
      setQIdx(0);
      setPhase('ask');
      setError('');
    } catch (e) {
      showToast(e.message || '重新出题失败', 'error');
    } finally {
      setRegenerating(false);
    }
  }

  async function submit() {
    const text = answer.text.trim();
    const code = answer.code.trim();
    const think = answer.think.trim();
    if (isAlgo) {
      if (!code && !think) { showToast('请至少填写代码或思路说明', 'error'); return; }
    } else {
      if (text.length < 10) { showToast('答案太短了，试着至少写一句话（≥10字）', 'error'); return; }
    }
    setPhase('scoring');
    setError('');
    try {
      const payload = { kpId: item.kpId, questionId: q.id, answerText: text, code, think, spoken };
      const r = await api.scoreAnswer(payload);
      setResult({ ...r.result, recordId: r.recordId });
      setDoneList(prev => [...prev, { kpId: item.kpId, questionId: q.id, total: r.result.total_score, level: r.result.level }]);
      setStall(null);
      setShowOpt(false);
      setShowStd(false);
      setPhase('result');
    } catch (e) {
      setError(e.message || '评分失败');
      setPhase('error');
    }
  }

  async function markStall(mark) {
    setStall(mark);
    try {
      if (result && result.recordId) await api.markStall(result.recordId, mark);
    } catch { /* 卡壳标记不影响主流程 */ }
  }

  function nextQuestion() {
    if (qIdx + 1 < item.questions.length) {
      setQIdx(qIdx + 1);
      setAnswer({ text: '', code: '', think: '' });
      setResult(null);
      setStall(null);
      setPlan(null);
      setPhase('ask');
    } else {
      nextItem();
    }
  }

  function nextItem() {
    if (idx + 1 < items.length) {
      setIdx(idx + 1);
      setQIdx(0);
      setAnswer({ text: '', code: '', think: '' });
      setResult(null);
      setStall(null);
      setPlan(null);
      setPhase('ask');
    } else {
      // 缺口检测题：答完直接返回缺口库（不进入「今日闭环完成」页）
      if (item && item.kind === 'gaptest') { onDone(); return; }
      setShowEnd(true);
    }
  }

  return (
    <div className="workbench">
      <div className="wb-head">
        <button className="btn btn-ghost wb-back" onClick={onDone} title="退出答题，返回列表">← 返回</button>
        <div className="wb-step">{idx + 1} / {items.length}{item.questions && item.questions.length > 1 ? ` · 本题 ${qIdx + 1}/${item.questions.length}` : ''}</div>
        <div className="wb-title">
          <span style={{ color: cat.color }}>{cat.name}</span>
          <span className="dot">·</span>
          {kp.title}
        </div>
        <div className="wb-progress">
          <div className="wb-progress-fill" style={{ width: ((idx + 1) / items.length) * 100 + '%' }} />
        </div>
      </div>

      {phase === 'ask' && (
        <div className="wb-card">
          {item.genError ? (
            <div className="wb-error">
              <p>题目生成失败：{item.genError}</p>
              <button className="btn btn-primary" onClick={regenerate} disabled={regenerating}>
                {regenerating ? 'AI 出题中…' : '重新生成题目'}
              </button>
            </div>
          ) : !q ? (
            <div className="wb-error">
              <p>本题尚未生成题目。</p>
              <button className="btn btn-primary" onClick={regenerate} disabled={regenerating}>
                {regenerating ? 'AI 出题中…' : '生成题目'}
              </button>
            </div>
          ) : (
            <>
              <div className="q-tags">
                <Tag text={typeMeta.name} color={typeMeta.color} />
                <Tag text={'难度 ' + stars(q.difficulty)} color="#64748b" />
                {item.kind === 'gaptest' ? <Tag text="缺口检测" color="#dc2626" bg="#fde8e8" />
                  : item.kind === 'review' ? <Tag text="复习" color="#1d4ed8" bg="#e8effc" />
                    : item.kind === 'weak' ? <Tag text="薄弱补强" color="#c2410c" bg="#fdeee2" />
                      : <Tag text="新知识点" color="#059669" bg="#e6f6ef" />}
                {isAlgo ? <Tag text="算法题" color="#7c3aed" bg="#f1eafe" /> : null}
                {q.type === 'T6' ? <Tag text="手写代码题" color="#7c3aed" bg="#f1eafe" /> : null}
                {q.point ? <Tag text={'考点：' + q.point} color="#7c3aed" bg="#f1eafe" /> : null}
              </div>
              <div className="q-source">{kp.domain} · {kp.chapter} · {item.reason}</div>
              <h2 className={'q-text' + (isAlgo || q.type === 'T6' ? ' q-text-multi' : '')}>{q.question}</h2>
              <div className="q-tip">铁律：作答前不看答案。答不上来也要硬讲「我知道…我不确定…」——这正是面试里要练的动作。</div>

              {isAlgo ? (
                <div className="ans-2col">
                  <div className="field">
                    <label>代码（Java/Python/C++）</label>
                    <textarea className="ta-code" rows={12} placeholder="// 在这里写代码…"
                      value={answer.code} onChange={e => setAnswer(a => ({ ...a, code: e.target.value }))} />
                  </div>
                  <div className="field">
                    <label>思路说明（≥10字：为什么选这个解法、怎么想出来的、复杂度）</label>
                    <textarea rows={12} placeholder="思路推导 + 复杂度分析 + 边界情况…"
                      value={answer.think} onChange={e => setAnswer(a => ({ ...a, think: e.target.value }))} />
                  </div>
                </div>
              ) : (
                <div className="field">
                  <label>你的回答（建议出声讲一遍再打字，或直接口述转文字）</label>
                  <textarea rows={8} placeholder="把你能讲的都写出来：结论、推理链、关键点、边界情况…"
                    value={answer.text} onChange={e => setAnswer(a => ({ ...a, text: e.target.value }))} />
                </div>
              )}

              <label className="check-line">
                <input type="checkbox" checked={spoken} onChange={e => setSpoken(e.target.checked)} />
                本次作答我出声讲了（口述练习计入「口述完成率」）
              </label>

              <div className="wb-actions">
                {item.questions.length > 1 ? (
                  <button className="btn btn-ghost" onClick={() => setQIdx((qIdx + 1) % item.questions.length)}>
                    换一道题（{qIdx + 1}/{item.questions.length}）
                  </button>
                ) : null}
                <button className="btn btn-primary" onClick={submit}>提交答案，交给面试官评分</button>
              </div>
            </>
          )}
        </div>
      )}

      {phase === 'scoring' && (
        <div className="wb-card scoring-card">
          <Spinner text={SCORE_STEPS[step]} />
          <div className="score-progress">
            <div className="score-progress-fill" style={{ width: ((step + 1) / SCORE_STEPS.length) * 100 + '%' }} />
          </div>
          <div className="scoring-sub">面试官正在按「关键点覆盖 → 表达修正 → 裁定」评分</div>
        </div>
      )}

      {phase === 'error' && (
        <div className="wb-card wb-error">
          <p>评分失败：{error}</p>
          <p className="dim">可能是 AI 服务暂不可用或网络波动（已自动重试过）。</p>
          <div className="wb-actions">
            <button className="btn btn-primary" onClick={submit}>重试评分</button>
            <button className="btn btn-ghost" onClick={() => { setPhase('ask'); setError(''); }}>返回修改答案</button>
          </div>
        </div>
      )}

      {phase === 'result' && result && (
        <div className="wb-result">
          {/* ===== 层 1：面试官裁决 ===== */}
          <div className="wb-card result-main">
            <div className="result-head">
              <ScoreRing score={result.total_score} level={result.level} />
              <div className="result-side">
                <div className="rs-verdict-row">
                  <VerdictBadge verdict={result.verdict} />
                  <span className="rs-cover">{result.covered_count}/{result.total_count} 关键点覆盖</span>
                </div>
                {result.score_breakdown ? <div className="rs-breakdown">{result.score_breakdown}</div> : null}
                {result.profile && result.profile.tag ? (
                  <div className="rs-profile">
                    <Tag text={'画像：' + result.profile.tag} color="#7c3aed" bg="#f1eafe" />
                    {result.profile.desc ? <div className="rs-profile-desc">{result.profile.desc}</div> : null}
                  </div>
                ) : null}
                <div className="rs-dim-head">诊断旁注（不计分）</div>
                <div className="dims">
                  {(result.dimensions || []).map(d => (
                    <div className="dim-row" key={d.name}>
                      <div className="dim-name">{d.name}<span className="dim-max">/{d.max}</span></div>
                      <div className="dim-bar"><div className="dim-fill" style={{ width: Math.min(100, (d.score / Math.max(1, d.max)) * 100) + '%' }} /></div>
                      <div className="dim-score">{d.score}</div>
                      {d.comment ? <div className="dim-comment">{d.comment}</div> : null}
                    </div>
                  ))}
                </div>
              </div>
            </div>

            {/* ===== 层 2：标准答案拆解（面试官期望）===== */}
            {result.standard_points && result.standard_points.length > 0 && (
              <div className="block">
                <div className="block-title">标准答案拆解（面试官期望你讲到这些）</div>
                {result.standard_points.map((p, i) => (
                  <div className="std-point" key={i}>
                    <span className="std-pid">{p.id}</span>
                    <div className="std-body">
                      <div className="std-text">{p.text}</div>
                      {p.why ? <div className="std-why">面试官为什么看它：{p.why}</div> : null}
                    </div>
                  </div>
                ))}
              </div>
            )}

            {/* ===== 层 3：逐点对比 ===== */}
            {result.point_compare && result.point_compare.length > 0 && (
              <div className="block">
                <div className="block-title">你的答案 vs 标准答案（逐点对比）</div>
                {result.point_compare.map((c, i) => {
                  const m = PC_META[c.status] || PC_META.missing;
                  return (
                    <div className="pc-row" key={i}>
                      <span className="pc-status" style={{ color: m.color, background: m.bg }}>{m.name}</span>
                      <div className="pc-body">
                        <div className="pc-head"><span className="std-pid">{c.id}</span><b>{c.text}</b></div>
                        {c.mine ? <div className="pc-mine"><span>你答的：</span>{c.mine}</div> : null}
                        {c.diff ? <div className="pc-diff"><span>差距：</span>{c.diff}</div> : null}
                        {c.fix ? <div className="pc-fix"><span>补法：</span>{c.fix}</div> : null}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}

            {(result.missing_points && result.missing_points.length > 0) && (
              <div className="block">
                <div className="block-title">漏掉的关键点</div>
                <ul className="dot-list">{result.missing_points.map((m, i) => <li key={i}>{m}</li>)}</ul>
              </div>
            )}
            {(result.errors && result.errors.length > 0) && (
              <div className="block">
                <div className="block-title err">答错的点（必须纠正）</div>
                <ul className="dot-list err">{result.errors.map((m, i) => <li key={i}>{m}</li>)}</ul>
              </div>
            )}
            {(result.gaps && result.gaps.length > 0) && (
              <div className="block">
                <div className="block-title">缺口标签（已入库）</div>
                <div className="gap-chips">
                  {result.gaps.map((g, i) => (
                    <Tag key={i} text={g.label + '：' + g.detail.slice(0, 40)} color={GAP_COLORS[g.label] || '#64748b'} />
                  ))}
                </div>
              </div>
            )}

            {/* ===== 层 4：按影响排序的改进指令 ===== */}
            {result.feedback && result.feedback.length > 0 && (
              <div className="block">
                <div className="block-title">改进指令（按影响排序，下次就这么答）</div>
                {result.feedback.map((f, i) => (
                  <div className="fb-row" key={i}>
                    <div className="fb-issue"><span className="fb-no">{i + 1}</span>问题：{f.issue}</div>
                    <div className="fb-improve">下次这样答：{f.improve}</div>
                  </div>
                ))}
              </div>
            )}

            {/* ===== 层 5：改写优化为标准答案（逐处 diff 对比）===== */}
            <div className="block">
              <button className="btn btn-ghost btn-block" onClick={() => setShowOpt(!showOpt)}>
                {showOpt ? '收起' : '展开'} 把你的答案改写优化成标准答案（逐处标注改了什么）
              </button>
              {showOpt && result.optimized_answer && (
                <div className="opt-answer">
                  <div className="opt-final">{result.optimized_answer}</div>
                </div>
              )}
              {showOpt && result.rewrite_diff && result.rewrite_diff.length > 0 && (
                <div className="diff-list">
                  <div className="diff-head">逐处修改对比（原话 → 优化后）</div>
                  {result.rewrite_diff.map((d, i) => {
                    const m = DIFF_META[d.type] || DIFF_META.rewrite;
                    return (
                      <div className="diff-row" key={i}>
                        <div className="diff-top">
                          <span className="diff-type" style={{ color: m.color, background: m.bg }}>{m.name}</span>
                          <span className="diff-where">{d.where}</span>
                        </div>
                        {d.original ? <div className="diff-original"><span>原话：</span>{d.original}</div> : null}
                        {d.optimized ? <div className="diff-optimized"><span>优化后：</span>{d.optimized}</div> : null}
                        {d.note ? <div className="diff-note">{d.note}</div> : null}
                      </div>
                    );
                  })}
                </div>
              )}
            </div>

            {/* ===== 考点进度 ===== */}
            {plan && plan.points && plan.points.length > 0 && (
              <div className="block">
                <div className="block-title">本知识点考点地图（逐考点扫清）</div>
                <div className="kp-points-progress">
                  {plan.points.map((p, i) => {
                    const done = plan.resolvedPoints && plan.resolvedPoints.includes(p.name);
                    return (
                      <div className={'kp-point' + (done ? ' done' : '')} key={i}>
                        <span className="kp-p-dot">{done ? '✓' : i + 1}</span>
                        <span className="kp-p-name">{p.name}</span>
                        {done ? <span className="kp-p-state">已掌握</span> : null}
                      </div>
                    );
                  })}
                </div>
                <div className="kp-points-tip">已掌握 {plan.resolvedPoints ? plan.resolvedPoints.length : 0}/{plan.points.length} 个考点；未掌握的考点会通过「学习舱」与后续出题持续覆盖，直到全部打勾。</div>
              </div>
            )}

            <div className="block advice-block">
              <div className="block-title">学习建议（最小行动）</div>
              <div className="advice-rows">
                <div className="adv-row"><span className="adv-k">动作</span><span>{result.advice.action}{result.advice.level === 'review' ? '（差 → 先学后答）' : result.advice.level === 'extend' ? '（好 → 横向拓展）' : '（练习）'}</span></div>
                {result.lcNo ? (
                  <div className="adv-row">
                    <span className="adv-k">跳转原文</span>
                    <span className="adv-path">力扣 {result.lcNo} 精讲页</span>
                    <button className="btn btn-mini" onClick={() => {
                      if (onOpenLc) onOpenLc(result.lcNo);
                      else { window.location.hash = '#/lc/' + result.lcNo; window.location.reload(); }
                    }}>打开精讲</button>
                  </div>
                ) : result.advice.target ? (
                  <div className="adv-row">
                    <span className="adv-k">跳转原文</span>
                    <span className="adv-path" title={result.advice.target}>{result.advice.target}</span>
                    <button className="btn btn-mini" onClick={() => setPreview({ kpId: item.kpId, anchor: result.advice.target })}>打开原文</button>
                    <button className="btn btn-mini" onClick={() => { navigator.clipboard && navigator.clipboard.writeText(result.advice.target); showToast('路径已复制，可在编辑器打开', 'ok'); }}>复制路径</button>
                  </div>
                ) : null}
                {result.advice.read ? <div className="adv-row"><span className="adv-k">读什么</span><span>{result.advice.read}</span></div> : null}
                {result.advice.practice ? <div className="adv-row"><span className="adv-k">复述练习</span><span>{result.advice.practice}</span></div> : null}
              </div>
            </div>

            <div className="block">
              <div className="block-title">对照标准答案后，你当时卡在哪？（如实标记，系统据此调度）</div>
              <div className="stall-row">
                {[['完全空白', '重新学一遍'], ['只记得名词讲不出机制', '重点补因果链'], ['讲了但有错', '重点纠错']].map(([t, hint]) => (
                  <button
                    key={t}
                    className={'stall-btn' + (stall === t ? ' on' : '')}
                    onClick={() => markStall(t)}
                  >
                    {t}
                    <span className="stall-hint">{hint}</span>
                  </button>
                ))}
              </div>
            </div>

            {item.kind === 'gaptest' ? (
              <div className={'block outcome-feedback ' + (result.total_score >= 75 ? 'pass' : 'fail')}>
                <b>{result.total_score >= 75 ? '缺口已消灭 ✓' : '缺口未消灭，还需补'}</b>
                <span>
                  {result.total_score >= 75
                    ? `检测答题 ${result.total_score} 分（≥75），该缺口已从缺口库移除。`
                    : `检测答题 ${result.total_score} 分（<75），缺口未通过。建议先进学习舱补这块，再回来重测。`}
                </span>
                {result.total_score < 75 && <button className="btn btn-mini" onClick={() => setCabin(true)}>进入学习舱补缺口</button>}
              </div>
            ) : item.kind === 'review' ? (
              <div className={'block outcome-feedback ' + (result.total_score >= 80 ? 'pass' : 'fail')}>
                <b>{result.total_score >= 80 ? '复习到位 ✓' : '复习未通过，会再回来'}</b>
                <span>
                  {result.total_score >= 80
                    ? `复习答题 ${result.total_score} 分（≥80），该知识点复习到位，下次按遗忘曲线延长间隔。`
                    : `复习答题 ${result.total_score} 分（<80），未掌握。本题 2 天内会回到今日队列等你复答同一题。`}
                </span>
              </div>
            ) : null}

            <div className="block cabin-entry">
              <div className="cabin-entry-txt">
                <b>学习舱：这一步才真正解决「记不住」</b>
                <span>三步循环 —— 精读原文 → 合上书凭记忆复述 → AI 只数你讲到几个关键点 → 漏点回看原文 → 再复述，直到覆盖 ≥80%。答得再好也建议过一遍，保证下一次能讲出来。</span>
              </div>
              <button className="btn btn-primary" onClick={() => setCabin(true)}>进入学习舱（精读 → 复述 → 回看）</button>
            </div>

            <div className="wb-actions result-actions">
              {item.questions.length > 1 && qIdx + 1 < item.questions.length ? (
                <button className="btn btn-ghost" onClick={nextQuestion}>同知识点下一题（{qIdx + 2}/{item.questions.length}）</button>
              ) : null}
              <button className="btn btn-primary" onClick={nextItem}>
                {idx + 1 < items.length ? '完成本题，进入下一知识点' : '完成全部，查看今日总结'}
              </button>
            </div>
          </div>
        </div>
      )}
      {preview && (
        <DocPreview
          kpId={preview.kpId}
          kpTitle={kp ? kp.title : ''}
          jumpAnchor={preview.anchor}
          onClose={() => setPreview(null)}
        />
      )}
    </div>
  );
}
