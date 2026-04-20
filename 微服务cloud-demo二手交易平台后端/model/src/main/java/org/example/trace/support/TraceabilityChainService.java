package org.example.trace.support;

import lombok.extern.slf4j.Slf4j;
import org.example.trace.command.TraceRecordCommand;
import org.example.trace.model.TraceAnchorInfo;
import org.example.trace.model.TraceRecordVO;
import org.example.trace.model.TraceabilityVO;
import org.example.trace.util.TraceHashUtil;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
public class TraceabilityChainService {

    private static final String CREATE_TRACE_ANCHOR_SQL = """
            CREATE TABLE IF NOT EXISTS trace_anchor (
                id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                entity_type VARCHAR(32) NOT NULL,
                entity_id INT NOT NULL,
                trace_id VARCHAR(64) NOT NULL,
                latest_version INT NOT NULL DEFAULT 0,
                latest_event_type VARCHAR(64) DEFAULT NULL,
                latest_payload_hash VARCHAR(64) DEFAULT NULL,
                latest_block_hash VARCHAR(64) DEFAULT NULL,
                latest_tx_hash VARCHAR(64) DEFAULT NULL,
                latest_record_time DATETIME DEFAULT NULL,
                created_time DATETIME NOT NULL,
                update_time DATETIME NOT NULL,
                UNIQUE KEY uk_trace_anchor_entity (entity_type, entity_id),
                UNIQUE KEY uk_trace_anchor_trace_id (trace_id)
            )
            """;

    private static final String CREATE_TRACE_RECORD_SQL = """
            CREATE TABLE IF NOT EXISTS trace_record (
                id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                trace_id VARCHAR(64) NOT NULL,
                entity_type VARCHAR(32) NOT NULL,
                entity_id INT NOT NULL,
                trace_version INT NOT NULL,
                business_no VARCHAR(64) DEFAULT NULL,
                event_type VARCHAR(64) NOT NULL,
                payload_hash VARCHAR(64) NOT NULL,
                previous_block_hash VARCHAR(64) DEFAULT NULL,
                block_hash VARCHAR(64) NOT NULL,
                tx_hash VARCHAR(64) NOT NULL,
                operator_id INT DEFAULT NULL,
                source_service VARCHAR(64) DEFAULT NULL,
                summary VARCHAR(255) DEFAULT NULL,
                payload_json LONGTEXT NOT NULL,
                create_time DATETIME NOT NULL,
                KEY idx_trace_record_trace_id (trace_id, trace_version),
                KEY idx_trace_record_entity (entity_type, entity_id),
                KEY idx_trace_record_business_no (business_no)
            )
            """;

    private final JdbcTemplate jdbcTemplate;

    public TraceabilityChainService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void initializeSchema() {
        jdbcTemplate.execute(CREATE_TRACE_ANCHOR_SQL);
        jdbcTemplate.execute(CREATE_TRACE_RECORD_SQL);
        log.info("traceability schema ready");
    }

    public TraceAnchorInfo getAnchorInfo(String entityType, Integer entityId) {
        List<TraceAnchorInfo> anchorInfos = jdbcTemplate.query(
                """
                        SELECT entity_type, entity_id, trace_id, latest_version, latest_event_type,
                               latest_payload_hash, latest_block_hash, latest_tx_hash, latest_record_time
                        FROM trace_anchor
                        WHERE entity_type = ? AND entity_id = ?
                        """,
                traceAnchorMapper(),
                entityType,
                entityId
        );
        return anchorInfos.isEmpty() ? null : anchorInfos.get(0);
    }

