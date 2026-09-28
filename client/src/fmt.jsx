// 通用格式化与小型 UI 组件

export const CATEGORY_META = {
  java: { name: 'Java后端', color: '#2563eb' },
  algo: { name: '数据结构与算法', color: '#059669' },
  ai: { name: 'AI Agents', color: '#7c3aed' }
};

export const LEVEL_META = {
  优秀: { color: '#059669', bg: '#e6f6ef' },
  良好: { color: '#0e9f9b', bg: '#e2f5f4' },
  及格: { color: '#b45309', bg: '#fdf0df' },
  不及格: { color: '#d97706', bg: '#fdeedd' },
  空白: { color: '#dc2626', bg: '#fde8e8' }
};

export const KIND_META = {
  review: { name: '复习', color: '#1d4ed8', bg: '#e8effc' },
  new: { name: '新知识点', color: '#059669', bg: '#e6f6ef' },
  weak: { name: '薄弱补强', color: '#c2410c', bg: '#fdeee2' }
};

export const TYPE_META = {
  T1: { name: 'T1 八股直问', color: '#1d4ed8' },
  T2: { name: 'T2 原理深挖', color: '#7c3aed' },
  T3: { name: 'T3 对比辨析', color: '#b45309' },
  T4: { name: 'T4 场景实战', color: '#059669' },
  T5: { name: 'T5 连环追问', color: '#be185d' }
};

export const GAP_COLORS = {
  概念混淆: '#dc2626',
  因果链断裂: '#d97706',
  边界缺失: '#7c3aed',
  术语不准: '#2563eb',
  表达卡顿: '#0e9f9b',
  空白: '#b91c1c'
};

export function todayStr() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

export function fmtDate(s) {
  if (!s) return '';
  const p = String(s).slice(0, 10).split('-');
  return `${Number(p[1])}月${Number(p[2])}日`;
}

export function stars(n) {
  const x = Math.max(0, Math.min(5, Number(n) || 0));
  return '★'.repeat(x) + '☆'.repeat(5 - x);
}

export function esc(s) {
  return String(s == null ? '' : s)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

export function classNames(...xs) {
  return xs.filter(Boolean).join(' ');
}

// 通用小组件
export function Tag({ text, color = '#1d4ed8', bg, style }) {
  return (
    <span className="tag" style={{ color, background: bg || color + '14', borderColor: color + '40', ...style }}>{text}</span>
  );
}

export function Stat({ label, value, sub, accent }) {
  return (
    <div className="stat-card">
      <div className="stat-value" style={accent ? { color: accent } : undefined}>{value}</div>
      <div className="stat-label">{label}</div>
      {sub ? <div className="stat-sub">{sub}</div> : null}
    </div>
  );
}

export function SectionTitle({ children, right }) {
  return (
    <div className="section-title">
      <span className="bar" />
      <span className="st-text">{children}</span>
      {right ? <span className="st-right">{right}</span> : null}
    </div>
  );
}

export function Spinner({ text = 'AI 生成中…' }) {
  return (
    <div className="spinner-box">
      <span className="spin" />
      <span>{text}</span>
    </div>
  );
}
