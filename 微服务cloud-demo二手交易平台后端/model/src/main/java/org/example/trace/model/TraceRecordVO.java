package org.example.trace.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TraceRecordVO {
    private Integer version;
    private String eventType;
    private String businessNo;
    private String payloadHash;
    private String previousBlockHash;
    private String blockHash;
    private String txHash;
    private Integer operatorId;
    private String sourceService;
    private String summary;
    private String payloadJson;
    private LocalDateTime createTime;
    private Boolean blockVerified;
    private Boolean previousLinkVerified;
}
