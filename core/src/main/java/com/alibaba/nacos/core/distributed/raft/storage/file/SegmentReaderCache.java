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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bounded reader LRU. File operations never run under the cache monitor.
 * The owning storage holds its read or write lock throughout borrow, read, and release.
 * Lifecycle cleanup holds the storage's write lock and discards the cache after shutdown.
 */
final class SegmentReaderCache implements AutoCloseable {
    
    private final int maxReaders;
    private final Map<Long, SegmentReader> readers = new LinkedHashMap<>(16, 0.75f, true);
    
    SegmentReaderCache(int maxReaders) {
        if (maxReaders <= 0) {
            throw new IllegalArgumentException("Reader cache capacity must be positive");
        }
        this.maxReaders = maxReaders;
    }
    
    SegmentReader borrow(Path root, UUID storeId, Manifest.SegmentMeta metadata)
        throws IOException {
        long segmentId = metadata.getSegmentId();
        SegmentReader stale;
        synchronized (this) {
            SegmentReader existing = readers.get(segmentId);
            if (existing != null && existing.getMetadata().equals(metadata)) {
                existing.retain();
                return existing;
            }
            stale = retire(readers.remove(segmentId));
        }
        if (stale != null) {
            stale.close();
        }
        SegmentReader opened = SegmentReader.open(root, storeId, metadata);
        SegmentReader selected;
        SegmentReader displaced = null;
        try {
            synchronized (this) {
                SegmentReader existing = readers.get(segmentId);
                if (existing != null && existing.getMetadata().equals(metadata)) {
                    selected = existing;
                } else {
                    displaced = retire(readers.remove(segmentId));
                    readers.put(segmentId, opened);
                    selected = opened;
                    if (readers.size() > maxReaders) {
                        Iterator<SegmentReader> oldest = readers.values().iterator();
                        SegmentReader victim = oldest.next();
                        oldest.remove();
                        displaced = retire(victim);
                    }
                }
                selected.retain();
            }
        } catch (RuntimeException failure) {
            try {
                opened.close();
            } catch (IOException closeError) {
                failure.addSuppressed(closeError);
            }
            throw failure;
        }
        try {
            closePair(selected == opened ? null : opened, displaced);
        } catch (IOException failure) {
            try {
                release(selected);
            } catch (IOException closeError) {
                failure.addSuppressed(closeError);
            }
            throw failure;
        }
        return selected;
    }
    
    void release(SegmentReader reader) throws IOException {
        boolean close;
        synchronized (this) {
            close = reader.releaseReference();
        }
        if (close) {
            reader.close();
        }
    }
    
    void discard(SegmentReader reader) throws IOException {
        SegmentReader discarded;
        synchronized (this) {
            readers.remove(reader.getMetadata().getSegmentId(), reader);
            discarded = retire(reader);
        }
        if (discarded != null) {
            discarded.close();
        }
    }
    
    void invalidate(long segmentId) throws IOException {
        SegmentReader invalidated;
        synchronized (this) {
            invalidated = retire(readers.remove(segmentId));
        }
        if (invalidated != null) {
            invalidated.close();
        }
    }
    
    void invalidateAll() throws IOException {
        List<SegmentReader> unused = new ArrayList<>();
        synchronized (this) {
            for (SegmentReader reader : readers.values()) {
                SegmentReader invalidated = retire(reader);
                if (invalidated != null) {
                    unused.add(invalidated);
                }
            }
            readers.clear();
        }
        IOException failure = null;
        for (SegmentReader reader : unused) {
            try {
                reader.close();
            } catch (IOException closeError) {
                if (failure == null) {
                    failure = closeError;
                } else {
                    failure.addSuppressed(closeError);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
    
    @Override
    public void close() throws IOException {
        invalidateAll();
    }
    
    private static SegmentReader retire(SegmentReader reader) {
        return reader != null && reader.retire() ? reader : null;
    }
    
    private static void closePair(SegmentReader first, SegmentReader second) throws IOException {
        IOException failure = null;
        if (first != null) {
            try {
                first.close();
            } catch (IOException closeError) {
                failure = closeError;
            }
        }
        if (second != null) {
            try {
                second.close();
            } catch (IOException closeError) {
                if (failure == null) {
                    failure = closeError;
                } else {
                    failure.addSuppressed(closeError);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
    
}
