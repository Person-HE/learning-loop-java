import React, { useEffect, useState } from 'react';
import { useStore } from './store.jsx';
import Today from './views/Today.jsx';
import Map from './views/Map.jsx';
import Curve from './views/Curve.jsx';
import Gaps from './views/Gaps.jsx';
import Records from './views/Records.jsx';
import Spec from './views/Spec.jsx';
import Projects from './views/Projects.jsx';
import Settings from './views/Settings.jsx';
import Lc from './views/Lc.jsx';
import { fmtDate } from './fmt.jsx';

const NAV = [
  { id: 'today', name: '今日闭环', icon: 'M3 12l9-9 9 9M5 10v10h14V10' },
  { id: 'map', name: '知识地图', icon: 'M3 11l19-9-9 19-2-8-8-2z' },
  { id: 'lc', name: '力扣100题', icon: 'M12 2l2.4 4.8 5.4.8-3.9 3.8.9 5.4-4.8-2.5-4.8 2.5.9-5.4L4.2 7.6l5.4-.8z' },
  { id: 'curve', name: '遗忘曲线', icon: 'M3 3v18h18M7 14l4-5 3 3 5-7' },
  { id: 'gaps', name: '缺口库', icon: 'M12 9v4M12 17h.01M10.3 3.9L1.8 18a2 2 0 001.7 3h17a2 2 0 001.7-3L13.7 3.9a2 2 0 00-3.4 0z' },
  { id: 'records', name: '学习记录', icon: 'M4 19.5A2.5 2.5 0 016.5 17H20M4 19.5A2.5 2.5 0 016.5 22H20V2H6.5A2.5 2.5 0 004 4.5v15z' },
  { id: 'projects', name: '项目部分', icon: 'M22 12h-4l-3 9L9 3l-3 9H2' },
  { id: 'spec', name: '机制说明', icon: 'M9 12h6M9 16h6M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2M23 21v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75' },
  { id: 'settings', name: '设置', icon: 'M12 15a3 3 0 100-6 3 3 0 000 6zM19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 11-2.83 2.83l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 11-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 11-2.83-2.83l.06-.06a1.65 1.65 0 00.33-1.82 1.65 1.65 0 00-1.51-1H3a2 2 0 110-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 112.83-2.83l.06.06a1.65 1.65 0 001.82.33H9a1.65 1.65 0 001-1.51V3a2 2 0 114 0v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 112.83 2.83l-.06.06a1.65 1.65 0 00-.33 1.82V9a1.65 1.65 0 001.51 1H21a2 2 0 110 4h-.09a1.65 1.65 0 00-1.51 1z' }
];

function readHash() {
  const h = window.location.hash.replace(/^#\/?/, '');
  if (h === 'lc' || h.startsWith('lc/')) return h;
  return NAV.some(n => n.id === h) ? h : 'today';
}

export default function App() {
  const { ready, toast } = useStore();
  const [view, setView] = useState(readHash());

  useEffect(() => {
    const onHash = () => setView(readHash());
    window.addEventListener('hashchange', onHash);
    return () => window.removeEventListener('hashchange', onHash);
  }, []);

  const today = new Date();
  const titleDate = `${today.getMonth() + 1}月${today.getDate()}日 周${'日一二三四五六'[today.getDay()]}`;

  return (
    <div className="app">
      <aside className="sidebar">
        <div className="logo">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M17 2l4 4-4 4" /><path d="M3 11v-1a4 4 0 0 1 4-4h14" /><path d="M7 22l-4-4 4-4" /><path d="M21 13v1a4 4 0 0 1-4 4H3" />
          </svg>
          <div>
            <div className="logo-t">学习闭环系统</div>
            <div className="logo-s">v2 · React + Node 全栈版</div>
          </div>
        </div>
        <nav className="nav">
          {NAV.map(n => (
            <a
              key={n.id}
              className={'nav-item' + (view === n.id ? ' active' : '')}
              href={'#/' + n.id}
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d={n.icon} /></svg>
              <span>{n.name}</span>
            </a>
          ))}
        </nav>
        <div className="sidebar-foot">
          <div className="sf-date">{titleDate}</div>
          <div className="sf-note">每日先答后学 · 缺口驱动 · 遗忘曲线调度</div>
        </div>
      </aside>

      <main className="main">
        {!ready ? (
          <div className="page-loading"><span className="spin" />正在加载学习数据…</div>
        ) : (
          <>
            {view === 'today' && <Today />}
            {view === 'map' && <Map />}
            {view.startsWith('lc') && <Lc no={view === 'lc' ? null : view.split('/')[1]} />}
            {view === 'curve' && <Curve />}
            {view === 'gaps' && <Gaps />}
            {view === 'records' && <Records />}
            {view === 'projects' && <Projects />}
            {view === 'spec' && <Spec />}
            {view === 'settings' && <Settings />}
          </>
        )}
      </main>

      {toast && (
        <div className={'toast ' + (toast.kind === 'error' ? 't-err' : toast.kind === 'ok' ? 't-ok' : '')}>
          {toast.msg}
        </div>
      )}
    </div>
  );
}
