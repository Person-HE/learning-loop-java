package com.closeloop.common;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** 日期工具：todayStr / daysBetween，语义与 Node 版 store.js 一致 */
public final class Dates {

    private Dates() {}

    public static String todayStr() { return todayStr(0); }

    public static String todayStr(int offsetDays) {
        return LocalDate.now().plusDays(offsetDays).toString(); // ISO yyyy-MM-dd
    }

    /** b - a 的整天数（四舍五入语义：ISO 日历日直接差值） */
    public static int daysBetween(String a, String b) {
        if (a == null || a.isEmpty() || b == null || b.isEmpty()) return 0;
        return (int) ChronoUnit.DAYS.between(LocalDate.parse(a), LocalDate.parse(b));
    }

    /** 字符串日期比较（ISO 格式字典序即时间序），null 视为最大，避免旧数据缺字段时 NPE */
    public static int cmpDate(String a, String b) {
        if (a == null) return b == null ? 0 : 1;
        if (b == null) return -1;
        return a.compareTo(b);
    }
}
