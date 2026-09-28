import React, { useEffect, useRef, useState } from 'react';
import { api } from '../api.js';
import { useStore } from '../store.jsx';
import Workbench from './Workbench.jsx';
import { CATEGORY_META, KIND_META, LEVEL_META, stars, fmtDate, Tag, Stat, SectionTitle } from '../fmt.jsx';

const CAT_ORDER = Object.keys(CATEGORY_META);

/** 队列输出分模块排序：大类（java/algo/ai 固定序）→ 模块（域，中文序）；sort 稳定，组内保持调度序 */
function orderQueueByModule(items, kpsMeta) {
  return items.slice().sort((a, b) => {
    const ka = kpsMeta[a.kpId] || {}, kb = kpsMeta[b.kpId] || {};
    const ca = CAT_ORDER.indexOf(ka.category), cb = CAT_ORDER.indexOf(kb.category);
    if (ca !== cb) return ca - cb;
    return (ka.domain || '').localeCompare(kb.domain || '', 'zh-CN');
  });
}

export default function Today() {
  const { data, refresh, showToast } = useStore();
  const [preparing, setPreparing] = useState(false);
  const [wb, setWb] = useState(null);
  const [planOpen, setPlanOpen] = useState(false);
  const autoRan = useRef(false);

  const kpi = data.kpi;
  const queue = (data.queue || []).filter(it => it.kpId && data.kpsMeta[it.kpId]);
  const session = data.sessionToday;
  const hasQuestions = queue.some(it => it.questions && it.questions.length);

  // 分组：大类 → 模块（域）。kind 不再决定分组，只作卡片标签
  const orderedQueue = orderQueueByModule(queue, data.kpsMeta);
  const groups = [];
  for (const it of orderedQueue) {
    const kp = data.kpsMeta[it.kpId];
    const dom = kp.domain || '未分模块';
    let g = groups[groups.length - 1];
    if (!g || g.cat !== kp.category) { g = { cat: kp.category, items: [], mods: [] }; groups.push(g); }
    let m = g.mods.find(x => x.dom === dom);
    if (!m) { m = { dom, items: [] }; g.mods.push(m); }
    m.items.push(it);
    g.items.push(it);
  }

  // 打开答题：队列已有题时直接进入，绝不重新生成换题
  function openQueue() {
    setWb(orderedQueue);
  }

  // 点击某张卡片：有题直接打开；未生成/生成失败则先为该题补生成，成功后从该题开始作答
  async function openItem(it) {
    if (!it.kpId) return;
    const target = queue.find(x => x.kpId === it.kpId);
    if (!target) return;
    it = target;
    if (it.questions && it.questions.length) {
      const ordered = orderedQueue.slice();
      const i = ordered.findIndex(x => x.kpId === it.kpId);
      if (i > 0) { const [t] = ordered.splice(i, 1); ordered.unshift(t); }
      setWb(ordered);
      return;
    }
    setPreparing(true);
    try {
      await api.genQuestions(it.kpId);
      const d = await refresh(true);
      const ordered = orderQueueByModule(d.queue || [], d.kpsMeta || data.kpsMeta);
      const i = ordered.findIndex(x => x.kpId === it.kpId);
      if (i > 0) { const [t] = ordered.splice(i, 1); ordered.unshift(t); }
      setWb(ordered);
    } catch (e) {
      showToast(e.message || '该题生成失败，请重试', 'error');
    } finally {
      setPreparing(false);
    }
  }

  // 首次进入：队列为空或无题时不自动出题，改为提示先选题（模块化两步流程）
  useEffect(() => {
    if (autoRan.current) return;
    autoRan.current = true;
    const hasAny = queue.some(it => (it.questions && it.questions.length) || it.genError);
    if (queue.length === 0 || !hasAny) setPlanOpen(true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (wb) {
    return (
      <Workbench
        items={wb}
        kpsMeta={data.kpsMeta}
        onDone={() => { setWb(null); refresh(true); }}
        showToast={showToast}
      />
    );
  }

  return (
    <div className="view">
      <div className="view-head">
        <div>
          <h1>今日闭环</h1>
          <div className="view-sub">每日先答后学 · 缺口驱动 · 遗忘曲线调度</div>
        </div>
        <div className="view-head-right">
          {data.mode === 'minimal' ? <Tag text="最小模式（每日1题）" color="#dc2626" /> : null}
          {data.frozen ? <Tag text="冻结期" color="#b45309" /> : null}
          <button className="btn btn-ghost" onClick={() => setPlanOpen(true)}>定制今日选题</button>
        </div>
      </div>

      {planOpen ? (
        <PlanPanel
          onClose={() => setPlanOpen(false)}
          onGenerated={(ordered) => { setPlanOpen(false); refresh(true); if (ordered) setWb(ordered); }}
          showToast={showToast}
        />
      ) : null}

      <div className="kpi-row">
        <Stat label="连续天数" value={kpi.streak} sub="完成至少1张卡即算连续" accent="#1d4ed8" />
        <Stat label="已消灭缺口" value={kpi.resolvedGaps} sub="面试能力存量的唯一数字" accent="#059669" />
        <Stat
          label="口述完成率"
          value={kpi.spokenRate + '%'}
          sub={kpi.spokenRate < 60 ? '你在用写字代替说话' : '目标 ≥80%'}
          accent={kpi.spokenRate >= 80 ? '#0e9f9b' : kpi.spokenRate < 60 ? '#dc2626' : '#d97706'}
        />
      </div>

      <div className="card queue-card">
        <SectionTitle right={queue.length + '题 · 预计' + queue.length * 4 + '分钟 · 点击卡片开始作答'}>
          今日队列
        </SectionTitle>
        {queue.length === 0 ? (
          <div className="empty-tip">
            <p>今日队列尚未组装。先点「定制今日选题」选择今天要学哪几个模块的知识点，系统再按权重生成对应题目。</p>
            <p className="dim">力扣算法题在 #/lc 独立闭环，不进今日队列。</p>
          </div>
        ) : (
          groups.map(g => {
            const cm = CATEGORY_META[g.cat] || { name: g.cat, color: '#64748b' };
            return (
            <div className="queue-group" key={g.cat}>
              <div className="queue-group-head">
                <span className="qg-dot" style={{ background: cm.color }} />
                <span className="qg-title">{cm.name}</span>
                <span className="qg-count">{g.items.length} 题</span>
                <span className="qg-sub">{g.mods.length} 个模块</span>
              </div>
              {g.mods.map(m => (
              <div className="queue-mod" key={m.dom}>
                <div className="qg-module">{m.dom} · {m.items.length} 卡</div>
                {m.items.map((it, i) => {
                  const kp = data.kpsMeta[it.kpId];
                  const cat = CATEGORY_META[kp.category] || { name: kp.category, color: '#64748b' };
                  const kind = KIND_META[it.kind] || KIND_META.review;
                  const q = it.questions && it.questions[0];
                  const ready = it.questions && it.questions.length > 0;
                  return (
                    <div className={'queue-item' + (ready ? ' clickable' : '')} key={it.kpId + i}
                      onClick={() => openItem(it)} title={ready ? '点击开始作答' : '题目尚未生成，点击生成并作答'}>
                      <div className="qi-top">
                        <Tag text={kind.name} color={kind.color} bg={kind.bg} />
                        <Tag text={cat.name} color={cat.color} />
                        <span className="qi-path">{kp.domain} · {kp.chapter}</span>
                        <span className="qi-stars">{stars(kp.difficulty)}</span>
                        <span className="qi-score">
                          {kp.state.lastScore != null ? (
                            <span style={{ color: (LEVEL_META[kp.state.lastScore >= 90 ? '优秀' : kp.state.lastScore >= 75 ? '良好' : kp.state.lastScore >= 60 ? '及格' : '不及格'] || {}).color }}>
                              上次{kp.state.lastScore}分
                            </span>
                          ) : '未作答'}
                          {kp.state.status === 'mastered' ? <Tag text="已掌握" color="#059669" /> : null}
                        </span>
                      </div>
                      <div className="qi-q">
                        {it.genError ? (
                          <span className="err-text">题目生成失败：{it.genError.slice(0, 80)}（点击重试）</span>
                        ) : q ? (
                          <span className="qi-q-text">{q.question.length > 90 ? q.question.slice(0, 90) + '…' : q.question}</span>
                        ) : (
                          <span className="dim">{it.reason} · 题目尚未生成，点击生成并作答</span>
                        )}
                      </div>
                      <div className="qi-meta">
                        {it.kind === 'review' ? <span>到期复习 · R={kp.state.R ?? '-'}</span> : it.kind === 'weak' ? <span>{it.reason}</span> : <span>{it.reason}</span>}
                        {ready ? <span className="dim">{it.questions.length} 道题可选 · 点击开始作答</span>
                          : <span className="dim">{it.genError ? '生成失败，点击重试' : '点击生成该题'}</span>}
                      </div>
                    </div>
                  );
                })}
              </div>
              ))}
            </div>
            );
          })
        )}

        <div className="queue-actions">
          {hasQuestions ? (
            <button className="btn btn-primary btn-lg" onClick={openQueue} disabled={preparing}>
              开始今日闭环（{queue.length}题）
            </button>
          ) : (
            <button className="btn btn-primary btn-lg" onClick={() => setPlanOpen(true)} disabled={preparing}>
              定制今日选题
            </button>
          )}
        </div>
      </div>

      <div className="card iron-card">
        <SectionTitle right="写进首页，每次打开可见">防退化三铁律</SectionTitle>
        <ol className="iron-list">
          <li><b>14 天冻结期</b>：前 14 天禁止改系统规则/数据/代码；想改的记进备忘，第 15 天再动。</li>
          <li><b>漏卡无惩罚</b>：漏一天，队列自动压缩到 5 题，绝不积累还债焦虑。</li>
          <li><b>最小模式</b>：连续漏 3 天 → 每日 1 题（2 分钟），恢复连续 3 天后升回标准模式。</li>
        </ol>
      </div>
    </div>
  );
}

/** 两步选题面板：先展示按权重分配好的候选（锁定卡不可取消），用户勾选/换入后确认，再按序生成题目 */
function PlanPanel({ onClose, onGenerated, showToast }) {
  const [plan, setPlan] = useState(null);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState('');
  const [picks, setPicks] = useState([]); // 有序 kpId 数组（用户勾选的新知识点 + 薄弱补强）
  const [openMod, setOpenMod] = useState({}); // domain 展开态：`${cat}/${domain}` -> bool
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    api.todayPlan().then(p => {
      if (!alive) return;
      setPlan(p);
      // 默认勾选各大类推荐候选（按 quota 分配），未超额度
      const def = [];
      let used = p.maxQ - p.locked.length;
      for (const c of p.categories) {
        for (const r of c.candidates) {
          if (used <= 0) break;
          def.push(r.kpId); used--;
        }
      }
      for (const r of p.weak) {
        if (used <= 0) break;
        if (!def.includes(r.kpId)) { def.push(r.kpId); used--; }
      }
      setPicks(def);
    }).catch(e => { if (alive) setErr(e.message || '选题计划加载失败'); })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, []);

  const lockedCount = plan ? plan.locked.length : 0;
  const slots = plan ? Math.max(0, plan.maxQ - lockedCount) : 0;
  const remaining = slots - picks.length;

  function toggle(id) {
    setPicks(prev => prev.includes(id) ? prev.filter(x => x !== id) : [...prev, id]);
  }

  async function confirmAndGenerate() {
    setBusy(true);
    try {
      const r = await api.todayConfirm(picks);
      await api.prepareQueue();
      if (r.dropped > 0) showToast(`额度已满，按勾选顺序丢弃了 ${r.dropped} 个超选题`, 'ok');
      onGenerated(null);
    } catch (e) {
      showToast(e.message || '确认选题失败，请重试', 'error');
    } finally {
      setBusy(false);
    }
  }

  if (loading) return <div className="card plan-card"><div className="plan-loading">正在按权重组装今日选题…</div></div>;
  if (err) return <div className="card plan-card"><div className="err-text">{err}</div><div className="plan-foot"><button className="btn" onClick={onClose}>返回</button></div></div>;

  return (
    <div className="card plan-card">
      <SectionTitle right={`额度 ${plan.maxQ} 题 · 锁定 ${lockedCount} · 可选 ${slots}`}>
        今日选题（先选模块与知识点，再按权重出题）
      </SectionTitle>

      {lockedCount > 0 ? (
        <div className="plan-sec">
          <div className="plan-sec-head">锁定 · 到期复习（系统保底，不可取消）</div>
          {plan.locked.map(r => (
            <label className="plan-row locked" key={r.kpId}>
              <input type="checkbox" checked disabled />
              <span className="pr-cat" style={{ color: (CATEGORY_META[r.category] || {}).color }}>{r.catName}</span>
              <span className="pr-dom">{r.domain} · {r.chapter}</span>
              <span className="pr-title">{r.title}</span>
              <span className="pr-reason">{r.reason}</span>
            </label>
          ))}
        </div>
      ) : null}

      {plan.categories.map(c => (
        <div className="plan-sec" key={c.cat}>
          <div className="plan-sec-head">
            <span style={{ color: (CATEGORY_META[c.cat] || {}).color }}>{c.catName}</span>
            <span className="dim">权重 {c.weight}% · 推荐 {c.quota} 题</span>
          </div>
          {c.candidates.length === 0 && c.modules.length === 0 ? (
            <div className="dim plan-empty">该大类暂无未学知识点。</div>
          ) : null}
          {c.candidates.map(r => (
            <label className={'plan-row' + (picks.includes(r.kpId) ? ' on' : '')} key={r.kpId}>
              <input type="checkbox" checked={picks.includes(r.kpId)} onChange={() => toggle(r.kpId)} />
              <span className="pr-dom">{r.domain} · {r.chapter}</span>
              <span className="pr-title">{r.title}</span>
              <span className="pr-stars">{stars(r.difficulty)}</span>
            </label>
          ))}
          {c.modules.map(mod => {
            const key = c.cat + '/' + mod.domain;
            const isOpen = !!openMod[key];
            const hidden = mod.pool.filter(kp => !c.candidates.some(r => r.kpId === kp.kpId));
            if (hidden.length === 0) return null;
            return (
              <div className="plan-mod" key={key}>
                <button className="plan-mod-toggle" onClick={() => setOpenMod(s => ({ ...s, [key]: !s[key] }))}>
                  {isOpen ? '▾' : '▸'} {mod.domain} 换题（{mod.pool.length} 个未学 · {hidden.length} 可换入）
                </button>
                {isOpen ? hidden.map(kp => (
                  <label className={'plan-row sub' + (picks.includes(kp.kpId) ? ' on' : '')} key={kp.kpId}>
                    <input type="checkbox" checked={picks.includes(kp.kpId)} onChange={() => toggle(kp.kpId)} />
                    <span className="pr-dom">{kp.chapter}</span>
                    <span className="pr-title">{kp.title}</span>
                    <span className="pr-stars">{stars(kp.difficulty)}</span>
                  </label>
                )) : null}
              </div>
            );
          })}
        </div>
      ))}

      {plan.weak.length > 0 ? (
        <div className="plan-sec">
          <div className="plan-sec-head">薄弱缺口补强（可选）</div>
          {plan.weak.map(r => (
            <label className={'plan-row' + (picks.includes(r.kpId) ? ' on' : '')} key={r.kpId}>
              <input type="checkbox" checked={picks.includes(r.kpId)} onChange={() => toggle(r.kpId)} />
              <span className="pr-cat" style={{ color: (CATEGORY_META[r.category] || {}).color }}>{r.catName}</span>
              <span className="pr-dom">{r.domain} · {r.chapter}</span>
              <span className="pr-title">{r.title}</span>
              <span className="pr-reason">{r.reason}</span>
            </label>
          ))}
        </div>
      ) : null}

      <div className="plan-foot">
        <span className={'plan-remain' + (remaining < 0 ? ' over' : '')}>
          已选 {picks.length} / {slots} 题位{remaining < 0 ? '（超出将在确认时按顺序截取）' : remaining > 0 ? `（还剩 ${remaining} 个题位）` : ''}
        </span>
        <button className="btn" onClick={onClose} disabled={busy}>返回</button>
        <button className="btn btn-primary" onClick={confirmAndGenerate} disabled={busy}>
          {busy ? 'AI 生成题目中…' : '确认选题并生成题目'}
        </button>
      </div>
    </div>
  );
}
