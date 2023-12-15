package com.alibaba.nacos.config.server.service.dump;

public class DumpRequest {
    
    String dataId;
    
    String group;
    
    String tenant;
    
    private boolean isBeta;
    
    private boolean isBatch;
    
    private String tag;
    
    private long lastModifiedTs;
    
    private String sourceIp;
    
    public String getDataId() {
        return dataId;
    }
    
    public void setDataId(String dataId) {
        this.dataId = dataId;
    }
    
    public String getGroup() {
        return group;
    }
    
    public void setGroup(String group) {
        this.group = group;
    }
    
    public String getTenant() {
        return tenant;
    }
    
    public void setTenant(String tenant) {
        this.tenant = tenant;
    }
    
    public boolean isBeta() {
        return isBeta;
    }
    
    public void setBeta(boolean beta) {
        isBeta = beta;
    }
    
    public boolean isBatch() {
        return isBatch;
    }
    
    public void setBatch(boolean batch) {
        isBatch = batch;
    }
    
    public String getTag() {
        return tag;
    }
    
    public void setTag(String tag) {
        this.tag = tag;
    }
    
    public long getLastModifiedTs() {
        return lastModifiedTs;
    }
    
    public void setLastModifiedTs(long lastModifiedTs) {
        this.lastModifiedTs = lastModifiedTs;
    }
    
    public String getSourceIp() {
        return sourceIp;
    }
    
    public void setSourceIp(String sourceIp) {
        this.sourceIp = sourceIp;
    }
    
    public static DumpRequest create(String dataId, String group, String tenant, long lastModifiedTs, String sourceIp) {
        DumpRequest dumpRequest = new DumpRequest();
        dumpRequest.dataId = dataId;
        dumpRequest.group = group;
        dumpRequest.tenant = tenant;
        dumpRequest.lastModifiedTs = lastModifiedTs;
        dumpRequest.sourceIp = sourceIp;
        return dumpRequest;
    }
}
