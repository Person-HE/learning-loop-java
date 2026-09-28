package com.closeloop.common;

import java.util.UUID;

/** 请求追踪 ID：贯穿 HTTP 入口、AI 审计与应用日志 */
public final class TraceIds {

    private TraceIds() {}

    public static String next() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
