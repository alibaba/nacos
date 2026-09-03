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
public class A2aMigrationMarker {
    
    public static final int SCHEMA_VERSION = 1;
    
    private int schemaVersion;
    
    private A2aMigrationState state;
    
    private String generation;
    
    private boolean legacyNamingShadow;
    
    private String storageProvider;
    
    private long startedAt;
    
    private long updatedAt;
    
    private Long completedAt;
    
    /**
     * Build the first durable migration plan.
     *
     * @param generation opaque plan generation
     * @param legacyNamingShadow frozen post-cutover shadow policy
     * @param storageProvider frozen Agent Version storage provider
     * @param now current epoch milliseconds
     * @return syncing marker
     */
    public static A2aMigrationMarker syncing(String generation, boolean legacyNamingShadow,
        String storageProvider, long now) {
        A2aMigrationMarker result = new A2aMigrationMarker();
        result.setSchemaVersion(SCHEMA_VERSION);
        result.setState(A2aMigrationState.SYNCING);
        result.setGeneration(generation);
        result.setLegacyNamingShadow(legacyNamingShadow);
        result.setStorageProvider(storageProvider);
        result.setStartedAt(now);
        result.setUpdatedAt(now);
        return result;
    }
    
    /**
     * Copy this marker into another state while preserving the migration plan.
     *
     * @param target target state
     * @param generation target generation
     * @param now current epoch milliseconds
     * @return copied marker
     */
    public A2aMigrationMarker transition(A2aMigrationState target, String generation,
        long now) {
        A2aMigrationMarker result = new A2aMigrationMarker();
        result.setSchemaVersion(schemaVersion);
        result.setState(target);
        result.setGeneration(generation);
        result.setLegacyNamingShadow(legacyNamingShadow);
        result.setStorageProvider(storageProvider);
        result.setStartedAt(startedAt);
        result.setUpdatedAt(now);
        result.setCompletedAt(A2aMigrationState.CANONICAL == target ? now : null);
        return result;
    }
    
    /**
     * Validate the authoritative marker shape.
     *
     * @return {@code true} when all required fields are valid
     */
    public boolean isValid() {
        if (SCHEMA_VERSION != schemaVersion || state == null
            || StringUtils.isBlank(generation) || StringUtils.isBlank(storageProvider)
            || startedAt <= 0 || updatedAt < startedAt) {
            return false;
        }
        return A2aMigrationState.CANONICAL == state ? completedAt != null && completedAt > 0
            : completedAt == null;
    }
    
    public int getSchemaVersion() {
        return schemaVersion;
    }
    
    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }
    
    public A2aMigrationState getState() {
        return state;
    }
    
    public void setState(A2aMigrationState state) {
        this.state = state;
    }
    
    public String getGeneration() {
        return generation;
    }
    
    public void setGeneration(String generation) {
        this.generation = generation;
    }
    
    public boolean isLegacyNamingShadow() {
        return legacyNamingShadow;
    }
    
    public void setLegacyNamingShadow(boolean legacyNamingShadow) {
        this.legacyNamingShadow = legacyNamingShadow;
    }
    
    public String getStorageProvider() {
        return storageProvider;
    }
    
    public void setStorageProvider(String storageProvider) {
        this.storageProvider = storageProvider;
    }
    
    public long getStartedAt() {
        return startedAt;
    }
    
    public void setStartedAt(long startedAt) {
        this.startedAt = startedAt;
    }
    
    public long getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public Long getCompletedAt() {
        return completedAt;
    }
    
    public void setCompletedAt(Long completedAt) {
        this.completedAt = completedAt;
    }
}
