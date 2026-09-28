package com.closeloop.ai;

/** 对话消息（OpenAI 兼容 role/content） */
public record AiMessage(String role, String content) {

    public static AiMessage system(String content) { return new AiMessage("system", content); }
    public static AiMessage user(String content) { return new AiMessage("user", content); }
    public static AiMessage assistant(String content) { return new AiMessage("assistant", content == null ? "" : content); }
}
