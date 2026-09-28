// 后端 API 封装（真实 AI，无模拟）
async function req(method, url, body) {
  const opts = { method, headers: {} };
  if (body !== undefined) {
    opts.headers['Content-Type'] = 'application/json';
    opts.body = JSON.stringify(body);
  }
  const res = await fetch(url, opts);
  let data = null;
  try { data = await res.json(); } catch { /* 非 JSON */ }
  if (!res.ok) {
    const msg = (data && data.error) || `请求失败（HTTP ${res.status}）`;
    const err = new Error(msg);
    err.code = (data && data.code) || 'HTTP';
    err.status = res.status;
    throw err;
  }
  return data;
}

export const api = {
  getState: () => req('GET', '/api/state'),
  getKb: () => req('GET', '/api/kb'),
  getKbDoc: (kpId) => req('GET', `/api/kb/doc?kpId=${encodeURIComponent(kpId)}`),
  getKpPoints: (kpId) => req('GET', `/api/kp/points?kpId=${encodeURIComponent(kpId)}`),
  restateScore: (payload) => req('POST', '/api/restate/score', payload),
  refreshKb: () => req('POST', '/api/kb/refresh'),
  buildQueue: () => req('POST', '/api/queue/build'),
  todayPlan: () => req('GET', '/api/today/plan'),
  todayConfirm: (picks) => req('POST', '/api/today/confirm', { picks }),
  prepareQueue: () => req('POST', '/api/queue/prepare'),
  genQuestions: (kpId) => req('POST', '/api/questions/gen', { kpId }),
  scoreAnswer: (payload) => req('POST', '/api/answer/score', payload),
  markStall: (recordId, mark) => req('POST', `/api/records/${recordId}/stall`, { mark }),
  resolveGap: (id) => req('POST', `/api/gaps/${id}/resolve`),
  gapTest: (id) => req('POST', `/api/gaps/${id}/test`),
  getRecords: () => req('GET', '/api/records'),
  weekly: () => req('POST', '/api/weekly'),
  getCurve: (kpId) => req('GET', `/api/curve?kpId=${encodeURIComponent(kpId)}`),
  getCurveSummary: () => req('GET', '/api/curve/summary'),
  getProjects: () => req('GET', '/api/projects'),
  projectQuestions: (facetId) => req('POST', '/api/projects/questions', { facetId }),
  projectScore: (payload) => req('POST', '/api/projects/score', payload),
  mockStart: (facetId) => req('POST', '/api/projects/mock/start', { facetId }),
  mockTurn: (answer) => req('POST', '/api/projects/mock/turn', { answer }),
  mockEnd: () => req('POST', '/api/projects/mock/end', {}),
  resumeScore: () => req('POST', '/api/projects/resume-score'),
  saveSettings: (payload) => req('POST', '/api/settings', payload),
  resetAll: () => req('POST', '/api/state/reset'),
  lcList: () => req('GET', '/api/lc'),
  lcDetail: (no) => req('GET', `/api/lc/${encodeURIComponent(no)}`),
  lcExplain: (no) => req('POST', `/api/lc/${encodeURIComponent(no)}/explain`, {}),
  lcPractice: (no) => req('POST', `/api/lc/${encodeURIComponent(no)}/practice`, {}),
  lcAsk: (no, messages) => req('POST', `/api/lc/${encodeURIComponent(no)}/ask`, { messages }),
};
