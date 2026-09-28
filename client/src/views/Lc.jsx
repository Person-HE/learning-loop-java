// 力扣 100 题模块 v3：仿力扣页面 —— 左侧题面+内置精讲（思考链/动画/多解法）｜右侧专业代码编辑器（纯手搓代码考察）｜侧边 AI 答疑
// 学习闭环：提交代码 → AI 代码评审评分 → SM-2 遗忘调度 / 缺口入库 / 状态徽章（复习与缺口在 LC 模块内独立闭环，不混入今日队列）
import React, { useEffect, useMemo, useRef, useState } from 'react';
import { api } from '../api.js';
import { useStore } from '../store.jsx';
import LCAnimation from '../components/LCAnimation.jsx';
import { stars, Tag, Spinner } from '../fmt.jsx';
import * as monacoNs from 'monaco-editor';
import Editor, { loader } from '@monaco-editor/react';
loader.config({ monaco: monacoNs });

// 配置 Monaco 编辑器 worker（本地打包，离线可用）
import editorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker';
self.MonacoEnvironment = {
  getWorker() { return new editorWorker(); }
};
const D_META = {
  1: { name: '简单', color: '#16a34a', bg: '#e9f7ef' },
  2: { name: '中等', color: '#d97706', bg: '#fdf3e2' },
  3: { name: '困难', color: '#dc2626', bg: '#fdeaea' }
};
const S_META = {
  new: { name: '未开始', color: '#64748b', bg: '#eef1f5' },
  learning: { name: '学习中', color: '#2563eb', bg: '#e8effc' },
  mastered: { name: '已掌握', color: '#059669', bg: '#e6f6ef' },
  review: { name: '待复习', color: '#b45309', bg: '#fdf0df' }
};
const LC_STYLE = { borderRadius: 6, padding: '0 10px', fontWeight: 600 };

function statusOf(s) {
  if (!s) return 'new';
  if (s === 'mastered') return 'mastered';
  if (s === 'review') return 'review';
  if (s === 'learning' || s === 'relearning') return 'learning';
  return 'new';
}

