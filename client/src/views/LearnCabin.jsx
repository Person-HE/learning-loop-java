// 学习舱：精读 → 合书复述（AI 只数 M/N 不泄露答案）→ 精准回看 → 再复述，循环直到覆盖 ≥80%
import React, { useState } from 'react';
import { api } from '../api.js';
import DocPreview from './DocPreview.jsx';
import { Spinner } from '../fmt.jsx';

export default function LearnCabin({ kpId, kpTitle, kp, q, onExit, showToast }) {
  const [mode, setMode] = useState('read');       // read | restate | result
  const [showDoc, setShowDoc] = useState(true);   // 原文是否可见（复述时隐藏）
  const [anchorTo, setAnchorTo] = useState(null); // 回看定位锚点
  const [restate, setRestate] = useState('');
  const [res, setRes] = useState(null);           // {covered,missed,coverage_count,total_count,coverage_pct,comment}
  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState('');

  const pass = res && res.coverage_pct >= 80;

  async function submitRestate() {
    const t = restate.trim();
    if (t.length < 10) { showToast('复述太短，试着至少写一句话（≥10字）', 'error'); return; }
    setLoading(true);
    setErr('');
    try {
      const r = await api.restateScore({ kpId, questionId: q.id, answerText: t });
      setRes(r);
      setMode('result');
    } catch (e) {
      setErr(e.message || '评估失败');
    } finally {
      setLoading(false);
    }
  }

  function backToRead(hint) {
    setAnchorTo(hint || null);
    setShowDoc(true);
    setMode('read');
  }

  return (
    <div className="workbench learn-cabin">
      <div className="wb-head">
        <button className="btn btn-ghost wb-back" onClick={onExit} title="退出学习舱，返回结果页">← 退出学习舱</button>
        <div className="wb-title">
          <span className="cabin-step-tag">{mode === 'read' ? '① 精读' : mode === 'restate' ? '② 合书复述' : '③ 复盘'}</span>
          <span className="dot">·</span>
          {kpTitle}
        </div>
      </div>

      {/* 精读 / 回看：原文全功能预览（左目录 + 拖拽分栏 + 记忆卡） */}
      {mode === 'read' && showDoc && (
        <div className="cabin-read">
          <DocPreview
            kpId={kpId}
            kpTitle={kpTitle}
            jumpAnchor={anchorTo}
            onClose={() => setShowDoc(false)}
            heightPx={window.innerHeight - 168}
          />
          <div className="cabin-read-bar">
            <div className="cabin-read-tip">精读要点：先看「记忆卡」背下核心结论，再按「考点地图」逐个过，重点看动画图解。</div>
            <button className="btn btn-primary" onClick={() => { setShowDoc(false); setMode('restate'); }}>
              我看完了，合上书复述 →
            </button>
          </div>
        </div>
      )}

      {/* 复述 */}
      {mode === 'restate' && (
        <div className="wb-card cabin-card">
          <div className="block-title">合书复述（不许看原文，凭记忆把这道题讲出来）</div>
          <div className="cabin-question">{q.question}</div>
          <textarea rows={8} className="ta-ans"
            placeholder="像面试一样把能记住的都写出来：核心结论、机制、关键数字、边界…写不全也没关系，AI 只数你讲到几个关键点，不会批你"
            value={restate} onChange={e => setRestate(e.target.value)} />
          {err && <div className="empty-tip err-text">评估失败：{err}</div>}
          <div className="wb-actions">
            <button className="btn btn-ghost" onClick={() => backToRead(null)}>没记住，回去再看一遍原文</button>
            <button className="btn btn-primary" onClick={submitRestate} disabled={loading}>
              {loading ? 'AI 数关键点中…' : '提交复述，让 AI 数我讲到了几个点'}
            </button>
          </div>
        </div>
      )}

      {/* 复述结果：M/N + 漏点回看 + 循环 */}
      {mode === 'result' && res && (
        <div className="wb-card cabin-card">
          <div className="restate-head">
            <div className={'restate-ring' + (pass ? ' pass' : '')}>
              <b>{res.coverage_pct}</b><span>%</span>
              <div className="restate-ring-sub">{res.coverage_count}/{res.total_count} 关键点</div>
            </div>
            <div className="restate-info">
              <div className="restate-comment">{res.comment}</div>
              {pass
                ? <div className="restate-verdict ok">覆盖达标 — 这道题的关键点你已经能凭记忆讲出来，进入下一题。</div>
                : <div className="restate-verdict">还没到 80%，点击下面的漏点回看原文，再回来复述一遍，直到能讲全。</div>}
            </div>
          </div>

          {res.covered && res.covered.length > 0 && (
            <div className="block">
              <div className="block-title ok">讲到了（{res.covered.length}）</div>
              {res.covered.map((c, i) => (
                <div className="restate-covered" key={i}>
                  <span className="cov-dot">✓</span>
                  <div><b>{c.point}</b>{c.note ? <div className="dim">{c.note}</div> : null}</div>
                </div>
              ))}
            </div>
          )}

          {res.missed && res.missed.length > 0 && (
            <div className="block">
              <div className="block-title err">漏掉了（{res.missed.length}）—— 点击回看原文对应位置</div>
              {res.missed.map((m, i) => (
                <div className="restate-missed" key={i}>
                  <div className="rm-left">
                    <b>{m.point}</b>
                    {m.hint ? <span className="rm-hint">{m.hint}</span> : null}
                  </div>
                  <button className="btn btn-mini" onClick={() => backToRead(m.hint)}>回看原文</button>
                </div>
              ))}
            </div>
          )}

          <div className="wb-actions">
            {!pass && <button className="btn btn-ghost" onClick={() => backToRead(null)}>整篇再精读一遍</button>}
            {!pass && <button className="btn btn-primary" onClick={() => setMode('restate')}>再复述一遍（覆盖不到 80% 不放过自己）</button>}
            {pass && <button className="btn btn-primary" onClick={onExit}>掌握，退出学习舱 ✓</button>}
          </div>
        </div>
      )}
    </div>
  );
}
