// 力扣题动画图解播放器 v2 —— 零基础教学级
// 帧结构（向后兼容旧数据）：
//   title    本帧步骤标题（如「第 1 步：先把后继节点记住」）
//   state    当前画面状态的一句话描述
//   action   这一步具体做了什么
//   why      为什么必须这样做（小白可懂的原因）
//   tip      小白提示 / 类比 / 口诀
//   explain  旧的 desc 字段自动归入此区
// 可视化增强字段（按类型）：
//   linked_list: nexts[] 每个节点的 next 当前指向（索引 或 -1=null），cut[] 被切断的边，newLink[] 新形成的边
//   hash:        target 要找的值 / phase(lookup|hit|write) / justWrite 刚写入的键
//   sliding_window: win 窗口字符串 / dupAt 重复字符下标 / shift 本次右移原因
//   stack:       op(push|pop|peek) / newTop 本次操作的元素
//   dp:          formula 推导式 / from 取值来源说明
import React, { useEffect, useRef, useState } from 'react';

function pick(v) {
  if (typeof v === 'string') {
    try { const j = JSON.parse(v); if (j && typeof j === 'object') return j; } catch { /* keep */ }
  }
  return v;
}

/* ============ 链表可视化（教学级） ============ */
function LinkedListCanvas({ frame, meta }) {
  const nodes = meta.nodes || [];
  const nexts = frame.nexts || null;      // 显式 next 指向（索引或 -1）
  const cut = frame.cut || [];            // 被切断的边
  const newLink = frame.newLink || [];    // 新形成的边（from 索引）
  const done = frame.done || [];
  const cur = frame.cur;
  const prev = frame.prev;
  const savedNext = frame.savedNext;      // 刚保存的后继节点值（显示在讲解区）

  // 默认 next 指向：i -> i+1，最后一个 -> null
  function nextOf(i) {
    if (nexts) return nexts[i] !== undefined ? nexts[i] : null;
    return i < nodes.length - 1 ? i + 1 : -1;
  }

  // 每个节点的 next 目标显示值
  function nextLabel(i) {
    const t = nextOf(i);
    if (t === -1) return 'null';
    return String(nodes[t]);
  }

  // 该边是否被切断 / 是新形成的
  const isCut = i => cut.includes(i);
  const isNew = i => newLink.includes(i);

  return (
    <div className="lc-anim-canvas">
      <div className="lc-ll-stage">
        <div className="lc-ll-row">
          {nodes.map((v, i) => {
            const cls = ['lc-ll-node'];
            if (i === cur) cls.push('cur');
            if (i === prev) cls.push('prev');
            if (done.includes(i)) cls.push('done');
            if (isCut(i)) cls.push('cut');
            if (isNew(i)) cls.push('newlink');
            const ptr = [];
            if (i === prev) ptr.push('prev');
            if (i === cur) ptr.push('cur');
            return (
              <React.Fragment key={i}>
                <div className={cls.join(' ')}>
                  {ptr.length > 0 && <div className="lc-ll-ptr">{ptr.join(' / ')}</div>}
                  <div className="lc-ll-val">{v}</div>
                  <div className={'lc-ll-next' + (isNew(i) ? ' new' : '') + (isCut(i) && !isNew(i) ? ' cut' : '')}>
                    {isNew(i) ? `next → ${nextLabel(i)}` : isCut(i) ? '✂ 已断开' : `next → ${nextLabel(i)}`}
                    {isNew(i) && <span className="lc-ll-newbadge">新</span>}
                  </div>
                </div>
                {i < nodes.length - 1 && (
                  <div className={'lc-ll-arrow' + (isCut(i) ? ' cut' : '') + (isNew(i) ? ' new' : '')}>
                    {isCut(i) ? <span className="lc-ll-cutmark">✂</span> : '→'}
                  </div>
                )}
              </React.Fragment>
            );
          })}
          {/* 末尾 null 节点 */}
          <div className={'lc-ll-node lc-ll-null' + (cur === -1 ? ' cur' : '')}>
            <div className="lc-ll-val">null</div>
            <div className="lc-ll-next">{'（终点）'}</div>
          </div>
        </div>
        <div className="lc-ll-legend">
          <span className="lg lg-prev">prev 前一个</span>
          <span className="lg lg-cur">cur 当前</span>
          <span className="lg lg-new">新改的 next</span>
          <span className="lg lg-cut">被切断的旧 next</span>
        </div>
        {savedNext !== undefined && <div className="lc-ll-saved">✋ 先把后继 {savedNext} 存进临时变量，防止断链后丢失</div>}
      </div>
    </div>
  );
}

