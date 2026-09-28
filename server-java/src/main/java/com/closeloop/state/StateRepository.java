package com.closeloop.state;

import com.closeloop.config.AppProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * state.json 读写（迁移源 / 导出备份）。
 * mysql 模式下 save() 不再做全量写——热路径走 MySqlStateStore.saveAfterAnswer。
 */
@Component
public class StateRepository {

    private static final Logger log = LoggerFactory.getLogger(StateRepository.class);

    private final AppProperties props;
    private final ObjectMapper mapper;

    public StateRepository(AppProperties props) {
        this.props = props;
        this.mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public ObjectMapper mapper() { return mapper; }

    public com.closeloop.state.model.AppState load() {
        Path file = props.stateFile();
        if (!Files.exists(file)) return null;
        try {
            com.closeloop.state.model.AppState st = mapper.readValue(file.toFile(), com.closeloop.state.model.AppState.class);
            return st != null && st.version == 2 ? st : null;
        } catch (IOException e) {
            log.error("[state] state.json 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /** mysql 模式：不写全量 JSON（避免 70ms 写放大）；json 模式：原样落盘 */
    public void save(com.closeloop.state.model.AppState st) {
        if (props.mysqlMode()) return;
        exportJson(st);
    }

    public void exportJson(com.closeloop.state.model.AppState st) {
        try {
            Files.createDirectories(props.dataPath());
            Path file = props.stateFile();
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), st);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("状态保存失败：" + e.getMessage(), e);
        }
    }
}
