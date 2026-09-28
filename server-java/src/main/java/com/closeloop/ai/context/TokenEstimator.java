package com.closeloop.ai.context;

/**
 * Token 粗估（中文按字符数/1.6，英文/数字按词近似）。刻意不引 tokenizer 重依赖——
 * 用于预算裁剪与审计摘要，不要求与上游计费精确一致。
 */
public final class TokenEstimator {

    private TokenEstimator() {}

    public static int estimate(String text) {
        if (text == null || text.isEmpty()) return 0;
        int cjk = 0;
        int other = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF || c >= 0x3000 && c <= 0x303F || c >= 0xFF00 && c <= 0xFFEF) {
                cjk++;
            } else if (!Character.isWhitespace(c)) {
                other++;
            }
        }
        // CJK ≈ 1 token / 1.6 字符；ASCII ≈ 4 字符 / 1 token
        return (int) Math.ceil(cjk / 1.6) + (int) Math.ceil(other / 4.0);
    }
}
