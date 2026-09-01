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

/**
 * Temporary compatibility support for migrating Nacos 3.0-3.2 A2A data.
 *
 * <p>TODO(remove in 4.0): remove after the historical A2A migration window closes.</p>
 *
 * @author Nacos
 */
public class A2aMigrationProgress {
    
    public static final int SCHEMA_VERSION = 1;
    
    private static final int MAX_CURSOR_LENGTH = 512;
    
    private static final int MAX_ERROR_LENGTH = 2048;
    
    private int schemaVersion = SCHEMA_VERSION;
    
    private A2aMigrationState state;
    
    private String generation;
    
    private long updatedAt;
    
    private String cursor;
    
    private long scanned;
    
    private long migrated;
    
    private long conflicts;
    
    private long failed;
    
    private String lastError;
    
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
    
    public long getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public String getCursor() {
        return cursor;
    }
    
    public void setCursor(String cursor) {
        this.cursor = truncate(cursor, MAX_CURSOR_LENGTH);
    }
    
    public long getScanned() {
        return scanned;
    }
    
    public void setScanned(long scanned) {
        this.scanned = scanned;
    }
    
    public long getMigrated() {
        return migrated;
    }
    
    public void setMigrated(long migrated) {
        this.migrated = migrated;
    }
    
    public long getConflicts() {
        return conflicts;
    }
    
    public void setConflicts(long conflicts) {
        this.conflicts = conflicts;
    }
    
    public long getFailed() {
        return failed;
    }
    
    public void setFailed(long failed) {
        this.failed = failed;
    }
    
    public String getLastError() {
        return lastError;
    }
    
    public void setLastError(String lastError) {
        this.lastError = truncate(lastError, MAX_ERROR_LENGTH);
    }
    
    private static String truncate(String value, int maximumLength) {
        return value == null || value.length() <= maximumLength ? value
            : value.substring(0, maximumLength);
    }
}