// ============ 列表页 ============
function LcList({ onOpen }) {
  const [list, setList] = useState(null);
  const [cat, setCat] = useState('');
  const [diff, setDiff] = useState(0);
  const [kw, setKw] = useState('');
  const { showToast } = useStore();

  useEffect(() => { load(); /* eslint-disable-next-line */ }, []);
  async function load() {
    try {
      const r = await api.lcList();
      setList(r.problems);
    } catch (e) { showToast(e.message || '加载力扣题库失败', 'error'); setList([]); }
  }

  const cats = useMemo(() => {
    if (!list) return [];
    const m = {};
    for (const p of list) m[p.category] = (m[p.category] || 0) + 1;
    return Object.entries(m).sort((a, b) => b[1] - a[1]);
  }, [list]);

  if (!list) return <div className="page-loading"><span className="spin" />正在加载力扣题库…</div>;

  const filtered = list.filter(p =>
    (!cat || p.category === cat) &&
    (!diff || p.d === diff) &&
    (!kw || String(p.no).includes(kw) || (p.title || '').toLowerCase().includes(kw.toLowerCase()))
  );
  const stats = {
    total: list.length,
    mastered: list.filter(p => statusOf(p.status) === 'mastered').length,
    learning: list.filter(p => statusOf(p.status) === 'learning').length,
    review: list.filter(p => statusOf(p.status) === 'review').length
  };

  return (
    <div className="page lc-page">
      <div className="page-head">
        <div>
          <h2>力扣 100 题</h2>
          <p className="page-sub">面试高频算法 · 零基础逐步思考链 · 动画图解 · 多解法 · 专业代码编辑器 · 复习缺口闭环</p>
        </div>
      </div>

      <div className="lc-stats">
        <div className="lc-stat"><div className="lc-stat-n">{stats.total}</div><div className="lc-stat-t">总题数</div></div>
        <div className="lc-stat"><div className="lc-stat-n" style={{ color: '#059669' }}>{stats.mastered}</div><div className="lc-stat-t">已掌握</div></div>
        <div className="lc-stat"><div className="lc-stat-n" style={{ color: '#2563eb' }}>{stats.learning}</div><div className="lc-stat-t">学习中</div></div>
        <div className="lc-stat"><div className="lc-stat-n" style={{ color: '#b45309' }}>{stats.review}</div><div className="lc-stat-t">待复习</div></div>
      </div>

      <div className="lc-filters">
        <div className="lc-filter-group">
          <button className={'lc-chip' + (cat === '' ? ' on' : '')} onClick={() => setCat('')}>全部</button>
          {cats.map(([c, n]) => (
            <button key={c} className={'lc-chip' + (cat === c ? ' on' : '')} onClick={() => setCat(c)}>{c} {n}</button>
          ))}
        </div>
        <div className="lc-filter-group">
          <button className={'lc-chip' + (diff === 0 ? ' on' : '')} onClick={() => setDiff(0)}>全部难度</button>
          {[1, 2, 3].map(d => (
            <button key={d} className={'lc-chip' + (diff === d ? ' on' : '')} onClick={() => setDiff(d)}>{D_META[d].name}</button>
          ))}
        </div>
        <input className="lc-search" placeholder="搜题号 / 标题，如 206 或 反转链表" value={kw} onChange={e => setKw(e.target.value)} />
      </div>

      <div className="lc-table">
        <div className="lc-tr lc-tr-head">
          <div className="lc-td lc-td-no">#</div>
          <div className="lc-td lc-td-title">题目</div>
          <div className="lc-td lc-td-cat">分类</div>
          <div className="lc-td lc-td-d">难度</div>
          <div className="lc-td lc-td-s">学习状态</div>
          <div className="lc-td lc-td-x">精讲</div>
        </div>
        {filtered.map(p => {
          const st = statusOf(p.status);
          const sm = S_META[st] || S_META.new;
          const dm = D_META[p.d] || D_META[1];
          return (
            <div key={p.no} className="lc-tr" onClick={() => onOpen(p.no)}>
              <div className="lc-td lc-td-no lc-td-hi">{p.no}</div>
              <div className="lc-td lc-td-title lc-td-main">{p.title}</div>
              <div className="lc-td lc-td-cat">{p.category}</div>
              <div className="lc-td lc-td-d"><span style={{ ...LC_STYLE, color: dm.color, background: dm.bg }}>{dm.name}</span></div>
              <div className="lc-td lc-td-s">
                <span style={{ ...LC_STYLE, color: sm.color, background: sm.bg }}>{sm.name}</span>
                {p.lastScore != null && <span className="lc-last">上次 {p.lastScore} 分</span>}
              </div>
              <div className="lc-td lc-td-x"><span className="lc-has-x">✓ 已内置</span></div>
            </div>
          );
        })}
        {filtered.length === 0 && <div className="lc-empty">没有符合条件的题目</div>}
      </div>
    </div>
  );
}

// ============ 代码块（精讲多解法展示用） ============
function CodeBlock({ code }) {
  const [copied, setCopied] = useState(false);
  return (
    <div className="lc-code">
      <div className="lc-code-head">
        <span>Java</span>
        <button className="btn btn-mini" onClick={() => {
          navigator.clipboard && navigator.clipboard.writeText(code);
          setCopied(true); setTimeout(() => setCopied(false), 1500);
        }}>{copied ? '已复制 ✓' : '复制代码'}</button>
      </div>
      <pre className="lc-code-pre"><code>{code}</code></pre>
    </div>
  );
}

function ThinkingChain({ thinking }) {
  const [open, setOpen] = useState({});
  return (
    <div className="lc-thinking">
      {thinking.map((s, i) => (
        <div key={i} className={'lc-think-step' + (open[i] ? ' open' : '')}>
          <div className="lc-think-head" onClick={() => setOpen(o => ({ ...o, [i]: !o[i] }))}>
            <span className="lc-think-no">{i + 1}</span>
            <span className="lc-think-t">{s.t}</span>
            <span className="lc-think-arrow">{open[i] ? '▾' : '▸'}</span>
          </div>
          {open[i] && <div className="lc-think-w">{s.w}</div>}
        </div>
      ))}
    </div>
  );
}

