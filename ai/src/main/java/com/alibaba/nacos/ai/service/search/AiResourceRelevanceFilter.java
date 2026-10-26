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

package com.alibaba.nacos.ai.service.search;

import com.alibaba.nacos.ai.config.ConditionalOnAiResourceSearchEnabled;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.monitor.NacosMeterRegistryCenter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded online evaluation. Any batch failure discards the entire request's scores.
 * @author nacos
 */
@Service
@ConditionalOnAiResourceSearchEnabled
public class AiResourceRelevanceFilter {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AiResourceRelevanceFilter.class);
    private final AiResourceRelevanceConfiguration configuration;
    private final AiResourceRelevanceProvider provider;
    private final ThreadPoolExecutor executor;
    private final Semaphore permits;
    private final AtomicInteger activeBatches = new AtomicInteger();
    
    private final AtomicLong lastWarning = new AtomicLong();
    
    public AiResourceRelevanceFilter(AiResourceRelevanceConfiguration configuration,
        List<AiResourceRelevanceProvider> providers) {
        this.configuration = configuration;
        Map<String, AiResourceRelevanceProvider> registry = new HashMap<>();
        for (AiResourceRelevanceProvider item : providers) {
            if (registry.putIfAbsent(item.name(), item) != null) {
                throw new IllegalArgumentException("Duplicate relevance provider: " + item.name());
            }
        }
        provider = registry.get(configuration.provider);
        if (configuration.enabled && (provider == null || provider.maxBatchSize() <= 0
            || provider.maxInputBytes() <= 0)) {
            throw new IllegalArgumentException(
                "Invalid relevance provider: " + configuration.provider);
        }
        permits = new Semaphore(configuration.maxConcurrency);
        executor = new ThreadPoolExecutor(configuration.maxConcurrency,
            configuration.maxConcurrency,
            30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(configuration.maxConcurrency), task -> {
                Thread thread = new Thread(task, "nacos-ai-relevance");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        try {
            NacosMeterRegistryCenter.gauge(NacosMeterRegistryCenter.CORE_STABLE_REGISTRY,
                "nacos_ai_relevance_active_batches",
                List.of(new io.micrometer.core.instrument.ImmutableTag(
                    "provider", configuration.provider)),
                activeBatches);
        } catch (RuntimeException ignored) {
            // Metrics initialization must not prevent server startup.
        }
    }
    
    public boolean enabled() {
        return configuration.enabled;
    }
    
    public int maxCandidates() {
        return configuration.maxCandidates;
    }
    
    public String fingerprint() {
        return configuration.fingerprint(provider.contractVersion());
    }
    
    /**
     * Evaluate one candidate window under one deadline; callers retain unassessed candidates.
     * @param namespace namespace for diagnostics only
     * @param query query text
     * @param candidates payloads with unique request-local IDs
     * @return all validated scores or an empty fallback result
     */
    public Evaluation evaluate(String namespace, String query,
        List<Map<String, String>> candidates) {
        return evaluate(namespace, query, candidates, System.nanoTime());
    }
    
    Evaluation evaluate(String namespace, String query, List<Map<String, String>> candidates,
        long started) {
        long deadline = started + TimeUnit.MILLISECONDS.toNanos(configuration.timeoutMs);
        String requestId = UUID.randomUUID().toString();
        List<Future<Map<String, Double>>> futures = new ArrayList<>();
        int batches = 0;
        String outcome = "bypassed";
        String reason = "no-candidates";
        int assessed = 0;
        int removed = 0;
        try {
            List<List<Map<String, String>>> groups = batches(query, candidates);
            batches = groups.size();
            if (groups.isEmpty()) {
                LOGGER.debug(
                    "Relevance bypassed: requestId={}, namespace={}, reason=no-assessable-candidates",
                    requestId, namespace);
                return new Evaluation(Map.of(), "bypassed");
            }
            BlockingQueue<Future<Map<String, Double>>> completion =
                new ArrayBlockingQueue<>(groups.size());
            int submitted = 0;
            int completed = 0;
            Map<String, Double> scores = new LinkedHashMap<>();
            while (completed < groups.size()) {
                while (submitted < groups.size()
                    && submitted - completed < configuration.batchConcurrency) {
                    List<Map<String, String>> batch = groups.get(submitted++);
                    int batchId = submitted;
                    futures.add(
                        submitBatch(() -> evaluateBatch(query, batch, deadline, requestId, batchId),
                            completion));
                }
                long remaining = deadline - System.nanoTime();
                Future<Map<String, Double>> done =
                    remaining <= 0 ? null : completion.poll(remaining, TimeUnit.NANOSECONDS);
                if (done == null) {
                    throw new TimeoutException("Relevance deadline exceeded");
                }
                scores.putAll(done.get());
                completed++;
            }
            if (System.nanoTime() >= deadline) {
                throw new TimeoutException("Relevance deadline exceeded");
            }
            String status = scores.size() == candidates.size() ? "applied" : "partial";
            outcome = status;
            reason = "success";
            assessed = scores.size();
            removed = (int) scores.values().stream().filter(score -> !keep(score)).count();
            LOGGER.debug(
                "Relevance completed: requestId={}, namespace={}, provider={}, fingerprint={}, "
                    + "status={}, batches={}, candidates={}, assessed={}, removed={}, elapsedMs={}",
                requestId, namespace,
                configuration.provider, fingerprint(), status, batches, candidates.size(),
                scores.size(), removed,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            return new Evaluation(scores, status);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            // Untrusted providers can embed payloads in exception messages; log only the failure type.
            Throwable cause = e.getCause() == null ? e : e.getCause();
            reason = failureReason(cause);
            int httpStatus = cause instanceof AiResourceModelHttpClient.HttpFailure
                ? ((AiResourceModelHttpClient.HttpFailure) cause).status : 0;
            String remoteId = cause instanceof AiResourceModelHttpClient.HttpFailure
                ? ((AiResourceModelHttpClient.HttpFailure) cause).requestId : "unknown";
            long now = System.nanoTime();
            long previous = lastWarning.get();
            if ((previous == 0 || now - previous > TimeUnit.SECONDS.toNanos(30))
                && lastWarning.compareAndSet(previous, now)) {
                Exception diagnostic = new Exception(
                    "Relevance provider failure: " + cause.getClass().getSimpleName());
                diagnostic.setStackTrace(cause.getStackTrace());
                LOGGER.warn(
                    "Relevance bypassed: requestId={}, namespace={}, provider={}, batches={}, "
                        + "candidates={}, reason={}, httpStatus={}, remoteRequestId={}, elapsedMs={}",
                    requestId, namespace, configuration.provider,
                    batches, candidates.size(), reason, httpStatus, remoteId,
                    TimeUnit.NANOSECONDS.toMillis(now - started), diagnostic);
            }
            return new Evaluation(Map.of(), "bypassed");
        } finally {
            record(outcome, reason, started, batches, candidates.size(), assessed, removed);
            for (Future<?> future : futures) {
                if (!future.isDone()) {
                    future.cancel(true);
                }
            }
        }
    }
    
    private String failureReason(Throwable cause) {
        if (cause instanceof RejectedExecutionException) {
            return "busy";
        }
        if (cause instanceof TimeoutException
            || cause instanceof java.net.http.HttpTimeoutException) {
            return "timeout";
        }
        if (cause instanceof InterruptedException) {
            return "interrupted";
        }
        if (cause instanceof AiResourceModelHttpClient.HttpFailure) {
            return "http";
        }
        return cause instanceof java.io.IOException ? "transport" : "invalid-result";
    }
    
    private Future<Map<String, Double>> submitBatch(Callable<Map<String, Double>> callable,
        BlockingQueue<Future<Map<String, Double>>> completion) {
        if (!permits.tryAcquire()) {
            throw new RejectedExecutionException("Relevance node capacity exhausted");
        }
        BatchTask task = new BatchTask(callable, completion);
        try {
            executor.execute(task);
        } catch (RejectedExecutionException e) {
            task.cancel(false);
            throw e;
        }
        return task;
    }
    
    private final class BatchTask extends FutureTask<Map<String, Double>> {
        
        // Pending=0, running=1, cancelled-before-start=2. Running cancellation never releases capacity.
        private final AtomicInteger state = new AtomicInteger();
        private final BlockingQueue<Future<Map<String, Double>>> completion;
        
        BatchTask(Callable<Map<String, Double>> callable,
            BlockingQueue<Future<Map<String, Double>>> completion) {
            super(callable);
            this.completion = completion;
        }
        
        @Override
        public void run() {
            if (!state.compareAndSet(0, 1)) {
                return;
            }
            try {
                super.run();
            } finally {
                permits.release();
                completion.add(this);
            }
        }
        
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean cancelled = super.cancel(mayInterruptIfRunning);
            if (cancelled && state.compareAndSet(0, 2)) {
                executor.remove(this);
                permits.release();
                completion.add(this);
            }
            return cancelled;
        }
    }
    
    private void record(String status, String reason, long started, int batches, int candidates,
        int assessed, int removed) {
        try {
            String registry = NacosMeterRegistryCenter.CORE_STABLE_REGISTRY;
            Timer timer = NacosMeterRegistryCenter.timer(registry, "nacos_ai_relevance_duration",
                "provider", configuration.provider, "status", status, "reason", reason);
            if (timer != null) {
                timer.record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
            }
            for (Map.Entry<String, Integer> entry : Map
                .of("batches", batches, "candidates", candidates,
                    "assessed", assessed, "removed", removed)
                .entrySet()) {
                Counter counter =
                    NacosMeterRegistryCenter.counter(registry, "nacos_ai_relevance_items",
                        "provider", configuration.provider, "kind", entry.getKey());
                if (counter != null) {
                    counter.increment(entry.getValue());
                }
            }
        } catch (RuntimeException ignored) {
            // Follow the existing AI metrics convention: telemetry cannot affect search availability.
        }
    }
    
    private Map<String, Double> evaluateBatch(String query, List<Map<String, String>> batch,
        long deadline, String requestId, int batchId) throws Exception {
        if (System.nanoTime() >= deadline) {
            throw new TimeoutException("Relevance deadline exceeded before HTTP");
        }
        long started = System.nanoTime();
        activeBatches.incrementAndGet();
        try {
            Map<String, Double> scores =
                provider.evaluateBatch(query, batch, configuration, deadline);
            if (scores == null || scores.size() != batch.size()) {
                throw new IllegalStateException("Invalid relevance answer set");
            }
            for (Map<String, String> item : batch) {
                Double score = scores.get(item.get("id"));
                if (score == null || !Double.isFinite(score) || score < 0 || score > 1) {
                    throw new IllegalStateException("Invalid relevance score");
                }
            }
            return scores;
        } finally {
            activeBatches.decrementAndGet();
            LOGGER.debug("Relevance batch terminated: requestId={}, batchId={}, elapsedMs={}",
                requestId, batchId, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        }
    }
    
    private List<List<Map<String, String>>> batches(String query,
        List<Map<String, String>> candidates) {
        List<List<Map<String, String>>> groups = new ArrayList<>();
        List<Map<String, String>> current = new ArrayList<>();
        // Reserve space for provider instructions, JSON framing and escaped query text.
        int queryBytes = JacksonUtils.toJson(query).getBytes(StandardCharsets.UTF_8).length + 1024;
        int bytes = queryBytes;
        int batchSize = Math.min(configuration.batchSize, provider.maxBatchSize());
        for (Map<String, String> candidate : candidates) {
            int size =
                JacksonUtils.toJson(candidate).getBytes(StandardCharsets.UTF_8).length + 1024;
            if ((long) queryBytes + size > provider.maxInputBytes()) {
                continue;
            }
            if (!current.isEmpty() && (current.size() == batchSize
                || (long) bytes + size > provider.maxInputBytes())) {
                groups.add(current);
                current = new ArrayList<>();
                bytes = queryBytes;
            }
            current.add(candidate);
            bytes += size;
        }
        if (!current.isEmpty()) {
            groups.add(current);
        }
        return groups;
    }
    
    public boolean keep(Double score) {
        return score == null || score >= configuration.threshold;
    }
    
    /** Stop online evaluation and cancel pending batches. */
    @PreDestroy
    public void close() {
        for (Runnable task : executor.shutdownNow()) {
            ((Future<?>) task).cancel(true);
        }
    }
    
    /** Validated outcome; an absent score means retain without assessment. */
    public static class Evaluation {
        
        private final Map<String, Double> scores;
        private final String status;
        
        Evaluation(Map<String, Double> scores, String status) {
            this.scores = Map.copyOf(scores);
            this.status = status;
        }
        
        public Map<String, Double> getScores() {
            return scores;
        }
        
        public String getStatus() {
            return status;
        }
    }
}
