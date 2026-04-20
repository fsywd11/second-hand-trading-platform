package org.example.trace.constant;

import lombok.Getter;

@Getter
public enum TraceEntityType {
    GOODS("GOODS", "GD"),
    ORDER("ORDER", "OD");

    private final String code;
    private final String prefix;

    TraceEntityType(String code, String prefix) {
        this.code = code;
        this.prefix = prefix;
    }
}
