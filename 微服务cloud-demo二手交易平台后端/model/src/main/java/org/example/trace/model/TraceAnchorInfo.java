package org.example.trace.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TraceAnchorInfo {
    private String entityType;
    private Integer entityId;
    private String traceId;
    private Integer latestVersion;
    private String latestEventType;
    private String latestPayloadHash;
    private String latestBlockHash;
    private String latestTxHash;
    private LocalDateTime latestRecordTime;
}
