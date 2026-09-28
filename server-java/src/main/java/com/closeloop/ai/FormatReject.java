package com.closeloop.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** AI 输出格式/结构校验失败（触发自动纠错重试，区别于网络类错误） */
public class FormatReject extends RuntimeException {
    public FormatReject(String message) { super(message); }
}
