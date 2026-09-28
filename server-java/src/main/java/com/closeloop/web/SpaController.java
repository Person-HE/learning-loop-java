package com.closeloop.web;

import com.closeloop.config.AppProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.nio.file.Files;
import java.nio.file.Path;

/** SPA 入口：`/` 与 `/index.html` 直接回 React 壳（hash 路由在前端内切换） */
@Controller
public class SpaController {

    private final AppProperties props;

    public SpaController(AppProperties props) {
        this.props = props;
    }

    @GetMapping(value = {"/", "/index.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> index() {
        Path index = Path.of(props.clientDist()).resolve("index.html");
        if (!Files.isRegularFile(index)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(new FileSystemResource(index));
    }
}