    public TraceRecordVO recordEvent(TraceRecordCommand command) {
        if (command == null || command.getEntityId() == null || command.getEntityType() == null || command.getSnapshot() == null) {
            throw new IllegalArgumentException("链上存证参数不完整");
        }
        LocalDateTime eventTime = command.getEventTime() == null ? LocalDateTime.now() : command.getEventTime();
        TraceAnchorInfo anchor = getOrCreateAnchorForUpdate(command.getEntityType(), command.getEntityId(), command.getTraceIdPrefix(), eventTime);
        int nextVersion = anchor.getLatestVersion() == null ? 1 : anchor.getLatestVersion() + 1;
        String payloadJson = TraceHashUtil.toCanonicalJson(command.getSnapshot());
        String payloadHash = TraceHashUtil.sha256(payloadJson);
        String previousBlockHash = anchor.getLatestBlockHash();
        String txHash = TraceHashUtil.sha256(anchor.getTraceId() + "|" + command.getEventType() + "|" + payloadHash + "|" + eventTime + "|" + nextVersion);
        String blockHash = TraceHashUtil.sha256(anchor.getTraceId() + "|" + nextVersion + "|" + command.getEventType()
                + "|" + payloadHash + "|" + nullSafe(previousBlockHash) + "|" + eventTime + "|" + txHash);

        jdbcTemplate.update(
                """
                        INSERT INTO trace_record (
                            trace_id, entity_type, entity_id, trace_version, business_no, event_type,
                            payload_hash, previous_block_hash, block_hash, tx_hash, operator_id,
                            source_service, summary, payload_json, create_time
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                anchor.getTraceId(),
                command.getEntityType(),
                command.getEntityId(),
                nextVersion,
                command.getBusinessNo(),
                command.getEventType(),
                payloadHash,
                previousBlockHash,
                blockHash,
                txHash,
                command.getOperatorId(),
                command.getSourceService(),
                command.getSummary(),
                payloadJson,
                Timestamp.valueOf(eventTime)
        );

        jdbcTemplate.update(
                """
                        UPDATE trace_anchor
                        SET latest_version = ?,
                            latest_event_type = ?,
                            latest_payload_hash = ?,
                            latest_block_hash = ?,
                            latest_tx_hash = ?,
                            latest_record_time = ?,
                            update_time = ?
                        WHERE entity_type = ? AND entity_id = ?
                        """,
                nextVersion,
                command.getEventType(),
                payloadHash,
                blockHash,
                txHash,
                Timestamp.valueOf(eventTime),
                Timestamp.valueOf(eventTime),
                command.getEntityType(),
                command.getEntityId()
        );

        TraceRecordVO recordVO = new TraceRecordVO();
        recordVO.setVersion(nextVersion);
        recordVO.setEventType(command.getEventType());
        recordVO.setBusinessNo(command.getBusinessNo());
        recordVO.setPayloadHash(payloadHash);
        recordVO.setPreviousBlockHash(previousBlockHash);
        recordVO.setBlockHash(blockHash);
        recordVO.setTxHash(txHash);
        recordVO.setOperatorId(command.getOperatorId());
        recordVO.setSourceService(command.getSourceService());
        recordVO.setSummary(command.getSummary());
        recordVO.setPayloadJson(payloadJson);
        recordVO.setCreateTime(eventTime);
        recordVO.setBlockVerified(true);
        recordVO.setPreviousLinkVerified(true);
        return recordVO;
    }

    public TraceabilityVO getTraceability(String entityType, Integer entityId, Map<String, Object> currentSnapshot) {
        TraceabilityVO traceabilityVO = new TraceabilityVO();
        traceabilityVO.setEntityType(entityType);
        traceabilityVO.setEntityId(entityId);

        TraceAnchorInfo anchor = getAnchorInfo(entityType, entityId);
        if (anchor == null) {
            traceabilityVO.setDataVerified(false);
            traceabilityVO.setChainVerified(false);
            traceabilityVO.setVerified(false);
            traceabilityVO.setMessage("当前数据尚未生成链上存证");
            return traceabilityVO;
        }

        traceabilityVO.setTraceId(anchor.getTraceId());
        traceabilityVO.setLatestVersion(anchor.getLatestVersion());
        traceabilityVO.setLatestEventType(anchor.getLatestEventType());
        traceabilityVO.setLatestPayloadHash(anchor.getLatestPayloadHash());
        traceabilityVO.setLatestBlockHash(anchor.getLatestBlockHash());
        traceabilityVO.setLatestTxHash(anchor.getLatestTxHash());

        List<TraceRecordVO> ascendingRecords = jdbcTemplate.query(
                """
                        SELECT trace_version, event_type, business_no, payload_hash, previous_block_hash,
                               block_hash, tx_hash, operator_id, source_service, summary, payload_json, create_time
                        FROM trace_record
                        WHERE entity_type = ? AND entity_id = ?
                        ORDER BY trace_version ASC
                        """,
                traceRecordMapper(),
                entityType,
                entityId
        );

        boolean chainVerified = verifyChain(anchor.getTraceId(), ascendingRecords);
        String currentPayloadHash = currentSnapshot == null ? null : TraceHashUtil.hashSnapshot(currentSnapshot);
        boolean dataVerified = currentPayloadHash != null && currentPayloadHash.equals(anchor.getLatestPayloadHash());

        traceabilityVO.setCurrentPayloadHash(currentPayloadHash);
        traceabilityVO.setChainVerified(chainVerified);
        traceabilityVO.setDataVerified(dataVerified);
        traceabilityVO.setVerified(chainVerified && dataVerified);
        traceabilityVO.setMessage(traceabilityVO.getVerified() ? "链上哈希校验通过，数据未发现篡改"
                : "链上哈希校验失败，当前数据或链路存在异常");

        List<TraceRecordVO> records = new ArrayList<>(ascendingRecords);
        Collections.reverse(records);
        traceabilityVO.setRecords(records);
        return traceabilityVO;
    }

    private TraceAnchorInfo getOrCreateAnchorForUpdate(String entityType, Integer entityId, String traceIdPrefix, LocalDateTime now) {
        List<TraceAnchorInfo> anchorInfos = jdbcTemplate.query(
                """
                        SELECT entity_type, entity_id, trace_id, latest_version, latest_event_type,
                               latest_payload_hash, latest_block_hash, latest_tx_hash, latest_record_time
                        FROM trace_anchor
                        WHERE entity_type = ? AND entity_id = ?
                        FOR UPDATE
                        """,
                traceAnchorMapper(),
                entityType,
                entityId
        );
        if (!anchorInfos.isEmpty()) {
            return anchorInfos.get(0);
        }

        String traceId = TraceHashUtil.generateTraceId(traceIdPrefix);
        try {
            jdbcTemplate.update(
                    """
                            INSERT INTO trace_anchor (
                                entity_type, entity_id, trace_id, latest_version, created_time, update_time
                            ) VALUES (?, ?, ?, 0, ?, ?)
                            """,
                    entityType,
                    entityId,
                    traceId,
                    Timestamp.valueOf(now),
                    Timestamp.valueOf(now)
            );
        } catch (DuplicateKeyException ex) {
            log.warn("trace anchor already exists, entityType={}, entityId={}", entityType, entityId);
        }

        return jdbcTemplate.queryForObject(
                """
                        SELECT entity_type, entity_id, trace_id, latest_version, latest_event_type,
                               latest_payload_hash, latest_block_hash, latest_tx_hash, latest_record_time
                        FROM trace_anchor
                        WHERE entity_type = ? AND entity_id = ?
                        FOR UPDATE
                        """,
                traceAnchorMapper(),
                entityType,
                entityId
        );
    }

    private boolean verifyChain(String traceId, List<TraceRecordVO> ascendingRecords) {
        String previousBlockHash = null;
        boolean verified = true;
        for (TraceRecordVO record : ascendingRecords) {
            String expectedBlockHash = TraceHashUtil.sha256(traceId + "|" + record.getVersion() + "|" + record.getEventType()
                    + "|" + record.getPayloadHash() + "|" + nullSafe(previousBlockHash) + "|" + record.getCreateTime()
                    + "|" + record.getTxHash());
            boolean blockMatched = expectedBlockHash.equals(record.getBlockHash());
            boolean linkMatched = previousBlockHash == null
                    ? record.getPreviousBlockHash() == null
                    : previousBlockHash.equals(record.getPreviousBlockHash());
            record.setBlockVerified(blockMatched);
            record.setPreviousLinkVerified(linkMatched);
            verified = verified && blockMatched && linkMatched;
            previousBlockHash = record.getBlockHash();
        }
        return verified;
    }

    private RowMapper<TraceAnchorInfo> traceAnchorMapper() {
        return (rs, rowNum) -> {
            TraceAnchorInfo anchorInfo = new TraceAnchorInfo();
            anchorInfo.setEntityType(rs.getString("entity_type"));
            anchorInfo.setEntityId(rs.getInt("entity_id"));
            anchorInfo.setTraceId(rs.getString("trace_id"));
            anchorInfo.setLatestVersion(rs.getInt("latest_version"));
            anchorInfo.setLatestEventType(rs.getString("latest_event_type"));
            anchorInfo.setLatestPayloadHash(rs.getString("latest_payload_hash"));
            anchorInfo.setLatestBlockHash(rs.getString("latest_block_hash"));
            anchorInfo.setLatestTxHash(rs.getString("latest_tx_hash"));
            anchorInfo.setLatestRecordTime(toLocalDateTime(rs, "latest_record_time"));
            return anchorInfo;
        };
    }

    private RowMapper<TraceRecordVO> traceRecordMapper() {
        return (rs, rowNum) -> {
            TraceRecordVO record = new TraceRecordVO();
            record.setVersion(rs.getInt("trace_version"));
            record.setEventType(rs.getString("event_type"));
            record.setBusinessNo(rs.getString("business_no"));
            record.setPayloadHash(rs.getString("payload_hash"));
            record.setPreviousBlockHash(rs.getString("previous_block_hash"));
            record.setBlockHash(rs.getString("block_hash"));
            record.setTxHash(rs.getString("tx_hash"));
            record.setOperatorId(rs.getObject("operator_id", Integer.class));
            record.setSourceService(rs.getString("source_service"));
            record.setSummary(rs.getString("summary"));
            record.setPayloadJson(rs.getString("payload_json"));
            record.setCreateTime(toLocalDateTime(rs, "create_time"));
            return record;
        };
    }

    private LocalDateTime toLocalDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
