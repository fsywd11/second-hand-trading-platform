package org.example.trace.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TraceabilityVO {
    private String entityType;
    private Integer entityId;
    private String traceId;
    private Integer latestVersion;
    private String latestEventType;
    private String latestPayloadHash;
    private String latestBlockHash;
    private String latestTxHash;
    private String currentPayloadHash;
    private Boolean dataVerified;
    private Boolean chainVerified;
    private Boolean verified;
    private String message;
    private List<TraceRecordVO> records = new ArrayList<>();
}
