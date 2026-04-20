package org.example.trace.command;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class TraceRecordCommand {
    private String entityType;
    private Integer entityId;
    private String businessNo;
    private String eventType;
    private Integer operatorId;
    private String sourceService;
    private String summary;
    private LocalDateTime eventTime;
    private String traceIdPrefix;
    private Map<String, Object> snapshot;
}
