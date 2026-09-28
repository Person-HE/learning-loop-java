package com.closeloop.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** 访问日志：方法/路径/状态/耗时，异常路径同样留痕 */
@Component
public class AccessLogInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger("access");
    private static final String START = "accessStartNanos";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START, System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        Long start = (Long) request.getAttribute(START);
        long ms = start == null ? -1 : (System.nanoTime() - start) / 1_000_000;
        int status = response.getStatus();
        String line = request.getMethod() + " " + request.getRequestURI()
                + " status=" + status + " ms=" + ms;
        if (ex != null || status >= 500) log.error("[http] {}", line);
        else if (status >= 400) log.warn("[http] {}", line);
        else log.info("[http] {}", line);
    }
}
