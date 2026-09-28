import React, { useEffect, useState } from 'react';
import { api } from '../api.js';
import { useStore } from '../store.jsx';
import { CATEGORY_META, fmtDate, Tag, SectionTitle } from '../fmt.jsx';

export default function Records() {
  const { data, showToast } = useStore();
  const [rec, setRec] = useState(null);
  const [report, setReport] = useState(null);
  const [genReport, setGenReport] = useState(false);

  const load = async () => {
    try { setRec(await api.getRecords()); } catch { /* ignore */ }
  };
  useEffect(() => { load(); }, []);

  async function weekly() {
    setGenReport(true);
    try {
      const r = await api.weekly();
      setReport(r.report);
      showToast('周报已生成', 'ok');
    } catch (e) {
      showToast(e.message || '周报生成失败', 'error');
    } finally {
      setGenReport(false);
    }
  }

  if (!rec) return <div className="view"><div className="page-loading"><span className="spin" />加载记录…</div></div>;

  const sessions = rec.sessions || [];
  const records = rec.records || [];
  const mastery = rec.mastery || {};
  const kpi = rec.kpi || data.kpi;

  // 近 14 天柱状 + 折线（SVG）
  const days = [];
  for (let i = 13; i >= 0; i--) {
    const d = new Date();
    d.setDate(d.getDate() - i);
    const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    const s = sessions.find(x => x.date === key);
    days.push({ key, count: s ? s.count : 0, avg: s ? s.avg : null, label: `${d.getMonth() + 1}/${d.getDate()}` });
  }
  const maxCount = Math.max(1, ...days.map(d => d.count));
  const W = 800, H = 220, PAD = { l: 34, r: 14, t: 18, b: 26 };
  const iw = W - PAD.l - PAD.r, ih = H - PAD.t - PAD.b;
  const step = iw / 14;
  const bx = i => PAD.l + i * step + step / 2; // 每个格子中心，柱与折线共用
  const barW = Math.min(step * 0.55, 26);
  const yOf = v => PAD.t + ih - (Math.max(0, Math.min(100, v)) / 100) * ih;
  const avgPts = days.map((d, i) => d.avg != null ? [bx(i), yOf(d.avg)] : null).filter(Boolean);
  const avgLine = avgPts.map(p => p[0].toFixed(1) + ',' + p[1].toFixed(1)).join(' ');

  return (
    <div className="view">
      <div className="view-head">
        <div>
          <h1>学习记录</h1>
          <div className="view-sub">会话历史 · 趋势 · 三类掌握度 · AI 教练周报</div>
        </div>
      </div>

      <div className="card">
        <SectionTitle right={`连续 ${kpi.streak} 天 · 已消灭 ${kpi.resolvedGaps} 缺口`}>近 14 天答题量（柱）与平均分（线）</SectionTitle>
        <svg viewBox={`0 0 ${W} ${H}`} className="chart-svg" role="img" aria-label="近14天答题量与平均分">
          <g className="chart-grid">
            {[0, 25, 50, 75, 100].map(v => (
              <g key={v}>
                <line x1={PAD.l} y1={yOf(v)} x2={W - PAD.r} y2={yOf(v)} stroke={v === 0 ? '#dde3ea' : '#eef1f5'} />
                <text x={PAD.l - 6} y={yOf(v) + 4} textAnchor="end" fontSize="11" fill="#94a3b8">{v}</text>
              </g>
            ))}
          </g>
          <g className="chart-bars">
            {days.map((d, i) => {
              const h = d.count ? (d.count / maxCount) * ih : 0;
              return (
                <g key={d.key}>
                  <rect x={bx(i) - barW / 2} y={yOf(0) - h} width={barW} height={Math.max(1, h)} rx="3"
                    fill={d.count > 0 ? '#2563eb' : '#e8edf5'} />
                  {d.count > 0 && <text x={bx(i)} y={yOf(0) - h - 5} textAnchor="middle" fontSize="10" fill="#1d4ed8">{d.count}</text>}
                </g>
              );
            })}
          </g>
          {avgLine && (
            <g className="chart-line">
              <polyline points={avgLine} fill="none" stroke="#059669" strokeWidth="2.5" strokeLinejoin="round" />
              {avgPts.map((p, i) => (
                <circle key={i} cx={p[0]} cy={p[1]} r="3.5" fill="#059669" stroke="#fff" strokeWidth="1.5" />
              ))}
            </g>
          )}
          <g className="chart-x">
            {days.map((d, i) => (
              <text key={d.key} x={bx(i)} y={H - 8} textAnchor="middle" fontSize="9.5" fill={d.count > 0 ? '#475569' : '#b6c2cf'}>{d.label}</text>
            ))}
          </g>
          <text x={PAD.l} y={PAD.t - 4} fontSize="11" fill="#2563eb" fontWeight="600">柱：答题数</text>
          <text x={PAD.l + 78} y={PAD.t - 4} fontSize="11" fill="#059669">— 平均分</text>
        </svg>
      </div>

      <div className="card">
        <SectionTitle>三类掌握度</SectionTitle>
        <div className="mastery-rows">
          {Object.entries(CATEGORY_META).map(([c, v]) => {
            const m = mastery[c] || { total: 0, learned: 0, mastered: 0 };
            return (
              <div className="mastery-row" key={c}>
                <span className="m-name" style={{ color: v.color }}>{v.name}</span>
                <div className="m-bar">
                  <div className="m-fill learned" style={{ width: m.total ? (m.learned / m.total) * 100 + '%' : 0, background: v.color + '33' }} />
                  <div className="m-fill mastered" style={{ width: m.total ? (m.mastered / m.total) * 100 + '%' : 0, background: v.color }} />
                </div>
                <span className="m-num">已学 {m.learned}/{m.total} · 掌握 {m.mastered}</span>
              </div>
            );
          })}
        </div>
      </div>

      <div className="card">
        <SectionTitle right={<button className="btn btn-mini" onClick={weekly} disabled={genReport}>{genReport ? 'AI 生成中…' : 'AI 教练周报'}</button>}>
          周报与里程碑
        </SectionTitle>
        {report ? (
          <div className="report">
            <p>{report.summary}</p>
            <div className="report-cols">
              <div>
                <b className="ok">本周亮点</b>
                <ul className="dot-list ok">{report.strengths.map((s, i) => <li key={i}>{s}</li>)}</ul>
              </div>
              <div>
                <b className="warn">本周薄弱</b>
                <ul className="dot-list warn">{report.weaknesses.map((s, i) => <li key={i}>{s}</li>)}</ul>
              </div>
              <div>
                <b>下周重点</b>
                <ul className="dot-list">{report.focus.map((s, i) => <li key={i}>{s}</li>)}</ul>
              </div>
            </div>
          </div>
        ) : (
          <div className="empty-tip dim">
            里程碑：连续 7/21/66 天 · 消灭 10/50/100 缺口。当前：连续 {kpi.streak} 天 / 消灭 {kpi.resolvedGaps} 缺口。
            {kpi.streak >= 7 && <span className="ms-done"> 已解锁「连续7天」</span>}
            {kpi.streak >= 21 && <span className="ms-done">「连续21天」</span>}
            {kpi.resolvedGaps >= 10 && <span className="ms-done">「消灭10缺口」</span>}
          </div>
        )}
      </div>

      <div className="card">
        <SectionTitle right={sessions.length + ' 天'}>会话历史</SectionTitle>
        {sessions.length === 0 && <div className="empty-tip dim">还没有完成的会话，去「今日闭环」开始第一天吧。</div>}
        <div className="table-wrap">
          <table className="tbl">
            <thead>
              <tr><th>日期</th><th>题数</th><th>平均分</th><th>新缺口</th><th>消灭缺口</th><th>用时</th><th>口述</th><th>模式</th></tr>
            </thead>
            <tbody>
              {sessions.slice().reverse().map((s, i) => (
                <tr key={i}>
                  <td>{s.date}</td>
                  <td>{s.count}</td>
                  <td>{s.avg}</td>
                  <td>{s.newG}</td>
                  <td>{s.resG}</td>
                  <td>{s.min} 分钟</td>
                  <td>{s.spoken ? '是' : '否'}</td>
                  <td>{s.mode === 'minimal' ? '最小' : '标准'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {records.length > 0 && (
        <div className="card">
          <SectionTitle right={records.length + ' 条'}>最近作答记录</SectionTitle>
          <div className="table-wrap">
            <table className="tbl">
              <thead>
                <tr><th>日期</th><th>知识点</th><th>得分</th><th>等级</th><th>卡壳标记</th></tr>
              </thead>
              <tbody>
                {records.slice(0, 30).map((r, i) => (
                  <tr key={i}>
                    <td>{r.date}</td>
                    <td className="tbl-title">{r.question}</td>
                    <td>{r.totalScore}</td>
                    <td>{r.level}</td>
                    <td>{r.stallMark || '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
