// 知识库原文预览（v3）：左目录栏（可展开/收缩 + 拖拽调宽）| 动画图解与正文拖拽分栏 | 记忆卡 | 锚点滚动高亮
import React, { useEffect, useMemo, useRef, useState } from 'react';
import { api } from '../api.js';
import { Spinner } from '../fmt.jsx';

// 极简 Markdown → HTML（输入先整体转义，输出仅含安全标签）
export function markdownToHtml(src) {
  if (!src) return '<p class="dim">（空文档）</p>';
  const esc = String(src)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
  const lines = esc.split(/\r?\n/);
  const out = [];
  let hIdx = 0;
  let inCode = false;
  let codeBuf = [];
  let inTable = false;
  let tableBuf = [];
  let listType = null;

  const flushTable = () => {
    if (!tableBuf.length) return;
    const rows = tableBuf.map(r => r.split('|').slice(1, -1).map(c => c.trim()));
    let html = '<div class="md-table-wrap"><table class="md-table">';
    rows.forEach((r, i) => {
      if (i === 1 && r.every(c => /^:?-{1,}:?$/.test(c))) return; // 分隔行
      html += '<tr>' + r.map(c => (i === 0 ? '<th>' + c + '</th>' : '<td>' + c + '</td>')).join('') + '</tr>';
    });
    html += '</table></div>';
    out.push(html);
    tableBuf = [];
  };

  for (let i = 0; i < lines.length; i++) {
    const raw = lines[i];

    if (raw.trim().startsWith('```')) {
      if (!inCode) { flushTable(); listType = null; inCode = true; codeBuf = []; }
      else { inCode = false; out.push('<pre class="md-code">' + codeBuf.join('\n') + '</pre>'); }
      continue;
    }
    if (inCode) { codeBuf.push(raw); continue; }

    if (raw.trim().startsWith('|') && raw.trim().endsWith('|')) {
      if (!inTable) { flushTable(); listType = null; inTable = true; }
      tableBuf.push(raw.trim());
      continue;
    }
    if (inTable) { flushTable(); inTable = false; }

    if (!raw.trim()) { listType = null; if (out.length && out[out.length - 1] !== '<br>') out.push('<br>'); continue; }

    const h = raw.match(/^(#{1,6})\s+(.*)$/);
    if (h) {
      listType = null;
      const text = h[2].replace(/\*\*(.+?)\*\*/g, '<b>$1</b>').replace(/`(.+?)`/g, '<code>$1</code>');
      const level = h[1].length;
      out.push(`<h${level} class="md-h" id="md-anchor-${hIdx++}">${text}</h${level}>`);
      continue;
    }

    const li = raw.match(/^\s*([-*+]|\d+[.)])\s+(.*)$/);
    if (li) {
      const ordered = /\d/.test(li[1]);
      const tag = ordered ? 'ol' : 'ul';
      if (listType !== tag) { listType = tag; out.push('<' + tag + ' class="md-list">'); }
      out.push('<li>' + inline(li[2]) + '</li>');
      const next = lines[i + 1];
      if (!next || !/^\s*([-*+]|\d+[.)])\s+/.test(next)) { out.push('</' + tag + '>'); listType = null; }
      continue;
    }

    const q = raw.match(/^\s*>\s?(.*)$/);
    if (q) { listType = null; out.push('<blockquote class="md-quote">' + inline(q[1]) + '</blockquote>'); continue; }

    if (/^\s*([-*_])\1{2,}\s*$/.test(raw)) { listType = null; out.push('<hr class="md-hr">'); continue; }

    listType = null;
    out.push('<p class="md-p">' + inline(raw) + '</p>');
  }
  if (inCode) out.push('<pre class="md-code">' + codeBuf.join('\n') + '</pre>');
  if (inTable) flushTable();

  function inline(s) {
    return s
      .replace(/\*\*(.+?)\*\*/g, '<b>$1</b>')
      .replace(/\*(.+?)\*/g, '<i>$1</i>')
      .replace(/`(.+?)`/g, '<code>$1</code>')
      .replace(/\[(.+?)\]\((https?:[^)\s]+)\)/g, '<a href="$2" target="_blank" rel="noopener">$1</a>');
  }

  return out.join('\n');
}

