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

import com.alibaba.nacos.core.utils.Loggers;
import com.alipay.sofa.jraft.conf.Configuration;
import com.alipay.sofa.jraft.conf.ConfigurationEntry;
import com.alipay.sofa.jraft.conf.ConfigurationManager;
import com.alipay.sofa.jraft.entity.EnumOutter.EntryType;
import com.alipay.sofa.jraft.entity.LogEntry;
import com.alipay.sofa.jraft.option.LogStorageOptions;
import com.alipay.sofa.jraft.option.RaftOptions;
import com.alipay.sofa.jraft.storage.LogStorage;
import com.alipay.sofa.jraft.util.Platform;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Synchronous, Manifest-published file log storage for one Raft group.
 */
public final class FileLogStorage implements LogStorage {
    
    static final int MAX_MANIFEST_BYTES = 16 * 1024 * 1024;
    
    static final int MAX_GROUP_ID_BYTES = 1024;
    
    private static final String MANIFEST_NAME = "manifest";
    
    private static final String MANIFEST_TEMP_NAME = "manifest.tmp";
    
    private final Path configuredLogUri;
    
    private Path root;
    
    private final RaftOptions raftOptions;
    
    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    
    private final Lock readLock = readWriteLock.readLock();
    
    private final Lock writeLock = readWriteLock.writeLock();
    
    private FileChannel lockChannel;
    
    private FileLock directoryLock;
    
    private Manifest manifest;
    
    private LogRecordCodec recordCodec;
    
    private final long maxSegmentBytes;
    
    private WriteRuntime writeRuntime;
    
    private long nextSegmentId;
    
    /**
     * Creates a file log foundation under {@code logUri/file-log}.
     *
     * @param logUri per-group JRaft log location
     * @param raftOptions JRaft durability options
     */
    public FileLogStorage(Path logUri, RaftOptions raftOptions) {
        this(logUri, raftOptions, Segment.MAX_SEGMENT_BYTES);
    }
    
    FileLogStorage(Path logUri, RaftOptions raftOptions, long maxSegmentBytes) {
        if (maxSegmentBytes < FileLogFormat.SEGMENT_HEADER_SIZE + FileLogFormat.RECORD_OVERHEAD
            || maxSegmentBytes > Segment.MAX_SEGMENT_BYTES) {
            throw new IllegalArgumentException("Invalid maximum segment size");
        }
        this.maxSegmentBytes = maxSegmentBytes;
        configuredLogUri = Objects.requireNonNull(logUri, "logUri").toAbsolutePath();
        this.raftOptions = Objects.requireNonNull(raftOptions, "raftOptions").copy();
    }
    
    /**
     * Opens and validates the published file state.
     *
     * @param options JRaft initialization context
     * @return {@code true} when the store was opened
     */
    @Override
    public boolean init(LogStorageOptions options) {
        writeLock.lock();
        try {
            if (directoryLock != null) {
                return isOpen() && writeRuntime != null;
            }
            clearRuntimeResources();
            if (!raftOptions.isSync()) {
                Loggers.RAFT.error("File log storage does not support asynchronous writes");
                return false;
            }
            try {
                validateInitializationOptions(options);
                recordCodec = new LogRecordCodec(options.getLogEntryCodecFactory());
                root = resolveLogDirectory(configuredLogUri).resolve("file-log");
                createRootDirectory();
                acquireDirectoryLock();
                createStorageDirectories();
                Path manifestPath = root.resolve(MANIFEST_NAME);
                if (Files.exists(manifestPath, LinkOption.NOFOLLOW_LINKS)) {
                    requireRegularFile(manifestPath);
                    manifest = readManifest(manifestPath);
                    if (!manifest.getGroupId().equals(options.getGroupId())) {
                        throw new IOException("Manifest group does not match JRaft group");
                    }
                } else {
                    checkEmptyInitialization();
                    Files.deleteIfExists(root.resolve(MANIFEST_TEMP_NAME));
                    manifest = Manifest.empty(UUID.randomUUID(), options.getGroupId(), 1);
                    writeManifest(manifest);
                }
                nextSegmentId = findNextSegmentId();
                List<ConfigurationEntry> configurations = recover();
                openActive();
                ConfigurationManager manager = options.getConfigurationManager();
                for (ConfigurationEntry configuration : configurations) {
                    if (!manager.add(configuration)) {
                        throw new IOException(
                            "Cannot restore configuration at " + configuration.getId());
                    }
                }
                if (!isOpen()) {
                    throw new IOException(
                        "File log storage resources are incomplete after initialization");
                }
                if (Platform.isWindows()) {
                    Loggers.RAFT.warn(
                        "Directory synchronization is skipped on Windows for file log storage at {}. "
                            + "Durability across power loss is not guaranteed without directory synchronization.",
                        root);
                }
                return true;
            } catch (IOException | RuntimeException e) {
                Loggers.RAFT.error("Failed to initialize file log storage: configuredLogUri="
                    + configuredLogUri + ", resolvedRoot=" + root, e);
                clearRuntimeResources();
                return false;
            }
        } finally {
            writeLock.unlock();
        }
    }
    
    private void validateInitializationOptions(LogStorageOptions options) throws IOException {
        if (options == null || options.getGroupId() == null || options.getGroupId().isBlank()
            || options.getConfigurationManager() == null
            || options.getLogEntryCodecFactory() == null) {
            throw new IOException("Missing file log initialization context");
        }
        encodeGroupId(options.getGroupId());
    }
    
