package com.closeloop.state.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 力扣题 AI 精讲（思考链 + 多解法 + 动画帧），缓存于 state.lcExplains */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LcExplain {

    public String point = "";
    /** [{t: 这步在做什么, w: 为什么能想到}] */
    public List<Map<String, Object>> thinking = new ArrayList<>();
    /** [{name,idea,code,time,space,note}] */
    public List<Map<String, Object>> solutions = new ArrayList<>();
    /** {type, frames:[{desc, ...状态字段}]} */
    public Map<String, Object> animation;
    public List<String> answerPoints = new ArrayList<>();
    public List<String> edge = new ArrayList<>();
    public List<String> tips = new ArrayList<>();
}
