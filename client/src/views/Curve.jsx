import React, { useEffect, useMemo, useState } from 'react';
import { api } from '../api.js';
import { useStore } from '../store.jsx';
import Workbench from './Workbench.jsx';
import { CATEGORY_META, fmtDate, Tag, SectionTitle, Spinner } from '../fmt.jsx';

export default function Curve() {
  const { data, refresh, showToast } = useStore();
  const [summary, setSummary] = useState(null);
  const [kpId, setKpId] = useState(null);
  const [curve, setCurve] = useState(null);
  const [wb, setWb] = useState(null);
  const [genning, setGenning] = useState(false);

  const loadSummary = async () => {
    try { setSummary(await api.getCurveSummary()); } catch { /* ignore */ }
  };
  useEffect(() => { loadSummary(); }, []);

  const loadCurve = async (id) => {
    setKpId(id);
    try { setCurve(await api.getCurve(id)); } catch (e) { showToast(e.message, 'error'); }
  };
  useEffect(() => {
    if (kpId) loadCurve(kpId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [kpId]);

  const kpOptions = useMemo(() => {
    const kps = Object.values(data.kpsMeta || {}).filter(k => k.state.status !== 'new');
    kps.sort((a, b) => (a.state.due || '') < (b.state.due || '') ? -1 : 1);
    return kps;
  }, [data.kpsMeta]);

  async function reviewKp(id) {
    setGenning(true);
    try {
      const r = await api.genQuestions(id);
      setWb([{ kpId: id, kind: 'review', reason: '到期复习', qIdx: 0, questions: r.questions, genError: null }]);
    } catch (e) {
      showToast(e.message || '出题失败', 'error');
    } finally {
      setGenning(false);
    }
  }

  if (wb) {
    return (
      <Workbench
        items={wb}
        kpsMeta={data.kpsMeta}
        onDone={() => { setWb(null); refresh(true); loadSummary(); }}
        showToast={showToast}
      />
    );
  }

  const cur = curve || {};
  const s = cur.state || {};
  const S = s.stability > 0 ? s.stability : 7;

  // SVG 双曲线：预测 R=e^(-t/S) + 实测历史跳升点
  const W = 760, H = 240, PAD = { l: 44, r: 20, t: 16, b: 30 };
  const iw = W - PAD.l - PAD.r, ih = H - PAD.t - PAD.b;
  const x = t => PAD.l + (t / 30) * iw;
  const y = r => PAD.t + (1 - r) * ih;
  const predPts = Array.from({ length: 31 }, (_, t) => `${x(t).toFixed(1)},${y(Math.exp(-t / S)).toFixed(1)}`).join(' ');

  const actualPts = (cur.actual || []).map((h, i) => {
    const t = Math.min(30, i * 3 + 1);
    return { t, s: h.stability, score: h.score, date: h.date };
  });

  return (
    <div className="view">
      <div className="view-head">
        <div>
          <h1>遗忘曲线驾驶舱</h1>
          <div className="view-sub">预测线 R=e^(−Δt/S) 虚线 · 实测复习跳升实点</div>
        </div>
      </div>

      <div className="kpi-row">
        {[
          { k: 'S 中位数', v: summary ? summary.medianS + ' 天' : '—', sub: '记忆强度，越大越牢' },
          { k: '平均可提取性 R', v: summary ? Math.round(summary.avgR * 100) + '%' : '—', sub: '越低越该复习' },
          { k: '今日到期', v: summary ? summary.dueCount : '—', sub: 'due ≤ 今天' }
        ].map((x, i) => (
          <div className="stat-card" key={i}>
            <div className="stat-value" style={{ color: x.k === '今日到期' && x.v !== '—' && Number(x.v) > 0 ? '#dc2626' : '#1d4ed8' }}>{x.v}</div>
            <div className="stat-label">{x.k}</div>
            <div className="stat-sub">{x.sub}</div>
          </div>
        ))}
      </div>

      <div className="card">
        <SectionTitle right={kpId ? (cur.kp ? cur.kp.title : '曲线加载中…') : '选择一个知识点查看曲线'}>
          预测与实测曲线
        </SectionTitle>
        <div className="curve-toolbar">
          <select value={kpId || ''} onChange={e => e.target.value && loadCurve(e.target.value)}>
            <option value="">— 选择知识点 —</option>
            {kpOptions.map(k => (
              <option key={k.id} value={k.id}>
                {k.title}（{CATEGORY_META[k.category] ? CATEGORY_META[k.category].name : k.category} · {k.state.due}）
              </option>
            ))}
          </select>
          {cur.R != null && (
            <div className="curve-meta">
              <Tag text={'当前 R = ' + Math.round(cur.R * 100) + '%'} color={cur.R < 0.9 ? '#dc2626' : '#059669'} />
              <Tag text={'距上次 ' + cur.daysSince + ' 天 · S=' + S + ' 天'} color="#64748b" />
              <Tag text={'上次得分 ' + (s.lastScore ?? '—')} color="#1d4ed8" />
            </div>
          )}
        </div>
        {kpId ? (
          <svg viewBox={`0 0 ${W} ${H}`} className="curve-svg">
            {/* 网格 */}
            {[0, 0.25, 0.5, 0.75, 1].map(r => (
              <g key={r}>
                <line x1={PAD.l} y1={y(r)} x2={W - PAD.r} y2={y(r)} stroke="#eef1f5" />
                <text x={PAD.l - 6} y={y(r) + 4} textAnchor="end" fontSize="11" fill="#94a3b8">{Math.round(r * 100)}%</text>
              </g>
            ))}
            {[0, 5, 10, 15, 20, 25, 30].map(t => (
              <g key={t}>
                <line x1={x(t)} y1={PAD.t} x2={x(t)} y2={H - PAD.b} stroke="#f5f7fa" />
                <text x={x(t)} y={H - 10} textAnchor="middle" fontSize="11" fill="#94a3b8">{t}</text>
              </g>
            ))}
            <text x={PAD.l + iw / 2} y={H - 6} textAnchor="middle" fontSize="11" fill="#64748b">距上次复习天数 t</text>

            {/* R=0.9 最优复习点 */}
            <line x1={PAD.l} y1={y(0.9)} x2={W - PAD.r} y2={y(0.9)} stroke="#f59e0b" strokeDasharray="5 4" />
            <text x={W - PAD.r - 2} y={y(0.9) - 4} textAnchor="end" fontSize="11" fill="#b45309">R=0.9 最优复习点</text>

            {/* 预测虚线 */}
            <polyline points={predPts} fill="none" stroke="#7c3aed" strokeWidth="2" strokeDasharray="6 5" />

            {/* 实测点 */}
            {actualPts.map((p, i) => (
              <g key={i}>
                <circle cx={x(p.t)} cy={y(Math.exp(-p.t / Math.max(1, p.s)))} r="5" fill="#059669" stroke="#fff" strokeWidth="1.5" />
                <title>{`${p.date} 得分${p.score} S=${p.s}天`}</title>
              </g>
            ))}
            <text x={PAD.l + 6} y={PAD.t + 14} fontSize="11" fill="#7c3aed">— 预测 R=e^(−t/S)</text>
            <text x={PAD.l + 6} y={PAD.t + 30} fontSize="11" fill="#059669">● 实测复习（S 跳升）</text>
          </svg>
        ) : (
          <div className="empty-tip dim">请选择知识点；或从下方「今日到期」直接复习。</div>
        )}
      </div>

      <div className="card">
        <SectionTitle right={summary ? summary.dueCount + ' 张到期' : ''}>今日到期卡（按 R 升序，最危险优先）</SectionTitle>
        {summary && summary.due.length === 0 && <div className="empty-tip dim">今天没有到期复习卡。</div>}
        {summary && summary.due.map(d => (
          <div className="queue-item" key={d.kp.id}>
            <div className="qi-top">
              <Tag text={CATEGORY_META[d.kp.category] ? CATEGORY_META[d.kp.category].name : d.kp.category} color={CATEGORY_META[d.kp.category] ? CATEGORY_META[d.kp.category].color : '#64748b'} />
              <span className="qi-path">{d.kp.domain}</span>
              <Tag text={'R=' + Math.round(d.R * 100) + '%'} color={d.R < 0.5 ? '#dc2626' : d.R < 0.9 ? '#d97706' : '#059669'} />
              <Tag text={'到期 ' + d.state.due} color="#64748b" />
            </div>
            <div className="qi-q">{d.kp.title}</div>
            <div className="qi-meta">
              <span className="dim">上次 {d.state.lastScore ?? '未作答'} 分 · S={d.state.stability} 天</span>
              <button className="btn btn-mini" onClick={() => reviewKp(d.kp.id)}>现在复习</button>
            </div>
          </div>
        ))}
      </div>

      {genning && <div className="gen-mask"><Spinner text="AI 出题中…" /></div>}
    </div>
  );
}
