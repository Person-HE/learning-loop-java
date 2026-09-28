package com.closeloop.web;

import com.closeloop.common.ApiException;
import com.closeloop.service.StateViewService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** 知识库动画图解静态服务（独立于 /api，供前端 img/iframe 直接引用，与 Node index.js /kb-assets/* 对齐） */
@RestController
public class KbAssetController {

    private final StateViewService view;

    public KbAssetController(StateViewService view) {
        this.view = view;
    }

    @GetMapping("/kb-assets/**")
    public ResponseEntity<byte[]> asset(jakarta.servlet.http.HttpServletRequest req) throws IOException {
        String uri = req.getRequestURI();
        String rel = uri.substring("/kb-assets/".length());
        rel = java.net.URLDecoder.decode(rel, java.nio.charset.StandardCharsets.UTF_8);
        Path file = view.resolveKbAsset(rel);
        String name = file.getFileName().toString().toLowerCase();
        MediaType type = MediaType.APPLICATION_OCTET_STREAM;
        if (name.endsWith(".svg")) type = MediaType.parseMediaType("image/svg+xml");
        else if (name.endsWith(".png")) type = MediaType.IMAGE_PNG;
        else if (name.endsWith(".jpg") || name.endsWith(".jpeg")) type = MediaType.IMAGE_JPEG;
        else if (name.endsWith(".gif")) type = MediaType.IMAGE_GIF;
        else if (name.endsWith(".webp")) type = MediaType.parseMediaType("image/webp");
        else if (name.endsWith(".html")) type = MediaType.TEXT_HTML;
        if (!Files.isRegularFile(file)) throw ApiException.notFound("资源不存在");
        return ResponseEntity.ok().contentType(type).body(Files.readAllBytes(file));
    }
}
