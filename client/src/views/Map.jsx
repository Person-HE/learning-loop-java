import React, { useMemo, useState } from 'react';
import { api } from '../api.js';
import { useStore } from '../store.jsx';
import Workbench from './Workbench.jsx';
import DocPreview from './DocPreview.jsx';
import { CATEGORY_META, stars, Tag, SectionTitle, Spinner } from '../fmt.jsx';

const STATUS_COLOR = { mastered: '#059669', learning: '#2563eb', relearning: '#d97706', new: '#94a3b8' };

/** 域内按章（子模块）分组，章名升序（NN- 前缀字典序即章节序）
 *  注意：本模块导出的组件名 Map 会遮蔽内置 Map 构造器，这里禁用 new Map() */
function groupByChapter(kps) {
  const m = Object.create(null);
  for (const k of kps) {
    const c = k.chapter || '';
    if (!(c in m)) m[c] = [];
    m[c].push(k);
  }
  return Object.entries(m).sort((a, b) => a[0].localeCompare(b[0], 'zh-CN'))
    .map(([chapter, list]) => ({ chapter, kps: list }));
}

export default function Map() {
  const { data, refresh, showToast } = useStore();
  const [cat, setCat] = useState('java');
  const [selKp, setSelKp] = useState(null);
  const [wb, setWb] = useState(null);
  const [genning, setGenning] = useState(false);
  const [preview, setPreview] = useState(null);

  const domains = useMemo(() => (data.kbSummary.domains || []).filter(d => d.category === cat), [data, cat]);
  const maxDocs = Math.max(1, ...(data.kbSummary.domains || []).map(d => d.docs));
  const kps = data.kpsMeta || {};

  const catKps = useMemo(() => Object.values(kps).filter(k => k.category === cat), [kps, cat]);

  async function startKp(kpId, kind) {
    setGenning(true);
    try {
      const r = await api.genQuestions(kpId);
      setWb([{ kpId, kind, reason: kind === 'review' ? '手动复习' : '地图直接出题', qIdx: 0, questions: r.questions, genError: null }]);
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
        kpsMeta={kps}
        onDone={() => { setWb(null); refresh(true); }}
        showToast={showToast}
      />
    );
  }

  const sel = selKp && kps[selKp];

  return (
    <div className="view">
      <div className="view-head">
        <div>
          <h1>知识地图</h1>
          <div className="view-sub">三大类 × 域 × 章 × 知识点 · 状态一目了然</div>
        </div>
        <div className="cat-tabs">
          {Object.entries(CATEGORY_META).map(([k, v]) => (
            <button key={k} className={'cat-tab' + (cat === k ? ' on' : '')} style={cat === k ? { color: '#fff', background: v.color } : { color: v.color }}
              onClick={() => { setCat(k); setSelKp(null); }}>
              {v.name}
            </button>
          ))}
        </div>
      </div>

      <div className="map-grid">
        <div className="map-domains">
          {domains.length === 0 && (
            <div className="empty-tip">
              <p>该类别下暂无知识库文档。</p>
              <p className="dim">知识库对应目录建成后自动挂载；可在「设置」→ 重新扫描知识库。</p>
            </div>
          )}
          {domains.map(d => {
            const dkps = Object.values(kps).filter(k => k.domain === d.name);
            const learned = dkps.filter(k => k.state.status !== 'new').length;
            const mastered = dkps.filter(k => k.state.status === 'mastered').length;
            return (
              <div className="map-domain card" key={d.name}>
                <div className="md-head">
                  <b>{d.name}</b>
                  <span className={'kb-state ' + d.status}>
                    {d.status === 'done' ? '知识库已建' : d.status === 'building' ? '建设中' : '待产出'}
                  </span>
                </div>
                <div className="dual-progress">
                  <div className="dp-row">
                    <span className="dp-label">知识库建设</span>
                    <div className="dp-bar"><div className="dp-fill kb" style={{ width: (d.docs / maxDocs) * 100 + '%' }} /></div>
                    <span className="dp-num">{d.docs} 篇</span>
                  </div>
                  <div className="dp-row">
                    <span className="dp-label">个人学习</span>
                    <div className="dp-bar"><div className="dp-fill me" style={{ width: dkps.length ? (learned / dkps.length) * 100 + '%' : 0 }} /></div>
                    <span className="dp-num">{learned}/{dkps.length} · 掌握{mastered}</span>
                  </div>
                </div>
                <div className="md-kps">
                  {dkps.length > 0 ? groupByChapter(dkps).map(g => (
                    <div className="md-chapter" key={g.chapter}>
                      <div className="md-chapter-head">{g.chapter || '未分章'}<span className="md-chapter-count">{g.kps.length} 篇</span></div>
                      {g.kps.map(k => (
                        <button key={k.id} className={'kp-chip' + (selKp === k.id ? ' on' : '')}
                          onClick={() => setSelKp(k.id)} title={k.chapter + ' / ' + k.title}>
                          <span className="kp-dot" style={{ background: STATUS_COLOR[k.state.status] || '#94a3b8' }} />
                          <span className="kp-title">{k.title}</span>
                          <span className="kp-stars">{stars(k.difficulty)}</span>
                        </button>
                      ))}
                    </div>
                  )) : null}
                  {dkps.length === 0 && (
                    <div className="planned-line">计划路径待知识库产出：{(data.kbSummary.plannedPaths[cat] || []).map(p => p.name).join(' / ') || '暂无规划'}</div>
                  )}
                </div>
              </div>
            );
          })}
        </div>

        <div className="map-detail">
          {!sel ? (
            <div className="card detail-empty">
              <p>点击左侧知识点查看详情</p>
              <p className="dim">显示难度 / 热度 / 记忆状态 / 缺口 / 原文路径，并可直接出题或复习</p>
            </div>
          ) : (
            <div className="card detail-card">
              <div className="dc-title">{sel.title}</div>
              <div className="dc-meta">
                <Tag text={CATEGORY_META[sel.category].name} color={CATEGORY_META[sel.category].color} />
                <span className="dim">{sel.domain} · {sel.chapter}</span>
                <span>{stars(sel.difficulty)}</span>
                <Tag text={'热度 ' + sel.hot + '/5'} color={sel.hot >= 4 ? '#dc2626' : '#64748b'} />
                <Tag text={sel.kbStatus === 'done' ? '知识库已建' : '建设中'} color={sel.kbStatus === 'done' ? '#059669' : '#d97706'} />
              </div>
              <div className="dc-rows">
                <div className="dc-row"><span className="dc-k">记忆状态</span><span>{sel.state.status === 'mastered' ? '已掌握' : sel.state.status === 'learning' ? '学习中' : sel.state.status === 'relearning' ? '复习中（重学）' : '未开始'}</span></div>
                <div className="dc-row"><span className="dc-k">记忆强度 S</span><span>{sel.state.stability || '—'} 天</span></div>
                <div className="dc-row"><span className="dc-k">间隔 / 到期</span><span>{sel.state.interval} 天 / {sel.state.due}</span></div>
                <div className="dc-row"><span className="dc-k">上次得分</span><span>{sel.state.lastScore != null ? sel.state.lastScore : '未作答'}</span></div>
                <div className="dc-row"><span className="dc-k">答错次数</span><span>{sel.state.lapses}</span></div>
                <div className="dc-row"><span className="dc-k">原文路径</span><span className="dc-path" title={sel.path}>{sel.path}</span></div>
              </div>
              <div className="dc-actions">
                <button className="btn btn-primary" disabled={genning} onClick={() => startKp(sel.id, 'new')}>
                  {genning ? 'AI 出题中…' : '开始出题'}
                </button>
                {sel.state.status !== 'new' && sel.state.due <= data.today ? (
                  <button className="btn" disabled={genning} onClick={() => startKp(sel.id, 'review')}>复习（已到期）</button>
                ) : null}
                <button className="btn" onClick={() => setPreview(sel.id)}>预览原文</button>
                <button className="btn btn-ghost" onClick={() => { navigator.clipboard && navigator.clipboard.writeText(sel.path); showToast('原文路径已复制', 'ok'); }}>复制路径</button>
              </div>
            </div>
          )}
        </div>
      </div>

      {genning && <div className="gen-mask"><Spinner text="AI 基于知识库文档出题中…" /></div>}
      {preview && <DocPreview kpId={preview} kpTitle={(kps[preview] || {}).title || ''} onClose={() => setPreview(null)} />}
    </div>
  );
}