// ============ 专业代码编辑器（力扣同款：Monaco / VS Code 内核 —— 语法高亮、智能提示、括号匹配、自动缩进、多光标，本地打包离线可用） ============
const JAVA_SNIPPETS = [
  {
    label: 'main 入口',
    insertText: 'public static void main(String[] args) {\n\t${1}\n}',
    detail: 'public static void main'
  },
  {
    label: 'for 循环',
    insertText: 'for (int ${1:i} = 0; ${1:i} < ${2:n}; ${1:i}++) {\n\t${3}\n}',
    detail: 'for 循环'
  },
  {
    label: 'if 判断',
    insertText: 'if (${1:condition}) {\n\t${2}\n} else {\n\t${3}\n}',
    detail: 'if / else'
  },
  {
    label: 'while 循环',
    insertText: 'while (${1:condition}) {\n\t${2}\n}',
    detail: 'while 循环'
  },
  {
    label: '数组遍历',
    insertText: 'for (int ${1:i} = 0; ${1:i} < ${2:arr}.length; ${1:i}++) {\n\t${3}\n}',
    detail: 'for 遍历数组'
  },
  {
    label: '增强 for',
    insertText: 'for (${1:int} ${2:x} : ${3:arr}) {\n\t${4}\n}',
    detail: 'for-each'
  },
  {
    label: 'ArrayList',
    insertText: 'List<${1:Integer}> ${2:list} = new ArrayList<>();',
    detail: 'new ArrayList'
  },
  {
    label: 'HashMap',
    insertText: 'Map<${1:Integer}, ${2:Integer}> ${3:map} = new HashMap<>();',
    detail: 'new HashMap'
  },
  {
    label: 'return',
    insertText: 'return ${1:result};',
    detail: 'return 语句'
  }
];

const DEFAULT_SKELETON = `class Solution {
    // 在这里手写你的代码
}`;

