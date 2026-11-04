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

package com.alibaba.nacos.core.distributed.raft.storage.file;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

final class Manifest {
    
    private final int formatVersion;
    
    private final UUID storeId;
    
    private final String groupId;
    
    private final long firstLogIndex;
    
    private final long lastLogIndex;
    
    private final List<SegmentMeta> segments;
    
    Manifest(int formatVersion, UUID storeId, String groupId, long firstLogIndex, long lastLogIndex,
        List<SegmentMeta> segments) {
        this.formatVersion = formatVersion;
        this.storeId = Objects.requireNonNull(storeId, "storeId");
        this.groupId = Objects.requireNonNull(groupId, "groupId");
        this.firstLogIndex = firstLogIndex;
        this.lastLogIndex = lastLogIndex;
        this.segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
    }
    
    static Manifest empty(UUID storeId, String groupId, long firstLogIndex) {
        if (firstLogIndex <= 0) {
            throw new IllegalArgumentException("Empty log origin must be positive");
        }
        return new Manifest(FileLogFormat.VERSION, storeId, groupId, firstLogIndex,
            Math.subtractExact(firstLogIndex, 1),
            List.of());
    }
    
    int getFormatVersion() {
        return formatVersion;
    }
    
    UUID getStoreId() {
        return storeId;
    }
    
    String getGroupId() {
        return groupId;
    }
    
    long getFirstLogIndex() {
        return firstLogIndex;
    }
    
    long getLastLogIndex() {
        return lastLogIndex;
    }
    
    List<SegmentMeta> getSegments() {
        return segments;
    }
    
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof Manifest)) {
            return false;
        }
        Manifest that = (Manifest) object;
        return formatVersion == that.formatVersion && firstLogIndex == that.firstLogIndex
            && lastLogIndex == that.lastLogIndex && storeId.equals(that.storeId)
            && groupId.equals(that.groupId) && segments.equals(that.segments);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(formatVersion, storeId, groupId, firstLogIndex, lastLogIndex, segments);
    }
    
    @Override
    public String toString() {
        return "Manifest{" + "formatVersion=" + formatVersion + ", storeId=" + storeId
            + ", groupId='"
            + groupId + '\'' + ", firstLogIndex=" + firstLogIndex + ", lastLogIndex="
            + lastLogIndex + ", segments=" + segments + '}';
    }
    
    static final class SegmentMeta {
        
        private final long segmentId;
        
        private final long firstIndex;
        
        private final long lastIndex;
        
        private final long firstRecordOffset;
        
        private final long publishedEndOffset;
        
        SegmentMeta(long segmentId, long firstIndex, long lastIndex,
            long firstRecordOffset, long publishedEndOffset) {
            this.segmentId = segmentId;
            this.firstIndex = firstIndex;
            this.lastIndex = lastIndex;
            this.firstRecordOffset = firstRecordOffset;
            this.publishedEndOffset = publishedEndOffset;
        }
        
        long getSegmentId() {
            return segmentId;
        }
        
        long getFirstIndex() {
            return firstIndex;
        }
        
        long getLastIndex() {
            return lastIndex;
        }
        
        long getFirstRecordOffset() {
            return firstRecordOffset;
        }
        
        long getPublishedEndOffset() {
            return publishedEndOffset;
        }
        
        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof SegmentMeta)) {
                return false;
            }
            SegmentMeta that = (SegmentMeta) object;
            return segmentId == that.segmentId
                && firstIndex == that.firstIndex && lastIndex == that.lastIndex
                && firstRecordOffset == that.firstRecordOffset
                && publishedEndOffset == that.publishedEndOffset;
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(segmentId, firstIndex, lastIndex, firstRecordOffset,
                publishedEndOffset);
        }
        
        @Override
        public String toString() {
            return "SegmentMeta{" + "segmentId=" + segmentId
                + ", firstIndex=" + firstIndex + ", lastIndex=" + lastIndex
                + ", firstRecordOffset=" + firstRecordOffset + ", publishedEndOffset="
                + publishedEndOffset + '}';
        }
    }
}