// 在正文中定位锚点文本 → 返回该标题在文档里的 index（与 markdownToHtml 的 md-anchor-N 完全一致）
function findAnchorIndex(docText, target) {
  if (!docText || !target) return -1;
  const norm = s => String(s).replace(/^#+\s*/, '').replace(/[*`"'「」『』<>《》]/g, '').replace(/\s+/g, '').trim();
  const t = norm(target);
  const m = docText.match(/^#{1,6}\s+.+$/gm) || [];
  for (let i = 0; i < m.length; i++) {
    const cand = norm(m[i]);
    if (cand === t || cand.includes(t) || t.includes(cand)) return i;
  }
  return -1;
}

// 精确定位跳转目标：支持「章节标题」「看'词袋缺陷'小节」等 hint，标题匹配失败则按正文关键词回溯最近标题
function locateAnchor(docText, target) {
  if (!docText || !target) return -1;
  const norm = s => String(s).replace(/^#+\s*/, '').replace(/[*`"'「」『』<>《》]/g, '').replace(/\s+/g, '').trim();
  let tRaw = String(target || '').trim();
  // 跳转目标若是「文档路径#章节」或 hint（看'XX'小节），提取真正的章节定位词
  if (tRaw.includes('.md') || tRaw.includes('#')) tRaw = tRaw.split('#')[1] || tRaw.split('/').pop().replace(/\.md$/, '');
  const quoted = tRaw.match(/['"「『]([^'"」』]{1,40})['"」』]/);
  const keys = [tRaw, quoted ? quoted[1] : null].filter(Boolean).map(norm).filter(k => k.length >= 2);
  if (!keys.length) return -1;

  const headings = (docText.match(/^#{1,6}\s+.+$/gm) || []);
  // 1) 先精确匹配标题
  for (const key of keys) {
    for (let i = 0; i < headings.length; i++) {
      const cand = norm(headings[i]);
      if (cand === key || (cand.length >= 2 && (cand.includes(key) || key.includes(cand)))) return i;
    }
  }
  // 2) 正文关键词 → 回溯到它前面最近的标题
  const lines = docText.split(/\r?\n/);
  let headingIdx = -1;
  for (let i = 0; i < lines.length; i++) {
    if (/^#{1,6}\s+/.test(lines[i])) { headingIdx++; continue; }
    const key = keys[0];
    if (key && norm(lines[i]).includes(key) && headingIdx >= 0) return headingIdx;
  }
  return -1;
}

export default function DocPreview({ kpId, kpTitle, jumpAnchor, onClose, plan: planProp, heightPx }) {
  const [doc, setDoc] = useState(null);
  const [err, setErr] = useState('');
  const [zoom, setZoom] = useState(null);
  const [plan, setPlan] = useState(planProp || null);
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [sidebarW, setSidebarW] = useState(232);
  const [assetH, setAssetH] = useState(180);
  const [assetOpen, setAssetOpen] = useState(true);
  const [activeAnchor, setActiveAnchor] = useState(-1);
  const bodyRef = useRef(null);
  const dragRef = useRef(null);

  useEffect(() => {
    let alive = true;
    setDoc(null);
    setErr('');
    api.getKbDoc(kpId)
      .then(d => { if (alive) setDoc(d); })
      .catch(e => { if (alive) setErr(e.message || '加载失败'); });
    return () => { alive = false; };
  }, [kpId]);

  // 记忆卡 / 考点：父级未传则自行加载
  useEffect(() => {
    if (planProp) { setPlan(planProp); return; }
    let alive = true;
    api.getKpPoints(kpId)
      .then(p => { if (alive) setPlan(p); })
      .catch(() => { /* 考点加载失败不阻塞原文 */ });
    return () => { alive = false; };
  }, [kpId, planProp]);

  // 侧边栏目录：与 markdownToHtml 的 md-anchor-N 完全一致的「全部标题」序列（含层级，按级缩进）
  const anchors = useMemo(() => {
    if (!doc) return [];
    const m = doc.text && doc.text.match(/^(#{1,6})\s+(.+)$/gm);
    if (m) return m.map(x => {
      const lv = (x.match(/^#+/) || [''])[0].length;
      return { text: x.replace(/^#+\s*/, '').replace(/[*`]/g, '').trim(), level: lv };
    });
    return (doc.anchors || []).map(a => ({ text: a.text, level: 2 }));
  }, [doc]);

  // 跳转到指定锚点（先渲染后滚动；支持章节标题 / hint「看'XX'小节」；滚动到可视区中部避免被头部遮挡）
  useEffect(() => {
    if (!doc || !jumpAnchor) return;
    const t = setTimeout(() => {
      const idx = locateAnchor(doc.text, jumpAnchor);
      if (idx >= 0) scrollToAnchor(idx);
    }, 150);
    return () => clearTimeout(t);
  }, [doc, jumpAnchor, anchors]);

  // 滚动高亮当前章节
  useEffect(() => {
    if (!doc || !bodyRef.current) return;
    const els = Array.from(bodyRef.current.querySelectorAll('[id^="md-anchor-"]'));
    if (!els.length) return;
    const obs = new IntersectionObserver(entries => {
      for (const en of entries) {
        if (en.isIntersecting) {
          const n = Number(en.target.id.replace('md-anchor-', ''));
          setActiveAnchor(n);
        }
      }
    }, { root: bodyRef.current, rootMargin: '-10% 0px -80% 0px', threshold: 0 });
    els.forEach(el => obs.observe(el));
    return () => obs.disconnect();
  }, [doc]);

  function scrollToAnchor(idx) {
    const el = document.getElementById('md-anchor-' + idx);
    if (el) {
      setActiveAnchor(idx);
      // 滚动到可视区中部：标题精确落在视口中心线，不受头部/动画区遮挡，位置最准
      el.scrollIntoView({ behavior: 'smooth', block: 'center' });
      el.classList.remove('anchor-flash');
      void el.offsetWidth;
      el.classList.add('anchor-flash');
    }
  }

  // 拖拽分栏（横向：目录栏宽度；纵向：动画区高度）
  function startDrag(e, axis) {
    e.preventDefault();
    e.stopPropagation();
    const startX = e.clientX;
    const startY = e.clientY;
    const startW = sidebarW;
    const startH = assetH;
    const move = ev => {
      if (axis === 'x') {
        const w = Math.max(170, Math.min(380, startW + (ev.clientX - startX)));
        setSidebarW(w);
      } else {
        const h = Math.max(80, Math.min(480, startH + (startY - ev.clientY)));
        setAssetH(h);
      }
    };
    const up = () => {
      window.removeEventListener('mousemove', move);
      window.removeEventListener('mouseup', up);
      dragRef.current = null;
    };
    window.addEventListener('mousemove', move);
    window.addEventListener('mouseup', up);
  }

  return (
    <>
    <div className="doc-mask" onClick={onClose}>
      <div className="doc-modal doc-modal-v3" style={heightPx ? { height: heightPx, maxHeight: heightPx } : undefined} onClick={e => e.stopPropagation()}>
        <div className="doc-head">
          <div className="doc-title">
            <b>{doc ? doc.title : kpTitle}</b>
            {doc && <span className="dim">{doc.path}</span>}
          </div>
          <button className="btn btn-mini" onClick={onClose}>关闭 ✕</button>
        </div>

        <div className="doc-layout">
          {/* ===== 左目录栏（可折叠 + 可拖拽调宽）===== */}
          {sidebarOpen && (
            <div className="doc-sidebar" style={{ width: sidebarW }}>
              <div className="doc-sidebar-head">
                <span>目录 / 记忆卡</span>
                <button className="btn btn-mini" title="收起目录栏" onClick={() => setSidebarOpen(false)}>«</button>
              </div>
              <div className="doc-sidebar-scroll">
                {plan && plan.memory_cards && plan.memory_cards.length > 0 && (
                  <div className="ds-section">
                    <div className="ds-title">记忆卡（先背这三五句）</div>
                    {plan.memory_cards.map((c, i) => (
                      <div className="mem-card" key={i}>
                        <div className="mem-core">{c.core}</div>
                        {c.detail ? <div className="mem-detail">{c.detail}</div> : null}
                      </div>
                    ))}
                  </div>
                )}
                {plan && plan.points && plan.points.length > 0 && (
                  <div className="ds-section">
                    <div className="ds-title">考点地图</div>
                    {plan.points.map((p, i) => (
                      <button key={i} className="ds-point"
                        onClick={() => { const idx = findAnchorIndex(doc && doc.text, p.anchor); if (idx >= 0) scrollToAnchor(idx); }}>
                        <span className={'dp-dot' + (plan.resolvedPoints && plan.resolvedPoints.includes(p.name) ? ' done' : '')} />
                        <span className="dp-name">{p.name}</span>
                      </button>
                    ))}
                  </div>
                )}
                {anchors.length > 0 && (
                  <div className="ds-section">
                    <div className="ds-title">章节锚点（点击跳转正文）</div>
                    {anchors.map((a, i) => (
                      <button key={i}
                        className={'ds-anchor lv' + Math.min(6, a.level || 2) + (activeAnchor === i ? ' on' : '')}
                        style={{ paddingLeft: 10 + Math.max(0, (a.level || 2) - 2) * 10 }}
                        onClick={() => scrollToAnchor(i)}>{a.text}</button>
                    ))}
                  </div>
                )}
              </div>
            </div>
          )}

          {/* 目录栏拖拽条 */}
          {sidebarOpen && <div className="doc-resizer-x" onMouseDown={e => startDrag(e, 'x')} />}

          {/* ===== 右侧内容区 ===== */}
          <div className="doc-main">
            {/* 动画图解区（可折叠 + 高度拖拽） */}
            {doc && doc.assets && doc.assets.length > 0 && (
              <div className="doc-assets-v3" style={{ height: assetOpen ? assetH : 40 }}>
                <div className="doc-assets-head">
                  <span>动画图解（{doc.assets.length}）· 点击放大</span>
                  <div className="doc-assets-ops">
                    <button className="btn btn-mini" onClick={() => setAssetOpen(o => !o)}>
                      {assetOpen ? '收起' : '展开'}
                    </button>
                  </div>
                </div>
                {assetOpen && (
                  <div className="doc-assets-scroll">
                    {doc.assets.map((a, i) => (
                      <figure key={i} className={'doc-asset doc-asset-v3 ' + a.kind} onClick={() => setZoom(a)} title="点击放大查看">
                        <figcaption>{a.name}</figcaption>
                        {a.kind === 'html' ? (
                          <iframe src={a.url} title={a.name} className="doc-asset-frame" sandbox="allow-scripts allow-same-origin" loading="lazy" />
                        ) : (
                          <img src={a.url} alt={a.name} loading="lazy" className="doc-asset-img" />
                        )}
                      </figure>
                    ))}
                  </div>
                )}
              </div>
            )}
            {/* 动画区拖拽条 */}
            {doc && doc.assets && doc.assets.length > 0 && assetOpen && (
              <div className="doc-resizer-y" onMouseDown={e => startDrag(e, 'y')} />
            )}

            {/* 正文（最大空间） */}
            <div className="doc-body" ref={bodyRef}>
              {!doc && !err && <div className="doc-loading"><Spinner text="加载知识库原文…" /></div>}
              {err && <div className="empty-tip err-text">加载失败：{err}</div>}
              {doc && (
                <div className="md-body" dangerouslySetInnerHTML={{ __html: markdownToHtml(doc.text) }} />
              )}
            </div>
          </div>
        </div>

        {doc && <div className="doc-foot dim">{doc.truncated ? '文档过长，已截取前 12 万字' : '共 ' + doc.text.length + ' 字'} · 知识库原文直读，供对照学习</div>}
      </div>
    </div>
    {zoom && (
      <div className="doc-zoom-mask" onClick={() => setZoom(null)}>
        <div className="doc-zoom" onClick={e => e.stopPropagation()}>
          <div className="doc-zoom-head">
            <b>动画图解 · {zoom.name}</b>
            <button className="btn btn-mini" onClick={() => setZoom(null)}>关闭 ✕</button>
          </div>
          {zoom.kind === 'html' ? (
            <iframe src={zoom.url} title={zoom.name} className="doc-zoom-frame" sandbox="allow-scripts allow-same-origin" />
          ) : (
            <img src={zoom.url} alt={zoom.name} className="doc-zoom-img" />
          )}
        </div>
      </div>
    )}
    </>
  );
}
