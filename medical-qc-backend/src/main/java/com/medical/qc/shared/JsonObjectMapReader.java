package com.medical.qc.shared;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * JSON 对象读取工具。
 *
 * <p>统一将 JSON 文本解析为 {@code Map<String, Object>}，
 * 避免各处重复使用原始类型导致编译告警。</p>
 */
public final class JsonObjectMapReader {
    private static final TypeReference<Map<String, Object>> OBJECT_MAP_TYPE = new TypeReference<>() {
    };

    private JsonObjectMapReader() {
    }

    public static Map<String, Object> read(ObjectMapper objectMapper, String rawJson) {
        if (objectMapper == null || !StringUtils.hasText(rawJson)) {
            return Map.of();
        }

        try {
            return objectMapper.readValue(rawJson, OBJECT_MAP_TYPE);
        } catch (Exception exception) {
            return Map.of();
        }
    }
}
