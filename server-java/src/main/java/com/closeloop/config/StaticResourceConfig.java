package com.closeloop.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 生产模式托管 React 构建产物（client/dist）。
 *  - `/` 与 SPA 路由回退 index.html
 *  - /api/** 与 /kb-assets/** 不参与静态匹配
 *  - Windows 中文路径用 UrlResource 解析，避免 NoResourceFound
 */
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(StaticResourceConfig.class);

    private final AppProperties props;

    public StaticResourceConfig(AppProperties props) {
        this.props = props;
    }

    @PostConstruct
    void logEnv() {
        Path dist = Path.of(props.clientDist());
        log.info("[static] client/dist {}", Files.isDirectory(dist) ? "已挂载：" + dist : "不存在（仅提供 API）");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path dist = Path.of(props.clientDist()).toAbsolutePath().normalize();
        if (!Files.isDirectory(dist)) return;
        String location = dist.toUri().toString();
        if (!location.endsWith("/")) location = location + "/";

        registry.addResourceHandler("/**")
                .addResourceLocations(location)
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        if (resourcePath == null || resourcePath.isBlank()) {
                            return location.createRelative("index.html");
                        }
                        if (resourcePath.startsWith("api/") || resourcePath.startsWith("kb-assets/")
                                || resourcePath.startsWith("actuator/")) {
                            return null;
                        }
                        Resource r = super.getResource(resourcePath, location);
                        if (r != null && r.exists()) return r;
                        // SPA 回退：未知路径返回 index.html（hash 路由下主要兜底直接刷新）
                        if (!resourcePath.contains(".")) {
                            return location.createRelative("index.html");
                        }
                        return null;
                    }
                });
    }
}
