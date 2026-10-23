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

package com.alibaba.nacos.client.ai.remote.capability;

import com.alibaba.nacos.api.exception.NacosException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.LongSupplier;

/**
 * Bounded target/identity cache with synchronous single-flight loading and no owned executors.
 *
 * @author Nacos
 */
public final class AiCapabilityCache {
    
    private static final int MAX_ENTRIES = 64;
    
    private static final long WAIT_MILLIS = 7000L;
    
    private final Map<String, Entry> entries = new LinkedHashMap<>(16, 0.75F, true);
    
    private final LongSupplier clock;
    
    private final long ttlNanos;
    
    private boolean closed;
    
    public AiCapabilityCache() {
        this(System::nanoTime, TimeUnit.SECONDS.toNanos(30));
    }
    
    AiCapabilityCache(LongSupplier clock, long ttlNanos) {
        this.clock = clock;
        this.ttlNanos = ttlNanos;
    }
    
    /**
     * Obtain evidence using the caller's executor. Failures are never cached.
     *
     * @param target full URL, including scheme and context path
     * @param identity identity headers used for this request
     * @param loader one bounded network attempt
     * @return capability evidence
     * @throws NacosException for request failure or lifecycle cancellation
     */
    public AiCapabilitySnapshot get(String target, Map<String, String> identity, Loader loader)
        throws NacosException {
        String key = key(target, identity);
        Entry entry;
        boolean owner = false;
        synchronized (this) {
            if (closed) {
                throw cancelled();
            }
            entry = entries.get(key);
            if (entry != null && entry.future.isDone()
                && clock.getAsLong() - entry.completedAt >= ttlNanos) {
                entries.remove(key);
                entry = null;
            }
            if (entry == null) {
                makeRoom();
                entry = new Entry();
                entries.put(key, entry);
                owner = true;
            }
        }
        if (owner) {
            try {
                AiCapabilitySnapshot result = loader.load();
                synchronized (this) {
                    entry.completedAt = clock.getAsLong();
                    entry.future.complete(result);
                }
            } catch (Exception e) {
                synchronized (this) {
                    entries.remove(key, entry);
                    entry.future.completeExceptionally(e);
                }
            }
        }
        try {
            return entry.future.get(WAIT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NacosException(NacosException.SERVER_ERROR,
                "AI capability request interrupted.", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof NacosException) {
                throw (NacosException) e.getCause();
            }
            throw new NacosException(NacosException.SERVER_ERROR,
                "AI capability request failed.", e.getCause());
        } catch (TimeoutException e) {
            throw new NacosException(NacosException.SERVER_ERROR,
                "Timed out waiting for AI capabilities.", e);
        }
    }
    
    private void makeRoom() throws NacosException {
        if (entries.size() < MAX_ENTRIES) {
            return;
        }
        Iterator<Entry> iterator = entries.values().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().future.isDone()) {
                iterator.remove();
                return;
            }
        }
        throw new NacosException(NacosException.OVER_THRESHOLD,
            "Too many concurrent AI capability requests.");
    }
    
    /**
     * Invalidate target evidence on reconnect without altering an instance's selected mode.
     */
    public synchronized void invalidate() {
        for (Entry entry : entries.values()) {
            entry.future.completeExceptionally(cancelled());
        }
        entries.clear();
    }
    
    /**
     * Cancel waiters and reject subsequent loads.
     */
    public synchronized void close() {
        closed = true;
        invalidate();
    }
    
    private static NacosException cancelled() {
        return new NacosException(NacosException.CLIENT_DISCONNECT,
            "AI capability request cancelled by lifecycle change.");
    }
    
    private static String key(String target, Map<String, String> identity) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateDigest(digest, target);
            for (Map.Entry<String, String> item : new TreeMap<>(identity).entrySet()) {
                updateDigest(digest, item.getKey());
                updateDigest(digest, item.getValue());
            }
            StringBuilder result = new StringBuilder();
            for (byte value : digest.digest()) {
                result.append(Character.forDigit((value >>> 4) & 15, 16));
                result.append(Character.forDigit(value & 15, 16));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime.", e);
        }
    }
    
    private static void updateDigest(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
        digest.update((byte) ':');
        digest.update(bytes);
    }
    
    public interface Loader {
        
        /**
         * Perform one bounded capability request.
         *
         * @return capability snapshot
         * @throws NacosException for request failure
         */
        AiCapabilitySnapshot load() throws NacosException;
    }
    
    private static final class Entry {
        
        private final CompletableFuture<AiCapabilitySnapshot> future = new CompletableFuture<>();
        
        private long completedAt;
    }
}
