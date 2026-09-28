package com.closeloop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/** 应用级路径与容量配置（app.* ） */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String kbRoot,
        String dataDir,
        String resumeHtml,
        String clientDist,
        int kbTextLimit,
        Storage storage,
        AiProperties ai
) {
    public Path kbRootPath() { return Path.of(kbRoot); }
    public Path dataPath() { return Path.of(dataDir); }
    public Path stateFile() { return dataPath().resolve("state.json"); }

    public boolean mysqlMode() {
        return storage != null && "mysql".equalsIgnoreCase(storage.mode());
    }

    public record Storage(String mode) {
        public Storage {
            if (mode == null || mode.isBlank()) mode = "json";
        }
    }
}
