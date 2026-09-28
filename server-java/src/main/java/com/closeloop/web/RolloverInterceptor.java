package com.closeloop.web;

import com.closeloop.state.StateManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** 每个 API 请求入口保证跨天状态先翻转（会话归档 / 防退化模式切换 / 冻结期刷新） */
@Component
public class RolloverInterceptor implements HandlerInterceptor {

    private final StateManager state;

    public RolloverInterceptor(StateManager state) {
        this.state = state;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        state.ensureToday();
        return true;
    }
}
