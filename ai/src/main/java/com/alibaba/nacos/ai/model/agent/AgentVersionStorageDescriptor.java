/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.ai.model.agent;

/**
 * Storage descriptor persisted in the Agent Version row.
 *
 * @author Nacos
 */
public class AgentVersionStorageDescriptor {
    
    public static final String NACOS_CONFIG_KEY_FORMAT = "agent-version-config-v1";
    
    public static final String RAD_AGENT_NAME_CODEC = "rad-ascii-v1";
    
    public static final String MEDIA_TYPE = "application/vnd.nacos.agent-version+json";
    
    public static final int SCHEMA_VERSION = 1;
    
    private String provider;
    
    private String key;
    
    private String keyFormat;
    
    private String agentNameCodec;
    
    private String contentDigest;
    
    private String mediaType;
    
    private Integer schemaVersion;
    
    private Long size;
    
    public String getProvider() {
        return provider;
    }
    
    public void setProvider(String provider) {
        this.provider = provider;
    }
    
    public String getKey() {
        return key;
    }
    
    public void setKey(String key) {
        this.key = key;
    }
    
    public String getKeyFormat() {
        return keyFormat;
    }
    
    public void setKeyFormat(String keyFormat) {
        this.keyFormat = keyFormat;
    }
    
    public String getAgentNameCodec() {
        return agentNameCodec;
    }
    
    public void setAgentNameCodec(String agentNameCodec) {
        this.agentNameCodec = agentNameCodec;
    }
    
    public String getContentDigest() {
        return contentDigest;
    }
    
    public void setContentDigest(String contentDigest) {
        this.contentDigest = contentDigest;
    }
    
    public String getMediaType() {
        return mediaType;
    }
    
    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }
    
    public Integer getSchemaVersion() {
        return schemaVersion;
    }
    
    public void setSchemaVersion(Integer schemaVersion) {
        this.schemaVersion = schemaVersion;
    }
    
    public Long getSize() {
        return size;
    }
    
    public void setSize(Long size) {
        this.size = size;
    }
}