/* ============ 哈希表可视化（教学级） ============ */
function HashCanvas({ frame, meta }) {
  const nums = meta.nums || [];
  const map = pick(frame.map) || {};
  const phase = frame.phase || (frame.hit ? 'hit' : 'write');
  const need = frame.need;
  const target = frame.target !== undefined ? frame.target : need;
  const justWrite = frame.justWrite; // 刚写入的键

  return (
    <div className="lc-anim-canvas">
      <div className="lc-anim-row-label">数组 nums（逐个找配对）</div>
      <div className="lc-arr">
        {nums.map((v, i) => (
          <div key={i} className={'lc-cell' + (i === frame.i ? ' hi' : '')}>
            <div className="lc-cell-v">{v}</div>
            <div className="lc-cell-i">下标 {i}</div>
          </div>
        ))}
      </div>
      <div className="lc-hash-search">
        <div className="lc-hash-tag">现在看 nums[{frame.i}] = <b>{frame.val}</b></div>
        {target !== undefined && (
          <div className="lc-hash-tag">
            要找配对：target - {frame.val} = <b className="lc-hash-need">{target}</b>
          </div>
        )}
        {phase === 'lookup' && <div className="lc-hash-tag lc-tag-blue">🔍 在哈希表里找 {target}……</div>}
        {phase === 'hit' && <div className="lc-hash-tag lc-tag-green">✓ 找到了！{target} 在下标 {map[String(target)]}，配对成功</div>}
        {phase === 'write' && <div className="lc-hash-tag lc-tag-amber">没找到 → 把 {frame.val} 记进哈希表（值 → 下标），留给后面的数配对</div>}
      </div>
      <div className="lc-map">
        <div className="lc-anim-row-label">哈希表（值 → 下标，用来记住「见过谁」）</div>
        {Object.keys(map).length === 0 ? <div className="lc-map-empty">（空表，还没记住任何数）</div> : (
          <div className="lc-map-rows">
            {Object.entries(map).map(([k, v]) => (
              <div key={k} className={'lc-map-kv' + (String(target) === String(k) && phase === 'hit' ? ' hit' : '') + (justWrite !== undefined && String(justWrite) === String(k) ? ' just' : '')}>
                <span className="lc-map-k">{k}</span><span className="lc-map-a">→</span><span className="lc-map-v">{v}</span>
                {justWrite !== undefined && String(justWrite) === String(k) && <span className="lc-map-newbadge">刚写入</span>}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

/* ============ 滑动窗口可视化（教学级） ============ */
function SlidingCanvas({ frame, meta }) {
  const s = meta.s || '';
  const l = frame.l, r = frame.r;
  const dupAt = frame.dupAt; // 重复字符下标（右指针遇到它）
  const shift = frame.shift; // 本次窗口移动原因
  return (
    <div className="lc-anim-canvas">
      <div className="lc-anim-row-label">字符串（绿色 = 当前窗口，红框 = 刚发现重复）</div>
      <div className="lc-arr">
        {s.split('').map((ch, i) => (
          <div key={i} className={'lc-cell' + (i >= l && i <= r ? ' win' : '') + (i === dupAt ? ' dup' : '')}>
            <div className="lc-cell-v">{ch}</div>
            <div className="lc-cell-i">{i}{i === l ? ' ←L' : ''}{i === r ? ' ←R' : ''}</div>
          </div>
        ))}
      </div>
      <div className="lc-slide-info">
        <div className="lc-slide-tag">窗口 s[{l}..{r}] = <b>"{frame.win}"</b>（长度 {r - l + 1}）</div>
        <div className="lc-slide-tag">历史最长无重复 = <b>{frame.max}</b></div>
        {shift && <div className="lc-slide-tag lc-tag-amber">→ {shift}</div>}
      </div>
    </div>
  );
}

/* ============ 栈可视化（教学级） ============ */
function StackCanvas({ frame, meta }) {
  const stack = (frame.stack || []).slice();
  const op = frame.op; // push | pop | peek
  const newTop = frame.newTop;
  const ch = frame.ch;
  return (
    <div className="lc-anim-canvas lc-stack-canvas">
      <div className="lc-stack-col">
        <div className="lc-stack-top">{stack.length ? '栈顶（最后进去的）' : '空栈'}</div>
        <div className="lc-stack-box">
          {[...stack].reverse().map((c, i) => (
            <div key={i} className={'lc-cell lc-stack-cell' + (i === 0 ? ' top' : '')}>{c}</div>
          ))}
          {stack.length === 0 && <div className="lc-stack-empty">（空）</div>}
        </div>
        <div className="lc-stack-foot">栈底（最先进去的）</div>
      </div>
      <div className="lc-stack-op">
        {op === 'push' && <div className="lc-op lc-op-push">⬆ 入栈：读到 <b>'{ch}'</b>，压进栈顶</div>}
        {op === 'pop' && <div className="lc-op lc-op-pop">⬇ 出栈：栈顶 <b>'{newTop}'</b> 与它配对，弹出</div>}
        {op === 'peek' && <div className="lc-op lc-op-peek">👀 只看栈顶：<b>'{newTop}'</b></div>}
        {!op && <div className="lc-op">{frame.desc || frame.explain || ''}</div>}
      </div>
    </div>
  );
}

/* ============ DP 可视化（教学级） ============ */
function DpCanvas({ frame, meta }) {
  const nums = meta.nums || [];
  const formula = frame.formula;      // 推导式（如 cur = max(nums[i], cur + nums[i])）
  const from = frame.from;            // 取值来源说明
  const kv = Object.fromEntries(Object.entries(frame).filter(([k]) =>
    !['desc', 'explain', 'title', 'state', 'action', 'why', 'tip', 'i', 'formula', 'from'].includes(k)
  ));
  return (
    <div className="lc-anim-canvas">
      {nums.length > 0 && (
        <>
          <div className="lc-anim-row-label">数组 nums（高亮 = 正在处理）</div>
          <div className="lc-arr">
            {nums.map((v, i) => (
              <div key={i} className={'lc-cell' + (i === frame.i ? ' hi' : '')}>
                <div className="lc-cell-v">{v}</div>
                <div className="lc-cell-i">下标 {i}</div>
              </div>
            ))}
          </div>
        </>
      )}
      {formula && <div className="lc-dp-formula">📐 推导公式：<b>{formula}</b></div>}
      <div className="lc-kv-panel">
        {Object.entries(kv).map(([k, v]) => (
          <div key={k} className="lc-kv"><span className="lc-kv-k">{k}</span><span className="lc-kv-v">{typeof v === 'object' ? JSON.stringify(v) : String(v)}</span></div>
        ))}
      </div>
      {from && <div className="lc-dp-from">💡 {from}</div>}
    </div>
  );
}

/* ============ 一维数组可视化（教学级） ============ */
function ArrayCanvas({ frame, meta }) {
  const nums = meta.nums || [];
  const i = frame.i, j = frame.j, k = frame.k, l = frame.l, r = frame.r, mid = frame.mid;
  const hi = frame.hi || [];
  const swap = frame.swap;
  const cmp = frame.cmp;
  const range = frame.range;
  const ptrs = {};
  const addPtr = (idx, name) => { if (idx !== undefined && idx >= 0) { (ptrs[idx] = ptrs[idx] || []).push(name); } };
  addPtr(i, 'i'); addPtr(j, 'j'); addPtr(k, 'k'); addPtr(l, 'l'); addPtr(r, 'r'); addPtr(mid, 'mid');
  const kv = Object.fromEntries(Object.entries(frame).filter(([k2]) =>
    !['title', 'state', 'action', 'why', 'tip', 'desc', 'i', 'j', 'k', 'l', 'r', 'mid', 'hi', 'swap', 'cmp', 'range'].includes(k2)
  ));
  return (
    <div className="lc-anim-canvas">
      <div className="lc-anim-row-label">数组 nums（指针与高亮标注当前操作）</div>
      <div className="lc-arr">
        {nums.map((v, idx) => {
          const cls = ['lc-cell'];
          if (hi.includes(idx)) cls.push('hi');
          if (range && idx >= range[0] && idx <= range[1]) cls.push('win');
          if (swap && (idx === swap[0] || idx === swap[1])) cls.push('swap');
          if (cmp && (idx === cmp[0] || idx === cmp[1])) cls.push('cmp');
          return (
            <div key={idx} className={cls.join(' ')}>
              {ptrs[idx] && <div className="lc-arr-ptr">{ptrs[idx].join(' / ')}</div>}
              <div className="lc-cell-v">{v}</div>
              <div className="lc-cell-i">下标 {idx}</div>
            </div>
          );
        })}
      </div>
      {swap && <div className="lc-swap-note">⇄ 交换 nums[{swap[0]}]（{nums[swap[0]]}）与 nums[{swap[1]}]（{nums[swap[1]]}）</div>}
      {cmp && <div className="lc-swap-note">⚖ 正在比较 nums[{cmp[0]}]（{nums[cmp[0]]}）与 nums[{cmp[1]}]（{nums[cmp[1]]}）</div>}
      {Object.keys(kv).length > 0 && (
        <div className="lc-kv-panel">
          {Object.entries(kv).map(([k2, v]) => (
            <div key={k2} className="lc-kv"><span className="lc-kv-k">{k2}</span><span className="lc-kv-v">{typeof v === 'object' ? JSON.stringify(v) : String(v)}</span></div>
          ))}
        </div>
      )}
    </div>
  );
}

/* ============ 二叉树 / 决策树可视化（教学级） ============ */
function TreeCanvas({ frame, meta }) {
  const tree = meta.tree || [];
  const n = tree.length;
  if (!n) return null;
  const visit = frame.visit;
  const hi = frame.hi || [];
  const done = frame.done || [];
  const order = frame.order || [];
  const labels = frame.label || {};

  // 建立 children 映射：优先用 meta.children（多叉决策树），否则用队列重建（压缩层序二叉树）
  const children = {};
  if (meta.children) {
    Object.keys(meta.children).forEach(k => { children[Number(k)] = meta.children[k]; });
  } else {
    const queue = [0];
    let i = 1;
    while (queue.length && i < n) {
      const p = queue.shift();
      if (tree[p] === null || tree[p] === undefined) continue;
      const arr = [];
      if (i < n) { if (tree[i] !== null && tree[i] !== undefined) { arr.push(i); queue.push(i); } i++; }
      if (i < n) { if (tree[i] !== null && tree[i] !== undefined) { arr.push(i); queue.push(i); } i++; }
      if (arr.length) children[p] = arr;
    }
  }
  // BFS 求深度
  const depth = new Array(n).fill(0);
  const bfs = [0];
  let maxD = 0;
  while (bfs.length) {
    const x = bfs.shift();
    if (tree[x] === null || tree[x] === undefined) continue;
    if (depth[x] > maxD) maxD = depth[x];
    for (const c of (children[x] || [])) { depth[c] = depth[x] + 1; bfs.push(c); }
  }
  // 布局：叶序分配（叶子占 1 列，内部节点覆盖其子树区间）
  const xLeft = new Array(n).fill(0), w = new Array(n).fill(1);
  function layout(node, lo) {
    const ch = children[node] || [];
    if (!ch.length) { xLeft[node] = lo; w[node] = 1; return lo + 1; }
    let x = lo;
    for (const c of ch) x = layout(c, x);
    const l = xLeft[ch[0]];
    const r = xLeft[ch[ch.length - 1]] + w[ch[ch.length - 1]];
    xLeft[node] = l;
    w[node] = Math.max(r - l, 1);
    return x;
  }
  const total = Math.max(1, layout(0, 0));
  // 按层收集非空节点
  const rowsByDepth = [];
  for (let x = 0; x < n; x++) if (tree[x] !== null && tree[x] !== undefined) {
    (rowsByDepth[depth[x]] = rowsByDepth[depth[x]] || []).push(x);
  }
  const H = rowsByDepth.length;
  if (!H) return null;
  const cellW = 66, nodeH = 74, rowGap = 58;
  const W = total * cellW;
  const centerOf = (node) => (xLeft[node] + w[node] / 2) * cellW;
  const yOf = (d) => d * (nodeH + rowGap);
  const lines = [];
  for (let x = 0; x < n; x++) if (tree[x] !== null && tree[x] !== undefined) {
    const px = centerOf(x), py = yOf(depth[x]) + nodeH;
    for (const c of (children[x] || [])) lines.push({ x1: px, y1: py, x2: centerOf(c), y2: yOf(depth[c]) });
  }
  return (
    <div className="lc-anim-canvas">
      <div className="lc-anim-row-label">{meta.treeLabel || '树结构（节点值 + 层序下标）'}</div>
      <div className="lc-tree-wrap" style={{ width: W }}>
        <svg className="lc-tree-svg" width={W} height={yOf(H - 1) + nodeH + 10} style={{ position: 'absolute', left: 0, top: 0, pointerEvents: 'none' }}>
          {lines.map((ln, idx) => <line key={idx} x1={ln.x1} y1={ln.y1} x2={ln.x2} y2={ln.y2} stroke="#94a3b8" strokeWidth="2" />)}
        </svg>
        {rowsByDepth.map((rowNodes, d) => (
          <div key={d} className="lc-tree-row" style={{ position: 'relative', height: nodeH, marginTop: d === 0 ? 0 : rowGap }}>
            {rowNodes.map((x) => {
              const cls = ['lc-tree-node'];
              if (x === visit) cls.push('visit');
              if (hi.includes(x)) cls.push('hi');
              if (done.includes(x)) cls.push('done');
              const bw = Math.max(cellW, Math.min(w[x] * cellW, W));
              return (
                <div key={x} className={cls.join(' ')}
                  style={{ position: 'absolute', left: centerOf(x) - bw / 2, width: bw, height: nodeH, textAlign: 'center' }}>
                  <div className="lc-tree-v">{tree[x]}</div>
                  <div className="lc-tree-i">{x}</div>
                  {labels[x] && <div className="lc-tree-lb">{labels[x]}</div>}
                </div>
              );
            })}
          </div>
        ))}
      </div>
      {order.length > 0 && <div className="lc-tree-order">📋 已产出顺序：{order.join(' → ')}</div>}
    </div>
  );
}

/* ============ 二维矩阵可视化（教学级） ============ */
function MatrixCanvas({ frame, meta }) {
  const m = meta.m || [];
  const r = frame.r, c = frame.c;
  const hi = frame.hi || [];
  const done = frame.done || [];
  const path = frame.path || [];
  const swapCells = frame.swapCells;
  const dir = frame.dir;
  const kv = Object.fromEntries(Object.entries(frame).filter(([k2]) =>
    !['title', 'state', 'action', 'why', 'tip', 'desc', 'r', 'c', 'hi', 'done', 'path', 'swapCells', 'dir'].includes(k2)
  ));
  return (
    <div className="lc-anim-canvas">
      <div className="lc-anim-row-label">二维矩阵 grid（行/列坐标标注）</div>
      <div className="lc-matrix">
        {m.map((row, ri) => (
          <div key={ri} className="lc-matrix-row">
            {row.map((v, ci) => {
              const cls = ['lc-mcell'];
              if (r === ri && c === ci) cls.push('cur');
              if (hi.some(([hr, hc]) => hr === ri && hc === ci)) cls.push('hi');
              if (path.some(([pr, pc]) => pr === ri && pc === ci)) cls.push('path');
              if (done.some(([dr, dc]) => dr === ri && dc === ci)) cls.push('done');
              if (swapCells && swapCells.some(([sr, sc]) => sr === ri && sc === ci)) cls.push('swap');
              return (
                <div key={ci} className={cls.join(' ')}>
                  <div className="lc-mcell-v">{v}</div>
                  <div className="lc-mcell-rc">{ri},{ci}</div>
                </div>
              );
            })}
          </div>
        ))}
      </div>
      {dir && <div className="lc-matrix-dir">🧭 当前方向：{dir}</div>}
      {Object.keys(kv).length > 0 && (
        <div className="lc-kv-panel">
          {Object.entries(kv).map(([k2, v]) => (
            <div key={k2} className="lc-kv"><span className="lc-kv-k">{k2}</span><span className="lc-kv-v">{typeof v === 'object' ? JSON.stringify(v) : String(v)}</span></div>
          ))}
        </div>
      )}
    </div>
  );
}

/* ============ 堆可视化（教学级） ============ */
function HeapCanvas({ frame, meta }) {
  const heap = meta.heap || [];
  const i = frame.i;
  const swap = frame.swap;
  const op = frame.op;
  const hi = frame.hi || [];
  const opText = {
    push: '⬆ 入堆：新元素加在末尾，然后与父节点比较（上浮）',
    pop: '⬇ 出堆：弹出堆顶（最小值），把末尾元素移到堆顶，再下沉',
    up: '⬆ 上浮：新元素比父节点小，向上交换',
    down: '⬇ 下沉：父节点比某个子节点大，向下交换'
  };
  return (
    <div className="lc-anim-canvas">
      <div className="lc-anim-row-label">堆数组（下标 + 父节点关系标注）</div>
      <div className="lc-arr">
        {heap.map((v, idx) => {
          const cls = ['lc-cell'];
          if (idx === i) cls.push('hi');
          if (swap && (idx === swap[0] || idx === swap[1])) cls.push('swap');
          if (hi.includes(idx)) cls.push('hi');
          const parent = idx > 0 ? Math.floor((idx - 1) / 2) : -1;
          return (
            <div key={idx} className={cls.join(' ')}>
              <div className="lc-cell-v">{v}</div>
              <div className="lc-cell-i">下标 {idx}</div>
              {parent >= 0 && <div className="lc-heap-parent">父↑{heap[parent]}</div>}
            </div>
          );
        })}
      </div>
      {op && <div className="lc-op">{opText[op] || op}</div>}
      {swap && <div className="lc-swap-note">⇄ 交换下标 {swap[0]} 与 {swap[1]}</div>}
    </div>
  );
}

/* ============ DP 表格可视化（教学级） ============ */
function DpTableCanvas({ frame, meta }) {
  const rows = meta.rows || [];
  const cols = meta.cols || [];
  const table = frame.table || meta.table || [];
  const i = frame.i, j = frame.j;
  const hi = frame.hi || [];
  const formula = frame.formula;
  const from = frame.from;
  return (
    <div className="lc-anim-canvas">
      <div className="lc-anim-row-label">DP 表格（行/列 = 递推维度，灰格 = 还没算）</div>
      <div className="lc-dp-table-scroll">
        <table className="lc-dp-table">
          <thead>
            <tr><th></th>{cols.map((cn, ci) => <th key={ci}>{cn}</th>)}</tr>
          </thead>
          <tbody>
            {rows.map((rn, ri) => (
              <tr key={ri}>
                <th>{rn}</th>
                {cols.map((cn, ci) => {
                  const v = table[ri] && table[ri][ci];
                  const cls = ['lc-dpt-cell'];
                  if (i === ri && j === ci) cls.push('cur');
                  if (hi.some(([hr, hc]) => hr === ri && hc === ci)) cls.push('hi');
                  if (v === null || v === undefined) cls.push('empty');
                  return <td key={ci} className={cls.join(' ')}>{v === null || v === undefined ? '·' : v}</td>;
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {formula && <div className="lc-dp-formula">📐 推导公式：<b>{formula}</b></div>}
      {from && <div className="lc-dp-from">💡 {from}</div>}
    </div>
  );
}

/* ============ 讲解区（所有类型共用） ============ */
function ExplainCard({ frame }) {
  const title = frame.title;
  const state = frame.state;
  const action = frame.action;
  const why = frame.why;
  const tip = frame.tip;
  const desc = frame.desc;
  return (
    <div className="lc-anim-explain">
      {title && <div className="lc-ex-title">{title}</div>}
      {state && <div className="lc-ex-state">{state}</div>}
      {action && <div className="lc-ex-row"><span className="lc-ex-label lc-ex-do">① 在做什么</span><span className="lc-ex-text">{action}</span></div>}
      {why && <div className="lc-ex-row"><span className="lc-ex-label lc-ex-why">② 为什么</span><span className="lc-ex-text">{why}</span></div>}
      {tip && <div className="lc-ex-row"><span className="lc-ex-label lc-ex-tip">③ 小白提示</span><span className="lc-ex-text">{tip}</span></div>}
      {desc && !action && <div className="lc-ex-row"><span className="lc-ex-label lc-ex-do">这一步</span><span className="lc-ex-text">{desc}</span></div>}
      {desc && action && <div className="lc-ex-row lc-ex-legacy"><span className="lc-ex-label lc-ex-why">补充</span><span className="lc-ex-text">{desc}</span></div>}
    </div>
  );
}

/* ============ 帧画布路由 ============ */
const TYPE_MAP = {
  '数组': 'array', '链表': 'linked_list', '栈': 'stack', '哈希表': 'hash',
  '滑动窗口': 'sliding_window', 'DP表格': 'dp_table', '堆': 'heap'
};

function FrameCanvas({ type, frame, meta }) {
  const t = TYPE_MAP[type] || type;
  if (t === 'hash') return <><HashCanvas frame={frame} meta={meta} /><ExplainCard frame={frame} /></>;
  if (t === 'sliding_window') return <><SlidingCanvas frame={frame} meta={meta} /><ExplainCard frame={frame} /></>;
  if (t === 'stack') return <><StackCanvas frame={frame} meta={meta} /><ExplainCard frame={frame} /></>;
  if (t === 'linked_list') return <><LinkedListCanvas frame={frame} meta={meta} /><ExplainCard frame={frame} /></>;
  if (t === 'dp') return <><DpCanvas frame={frame} meta={meta} /><ExplainCard frame={frame} /></>;
  if (t === 'array') return <><ArrayCanvas frame={frame} meta={meta} /><ExplainCard frame={frame} /></>;
  if (t === 'tree') return <><TreeCanvas frame={frame} meta={meta} /><ExplainCard frame={frame} /></>;
  if (t === 'matrix') return <><MatrixCanvas frame={frame} meta={meta} /><ExplainCard frame={frame} /></>;
  if (t === 'heap') return <><HeapCanvas frame={frame} meta={meta} /><ExplainCard frame={frame} /></>;
  if (t === 'dp_table') return <><DpTableCanvas frame={frame} meta={meta} /><ExplainCard frame={frame} /></>;
  // 通用兜底
  const rest = Object.fromEntries(Object.entries(frame || {}).filter(([k]) => !['title', 'state', 'action', 'why', 'tip', 'desc'].includes(k)));
  return (
    <div className="lc-anim-canvas">
      <div className="lc-kv-panel">
        {Object.entries(rest).map(([k, v]) => (
          <div key={k} className="lc-kv"><span className="lc-kv-k">{k}</span><span className="lc-kv-v">{typeof v === 'object' ? JSON.stringify(v) : String(v)}</span></div>
        ))}
      </div>
      <ExplainCard frame={frame} />
    </div>
  );
}

export default function LCAnimation({ animation }) {
  const [idx, setIdx] = useState(0);
  const [playing, setPlaying] = useState(false);
  const timer = useRef(null);

  const frames = (animation && animation.frames) || [];
  const type = (animation && animation.type) || '数组';

  useEffect(() => {
    setIdx(0);
    setPlaying(false);
    return () => { if (timer.current) clearInterval(timer.current); };
  }, [animation]);

  function togglePlay() {
    if (playing) { clearInterval(timer.current); setPlaying(false); return; }
    setPlaying(true);
    timer.current = setInterval(() => {
      setIdx(prev => {
        if (prev + 1 >= frames.length) { clearInterval(timer.current); setPlaying(false); return prev; }
        return prev + 1;
      });
    }, 2200);
  }

  if (!frames.length) return null;

  const frame = frames[idx] || {};

  return (
    <div className="lc-anim v2">
      <div className="lc-anim-head">
        <span className="lc-anim-title">动画图解 · {type}</span>
        <span className="lc-anim-count">第 {idx + 1} / {frames.length} 步</span>
      </div>
      <FrameCanvas type={type} frame={frame} meta={animation} />
      <div className="lc-anim-controls">
        <button className="btn btn-mini" onClick={() => { clearInterval(timer.current); setPlaying(false); setIdx(Math.max(0, idx - 1)); }} disabled={idx === 0}>◀ 上一步</button>
        <button className="btn btn-mini" onClick={togglePlay}>{playing ? '⏸ 暂停' : '▶ 自动播放'}</button>
        <button className="btn btn-mini" onClick={() => { clearInterval(timer.current); setPlaying(false); setIdx(Math.min(frames.length - 1, idx + 1)); }} disabled={idx === frames.length - 1}>下一步 ▶</button>
        <div className="lc-anim-bar">
          <div className="lc-anim-bar-fill" style={{ width: ((idx + 1) / frames.length) * 100 + '%' }} />
        </div>
      </div>
    </div>
  );
}
