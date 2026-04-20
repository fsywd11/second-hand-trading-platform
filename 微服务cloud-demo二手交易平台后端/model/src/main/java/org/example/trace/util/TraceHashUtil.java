package org.example.trace.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class TraceHashUtil {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final DateTimeFormatter TRACE_ID_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private TraceHashUtil() {
    }

    public static String generateTraceId(String prefix) {
        String safePrefix = prefix == null || prefix.isBlank() ? "TR" : prefix;
        return safePrefix + "-" + LocalDateTime.now().format(TRACE_ID_FORMATTER) + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    public static String toCanonicalJson(Map<String, Object> snapshot) {
        Map<String, Object> safeSnapshot = snapshot == null ? Map.of() : new LinkedHashMap<>(snapshot);
        try {
            return OBJECT_MAPPER.writeValueAsString(safeSnapshot);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("生成链上快照失败", ex);
        }
    }

    public static String hashSnapshot(Map<String, Object> snapshot) {
        return sha256(toCanonicalJson(snapshot));
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte current : bytes) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("系统不支持 SHA-256", ex);
        }
    }

    public static void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }
}