function CodeEditor({ problem, kpId, onSubmitted, onResult, disabled }) {
  const [code, setCode] = useState(DEFAULT_SKELETON);
  const [submitting, setSubmitting] = useState(false);
  const edRef = useRef(null);
  const { showToast } = useStore();

  useEffect(() => {
    if (problem && problem.no) setCode(DEFAULT_SKELETON);
  }, [problem && problem.no]);

  useEffect(() => {
    // 编辑器就绪后自动聚焦
    const ed = edRef.current;
    if (ed) { ed.focus(); ed.setPosition({ lineNumber: 2, column: 5 }); }
  }, [code === DEFAULT_SKELETON]);

  async function submit() {
    const c = (code || '').trim();
    if (!c || c === DEFAULT_SKELETON.trim()) { showToast('请先编写你的代码', 'error'); return; }
    setSubmitting(true);
    try {
      const r = await api.scoreAnswer({
        kpId,
        questionId: 'LC-' + problem.no,
        answerText: '',
        code: c,
        think: ''
      });
      onResult(r);
      if (onSubmitted) onSubmitted(r);
    } catch (e) {
      showToast(e.message || '提交测评失败，请重试', 'error');
    } finally { setSubmitting(false); }
  }

  function handleEditorMount(editor, monaco) {
    edRef.current = editor;
    // 代码片段补全（力扣式智能提示）
    monaco.languages.registerCompletionItemProvider('java', {
      provideCompletionItems(model, position) {
        const word = model.getWordUntilPosition(position);
        const range = new monaco.Range(position.lineNumber, word.startColumn, position.lineNumber, word.endColumn);
        return {
          suggestions: JAVA_SNIPPETS.map(s => ({
            label: s.label,
            kind: monaco.languages.CompletionItemKind.Snippet,
            insertText: s.insertText,
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            detail: s.detail,
            range
          }))
        };
      }
    });
  }

  function handleEditorChange(value) {
    setCode(value || '');
  }

  return (
    <div className="lc-editor">
      <div className="lc-editor-bar">
        <span className="lc-editor-lang">Java</span>
        <span className="lc-editor-tip">智能提示 Ctrl+Space · Tab 缩进 · 多光标 Alt+Click · 直接手搓代码 · 提交后 AI 面试官评审</span>
        <button className="btn btn-mini lc-editor-reset" onClick={() => setCode(DEFAULT_SKELETON)}>重置</button>
      </div>
      <div className="lc-editor-body">
        <Editor
          height="100%"
          defaultLanguage="java"
          value={code}
          theme="vs-dark"
          onChange={handleEditorChange}
          onMount={handleEditorMount}
          options={{
            fontSize: 14,
            fontFamily: '"JetBrains Mono", Consolas, "Courier New", monospace',
            lineNumbers: 'on',
            minimap: { enabled: true, scale: 1 },
            scrollBeyondLastLine: false,
            automaticLayout: true,
            tabSize: 4,
            insertSpaces: true,
            wordWrap: 'off',
            renderLineHighlight: 'all',
            cursorBlinking: 'smooth',
            cursorSmoothCaretAnimation: 'on',
            smoothScrolling: true,
            bracketPairColorization: { enabled: true },
            guides: { bracketPairs: true, indentation: true },
            suggestOnTriggerCharacters: true,
            quickSuggestions: { other: true, comments: false, strings: false },
            folding: true,
            renderWhitespace: 'selection',
            padding: { top: 14, bottom: 14 },
            scrollbar: { verticalScrollbarSize: 12, horizontalScrollbarSize: 12 }
          }}
        />
      </div>
      <div className="lc-editor-foot">
        <button className="btn btn-primary lc-submit-btn" onClick={submit} disabled={submitting}>
          {submitting ? <Spinner /> : null}{submitting ? 'AI 正在评审你的代码…' : '提交测评'}
        </button>
        <span className="lc-editor-foot-hint">评分维度：正确性 / 复杂度 / 边界处理 / 代码规范 · 通过(≥80)才进入遗忘调度</span>
      </div>
    </div>
  );
}
// ============ 提交结果（力扣风格：通过/未通过 + AI 代码评审报告） ============
function LcResult({ result, onReset, onAsk }) {
  const r = result && result.result;
  if (!r) return null;
  const passed = r.verdict === 'pass' || r.total_score >= 80;
  const levelColor = { 优秀: '#059669', 良好: '#16a34a', 及格: '#d97706', 不及格: '#dc2626', 空白: '#64748b' }[r.level] || '#2563eb';
  const kpState = result.kpState;
  const [showOpt, setShowOpt] = useState(false);

  return (
    <div className={'lc-result' + (passed ? ' pass' : ' fail')}>
      <div className="lc-result-top">
        <div className="lc-result-verdict">
          <div className="lc-result-verdict-big">{passed ? '通过' : '未通过'}</div>
          <div className="lc-result-en">{passed ? 'Accepted' : 'Not Accepted'}</div>
        </div>
        <div className="lc-result-score">
          <div className="lc-result-score-n" style={{ color: levelColor }}>{r.total_score}</div>
          <div className="lc-result-score-l">面试官评分 / 100</div>
          <div className="lc-result-level" style={{ color: levelColor }}>{r.level} · {r.verdict === 'pass' ? '这轮代码我给你过' : r.verdict === 'followup' ? '需再追问或重写' : '这轮挂了'}</div>
        </div>
      </div>
      <div className="lc-result-cov">{r.coverage_score != null ? `${r.covered_count}/${r.total_count} 验收点覆盖 = ${r.coverage_score} 分` : ''} {r.score_breakdown ? `｜${r.score_breakdown}` : ''}</div>

      {r.profile && (
        <div className="lc-result-profile">
          <span className="lc-result-profile-tag">画像：{r.profile.tag}</span>
          <span className="lc-result-profile-desc">{r.profile.desc}</span>
        </div>
      )}

      {(r.dimensions || []).length > 0 && (
        <div className="lc-result-dims">
          {r.dimensions.map((d, i) => (
            <div key={i} className="lc-dim-row">
              <div className="lc-dim-name">{d.name}</div>
              <div className="lc-dim-bar"><div className="lc-dim-fill" style={{ width: Math.min(100, (d.score / (d.max || 1)) * 100) + '%' }} /></div>
              <div className="lc-dim-score">{d.score}/{d.max}</div>
            </div>
          ))}
        </div>
      )}

      {(r.standard_points || []).length > 0 && (
        <div className="lc-block">
          <div className="lc-block-title">标准验收点（面试官盯这些）</div>
          {r.standard_points.map(p => (
            <div key={p.id} className="lc-sp">
              <b>{p.id}</b> {p.text}
              <div className="lc-sp-why">为什么看它：{p.why}</div>
            </div>
          ))}
        </div>
      )}

      {(r.point_compare || []).length > 0 && (
        <div className="lc-block">
          <div className="lc-block-title">你的代码 vs 标准（逐点对比）</div>
          {r.point_compare.map(p => (
            <div key={p.id} className={'lc-pc ' + p.status}>
              <div className="lc-pc-head">
                <span className={'lc-pc-st lc-pc-' + p.status}>{p.status === 'covered' ? '✓ 达到' : p.status === 'partial' ? '◐ 沾边' : p.status === 'wrong' ? '✗ 错误' : '○ 缺失'}</span>
                <b>{p.id}</b> {p.text}
              </div>
              {p.mine ? <div className="lc-pc-mine">你的代码：<code>{p.mine}</code></div> : null}
              {p.diff ? <div className="lc-pc-diff">差距：{p.diff}</div> : null}
              {p.fix ? <div className="lc-pc-fix">补法：{p.fix}</div> : null}
            </div>
          ))}
        </div>
      )}

      {(r.feedback || []).length > 0 && (
        <div className="lc-block">
          <div className="lc-block-title">改进指令（按影响排序，下次就这么写）</div>
          {r.feedback.map((f, i) => (
            <div key={i} className="lc-fb">
              <div><b>{i + 1}.</b> 问题：{f.issue}</div>
              <div className="lc-fb-imp">下次这样写：{f.improve}</div>
            </div>
          ))}
        </div>
      )}

      {(r.errors || []).length > 0 && (
        <div className="lc-block">
          <div className="lc-block-title">代码错误</div>
          <ul className="lc-points lc-errs">{r.errors.map((e, i) => <li key={i}>{e}</li>)}</ul>
        </div>
      )}

      {r.optimized_answer && (
        <div className="lc-block">
          <div className="lc-block-title lc-block-title-btn" onClick={() => setShowOpt(s => !s)}>
            最优解法代码（AI 面试官给出） <span className="lc-think-arrow">{showOpt ? '▾' : '▸'}</span>
          </div>
          {showOpt && <CodeBlock code={r.optimized_answer} />}
        </div>
      )}

      {kpState && (
        <div className="lc-result-state">
          学习状态已更新：复习 <b>{kpState.reviews}</b> 次 · 上次 <b>{kpState.lastScore}</b> 分 · 下次到期 <b>{kpState.due || '—'}</b>
          <div className="lc-result-state-hint">{passed ? '已通过，进入遗忘曲线调度（按记忆曲线到期复习）' : '未通过，答题记录已入缺口库与同题复答队列（2 天内回来重写同一题）'}</div>
        </div>
      )}

      <div className="lc-result-actions">
        <button className="btn btn-primary" onClick={onReset}>重新手搓代码</button>
        {onAsk && <button className="btn btn-ghost" onClick={onAsk}>去问 AI 答疑</button>}
      </div>
    </div>
  );
}

