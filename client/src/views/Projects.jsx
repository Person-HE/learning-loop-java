// 项目部分：项目面试（AI 深挖 + 面试官评估）/ 简历评分 / 面试官思维 / 简历速览
import React, { useEffect, useState } from 'react';
import { api } from '../api.js';
import { useStore } from '../store.jsx';
import { fmtDate, stars, Tag, SectionTitle, Spinner, LEVEL_META } from '../fmt.jsx';

const PROJECT_TABS = [
  { id: 'interview', name: '模拟面试' },
  { id: 'resume', name: '简历评分' },
  { id: 'mind', name: '面试官思维' },
  { id: 'overview', name: '简历速览' },
  { id: 'resumeview', name: '项目简历' }
];

export default function Projects() {
  const { data, refresh, showToast } = useStore();
  const [tab, setTab] = useState('interview');
  const [proj, setProj] = useState(null);
  const [facetId, setFacetId] = useState('f-all');
  // 对话式模拟面试状态
  const [chat, setChat] = useState([]);          // [{role:'interviewer'|'me', text, kind:'open'|'q'|'answer'|'feedback'}]
  const [input, setInput] = useState('');
  const [busy, setBusy] = useState(false);       // AI 思考中
  const [mockInfo, setMockInfo] = useState(null); // { qCount, maxQuestions }
  const [result, setResult] = useState(null);    // 总结报告
  const [err, setErr] = useState('');
  // 旧列表模式（保留后端兼容，不再作为主入口）
  const [phase, setPhase] = useState('idle');

  const load = async () => {
    try { setProj(await api.getProjects()); } catch (e) { showToast(e.message, 'error'); }
  };
  useEffect(() => { load(); }, []);

  // ===== 对话式模拟面试 =====
  async function startMock() {
    setBusy(true); setErr(''); setResult(null); setChat([]); setMockInfo(null);
    try {
      const r = await api.mockStart('f-all'); // 不选分类：面试官自主从简历选切入点
      setChat([{ role: 'interviewer', text: r.speech, kind: 'q' }]);
      setMockInfo({ qCount: r.qCount, maxQuestions: r.maxQuestions });
    } catch (e) { setErr(e.message); }
    setBusy(false);
  }

  async function sendAnswer() {
    const a = input.trim();
    if (a.length < 4) { showToast('回答太短，至少写一句话', 'error'); return; }
    setChat(c => [...c, { role: 'me', text: a, kind: 'answer' }]);
    setInput(''); setBusy(true); setErr('');
    try {
      const r = await api.mockTurn(a);
      setMockInfo({ qCount: r.qCount, maxQuestions: r.maxQuestions });
      if (r.finished) {
        setChat(c => [...c, { role: 'interviewer', text: r.feedback, kind: 'feedback' }]);
        // 自动收尾：调用总结
        const end = await api.mockEnd();
        setResult(end.result);
        setChat([]); // 对话已结束，展示总结报告
      } else {
        const txt = (r.feedback ? r.feedback + (r.question ? '\n\n' + r.question : '') : r.question);
        setChat(c => [...c, { role: 'interviewer', text: txt, kind: 'feedback' }]);
      }
    } catch (e) { setErr(e.message); }
    setBusy(false);
  }

  async function endMock() {
    setBusy(true); setErr('');
    try {
      const r = await api.mockEnd();
      setResult(r.result);
      setChat([]);
      load();
    } catch (e) { setErr(e.message); }
    setBusy(false);
  }

  async function resumeScore() {
    setPhase('gen');
    try {
      const r = await api.resumeScore();
      setProj(prev => prev ? { ...prev, resumeScore: r.result } : { resumeScore: r.result });
      setPhase('idle');
      showToast('简历评分完成', 'ok');
    } catch (e) {
      setErr(e.message); setPhase('error');
    }
  }

  if (!proj) return <div className="view"><div className="page-loading"><span className="spin" />加载简历与项目数据…</div></div>;

  const resumeScoreRes = proj.resumeScore;
  const facetsStates = proj.states || {};
  const r = proj.resume || {};

  return (
    <div className="view">
      <div className="view-head">
        <div>
          <h1>项目部分</h1>
          <div className="view-sub">基于何宏鑫简历 · 严格参考《面试官Skills.md》 · 全链路 AI 面试官</div>
        </div>
        <div className="cat-tabs">
          {PROJECT_TABS.map(t => (
            <button key={t.id} className={'cat-tab' + (tab === t.id ? ' on' : '')} onClick={() => setTab(t.id)}>
              {t.name}
            </button>
          ))}
        </div>
      </div>

      {tab === 'interview' && (
        <>
          <div className="card">
            <SectionTitle right={'简历：' + (proj.resume?.project?.name || '知缘Flow情绪社区平台')}>
              AI 模拟真人面试官 · 项目拷打（对话式 · 面试官自主出题）
            </SectionTitle>
            <p className="dim">
              无需选择分类——面试官会先扫描你的简历，自主挑出最值得深挖的技术点（缓存 / MQ / LLM / SQL 优化等）直接拷打。
              答完一题，面试官当场给出反馈并继续追问或切换到下一个技术点；面试官判定已覆盖主要爆点后自动收尾出总结。
            </p>
            <div className="wb-actions">
              {!mockInfo && (
                <button className="btn btn-primary" onClick={startMock} disabled={busy}>
                  {busy ? '面试官正在扫简历并准备开场…' : '开始模拟面试（AI 面试官开场）'}
                </button>
              )}
              {mockInfo && chat.length > 0 && (
                <button className="btn btn-ghost" onClick={endMock} disabled={busy}>
                  {busy ? '面试官思考中…' : `结束面试并总结（已问 ${mockInfo.qCount}/${mockInfo.maxQuestions} 个主问题）`}
                </button>
              )}
            </div>
          </div>

          {err && <div className="card wb-error"><p>操作失败：{err}</p>
            <button className="btn btn-primary" onClick={() => { setErr(''); startMock(); }}>重试</button></div>}

          {chat.length > 0 && (
            <div className="card mock-chat">
              <div className="mock-progress dim">
                {mockInfo ? `主问题进度：${mockInfo.qCount} / ${mockInfo.maxQuestions}` : ''}
              </div>
              {chat.map((m, i) => (
                <div key={i} className={'mock-msg ' + m.role}>
                  <div className="mock-role">{m.role === 'interviewer' ? '面试官' : '我'}</div>
                  <div className="mock-bubble">{m.text}</div>
                </div>
              ))}
              {busy && <div className="mock-msg interviewer"><div className="mock-role">面试官</div><div className="mock-bubble dim">正在思考你的回答…</div></div>}
            </div>
          )}

          {mockInfo && chat.length > 0 && !busy && (
            <div className="card">
              <div className="field">
                <label>你的回答（建议先出声讲一遍再写：结论先行 → 机制 → 边界/代价 → 数据）</label>
                <textarea rows={5} value={input} onChange={e => setInput(e.target.value)}
                  placeholder="面试官在等你回答…（答完点发送，面试官会即时反馈并继续追问）" />
              </div>
              <div className="wb-actions">
                <button className="btn btn-primary" onClick={sendAnswer} disabled={busy || input.trim().length < 4}>
                  发送回答
                </button>
              </div>
            </div>
          )}

          {result && (
            <div className="wb-result">
              <div className="wb-card result-main">
                {result.finalWords && (
                  <div className="block final-words">
                    <div className="block-title">面试官最终评语</div>
                    <p className="quote">{result.finalWords}</p>
                  </div>
                )}
                {result.review && result.review.length > 0 && (
                  <div className="block">
                    <div className="block-title">面试官逐题复盘（每轮：问的什么 → 你怎么答的 → 面试官当时怎么评价）</div>
                    <div className="review-list">
                      {result.review.map((r, i) => (
                        <div className="review-item" key={i}>
                          <div className="review-q"><b>Q{i + 1}</b> {r.q}</div>
                          {r.candidate && <div className="review-cand">我的回答：{r.candidate}</div>}
                          <div className="review-judge">{r.judge}</div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
                <div className="result-head">
                  <div className="ring-wrap">
                    <svg viewBox="0 0 110 110" className="ring">
                      <circle cx="55" cy="55" r="44" fill="none" stroke="#eef1f5" strokeWidth="10" />
                      <circle cx="55" cy="55" r="44" fill="none" stroke={(LEVEL_META[result.level] || LEVEL_META['及格']).color} strokeWidth="10" strokeLinecap="round"
                        strokeDasharray={`${2 * Math.PI * 44 * result.total100 / 100} ${2 * Math.PI * 44}`} transform="rotate(-90 55 55)" />
                    </svg>
                    <div className="ring-num">
                      <div className="ring-score" style={{ color: (LEVEL_META[result.level] || {}).color }}>{result.total100}</div>
                      <div className="ring-level" style={{ color: (LEVEL_META[result.level] || {}).color }}>{result.level} · {result.total10}/10</div>
                    </div>
                  </div>
                  <div className="result-side">
                    <div className="rs-dim-head">面试官六维评估（1-10 加权）</div>
                    {result.dimensions.map(d => (
                      <div className="dim-row" key={d.name}>
                        <div className="dim-name">{d.name}<span className="dim-max">·{d.weight}%</span></div>
                        <div className="dim-bar"><div className="dim-fill" style={{ width: d.score * 10 + '%' }} /></div>
                        <div className="dim-score">{d.score}</div>
                        {d.comment ? <div className="dim-comment">{d.comment}</div> : null}
                      </div>
                    ))}
                    <div className="proj-extra">
                      <Tag text={'STAR 运用 ' + result.starScore + '/10'} color={result.starScore >= 7 ? '#059669' : result.starScore >= 4 ? '#d97706' : '#dc2626'} />
                      <Tag text={'大厂通过率预估 ' + result.passRate + '%'} color={result.passRate >= 50 ? '#059669' : result.passRate >= 20 ? '#d97706' : '#dc2626'} />
                    </div>
                  </div>
                </div>

                {(result.strengths && result.strengths.length > 0) && (
                  <div className="block"><div className="block-title ok">面试官眼中的加分点</div>
                    <ul className="dot-list ok">{result.strengths.map((s, i) => <li key={i}>{s}</li>)}</ul></div>
                )}
                {(result.issues && result.issues.length > 0) && (
                  <div className="block"><div className="block-title err">红旗信号 / 扣分点（必须改）</div>
                    <ul className="dot-list err">{result.issues.map((s, i) => <li key={i}>{s}</li>)}</ul></div>
                )}
                {(result.gaps && result.gaps.length > 0) && (
                  <div className="block"><div className="block-title">缺口（已记入复习）</div>
                    <div className="gap-chips">{result.gaps.map((g, i) => <Tag key={i} text={g.label} color="#d97706" />)}</div></div>
                )}
                {result.advice && result.advice.length > 0 && (
                  <div className="block">
                    <div className="block-title">改进建议（按优先级）</div>
                    {result.advice.map((a, i) => (
                      <div className="advice-item" key={i}><b>{i + 1}. {a.title}</b><p>{a.detail}</p></div>
                    ))}
                  </div>
                )}
                <div className="wb-actions">
                  <button className="btn btn-primary" onClick={() => { setResult(null); setChat([]); setMockInfo(null); startMock(); }}>
                    再来一场模拟面试
                  </button>
                </div>
              </div>
            </div>
          )}
        </>
      )}

      {tab === 'resume' && (
        <div className="card">
          <SectionTitle right={resumeScoreRes ? '评分于 ' + fmtDate(resumeScoreRes.at) : ''}>
            简历评分（Skills 模型：匹配25 + 项目25 + 深度20 + STAR15 + 真实10 + 排版5）
          </SectionTitle>
          {resumeScoreRes && proj.resumeVersion && String(resumeScoreRes.at || '').slice(0, 10) < proj.resumeVersion && (
            <div className="resume-stale" style={{ background: '#fdf0df', border: '1px solid #f5c26b', borderRadius: 8, padding: '10px 14px', marginBottom: 12, fontSize: 13 }}>
              <b>简历已更新（{proj.resumeVersion}）→ 知缘Flow情绪社区平台</b>：当前评分为<b>旧版简历</b>结果，不代表新简历水平。
              <button className="btn btn-mini" style={{ marginLeft: 10 }} onClick={resumeScore} disabled={phase === 'gen'}>重新评分新简历</button>
            </div>
          )}
          {!resumeScoreRes ? (
            <div>
              <p className="dim">让 AI 面试官按《面试官Skills》2.1 简历评分模型给这份简历打分，并给出改进建议。</p>
              <button className="btn btn-primary" onClick={resumeScore} disabled={phase === 'gen'}>AI 简历评分</button>
            </div>
          ) : (
            <>
              <div className="resume-total">
                <div className="rt-score" style={{ color: resumeScoreRes.total >= 80 ? '#059669' : resumeScoreRes.total >= 65 ? '#d97706' : '#dc2626' }}>{resumeScoreRes.total}</div>
                <div className="rt-label">总分 / 100</div>
              </div>
              <div className="dims">
                {resumeScoreRes.dimensions.map(d => (
                  <div className="dim-row" key={d.name}>
                    <div className="dim-name">{d.name}<span className="dim-max">/{d.max}</span></div>
                    <div className="dim-bar"><div className="dim-fill" style={{ width: (d.score / d.max) * 100 + '%' }} /></div>
                    <div className="dim-score">{d.score}</div>
                    {d.comment ? <div className="dim-comment">{d.comment}</div> : null}
                  </div>
                ))}
              </div>
              {resumeScoreRes.highlight && resumeScoreRes.highlight.length > 0 && (
                <div className="block"><div className="block-title ok">简历亮点</div>
                  <ul className="dot-list ok">{resumeScoreRes.highlight.map((s, i) => <li key={i}>{s}</li>)}</ul></div>
              )}
              {resumeScoreRes.issues && resumeScoreRes.issues.length > 0 && (
                <div className="block"><div className="block-title err">诊断问题</div>
                  {resumeScoreRes.issues.map((s, i) => (
                    <div className="advice-item" key={i}><b>{s.type}</b><p>{s.detail}</p></div>
                  ))}</div>
              )}
              {resumeScoreRes.suggestions && resumeScoreRes.suggestions.length > 0 && (
                <div className="block"><div className="block-title">优先改进建议</div>
                  {resumeScoreRes.suggestions.map((s, i) => (
                    <div className="advice-item" key={i}><b>{i + 1}. {s.title}</b><p>{s.detail}</p></div>
                  ))}</div>
              )}
            </>
          )}
        </div>
      )}

      {tab === 'mind' && (
        <>
          <div className="card">
            <SectionTitle>面试官提问原则（内心独白）</SectionTitle>
            <div className="quote-list">
              <div className="quote">「我不是在考你『会不会用』，我是在考你『理不理解为什么』。」</div>
              <div className="quote">「你简历上写了就必须能讲清楚原理，否则不如不写。」</div>
              <div className="quote">「项目经验我要听的是『你怎么做的』和『做到了什么程度』，不是『你们团队做了什么』。」</div>
              <div className="quote">「你说 P99 274ms→6ms / 每轮 DB 回源从 6267 降到 3 次，那我要问你这是怎么测出来的、瓶颈在哪。」</div>
            </div>
          </div>
          <div className="card">
            <SectionTitle>追问深度矩阵（Level 1~5）</SectionTitle>
            <div className="grade-table">
              {[['L1', '能详细说说吗？', '基本了解程度'], ['L2', '为什么选这个方案？有没有其他选择？', '技术选型能力'], ['L3', '如果数据量增大 10 倍怎么办？', '扩展性思维'], ['L4', '项目中遇到了什么问题？怎么解决的？', '实战经验'], ['L5', '这个方案的 trade-off 是什么？放弃了什么？', '深度思考']].map(([l, q, p]) => (
                <div className="gt-row" key={l}><Tag text={l} color="#1d4ed8" bg="#e8effc" /><b>{q}</b><span className="dim">{p}</span></div>
              ))}
            </div>
          </div>
          <div className="card">
            <SectionTitle>红旗（一票否决）与绿灯（强烈加分）</SectionTitle>
            <div className="mind-cols">
              <div>
                <b className="err">红旗</b>
                <ul className="dot-list err">
                  <li>简历写了但完全答不上来 → 诚信问题</li>
                  <li>吐槽前公司/前同事 → 态度问题</li>
                  <li>项目经验全是「我们团队」没有「我」 → 能力存疑</li>
                  <li>遇到不会的问题态度消极 → 学习态度问题</li>
                </ul>
              </div>
              <div>
                <b className="ok">绿灯</b>
                <ul className="dot-list ok">
                  <li>某个技术点能讲到面试官都不知道的深度</li>
                  <li>有量化数据 + 技术选型思考 → 工程素养</li>
                  <li>主动承认不足并给出学习计划 → 学习能力</li>
                  <li>对行业趋势有自己的思考 → 技术视野</li>
                </ul>
              </div>
            </div>
          </div>
          <div className="card">
            <SectionTitle>Java 后端必问级考点（出现率 &gt;80%）</SectionTitle>
            <div className="hot-words">
              {['HashMap 底层/扩容/线程安全', 'ConcurrentHashMap CAS', 'JVM 内存模型/GC', 'Spring IoC/AOP', 'MySQL B+树索引', '事务隔离级别', '线程池七大参数', 'synchronized 锁升级', '缓存穿透/击穿/雪崩', 'Redis 持久化/分布式锁', 'TCP 三次握手', 'JWT 认证', 'RAG/Agent/Function Calling'].map((h, i) => (
                <Tag key={i} text={h} color="#b45309" bg="#fdf0df" />
              ))}
            </div>
          </div>
        </>
      )}

      {tab === 'overview' && (
        <div className="card resume-view">
          <div className="rv-head">
            <div>
              <h2>{r.name}</h2>
              <div className="dim">{r.target}</div>
            </div>
            <div className="dim rv-contact">{r.phone} · {r.email}<br />{r.github} · {r.blog}</div>
          </div>
          <div className="rv-sec">
            <b>教育背景</b>
            <p>{r.education.school} · {r.education.major} · {r.education.degree}（{r.education.period}）</p>
          </div>
          <div className="rv-sec">
            <b>技术栈</b>
            <div className="hot-words">{r.skills.flatMap(s => s.tags).map((t, i) => <Tag key={i} text={t} color="#1d4ed8" bg="#e8effc" />)}</div>
          </div>
          <div className="rv-sec">
            <b>自我评价</b>
            <ul className="dot-list">{r.selfEval.map((x, i) => <li key={i}><b>{x.k}</b>：{x.v}</li>)}</ul>
          </div>
          <div className="rv-sec">
            <b>项目：{r.project.name}（{r.project.role} · {r.project.period}）</b>
            <div className="rv-metrics">
              {[['接口/表规模', r.project.metrics.apiScale], ['100RPS P99', r.project.metrics.p99_100], ['200RPS P99', r.project.metrics.p99_200], ['DB回源/轮', r.project.metrics.dbMiss], ['扫描行数', r.project.metrics.scanRows], ['MQ降级', r.project.metrics.mqDegrade], ['LLM熔断', r.project.metrics.llmFallback]].map(([k, v], i) => (
                <div className="rv-metric" key={i}><b>{v}</b><span>{k}</span></div>
              ))}
            </div>
            <p className="dim">{r.project.stack}</p>
            {r.project.modules.map(m => (
              <div className="rv-module" key={m.no}>
                <b>{m.no}. {m.title}</b>
                <p>{m.text}</p>
              </div>
            ))}
          </div>
        </div>
      )}
      {tab === 'resumeview' && (
        <div className="card">
          <SectionTitle right={'版本：' + (proj.resumeVersion || '—')}>
            项目简历（最新版 · 知缘Flow情绪社区平台）
          </SectionTitle>
          <p className="dim">以下为上传的最新版简历原文（resume_upload.html），可滚动查看完整内容。</p>
          {proj.resumeHtml ? (
            <iframe title="项目简历" srcDoc={proj.resumeHtml} style={{ width: '100%', height: '70vh', border: '1px solid #e5e9f0', borderRadius: 8, background: '#fff' }} />
          ) : (
            <div className="wb-error"><p>未找到简历原文文件（server/resume_upload.html 不存在），请在设置中上传。</p></div>
          )}
        </div>
      )}
    </div>
  );
}
