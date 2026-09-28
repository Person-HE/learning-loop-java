import React, { useState } from 'react';
import { api } from '../api.js';
import { useStore } from '../store.jsx';
import { SectionTitle, Tag } from '../fmt.jsx';

export default function Settings() {
  const { data, refresh, showToast } = useStore();
  const [form, setForm] = useState({
    baseUrl: data.settings.baseUrl || 'https://api.agnes-ai.cn/v1',
    apiKey: data.settings.apiKey || '',
    model: data.settings.model || 'agnes-2.5-flash',
    java: data.settings.weights.java,
    algo: data.settings.weights.algo,
    ai: data.settings.weights.ai
  });
  const [saving, setSaving] = useState(false);
  const [scanning, setScanning] = useState(false);
  const [confirmReset, setConfirmReset] = useState(false);

  const s = data.settings;

  async function save() {
    setSaving(true);
    try {
      const payload = {
        baseUrl: form.baseUrl,
        apiKey: form.apiKey,
        model: form.model,
        weights: { java: Number(form.java), algo: Number(form.algo), ai: Number(form.ai) }
      };
      const r = await api.saveSettings(payload);
      await refresh(true);
      showToast('设置已保存' + (r.frozen ? '（冻结期内权重不可改，已保留原值）' : ''), 'ok');
    } catch (e) {
      showToast(e.message || '保存失败', 'error');
    } finally {
      setSaving(false);
    }
  }

  async function scan() {
    setScanning(true);
    try {
      const r = await api.refreshKb();
      await refresh(true);
      showToast('知识库扫描完成：新增 ' + r.added + ' 个知识点（共 ' + r.totalDocs + ' 篇）', 'ok');
    } catch (e) {
      showToast(e.message || '扫描失败', 'error');
    } finally {
      setScanning(false);
    }
  }

  async function reset() {
    try {
      await api.resetAll();
      await refresh(true);
      showToast('学习数据已重置', 'ok');
      setConfirmReset(false);
    } catch (e) {
      showToast(e.message || '重置失败', 'error');
    }
  }

  function exportData() {
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = '学习闭环系统-状态备份-' + new Date().toISOString().slice(0, 10) + '.json';
    a.click();
    URL.revokeObjectURL(a.href);
  }

  const sum = (Number(form.java) || 0) + (Number(form.algo) || 0) + (Number(form.ai) || 0);

  return (
    <div className="view">
      <div className="view-head">
        <div>
          <h1>设置</h1>
          <div className="view-sub">AI 配置随时可改 · 学习规则受 14 天冻结期保护</div>
        </div>
      </div>

      <div className="settings-grid">
        <div className="card">
          <SectionTitle right={<Tag text="已固定" color="#059669" />}>AI 接口配置（OpenAI 兼容·锁定）</SectionTitle>
          <div className="field">
            <label>Base URL</label>
            <input value={form.baseUrl} disabled title="AI 配置已固定，不可修改" />
          </div>
          <div className="field">
            <label>API Key{data.settings.apiKey ? '（已配置，显示为 ' + data.settings.apiKey + '）' : ''}</label>
            <input type="password" value={form.apiKey} disabled title="AI 配置已固定，不可修改" placeholder="sk-…" />
          </div>
          <div className="field">
            <label>模型</label>
            <input value={form.model} disabled title="AI 配置已固定，不可修改" />
          </div>
          <div className="dim">AI 配置已固定锁定（Base URL / API Key / 模型均不可修改），所有出题 / 评分 / 标准答案 / 项目面试均为真实 AI 调用，无模拟。</div>
        </div>

        <div className="card">
          <SectionTitle right={data.frozen ? <Tag text="冻结中" color="#b45309" /> : <Tag text="可调整" color="#059669" />}>
            三类权重（每日队列分配）
          </SectionTitle>
          {[
            ['java', 'Java 后端', '#2563eb'],
            ['algo', '数据结构与算法', '#059669'],
            ['ai', 'AI Agents', '#7c3aed']
          ].map(([k, n, c]) => (
            <div className="field" key={k}>
              <label>{n}（{form[k]}%）</label>
              <input type="range" min="0" max="100" step="5" value={form[k]}
                disabled={data.frozen}
                onChange={e => setForm(f => ({ ...f, [k]: e.target.value }))} />
            </div>
          ))}
          <div className="dim" style={{ color: sum === 100 ? '#059669' : '#dc2626' }}>
            当前合计 {sum}%（必须 = 100）· 冻结期内权重修改会被系统拒绝
          </div>
          <button className="btn btn-primary" onClick={save} disabled={saving || sum !== 100}>
            {saving ? '保存中…' : '保存设置'}
          </button>
        </div>

        <div className="card">
          <SectionTitle>防退化状态</SectionTitle>
          <div className="spec-rows">
            <div className="spec-row"><b>当前模式</b><span>{data.mode === 'minimal' ? '最小模式（每日1题）' : '标准模式（每日≤5题）'}</span></div>
            <div className="spec-row"><b>连续漏卡</b><span>{s.missedDays} 天（满 3 天触发最小模式，当前还差 {data.missLeftToMinimal} 天）</span></div>
            <div className="spec-row"><b>冻结期</b><span>{data.frozen ? '第 1~14 天：学习规则锁定' : '冻结期已结束，可调整权重与模式'}</span></div>
            <div className="spec-row"><b>口述目标</b><span>≥80%，当前 {data.kpi.spokenRate}%</span></div>
          </div>
        </div>

        <div className="card">
          <SectionTitle>数据与知识库</SectionTitle>
          <div className="spec-rows">
            <div className="spec-row"><b>知识库根</b><span className="dc-path">{data.kbSummary.root}</span></div>
            <div className="spec-row"><b>已挂载</b><span>{data.kbSummary.domains.length} 个域 / {data.kbSummary.totalDocs} 篇文档 / 知识点 {Object.keys(data.kpsMeta).length} 个</span></div>
            <div className="spec-row"><b>学习记录</b><span>{data.recordsCount} 条作答 / {data.gaps.length} 个缺口 / 会话 {data.sessionToday.count > 0 ? '今日进行中' : '今日未开始'}</span></div>
          </div>
          <div className="btn-row">
            <button className="btn" onClick={scan} disabled={scanning}>{scanning ? '扫描中…' : '重新扫描知识库'}</button>
            <button className="btn" onClick={exportData}>导出数据备份</button>
          </div>
          <div className="btn-row">
            {!confirmReset ? (
              <button className="btn btn-danger" onClick={() => setConfirmReset(true)}>重置全部学习数据</button>
            ) : (
              <div className="confirm-row">
                <span className="err-text">确定清空所有学习记录？（知识库与项目设置保留）</span>
                <button className="btn btn-danger" onClick={reset}>确认重置</button>
                <button className="btn btn-ghost" onClick={() => setConfirmReset(false)}>取消</button>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
