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

package com.alibaba.nacos.ai.service.a2a.migration;

import com.alibaba.nacos.common.utils.StringUtils;

/**
 * Temporary compatibility support for migrating Nacos 3.0-3.2 A2A data.
 *
 * <p>TODO(remove in 4.0): remove after the historical A2A migration window closes.</p>
 *
 * @author Nacos
 */
public class A2aMigrationLeaseRecord {
    
    public static final int SCHEMA_VERSION = 1;
    
    private int schemaVersion;
    
    private String owner;
    
    private long expiresAt;
    
    /**
     * Create a lease value.
     *
     * @param owner lease owner
     * @param expiresAt expiry epoch milliseconds
     * @return lease record
     */
    public static A2aMigrationLeaseRecord of(String owner, long expiresAt) {
        A2aMigrationLeaseRecord result = new A2aMigrationLeaseRecord();
        result.setSchemaVersion(SCHEMA_VERSION);
        result.setOwner(owner);
        result.setExpiresAt(expiresAt);
        return result;
    }
    
    /**
     * Validate the persisted lease.
     *
     * @return {@code true} when required fields are present
     */
    public boolean isValid() {
        return SCHEMA_VERSION == schemaVersion && StringUtils.isNotBlank(owner)
            && expiresAt >= 0;
    }
    
    public int getSchemaVersion() {
        return schemaVersion;
    }
    
    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }
    
    public String getOwner() {
        return owner;
    }
    
    public void setOwner(String owner) {
        this.owner = owner;
    }
    
    public long getExpiresAt() {
        return expiresAt;
    }
    
    public void setExpiresAt(long expiresAt) {
        this.expiresAt = expiresAt;
    }
}