    private static Path resolveLogDirectory(Path path) throws IOException {
        try {
            // An existing dangling link must fail resolution, rather than create its target.
            Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException e) {
            Path parent = path.getParent();
            if (parent == null) {
                throw e;
            }
            Path candidate = resolveLogDirectory(parent).resolve(path.getFileName());
            try {
                Files.createDirectory(candidate);
            } catch (FileAlreadyExistsException existing) {
                // Another initializer may have created this configuration directory.
                // Resolve and validate it below, including directory links at this boundary.
            }
            Path resolved = candidate.toRealPath();
            requireDirectory(resolved);
            return resolved;
        }
        Path resolved = path.toRealPath();
        requireDirectory(resolved);
        return resolved;
    }
    
    private void createRootDirectory() throws IOException {
        Files.createDirectories(root);
        requireDirectory(root);
    }
    
    private void createStorageDirectories() throws IOException {
        Path segments = root.resolve("segments");
        Path index = root.resolve("index");
        Files.createDirectories(segments);
        requireDirectory(segments);
        Files.createDirectories(index);
        requireDirectory(index);
        forceDirectory(segments);
        forceDirectory(index);
        // Visible ancestors may remain from a failed init; persist the whole chain on retry too.
        for (Path directory = root; directory != null; directory = directory.getParent()) {
            forceDirectory(directory);
        }
    }
    
    private void acquireDirectoryLock() throws IOException {
        lockChannel = FileChannel.open(root.resolve("LOCK"), StandardOpenOption.CREATE,
            StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        directoryLock = lockChannel.tryLock();
        if (directoryLock == null) {
            throw new IOException("File log directory is already in use: " + root);
        }
    }
    
    private void checkEmptyInitialization() throws IOException {
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(root)) {
            for (Path entry : entries) {
                String name = entry.getFileName().toString();
                if ("segments".equals(name) || "index".equals(name)) {
                    requireDirectory(entry);
                    try (DirectoryStream<Path> children = Files.newDirectoryStream(entry)) {
                        if (children.iterator().hasNext()) {
                            throw new IOException(
                                "Manifest is missing with existing data: " + entry);
                        }
                    }
                } else if ("LOCK".equals(name) || MANIFEST_TEMP_NAME.equals(name)) {
                    requireRegularFile(entry);
                } else {
                    throw new IOException("Unexpected content without a manifest: " + entry);
                }
            }
        }
    }
    
    Manifest getManifest() {
        readLock.lock();
        try {
            if (!isOpen()) {
                throw new IllegalStateException("File log store is not open");
            }
            return manifest;
        } finally {
            readLock.unlock();
        }
    }
    
    @Override
    public long getFirstLogIndex() {
        readLock.lock();
        try {
            return isOpen() ? manifest.getFirstLogIndex() : 0;
        } finally {
            readLock.unlock();
        }
    }
    
    @Override
    public long getLastLogIndex() {
        readLock.lock();
        try {
            return isOpen() ? manifest.getLastLogIndex() : 0;
        } finally {
            readLock.unlock();
        }
    }
    
    @Override
    public LogEntry getEntry(long index) {
        readLock.lock();
        try {
            return isOpen() ? readEntry(index) : null;
        } catch (IOException | RuntimeException e) {
            Loggers.RAFT.error("Failed to read file log at " + root + " index " + index, e);
            return null;
        } finally {
            readLock.unlock();
        }
    }
    
    @Override
    @Deprecated
    public long getTerm(long index) {
        LogEntry entry = getEntry(index);
        return entry == null ? 0 : entry.getId().getTerm();
    }
    
    private LogEntry readEntry(long index) throws IOException {
        if (index < manifest.getFirstLogIndex() || index > manifest.getLastLogIndex()) {
            return null;
        }
        Manifest.SegmentMeta metadata = findSegment(manifest, index);
        Index.Entry location = locate(metadata, index);
        return readLocatedEntry(metadata, index, location);
    }
    
    private LogEntry readLocatedEntry(Manifest.SegmentMeta metadata, long index,
        Index.Entry location) throws IOException {
        LogEntry result;
        if (writeRuntime != null && writeRuntime.segment != null
            && writeRuntime.segment.getHeader().getSegmentId() == metadata.getSegmentId()) {
            result = decodeEntry(writeRuntime.segment, metadata, location);
        } else {
            try (Segment segment = Segment.open(segmentPath(metadata.getSegmentId()),
                metadata.getPublishedEndOffset())) {
                validateSegmentIdentity(segment, metadata);
                result = decodeEntry(segment, metadata, location);
            }
        }
        if (result.getId().getIndex() != index) {
            throw new IOException("Decoded log index does not match requested index " + index);
        }
        return result;
    }
    
    private LogEntry decodeEntry(Segment segment, Manifest.SegmentMeta metadata,
        Index.Entry location) throws IOException {
        ByteBuffer bytes = segment.read(location.getOffset(), location.getRecordSize(),
            metadata.getPublishedEndOffset());
        if (recordCodec.recordSize(bytes) != location.getRecordSize()) {
            throw new IOException("Index record size disagrees with record header");
        }
        return recordCodec.decode(bytes);
    }
    
