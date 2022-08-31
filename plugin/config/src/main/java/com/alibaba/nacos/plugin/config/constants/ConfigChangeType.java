package com.alibaba.nacos.plugin.config.constants;

/**
 * @author liyunfei
 */
public enum ConfigChangeType {
    
    /**
     * config publish
     */
    PUBLISH("publish"),
    /**
     * config update
     */
    UPDATE("update"),
    /**
     * config import
     */
    IMPORT("import"),
    /**
     * config remove
     */
    REMOVE("remove"),
    /**
     * batch remove
     */
    BATCH_REMOVE("batchRemove");
    
    private final String value;
    
    ConfigChangeType(String value) {
        this.value = value;
    }
    
    public String value() {
        return value;
    }
}
