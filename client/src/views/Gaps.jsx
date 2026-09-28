import React, { useMemo, useState } from 'react';
import { api } from '../api.js';
import { useStore } from '../store.jsx';
import { CATEGORY_META, GAP_COLORS, fmtDate, Tag, SectionTitle } from '../fmt.jsx';
import DocPreview from './DocPreview.jsx';
import Workbench from './Workbench.jsx';

const GAP_LABELS = ['概念混淆', '因果链断裂', '边界缺失', '术语不准', '表达卡顿', '空白'];

export default function Gaps() {
  const { data, refresh, showToast } = useStore();
  const [label, setLabel] = useState('all');
  const [status, setStatus] = useState('open');
  const [testing, setTesting] = useState(null);
  const [preview, setPreview] = useState(null);
  const [wb, setWb] = useState(null);

  const gaps = data.gaps || [];
  const kps = data.kpsMeta || {};

  const stats = useMemo(() => {
    const m = {};
    GAP_LABELS.forEach(l => { m[l] = 0; });
    gaps.forEach(g => { if (!g.resolvedAt) m[g.label] = (m[g.label] || 0) + 1; });
    return m;
  }, [gaps]);

  const filtered = gaps.filter(g => {
    if (label !== 'all' && g.label !== label) return false;
    if (status === 'open' && g.resolvedAt) return false;
    if (status === 'resolved' && !g.resolvedAt) return false;
    return true;
  });

  async function startTest(g) {
    setTesting(g.id);
    try {
      const r = await api.gapTest(g.id);
      setWb(r.item);
    } catch (e) {
      showToast(e.message || '检测题生成失败，请重试', 'error');
    } finally {
      setTesting(null);
    }
  }

  const totalOpen = gaps.filter(g => !g.resolvedAt).length;
  const maxLabel = Math.max(1, ...Object.values(stats));

  // 缺口检测答题：全视图切换到答题工作台（替代缺口列表，答完返回）
  if (wb) {
    return (
      <Workbench
        items={[wb]}
        kpsMeta={kps}
        onDone={() => { setWb(null); refresh(true); }}
        showToast={showToast}
      />
    );
  }

  return (
    <div className="view">
      <div className="view-head">
        <div>
          <h1>缺口库</h1>
          <div className="view-sub">缺口是唯一复习对象 · 消灭缺口 = 面试能力存量</div>
        </div>
      </div>

      <div className="card">
        <SectionTitle right={totalOpen + ' 个未解决'}>未解决缺口分布（按标签）</SectionTitle>
        <div className="gap-dist">
          {GAP_LABELS.map(l => (
            <div className="gd-row" key={l}>
              <span className="gd-label" style={{ color: GAP_COLORS[l] }}>{l}</span>
              <div className="gd-bar"><div className="gd-fill" style={{ width: (stats[l] / maxLabel) * 100 + '%', background: GAP_COLORS[l] }} /></div>
              <span className="gd-num">{stats[l]}</span>
            </div>
          ))}
        </div>
      </div>

      <div className="card">
        <div className="filter-row">
          <select value={label} onChange={e => setLabel(e.target.value)}>
            <option value="all">全部标签</option>
            {GAP_LABELS.map(l => <option key={l} value={l}>{l}</option>)}
          </select>
          <select value={status} onChange={e => setStatus(e.target.value)}>
            <option value="open">未解决</option>
            <option value="resolved">已消灭</option>
            <option value="all">全部</option>
          </select>
        </div>

        {filtered.length === 0 && <div className="empty-tip dim">暂无缺口{status === 'resolved' ? '已消灭记录' : ''}。</div>}
        {filtered.map(g => {
          const kp = kps[g.kpId];
          return (
            <div className={'gap-item' + (g.resolvedAt ? ' done' : '')} key={g.id}>
              <div className="gi-top">
                <Tag text={g.label} color={GAP_COLORS[g.label] || '#64748b'} />
                <span className="gi-title">{kp ? kp.title : g.title}</span>
                {kp && kp.category ? (
                  <Tag text={CATEGORY_META[kp.category] ? CATEGORY_META[kp.category].name : kp.category}
                    color={CATEGORY_META[kp.category] ? CATEGORY_META[kp.category].color : '#64748b'} />
                ) : null}
                <span className="gi-date dim">{fmtDate(g.createdAt)} 产生</span>
              </div>
              {g.detail ? <div className="gi-detail">{g.detail}</div> : null}
              <div className="gi-bottom">
                {g.recurredAt ? <span className="dim">复发于 {fmtDate(g.recurredAt)}</span> : null}
                <button className="btn btn-mini" onClick={() => setPreview(g.kpId)}>预览原文</button>
                {g.resolvedAt ? (
                  <span className="gi-resolved">已消灭 · {fmtDate(g.resolvedAt)}（经缺口检测答题）</span>
                ) : (
                  <button className="btn btn-mini btn-ok" disabled={testing === g.id} onClick={() => startTest(g)}>
                    {testing === g.id ? 'AI 生成检测题中…' : '缺口检测答题（≥75 分自动消灭）'}
                  </button>
                )}
              </div>
            </div>
          );
        })}
      </div>
      {preview && <DocPreview kpId={preview} kpTitle={(kps[preview] || {}).title || ''} onClose={() => setPreview(null)} />}
    </div>
  );
}
