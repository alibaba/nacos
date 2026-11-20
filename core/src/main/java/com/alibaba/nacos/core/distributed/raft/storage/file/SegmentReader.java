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

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

/**
 * One immutable segment/index pair. Reference state is guarded by its owning cache.
 */
final class SegmentReader implements AutoCloseable {
    
    private final Manifest.SegmentMeta metadata;
    private final Segment segment;
    private final Index index;
    private int references;
    private boolean retired;
    
    SegmentReader(Manifest.SegmentMeta metadata, Segment segment, Index index) {
        this.metadata = metadata;
        this.segment = segment;
        this.index = index;
    }
    
    static SegmentReader open(Path root, UUID storeId, Manifest.SegmentMeta metadata)
        throws IOException {
        Segment segment = Segment.open(root.resolve("segments")
            .resolve(FileLogFormat.segmentName(metadata.getSegmentId())),
            metadata.getPublishedEndOffset());
        try {
            Segment.SegmentHeader header = segment.getHeader();
            if (!storeId.equals(header.getStoreId())
                || header.getSegmentId() != metadata.getSegmentId()) {
                throw new IOException("Segment identity does not match manifest: "
                    + segment.getPath());
            }
            Index index = Index.open(root.resolve("index")
                .resolve(FileLogFormat.indexName(metadata.getSegmentId())),
                metadata.getFirstIndex(), metadata.getLastIndex());
            return new SegmentReader(metadata, segment, index);
        } catch (IOException | RuntimeException failure) {
            try {
                segment.close();
            } catch (IOException closeError) {
                failure.addSuppressed(closeError);
            }
            throw failure;
        }
    }
    
    Manifest.SegmentMeta getMetadata() {
        return metadata;
    }
    
    Segment getSegment() {
        return segment;
    }
    
    Index getIndex() {
        return index;
    }
    
    void retain() {
        if (retired) {
            throw new IllegalStateException("Cannot borrow a retired segment reader");
        }
        references = Math.addExact(references, 1);
    }
    
    boolean releaseReference() {
        if (references == 0) {
            throw new IllegalStateException("Segment reader was not borrowed");
        }
        references--;
        return retired && references == 0;
    }
    
    boolean retire() {
        if (retired) {
            return false;
        }
        retired = true;
        return references == 0;
    }
    
    @Override
    public void close() throws IOException {
        IOException failure = null;
        try {
            index.close();
        } catch (IOException error) {
            failure = error;
        }
        try {
            segment.close();
        } catch (IOException error) {
            if (failure == null) {
                failure = error;
            } else {
                failure.addSuppressed(error);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
