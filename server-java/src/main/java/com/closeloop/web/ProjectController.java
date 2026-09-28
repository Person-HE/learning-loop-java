package com.closeloop.web;

import com.closeloop.service.MockInterviewService;
import com.closeloop.service.ProjectInterviewService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 项目面试路由：总览 / 深挖出题与评分 / 模拟真人面试官 / 简历评分 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectInterviewService project;
    private final MockInterviewService mock;

    public ProjectController(ProjectInterviewService project, MockInterviewService mock) {
        this.project = project;
        this.mock = mock;
    }

    @GetMapping
    public Map<String, Object> overview() {
        return project.overview();
    }

    @PostMapping("/questions")
    public Map<String, Object> questions(@RequestBody(required = false) Map<String, Object> body) {
        return project.generateQuestions(Req.str(body, "facetId", null));
    }

    @PostMapping("/score")
    public Map<String, Object> score(@RequestBody(required = false) Map<String, Object> body) {
        return project.score(Req.str(body, "facetId", null), Req.intOrNull(body, "qIdx"), Req.str(body, "answer", null));
    }

    @PostMapping("/mock/start")
    public Map<String, Object> mockStart(@RequestBody(required = false) Map<String, Object> body) {
        return mock.start(Req.str(body, "facetId", null));
    }

    @PostMapping("/mock/turn")
    public Map<String, Object> mockTurn(@RequestBody(required = false) Map<String, Object> body) {
        return mock.turn(Req.str(body, "answer", null));
    }

    @PostMapping("/mock/end")
    public Map<String, Object> mockEnd() {
        return mock.end();
    }

    @PostMapping("/resume-score")
    public Map<String, Object> resumeScore() {
        return project.resumeScore();
    }
}