// ============ 侧边 AI 答疑面板 ============
function LcAskPanel({ no, problemTitle }) {
  const [msgs, setMsgs] = useState([]);
  const [input, setInput] = useState('');
  const [busy, setBusy] = useState(false);
  const boxRef = useRef(null);
  const { showToast } = useStore();

  useEffect(() => {
    if (boxRef.current) boxRef.current.scrollTop = boxRef.current.scrollHeight;
  }, [msgs]);

  async function send(text) {
    const q = (text || input).trim();
    if (!q || busy) return;
    const next = [...msgs, { role: 'user', content: q }];
    setMsgs(next);
    setInput('');
    setBusy(true);
    try {
      const r = await api.lcAsk(no, next);
      setMsgs(m => [...m, { role: 'assistant', content: r.reply }]);
    } catch (e) {
      showToast(e.message || '答疑失败，请重试', 'error');
      setMsgs(m => [...m, { role: 'assistant', content: '（答疑服务暂时不可用，请稍后重试）' }]);
    } finally { setBusy(false); }
  }

  const quick = ['这题思路怎么想到的？', '我的代码哪里不对？', '有没有更简单的解法？', '复杂度怎么分析？'];

  return (
    <div className="lc-ask">
      <div className="lc-ask-head">
        <span>AI 答疑</span>
        <span className="lc-ask-sub">针对 {problemTitle} 的疑问</span>
      </div>
      <div className="lc-ask-body" ref={boxRef}>
        {msgs.length === 0 && (
          <div className="lc-ask-empty">
            <div className="lc-ask-empty-t">想不通就问，老师一步步带你想到答案</div>
            <div className="lc-ask-quick">
              {quick.map(q => <button key={q} className="lc-chip" onClick={() => send(q)}>{q}</button>)}
            </div>
          </div>
        )}
        {msgs.map((m, i) => (
          <div key={i} className={'lc-msg ' + m.role}>
            <div className="lc-msg-bubble">{m.content}</div>
          </div>
        ))}
        {busy && <div className="lc-msg assistant"><div className="lc-msg-bubble"><Spinner /> 老师正在思考…</div></div>}
      </div>
      <div className="lc-ask-foot">
        <textarea
          className="lc-ask-input"
          placeholder="输入你的疑问，如：为什么这里要用哈希表？"
          value={input}
          onChange={e => setInput(e.target.value)}
          onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send(); } }}
        />
        <button className="btn btn-primary lc-ask-send" onClick={() => send()} disabled={busy}>发送</button>
      </div>
    </div>
  );
}

