// 全局数据仓库：加载 /api/state，提供刷新、Toast、忙态
import React, { createContext, useContext, useEffect, useState, useCallback, useRef } from 'react';
import { api } from './api.js';

const Ctx = createContext(null);

export function StoreProvider({ children }) {
  const [data, setData] = useState(null);
  const [ready, setReady] = useState(false);
  const [busy, setBusy] = useState(false);
  const [toast, setToast] = useState(null);
  const toastTimer = useRef(null);

  const showToast = useCallback((msg, kind = 'info') => {
    setToast({ msg, kind, id: Date.now() });
    if (toastTimer.current) clearTimeout(toastTimer.current);
    toastTimer.current = setTimeout(() => setToast(null), 4000);
  }, []);

  const refresh = useCallback(async (silent = false) => {
    try {
      const d = await api.getState();
      setData(d);
      setReady(true);
      return d;
    } catch (e) {
      if (!silent) showToast(e.message || '加载失败', 'error');
      throw e;
    }
  }, [showToast]);

  useEffect(() => {
    refresh(true).catch(() => setReady(true));
  }, [refresh]);

  const value = {
    data, setData, ready, busy, setBusy, toast, showToast, refresh
  };
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useStore() {
  return useContext(Ctx);
}