    private Index.Entry locate(Manifest.SegmentMeta metadata, long index) throws IOException {
        if (writeRuntime != null && writeRuntime.segment != null
            && writeRuntime.segment.getHeader().getSegmentId() == metadata.getSegmentId()) {
            return writeRuntime.index.read(index, metadata.getFirstRecordOffset(),
                metadata.getPublishedEndOffset());
        }
        try (Index file =
            Index.open(indexPath(metadata.getSegmentId()), metadata.getFirstIndex(),
                metadata.getLastIndex())) {
            return file.read(index, metadata.getFirstRecordOffset(),
                metadata.getPublishedEndOffset());
        }
    }
    
    private static Manifest.SegmentMeta findSegment(Manifest state, long index) throws IOException {
        List<Manifest.SegmentMeta> segments = state.getSegments();
        int low = 0;
        int high = segments.size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            Manifest.SegmentMeta segment = segments.get(middle);
            if (index < segment.getFirstIndex()) {
                high = middle - 1;
            } else if (index > segment.getLastIndex()) {
                low = middle + 1;
            } else {
                return segment;
            }
        }
        throw new IOException("No segment for retained log index " + index);
    }
    
    @Override
    public boolean appendEntry(LogEntry entry) {
        return appendEntries(Collections.singletonList(entry)) == 1;
    }
    
    @Override
    public int appendEntries(List<LogEntry> entries) {
        writeLock.lock();
        try {
            if (!isOpen() || writeRuntime == null || entries == null || entries.isEmpty()) {
                return 0;
            }
            List<ByteBuffer> records = new ArrayList<>(entries.size());
            Manifest next;
            long first = manifest.getFirstLogIndex();
            try {
                first = Math.addExact(manifest.getLastLogIndex(), 1);
                long expected = first;
                for (LogEntry entry : entries) {
                    ByteBuffer record = recordCodec.encode(entry);
                    if (entry.getId().getIndex() != expected) {
                        throw new IOException("Non-contiguous append at index " + expected);
                    }
                    expected = Math.addExact(expected, 1);
                    records.add(record);
                }
                next = planAppend(manifest, first, records);
            } catch (IOException | RuntimeException e) {
                Loggers.RAFT.warn("Rejected file log append at " + root, e);
                return 0;
            }
            try {
                writeBatch(next, first, records);
                return records.size();
            } catch (IOException | RuntimeException e) {
                failMutation("append", e);
                return 0;
            }
        } finally {
            writeLock.unlock();
        }
    }
    
    private Manifest planAppend(Manifest base, long first, List<ByteBuffer> records)
        throws IOException {
        List<Manifest.SegmentMeta> segments = new ArrayList<>(base.getSegments());
        long candidateId = nextSegmentId;
        long index = first;
        for (ByteBuffer record : records) {
            int size = record.remaining();
            if (size > maxSegmentBytes - FileLogFormat.SEGMENT_HEADER_SIZE) {
                throw new IOException("Record does not fit an empty segment");
            }
            Manifest.SegmentMeta previous =
                segments.isEmpty() ? null : segments.get(segments.size() - 1);
            if (previous == null
                || Math.addExact(previous.getPublishedEndOffset(), size) > maxSegmentBytes) {
                long id = candidateId;
                candidateId = Math.addExact(candidateId, 1);
                segments.add(new Manifest.SegmentMeta(id, index, index,
                    FileLogFormat.SEGMENT_HEADER_SIZE,
                    Math.addExact(FileLogFormat.SEGMENT_HEADER_SIZE, size)));
            } else {
                segments.set(segments.size() - 1, new Manifest.SegmentMeta(previous.getSegmentId(),
                    previous.getFirstIndex(), index, previous.getFirstRecordOffset(),
                    Math.addExact(previous.getPublishedEndOffset(), size)));
            }
            index = Math.addExact(index, 1);
        }
        Manifest next = new Manifest(FileLogFormat.VERSION, base.getStoreId(), base.getGroupId(),
            base.getFirstLogIndex(), Math.subtractExact(index, 1), segments);
        // Validate the entire publication, including its size, before touching files.
        encodeManifest(next);
        return next;
    }
    
    private void writeBatch(Manifest next, long first, List<ByteBuffer> records)
        throws IOException {
        long index = first;
        for (ByteBuffer record : records) {
            Manifest.SegmentMeta target = findSegment(next, index);
            if (writeRuntime.segment == null
                || writeRuntime.segment.getHeader().getSegmentId() != target.getSegmentId()) {
                // Every rolled segment is durable before the batch Manifest is published.
                if (writeRuntime.segment != null) {
                    writeRuntime.segment.force();
                }
                closeActive();
                nextSegmentId = Math.addExact(target.getSegmentId(), 1);
                writeRuntime.segment = Segment.create(root.resolve("segments"),
                    manifest.getStoreId(), target.getSegmentId());
                writeRuntime.index =
                    Index.create(indexPath(target.getSegmentId()), target.getFirstIndex());
            }
            int size = record.remaining();
            long offset = writeRuntime.segment.append(record);
            writeRuntime.index.append(index, offset, size);
            index = Math.addExact(index, 1);
        }
        writeRuntime.segment.force();
        publishManifest(next);
    }
    
    @Override
    public boolean truncatePrefix(long firstIndexKept) {
        writeLock.lock();
        try {
            if (!isOpen() || writeRuntime == null || firstIndexKept <= 0) {
                return false;
            }
            if (firstIndexKept <= manifest.getFirstLogIndex()) {
                return true;
            }
            Manifest previous = manifest;
            try {
                Manifest next;
                Manifest.SegmentMeta boundary = null;
                if (firstIndexKept > previous.getLastLogIndex()) {
                    next = Manifest.empty(previous.getStoreId(), previous.getGroupId(),
                        firstIndexKept);
                } else {
                    Manifest.SegmentMeta old = findSegment(previous, firstIndexKept);
                    Index.Entry location = locate(old, firstIndexKept);
                    readLocatedEntry(old, firstIndexKept, location);
                    long offset = location.getOffset();
                    boundary = new Manifest.SegmentMeta(old.getSegmentId(), firstIndexKept,
                        old.getLastIndex(), offset, old.getPublishedEndOffset());
                    List<Manifest.SegmentMeta> segments = new ArrayList<>();
                    segments.add(boundary);
                    for (Manifest.SegmentMeta segment : previous.getSegments()) {
                        if (segment.getSegmentId() > old.getSegmentId()) {
                            segments.add(segment);
                        }
                    }
                    next = new Manifest(FileLogFormat.VERSION, previous.getStoreId(),
                        previous.getGroupId(),
                        firstIndexKept, previous.getLastLogIndex(), segments);
                }
                publishManifest(next);
                closeActive();
                if (boundary != null) {
                    rebuildIndex(boundary);
                }
                openActive();
                retireSegments(previous);
                return true;
            } catch (IOException | RuntimeException e) {
                failMutation("truncate prefix", e);
                return false;
            }
        } finally {
            writeLock.unlock();
        }
    }
    
    @Override
    public boolean truncateSuffix(long lastIndexKept) {
        writeLock.lock();
        try {
            if (!isOpen() || writeRuntime == null || lastIndexKept < 0) {
                return false;
            }
            if (lastIndexKept >= manifest.getLastLogIndex()) {
                return true;
            }
            Manifest previous = manifest;
            try {
                Manifest next;
                Manifest.SegmentMeta old = null;
                long end = 0;
                if (lastIndexKept < previous.getFirstLogIndex()) {
                    next = Manifest.empty(previous.getStoreId(), previous.getGroupId(),
                        previous.getFirstLogIndex());
                } else {
                    old = findSegment(previous, lastIndexKept);
                    Index.Entry location = locate(old, lastIndexKept);
                    readLocatedEntry(old, lastIndexKept, location);
                    end = Math.addExact(location.getOffset(), location.getRecordSize());
                    List<Manifest.SegmentMeta> segments = new ArrayList<>();
                    for (Manifest.SegmentMeta segment : previous.getSegments()) {
                        if (segment.getSegmentId() < old.getSegmentId()) {
                            segments.add(segment);
                        }
                    }
                    segments.add(new Manifest.SegmentMeta(old.getSegmentId(), old.getFirstIndex(),
                        lastIndexKept, old.getFirstRecordOffset(), end));
                    next = new Manifest(FileLogFormat.VERSION, previous.getStoreId(),
                        previous.getGroupId(),
                        previous.getFirstLogIndex(), lastIndexKept, segments);
                }
                publishManifest(next);
                closeActive();
                if (old != null) {
                    try (Segment segment = Segment.open(segmentPath(old.getSegmentId()),
                        old.getPublishedEndOffset());
                        Index index = Index.openWritable(indexPath(old.getSegmentId()),
                            old.getFirstIndex(), old.getLastIndex())) {
                        segment.truncate(end);
                        segment.force();
                        index.truncateSuffix(lastIndexKept);
                    }
                }
                openActive();
                retireSegments(previous);
                return true;
            } catch (IOException | RuntimeException e) {
                failMutation("truncate suffix", e);
                return false;
            }
        } finally {
            writeLock.unlock();
        }
    }
    
    @Override
    public boolean reset(long nextLogIndex) {
        writeLock.lock();
        try {
            if (!isOpen() || writeRuntime == null || nextLogIndex <= 0) {
                return false;
            }
            Manifest previous = manifest;
            Manifest next = Manifest.empty(previous.getStoreId(), previous.getGroupId(),
                nextLogIndex);
            try {
                encodeManifest(next);
            } catch (IOException | RuntimeException error) {
                Loggers.RAFT.warn("Rejected file log reset at " + root, error);
                return false;
            }
            try {
                publishManifest(next);
                closeActive();
                openActive();
                retireSegments(previous);
                return true;
            } catch (IOException | RuntimeException error) {
                failMutation("reset", error);
                return false;
            }
        } finally {
            writeLock.unlock();
        }
    }
    
    private void rebuildIndex(Manifest.SegmentMeta metadata) throws IOException {
        try (Index index = Index.create(indexPath(metadata.getSegmentId()),
            metadata.getFirstIndex())) {
            scanSegment(metadata, index, null);
        }
    }
    
    private void retireSegments(Manifest previous) {
        Set<Long> retained = new HashSet<>();
        for (Manifest.SegmentMeta segment : manifest.getSegments()) {
            retained.add(segment.getSegmentId());
        }
        for (Manifest.SegmentMeta segment : previous.getSegments()) {
            if (!retained.contains(segment.getSegmentId())) {
                deleteGarbage(segmentPath(segment.getSegmentId()));
                deleteGarbage(indexPath(segment.getSegmentId()));
            }
        }
        synchronizeGarbageDirectory();
    }
    
    private void synchronizeGarbageDirectory() {
        try {
            forceDirectory(root.resolve("segments"));
        } catch (IOException error) {
            Loggers.RAFT.warn("Cannot synchronize reclaimed file log directory at " + root, error);
        }
    }
    
    private void deleteGarbage(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException error) {
            Loggers.RAFT.warn("Cannot reclaim unused file log file " + path, error);
        }
    }
    
    private static Manifest.SegmentMeta lastSegment(Manifest state) {
        return state.getSegments().get(state.getSegments().size() - 1);
    }
    
    private void openActive() throws IOException {
        WriteRuntime opened = new WriteRuntime();
        try {
            if (!manifest.getSegments().isEmpty()) {
                Manifest.SegmentMeta last = lastSegment(manifest);
                opened.segment = Segment.open(segmentPath(last.getSegmentId()),
                    last.getPublishedEndOffset());
                validateSegmentIdentity(opened.segment, last);
                opened.index = Index.openWritable(indexPath(last.getSegmentId()),
                    last.getFirstIndex(), last.getLastIndex());
            }
            writeRuntime = opened;
        } catch (IOException | RuntimeException error) {
            try {
                opened.close();
            } catch (IOException closeError) {
                error.addSuppressed(closeError);
            }
            throw error;
        }
    }
    
    private void closeActive() throws IOException {
        if (writeRuntime != null) {
            writeRuntime.close();
        }
    }
    
    private void failMutation(String operation, Exception cause) {
        try {
            closeActive();
        } catch (IOException closeError) {
            cause.addSuppressed(closeError);
        } finally {
            writeRuntime = null;
        }
        Loggers.RAFT.error("Failed to " + operation + " file log at " + root, cause);
    }
    
    private static final class WriteRuntime implements AutoCloseable {
        
        private Segment segment;
        private Index index;
        
        @Override
        public void close() throws IOException {
            IOException failure = null;
            try {
                if (index != null) {
                    index.close();
                }
            } catch (IOException error) {
                failure = error;
            } finally {
                index = null;
            }
            try {
                if (segment != null) {
                    segment.close();
                }
            } catch (IOException error) {
                if (failure == null) {
                    failure = error;
                } else {
                    failure.addSuppressed(error);
                }
            } finally {
                segment = null;
            }
            if (failure != null) {
                throw failure;
            }
        }
    }
    
    private long findNextSegmentId() throws IOException {
        long largest = manifest.getSegments().isEmpty() ? 0
            : lastSegment(manifest).getSegmentId();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(root.resolve("segments"))) {
            for (Path file : files) {
                Long id = FileLogFormat.parseFileId(file.getFileName().toString(), ".seg");
                if (id != null) {
                    largest = Math.max(largest, id);
                }
            }
        }
        try {
            return Math.addExact(largest, 1);
        } catch (ArithmeticException error) {
            throw new IOException("Segment ID exhausted", error);
        }
    }
    
    private static long manifestSize(int groupLength, int segmentCount) throws IOException {
        try {
            long descriptors = Math.multiplyExact((long) segmentCount,
                FileLogFormat.SEGMENT_META_SIZE);
            return Math.addExact(Math.addExact(FileLogFormat.MANIFEST_HEADER_SIZE,
                (long) groupLength), Math.addExact(descriptors, Long.BYTES));
        } catch (ArithmeticException error) {
            throw new IOException("Manifest size overflow", error);
        }
    }
    
    ByteBuffer encodeManifest(Manifest value) throws IOException {
        validateManifest(value);
        byte[] group = encodeGroupId(value.getGroupId());
        long calculatedSize = manifestSize(group.length, value.getSegments().size());
        if (calculatedSize > MAX_MANIFEST_BYTES) {
            throw new IOException("Manifest exceeds size limit");
        }
        int fileSize = Math.toIntExact(calculatedSize);
        ByteBuffer result = ByteBuffer.allocate(fileSize).order(ByteOrder.BIG_ENDIAN);
        result.putInt(FileLogFormat.MANIFEST_MAGIC);
        result.putShort((short) value.getFormatVersion());
        result.putShort((short) FileLogFormat.MANIFEST_HEADER_SIZE);
        result.putInt(fileSize);
        result.putLong(value.getStoreId().getMostSignificantBits());
        result.putLong(value.getStoreId().getLeastSignificantBits());
        result.putLong(value.getFirstLogIndex());
        result.putLong(value.getLastLogIndex());
        result.putInt(group.length);
        result.putInt(value.getSegments().size());
        result.put(group);
        for (Manifest.SegmentMeta segment : value.getSegments()) {
            result.putLong(segment.getSegmentId());
            result.putLong(segment.getFirstIndex());
            result.putLong(segment.getLastIndex());
            result.putLong(segment.getFirstRecordOffset());
            result.putLong(segment.getPublishedEndOffset());
        }
        result.putLong(FileLogFormat.crc64(result.array(), 0, fileSize - Long.BYTES));
        return result.flip();
    }
    
    Manifest decodeManifest(ByteBuffer source) throws IOException {
        if (source.remaining() < FileLogFormat.MANIFEST_HEADER_SIZE + Long.BYTES
            || source.remaining() > MAX_MANIFEST_BYTES) {
            throw new IOException("Manifest size is outside bounds");
        }
        byte[] bytes = new byte[source.remaining()];
        source.duplicate().get(bytes);
        ByteBuffer input = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        int magic = input.getInt();
        int version = Short.toUnsignedInt(input.getShort());
        int headerSize = Short.toUnsignedInt(input.getShort());
        int fileSize = input.getInt();
        UUID storeId = new UUID(input.getLong(), input.getLong());
        long firstLogIndex = input.getLong();
        long lastLogIndex = input.getLong();
        int groupLength = input.getInt();
        int segmentCount = input.getInt();
        if (magic != FileLogFormat.MANIFEST_MAGIC || version != FileLogFormat.VERSION
            || headerSize != FileLogFormat.MANIFEST_HEADER_SIZE || fileSize != bytes.length
            || groupLength <= 0 || groupLength > MAX_GROUP_ID_BYTES || segmentCount < 0) {
            throw new IOException("Invalid manifest header");
        }
        long expectedSize = manifestSize(groupLength, segmentCount);
        if (expectedSize != fileSize
            || input.getLong(fileSize - Long.BYTES) != FileLogFormat.crc64(bytes, 0,
                fileSize - Long.BYTES)) {
            throw new IOException("Manifest size or checksum mismatch");
        }
        byte[] groupBytes = new byte[groupLength];
        input.get(groupBytes);
        String groupId = decodeGroupId(groupBytes);
        List<Manifest.SegmentMeta> segments = new ArrayList<>(segmentCount);
        for (int i = 0; i < segmentCount; i++) {
            segments.add(new Manifest.SegmentMeta(input.getLong(), input.getLong(), input.getLong(),
                input.getLong(), input.getLong()));
        }
        Manifest result = new Manifest(version, storeId, groupId, firstLogIndex, lastLogIndex,
            segments);
        validateManifest(result);
        return result;
    }
    
    private static byte[] encodeGroupId(String groupId) throws IOException {
        if (groupId == null || groupId.isBlank() || groupId.length() > MAX_GROUP_ID_BYTES) {
            throw new IOException("Invalid JRaft group id");
        }
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(groupId));
            if (encoded.remaining() > MAX_GROUP_ID_BYTES) {
                throw new IOException("JRaft group id is too long");
            }
            byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            return bytes;
        } catch (CharacterCodingException e) {
            throw new IOException("JRaft group id cannot be encoded as UTF-8", e);
        }
    }
    
    private static String decodeGroupId(byte[] groupBytes) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(groupBytes))
                .toString();
        } catch (CharacterCodingException e) {
            throw new IOException("Manifest group id is not valid UTF-8", e);
        }
    }
    
    private void validateManifest(Manifest value) throws IOException {
        if (value == null || value.getFormatVersion() != FileLogFormat.VERSION
            || value.getStoreId() == null
            || value.getFirstLogIndex() <= 0
            || value.getLastLogIndex() < Math.subtractExact(value.getFirstLogIndex(), 1)) {
            throw new IOException("Invalid manifest identity or log range");
        }
        encodeGroupId(value.getGroupId());
        List<Manifest.SegmentMeta> segments = value.getSegments();
        if (segments.isEmpty()) {
            if (value.getLastLogIndex() != Math.subtractExact(value.getFirstLogIndex(), 1)) {
                throw new IOException("Non-empty manifest range has no segments");
            }
            return;
        }
        long expectedIndex = value.getFirstLogIndex();
        long previousSegmentId = 0;
        for (Manifest.SegmentMeta segment : segments) {
            if (segment.getSegmentId() <= previousSegmentId
                || segment.getFirstIndex() != expectedIndex
                || segment.getLastIndex() < segment.getFirstIndex()
                || segment.getFirstRecordOffset() < FileLogFormat.SEGMENT_HEADER_SIZE
                || segment.getPublishedEndOffset() <= segment.getFirstRecordOffset()
                || segment.getPublishedEndOffset() > Segment.MAX_SEGMENT_BYTES) {
                throw new IOException("Invalid manifest segment descriptor");
            }
            previousSegmentId = segment.getSegmentId();
            try {
                expectedIndex = Math.addExact(segment.getLastIndex(), 1);
            } catch (ArithmeticException e) {
                throw new IOException(
                    "Manifest segment range overflow for segment " + segment.getSegmentId(), e);
            }
        }
        if (segments.get(0).getFirstIndex() != value.getFirstLogIndex()
            || segments.get(segments.size() - 1).getLastIndex() != value.getLastLogIndex()) {
            throw new IOException("Manifest global and segment ranges disagree");
        }
    }
    
    /**
     * Publishes a Manifest after its referenced new Segment data has been forced.
     * This method does not force Segment payloads; callers must complete those
     * durability operations before invoking it.
     *
     * @param next Manifest to publish
     * @throws IOException if publication I/O fails
     */
    void publishManifest(Manifest next) throws IOException {
        writeLock.lock();
        try {
            if (!isOpen()) {
                throw new IllegalStateException("File log store is not open");
            }
            validateManifest(next);
            if (!manifest.getStoreId().equals(next.getStoreId())
                || !manifest.getGroupId().equals(next.getGroupId())) {
                throw new IOException("Manifest identity cannot change");
            }
            Set<Long> currentSegments = new HashSet<>();
            for (Manifest.SegmentMeta segment : manifest.getSegments()) {
                currentSegments.add(segment.getSegmentId());
            }
            boolean addsSegment = next.getSegments().stream()
                .anyMatch(segment -> !currentSegments.contains(segment.getSegmentId()));
            if (addsSegment) {
                forceDirectory(root.resolve("segments"));
            }
            writeManifest(next);
        } finally {
            writeLock.unlock();
        }
    }
    
    private Manifest readManifest(Path path) throws IOException {
        long size = Files.size(path);
        if (size < FileLogFormat.MANIFEST_HEADER_SIZE + Long.BYTES || size > MAX_MANIFEST_BYTES) {
            throw new IOException("Manifest size is outside bounds: " + path);
        }
        ByteBuffer content = ByteBuffer.allocate((int) size);
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ,
            LinkOption.NOFOLLOW_LINKS)) {
            readFully(channel, content, 0, path);
        }
        content.flip();
        return decodeManifest(content);
    }
    
    private void writeManifest(Manifest next) throws IOException {
        ByteBuffer content = encodeManifest(next);
        Path temporary = root.resolve(MANIFEST_TEMP_NAME);
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.READ,
            StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            writeFully(channel, content, 0, temporary);
            channel.force(true);
        }
        Files.move(temporary, root.resolve(MANIFEST_NAME), StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
        forceDirectory(root);
        // Install the confirmed range before any fallible post-publication work.
        manifest = next;
    }
    
    private List<ConfigurationEntry> recover() throws IOException {
        List<ConfigurationEntry> configurations = new ArrayList<>();
        long expectedIndex = manifest.getFirstLogIndex();
        for (Manifest.SegmentMeta segment : manifest.getSegments()) {
            if (segment.getFirstIndex() != expectedIndex) {
                throw new IOException("Non-contiguous manifest segment ranges");
            }
            // Index is derived data; an interrupted rebuild is repeated on the next init.
            try (Index index = Index.create(indexPath(segment.getSegmentId()),
                segment.getFirstIndex())) {
                expectedIndex = scanSegment(segment, index, configurations);
            }
        }
        if (expectedIndex != Math.addExact(manifest.getLastLogIndex(), 1)) {
            throw new IOException("Recovered log range does not match manifest");
        }
        List<Path> unpublishedSegments = validateUnpublishedSegments();
        // Pin the validated Manifest directory entry before discarding any old data.
        forceDirectory(root);
        truncateUnpublishedTails();
        for (Path unused : unpublishedSegments) {
            deleteGarbage(unused);
        }
        synchronizeGarbageDirectory();
        cleanupUnusedIndexes();
        deleteGarbage(root.resolve(MANIFEST_TEMP_NAME));
        return configurations;
    }
    
    private long scanSegment(Manifest.SegmentMeta metadata, Index index,
        List<ConfigurationEntry> configurations) throws IOException {
        Path path = segmentPath(metadata.getSegmentId());
        long offset = metadata.getFirstRecordOffset();
        long expectedIndex = metadata.getFirstIndex();
        try (Segment segment = Segment.open(path, metadata.getPublishedEndOffset())) {
            validateSegmentIdentity(segment, metadata);
            while (offset < metadata.getPublishedEndOffset()) {
                ByteBuffer recordHeader = segment.read(offset, FileLogFormat.RECORD_HEADER_SIZE,
                    metadata.getPublishedEndOffset());
                int recordSize = recordCodec.recordSize(recordHeader);
                long nextOffset = Math.addExact(offset, recordSize);
                if (nextOffset > metadata.getPublishedEndOffset()) {
                    throw new IOException("Published record is incomplete at " + path + " offset "
                        + offset);
                }
                LogEntry entry = recordCodec.decode(segment.read(offset, recordSize,
                    metadata.getPublishedEndOffset()));
                if (entry.getId().getIndex() != expectedIndex
                    || expectedIndex > metadata.getLastIndex()) {
                    throw new IOException(
                        "Non-contiguous log index at " + path + " offset " + offset);
                }
                if (configurations != null
                    && entry.getType() == EntryType.ENTRY_TYPE_CONFIGURATION) {
                    Configuration current =
                        new Configuration(entry.getPeers(), entry.getLearners());
                    Configuration old = entry.getOldPeers() == null ? new Configuration()
                        : new Configuration(entry.getOldPeers(), entry.getOldLearners());
                    configurations.add(new ConfigurationEntry(entry.getId(), current, old));
                }
                index.append(expectedIndex, offset, recordSize);
                offset = nextOffset;
                expectedIndex = Math.addExact(expectedIndex, 1);
            }
        }
        if (offset != metadata.getPublishedEndOffset()
            || expectedIndex != Math.addExact(metadata.getLastIndex(), 1)) {
            throw new IOException("Segment range does not match manifest: " + path);
        }
        return expectedIndex;
    }
    
    private void validateSegmentIdentity(Segment segment, Manifest.SegmentMeta metadata)
        throws IOException {
        Segment.SegmentHeader header = segment.getHeader();
        if (!header.getStoreId().equals(manifest.getStoreId())
            || header.getSegmentId() != metadata.getSegmentId()) {
            throw new IOException("Segment identity does not match manifest: " + segment.getPath());
        }
    }
    
    private void truncateUnpublishedTails() throws IOException {
        for (Manifest.SegmentMeta metadata : manifest.getSegments()) {
            Path path = segmentPath(metadata.getSegmentId());
            try (Segment segment = Segment.open(path, metadata.getPublishedEndOffset())) {
                if (segment.getPhysicalSize() > segment.getLogicalEnd()) {
                    segment.truncateToLogicalEnd();
                    segment.force();
                }
            }
        }
    }
    
    private List<Path> validateUnpublishedSegments() throws IOException {
        List<Path> result = new ArrayList<>();
        Set<String> published = new HashSet<>();
        for (Manifest.SegmentMeta segment : manifest.getSegments()) {
            published.add(FileLogFormat.segmentName(segment.getSegmentId()));
        }
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(root.resolve("segments"))) {
            for (Path entry : entries) {
                String name = entry.getFileName().toString();
                Long segmentId = FileLogFormat.parseFileId(name, ".seg");
                if (segmentId == null || published.contains(name)) {
                    continue;
                }
                boolean owned;
                try (Segment segment = Segment.open(entry,
                    FileLogFormat.SEGMENT_HEADER_SIZE)) {
                    Segment.SegmentHeader header = segment.getHeader();
                    if (header.getSegmentId() != segmentId) {
                        throw new IOException("Segment id does not match its file name: " + entry);
                    }
                    owned = header.getStoreId().equals(manifest.getStoreId());
                } catch (Segment.CorruptHeaderException e) {
                    // Retain unknown ownership and its filename's allocation high-water mark.
                    Loggers.RAFT.warn("Preserving unpublished segment with unknown ownership: "
                        + entry, e);
                    continue;
                }
                if (!owned) {
                    throw new IOException("Foreign segment in file log directory: " + entry);
                }
                result.add(entry);
            }
        }
        return result;
    }
    
    private void cleanupUnusedIndexes() throws IOException {
        Set<String> published = new HashSet<>();
        for (Manifest.SegmentMeta segment : manifest.getSegments()) {
            published.add(FileLogFormat.indexName(segment.getSegmentId()));
        }
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(root.resolve("index"))) {
            for (Path entry : entries) {
                String name = entry.getFileName().toString();
                if (FileLogFormat.parseFileId(name, ".idx") != null && !published.contains(name)) {
                    requireRegularFile(entry);
                    deleteGarbage(entry);
                }
            }
        }
    }
    
    private Path segmentPath(long segmentId) {
        return root.resolve("segments").resolve(FileLogFormat.segmentName(segmentId));
    }
    
    private Path indexPath(long segmentId) {
        return root.resolve("index").resolve(FileLogFormat.indexName(segmentId));
    }
    
    private static void readFully(FileChannel channel, ByteBuffer target, long offset, Path path)
        throws IOException {
        long position = offset;
        while (target.hasRemaining()) {
            int read = channel.read(target, position);
            if (read < 0) {
                throw new IOException("Unexpected EOF in " + path + " at offset " + position);
            }
            if (read == 0) {
                throw new IOException("No read progress in " + path + " at offset " + position);
            }
            position = Math.addExact(position, read);
        }
    }
    
    private static void writeFully(FileChannel channel, ByteBuffer source, long offset, Path path)
        throws IOException {
        long position = offset;
        while (source.hasRemaining()) {
            int written = channel.write(source, position);
            if (written == 0) {
                throw new IOException("No write progress in " + path + " at offset " + position);
            }
            position = Math.addExact(position, written);
        }
    }
    
    static void forceDirectory(Path directory) throws IOException {
        requireDirectory(directory);
        if (Platform.isWindows()) {
            return;
        }
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ,
            LinkOption.NOFOLLOW_LINKS)) {
            channel.force(true);
        }
    }
    
    private static void requireDirectory(Path path) throws IOException {
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Not a real directory: " + path);
        }
    }
    
    private static void requireRegularFile(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Not a regular file: " + path);
        }
    }
    
    private boolean isOpen() {
        return recordCodec != null && manifest != null && lockChannel != null
            && lockChannel.isOpen() && directoryLock != null && directoryLock.isValid();
    }
    
    /**
     * Closes this store without publishing unfinished work.
     */
    @Override
    public void shutdown() {
        writeLock.lock();
        try {
            clearRuntimeResources();
        } finally {
            writeLock.unlock();
        }
    }
    
    private void clearRuntimeResources() {
        try {
            closeActive();
        } catch (IOException e) {
            Loggers.RAFT.warn("Failed to close active file log at " + root, e);
        }
        writeRuntime = null;
        nextSegmentId = 0;
        if (directoryLock != null) {
            try {
                directoryLock.release();
            } catch (IOException e) {
                Loggers.RAFT.warn("Failed to release file log directory lock at " + root, e);
            } finally {
                directoryLock = null;
            }
        }
        if (lockChannel != null) {
            try {
                lockChannel.close();
            } catch (IOException e) {
                Loggers.RAFT.warn("Failed to close file log lock channel at " + root, e);
            } finally {
                lockChannel = null;
            }
        }
        recordCodec = null;
        manifest = null;
        root = null;
    }
    
}