// ============ 详情页（力扣式三栏） ============
function LcDetail({ no, onBack }) {
  const [data, setData] = useState(null);
  const [result, setResult] = useState(null);
  const [tab, setTab] = useState('code'); // code | ask
  const { data: global, showToast, refresh } = useStore();

  useEffect(() => { load(); /* eslint-disable-next-line */ }, [no]);
  async function load() {
    setData(null);
    setResult(null);
    try {
      const d = await api.lcDetail(no);
      setData(d);
    } catch (e) { showToast(e.message || '加载题目失败', 'error'); }
  }

  if (!data) return <div className="page-loading"><span className="spin" />正在加载题目…</div>;

  const p = data.problem;
  const dm = D_META[p.d] || D_META[1];
  const st = statusOf(data.state && data.state.status);
  const sm = S_META[st] || S_META.new;
  const explain = data.explain;
  const openGap = data.openGap;

  function onResult(r) {
    setResult(r);
    refresh(true);
  }

  return (
    <div className="page lc-page lc-detail-v3">
      <div className="lc-detail-top">
        <button className="btn btn-ghost" onClick={onBack}>← 返回题库</button>
        <div className="lc-detail-head">
          <h2><span className="lc-detail-no">{p.no}.</span> {p.title}</h2>
          <div className="lc-detail-tags">
            <span style={{ ...LC_STYLE, color: dm.color, background: dm.bg }}>{dm.name}</span>
            <Tag text={p.category} color="#7c3aed" bg="#f1eafe" />
            <Tag text={p.algo} color="#0e7490" bg="#e3f4f8" />
            <a className="lc-lc-link" href={p.url} target="_blank" rel="noreferrer">LeetCode 原题 ↗</a>
          </div>
        </div>
        <div className="lc-state-mini">
          <span style={{ ...LC_STYLE, color: sm.color, background: sm.bg }}>{sm.name}</span>
          {data.state && data.state.reviews > 0 && <span className="lc-mini">复习 {data.state.reviews} 次 · 上次 {data.state.lastScore ?? '—'} 分 · 下次 {data.state.due || '—'}</span>}
        </div>
      </div>

      <div className="lc-detail-body">
        {/* 左栏：题面 + 内置精讲 */}
        <div className="lc-left">
          <div className="lc-block">
            <div className="lc-block-title">题目描述</div>
            <div className="lc-q">{p.q}</div>
            {p.ex && p.ex.length > 0 && (
              <div className="lc-ex">
                {p.ex.map((e, i) => (
                  <div key={i} className="lc-ex-row">
                    <div><span className="lc-ex-k">输入：</span>{e.in}</div>
                    <div><span className="lc-ex-k">输出：</span>{e.out}</div>
                    {e.why ? <div className="lc-ex-why">{e.why}</div> : null}
                  </div>
                ))}
              </div>
            )}
            <div className="lc-c"><span className="lc-ex-k">约束：</span>{p.c}</div>
          </div>

          <div className="lc-block">
            <div className="lc-block-title">学习状态</div>
            <div className="lc-state-grid">
              <div className="lc-state-cell"><span className="lc-k">状态</span><b style={{ color: sm.color }}>{sm.name}</b></div>
              <div className="lc-state-cell"><span className="lc-k">复习次数</span><b>{data.state ? data.state.reviews : 0}</b></div>
              <div className="lc-state-cell"><span className="lc-k">上次得分</span><b>{data.state && data.state.lastScore != null ? data.state.lastScore : '—'}</b></div>
              <div className="lc-state-cell"><span className="lc-k">下次到期</span><b>{data.state && data.state.due ? data.state.due : '—'}</b></div>
            </div>
            {openGap && (
              <div className="lc-gap-note">
                存在未解决缺口：<b>{openGap.label}</b> —— {openGap.detail}
                <div className="lc-gap-hint">重写本题代码并通过（≥80 分）即可自动消灭该缺口。</div>
              </div>
            )}
          </div>

          {explain && (
            <div className="lc-block">
              <div className="lc-block-title">精讲 · 核心思想</div>
              <div className="lc-point">{explain.point}</div>
              <div className="lc-sub-row">
                <span className="lc-sub">逐步思考链（怎么想出来的 · 零基础可循）</span>
                <ThinkingChain thinking={explain.thinking} />
              </div>
              {explain.animation && <LCAnimation animation={explain.animation} />}
              <div className="lc-sub">多解法（完整可运行 Java 代码）</div>
              <div className="lc-sols">
                {(explain.solutions || []).map((s, i) => (
                  <div key={i} className="lc-sol">
                    <div className="lc-sol-head">
                      <span className="lc-sol-name">{s.name}</span>
                      <span className="lc-sol-cx">时间 {s.time} · 空间 {s.space}</span>
                    </div>
                    <div className="lc-sol-idea">{s.idea}</div>
                    <CodeBlock code={s.code} />
                    {s.note ? <div className="lc-sol-note">{s.note}</div> : null}
                  </div>
                ))}
              </div>
              {(explain.answerPoints || []).length > 0 && (
                <>
                  <div className="lc-sub">标准答案要点（面试官踩点）</div>
                  <ul className="lc-points">
                    {explain.answerPoints.map((a, i) => <li key={i}>{a}</li>)}
                  </ul>
                </>
              )}
              {(explain.edge || []).length > 0 && (
                <>
                  <div className="lc-sub">边界与坑</div>
                  <ul className="lc-points lc-edge">
                    {explain.edge.map((a, i) => <li key={i}>{a}</li>)}
                  </ul>
                </>
              )}
              {(explain.tips || []).length > 0 && (
                <>
                  <div className="lc-sub">面试答题技巧</div>
                  <ul className="lc-points lc-tips">
                    {explain.tips.map((a, i) => <li key={i}>{a}</li>)}
                  </ul>
                </>
              )}
            </div>
          )}
        </div>

        {/* 右栏：代码编辑器 / AI 答疑（标签切换） */}
        <div className="lc-right">
          <div className="lc-tabs">
            <button className={'lc-tab' + (tab === 'code' ? ' on' : '')} onClick={() => setTab('code')}>代码作答</button>
            <button className={'lc-tab' + (tab === 'ask' ? ' on' : '')} onClick={() => setTab('ask')}>AI 答疑</button>
          </div>
          {tab === 'code' ? (
            result ? (
              <LcResult
                result={result}
                onReset={() => { setResult(null); }}
                onAsk={() => { setTab('ask'); }}
              />
            ) : (
              <CodeEditor
                problem={p}
                kpId={data.kpId}
                onResult={onResult}
              />
            )
          ) : (
            <LcAskPanel no={no} problemTitle={`${p.no}. ${p.title}`} />
          )}
        </div>
      </div>
    </div>
  );
}

export default function Lc({ no }) {
  const [cur, setCur] = useState(no ? String(no) : null);
  useEffect(() => { setCur(no ? String(no) : null); }, [no]);
  return cur ? <LcDetail no={cur} onBack={() => { setCur(null); window.location.hash = '#/lc'; }} /> : <LcList onOpen={n => { setCur(String(n)); window.location.hash = '#/lc/' + n; }} />;
}
