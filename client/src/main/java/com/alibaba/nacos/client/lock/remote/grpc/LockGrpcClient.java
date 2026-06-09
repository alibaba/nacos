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

package com.alibaba.nacos.client.lock.remote.grpc;

import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.api.ability.constant.AbilityStatus;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.lock.common.LockNotificationType;
import com.alibaba.nacos.api.lock.constant.PropertyConstants;
import com.alibaba.nacos.api.lock.model.LockInstance;
import com.alibaba.nacos.api.lock.model.LockResult;
import com.alibaba.nacos.api.lock.remote.AbstractLockRequest;
import com.alibaba.nacos.api.lock.remote.LockOperationEnum;
import com.alibaba.nacos.api.lock.remote.request.LockNotificationRequest;
import com.alibaba.nacos.api.lock.remote.request.LockOperationRequest;
import com.alibaba.nacos.api.lock.remote.response.LockNotificationResponse;
import com.alibaba.nacos.api.lock.remote.response.LockOperationResponse;
import com.alibaba.nacos.api.remote.RemoteConstants;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.lock.remote.AbstractLockClient;
import com.alibaba.nacos.client.security.SecurityProxy;
import com.alibaba.nacos.client.utils.AppNameUtils;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.remote.client.RpcClientFactory;
import com.alibaba.nacos.common.remote.client.RpcClientTlsConfigFactory;
import com.alibaba.nacos.common.remote.client.Connection;
import com.alibaba.nacos.common.remote.client.ServerListFactory;
import com.alibaba.nacos.common.remote.client.ServerRequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * lock grpc client.
 *
 * @author 985492783@qq.com
 * @description LockGrpcClient
 * @date 2023/6/28 17:35
 */
public class LockGrpcClient extends AbstractLockClient {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(LockGrpcClient.class);
    
    private static final long DEFAULT_REQUEST_TIMEOUT_MS = 15000L;
    
    private static final long DEFAULT_NOTIFICATION_POLL_MS = 2000L;
    
    private final String uuid;
    
    private final Long requestTimeout;
    
    private final RpcClient rpcClient;
    
    private final ConcurrentHashMap<String, CompletableFuture<LockNotificationType>> notificationFutures =
        new ConcurrentHashMap<>();
    
    /**
     * Tracks whether the client has been shut down. When true, pending notification futures
     * are completed exceptionally to prevent thread leaks from indefinite blocking.
     */
    private final AtomicBoolean closed = new AtomicBoolean(false);
    
    public LockGrpcClient(NacosClientProperties properties, ServerListFactory serverListFactory,
        SecurityProxy securityProxy) throws NacosException {
        super(securityProxy);
        this.uuid = UUID.randomUUID().toString();
        this.requestTimeout = Long
            .parseLong(properties.getProperty(PropertyConstants.LOCK_REQUEST_TIMEOUT, "-1"));
        Map<String, String> labels = new HashMap<>();
        labels.put(RemoteConstants.LABEL_SOURCE, RemoteConstants.LABEL_SOURCE_SDK);
        labels.put(RemoteConstants.LABEL_MODULE, RemoteConstants.LABEL_MODULE_LOCK);
        labels.put(Constants.APPNAME, AppNameUtils.getAppName());
        this.rpcClient = RpcClientFactory.createClient(uuid, ConnectionType.GRPC, labels,
            RpcClientTlsConfigFactory.getInstance().createSdkConfig(properties.asProperties()));
        registerServerRequestHandler();
        start(serverListFactory);
    }
    
    private void registerServerRequestHandler() {
        rpcClient.registerServerRequestHandler(new ServerRequestHandler() {
            
            @Override
            public Response requestReply(Request request, Connection connection) {
                if (request instanceof LockNotificationRequest) {
                    LockNotificationRequest notification = (LockNotificationRequest) request;
                    String waitKey =
                        buildWaitKey(notification.getLockKey(), notification.getOwner());
                    CompletableFuture<LockNotificationType> future =
                        notificationFutures.get(waitKey);
                    if (future != null) {
                        future.complete(notification.getNotificationType());
                    }
                    return new LockNotificationResponse();
                }
                return null;
            }
        });
    }
    
    private void start(ServerListFactory serverListFactory) throws NacosException {
        rpcClient.serverListFactory(serverListFactory);
        rpcClient.start();
    }
    
    @Override
    public Boolean lock(LockInstance instance) throws NacosException {
        if (!isAbilitySupportedByServer()) {
            throw new NacosRuntimeException(NacosException.SERVER_NOT_IMPLEMENTED,
                "Request Nacos server version is too low, not support lock feature.");
        }
        // Defensive copy to avoid mutating the caller's object.
        LockInstance copy = new LockInstance();
        copy.setKey(instance.getKey());
        copy.setLockType(instance.getLockType());
        copy.setOwner(instance.getOwner());
        copy.setExpiredTime(instance.getExpiredTime());
        copy.setWaitTime(instance.getWaitTime());
        copy.setParams(instance.getParams());
        
        long waitTime = copy.getWaitTime();
        if (waitTime == 0) {
            waitTime = DEFAULT_REQUEST_TIMEOUT_MS;
        }
        boolean useWaitQueue = waitTime > 0;
        long deadline = useWaitQueue ? System.currentTimeMillis() + waitTime : 0;
        boolean acquired = false;
        
        // Register for notification ONCE before the loop to avoid TOCTOU race condition.
        // If we register inside the loop, each call replaces the future, and a notification
        // that arrives between two registrations would be lost, causing indefinite blocking.
        if (useWaitQueue) {
            registerForNotification(copy.getKey(), copy.getOwner());
        }
        boolean firstAttempt = true;
        try {
            while (true) {
                if (closed.get()) {
                    return false;
                }
                // After the first failed attempt, mark as waiter retry so the server
                // knows this request comes from the wait queue (FIFO enforcement).
                if (!firstAttempt) {
                    copy.setWaiterRetry(true);
                }
                LockOperationRequest request = new LockOperationRequest();
                request.setLockInstance(copy);
                request.setLockOperationEnum(LockOperationEnum.ACQUIRE);
                LockOperationResponse response =
                    requestToServer(request, LockOperationResponse.class);
                LockResult lockResult = response.getLockResult();
                if (lockResult != null) {
                    acquired = lockResult.isSuccess();
                } else {
                    Boolean raw = (Boolean) response.getResult();
                    acquired = raw != null && raw;
                }
                if (acquired) {
                    return true;
                }
                firstAttempt = false;
                if (!useWaitQueue) {
                    return false;
                }
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return false;
                }
                try {
                    long pollTimeout = Math.min(remaining,
                        DEFAULT_NOTIFICATION_POLL_MS + ThreadLocalRandom.current().nextInt(200));
                    LockNotificationType notificationType = waitForNotification(
                        copy.getKey(), copy.getOwner(), pollTimeout);
                    if (notificationType == LockNotificationType.TIMEOUT) {
                        return false;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        } finally {
            if (useWaitQueue) {
                if (acquired) {
                    cancelWait(copy.getKey(), copy.getOwner());
                } else {
                    cancelWait(copy.getKey(), copy.getLockType(), copy.getOwner());
                }
            }
        }
    }
    
    @Override
    public Boolean unLock(LockInstance instance) throws NacosException {
        LockResult result = unLockWithResult(instance);
        return result.isSuccess();
    }
    
    /**
     * Release lock and return structured result with remaining reentrant count.
     *
     * @param instance lock instance with owner
     * @return structured lock result
     * @throws NacosException on server error
     */
    public LockResult unLockWithResult(LockInstance instance) throws NacosException {
        if (!isAbilitySupportedByServer()) {
            throw new NacosRuntimeException(NacosException.SERVER_NOT_IMPLEMENTED,
                "Request Nacos server version is too low, not support lock feature.");
        }
        LockOperationRequest request = new LockOperationRequest();
        request.setLockInstance(instance);
        request.setLockOperationEnum(LockOperationEnum.RELEASE);
        LockOperationResponse response = requestToServer(request, LockOperationResponse.class);
        LockResult lockResult = response.getLockResult();
        if (lockResult != null) {
            return lockResult;
        }
        Boolean raw = (Boolean) response.getResult();
        return new LockResult(raw != null && raw);
    }
    
    /**
     * Renew lock lease time (watchdog heartbeat).
     *
     * @param instance lock instance with owner
     * @return true if renewed successfully
     * @throws NacosException on server error
     */
    public Boolean renew(LockInstance instance) throws NacosException {
        LockResult result = renewWithResult(instance);
        return result.isSuccess();
    }
    
    /**
     * Renew lock lease time and return structured result.
     *
     * @param instance lock instance with owner
     * @return structured lock result
     * @throws NacosException on server error
     */
    public LockResult renewWithResult(LockInstance instance) throws NacosException {
        if (!isAbilitySupportedByServer()) {
            throw new NacosRuntimeException(NacosException.SERVER_NOT_IMPLEMENTED,
                "Request Nacos server version is too low, not support lock feature.");
        }
        LockOperationRequest request = new LockOperationRequest();
        request.setLockInstance(instance);
        request.setLockOperationEnum(LockOperationEnum.RENEW);
        LockOperationResponse response = requestToServer(request, LockOperationResponse.class);
        LockResult lockResult = response.getLockResult();
        if (lockResult != null) {
            return lockResult;
        }
        Boolean raw = (Boolean) response.getResult();
        return new LockResult(raw != null && raw);
    }
    
    /**
     * Acquire lock and return structured result with reentrant count or error details.
     *
     * @param instance lock instance with owner
     * @return structured lock result
     * @throws NacosException on server error
     */
    public LockResult lockWithResult(LockInstance instance) throws NacosException {
        if (!isAbilitySupportedByServer()) {
            throw new NacosRuntimeException(NacosException.SERVER_NOT_IMPLEMENTED,
                "Request Nacos server version is too low, not support lock feature.");
        }
        LockOperationRequest request = new LockOperationRequest();
        request.setLockInstance(instance);
        request.setLockOperationEnum(LockOperationEnum.ACQUIRE);
        LockOperationResponse response = requestToServer(request, LockOperationResponse.class);
        LockResult lockResult = response.getLockResult();
        if (lockResult != null) {
            return lockResult;
        }
        Boolean raw = (Boolean) response.getResult();
        return new LockResult(raw != null && raw);
    }
    
    /**
     * Block until a server push notification arrives for the specified lock and owner.
     *
     * <p>If an existing pending future is already registered for this waitKey, it is reused
     * instead of creating a new one. This prevents race conditions where a notification
     * arrives between two consecutive waitForNotification calls but is lost because the
     * first future was replaced.
     *
     * @param lockKey lock key
     * @param owner lock owner identifier
     * @param timeoutMs max wait time in milliseconds, 0 means wait indefinitely
     * @return notification type, or null on timeout
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public LockNotificationType waitForNotification(String lockKey, String owner, long timeoutMs)
        throws InterruptedException {
        if (closed.get()) {
            return LockNotificationType.TIMEOUT;
        }
        String waitKey = buildWaitKey(lockKey, owner);
        CompletableFuture<LockNotificationType> future =
            notificationFutures.compute(waitKey, (k, existing) -> {
                if (existing != null && !existing.isDone()) {
                    return existing;
                }
                return new CompletableFuture<>();
            });
        try {
            if (timeoutMs <= 0) {
                return future.get();
            }
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            return null;
        } catch (ExecutionException e) {
            LOGGER.warn("Notification wait failed for key={}, owner={}", lockKey, owner, e);
            return null;
        }
    }
    
    /**
     * Register for push notification BEFORE sending lock request.
     *
     * <p>This must be called before {@link #lockWithResult(LockInstance)} to avoid
     * a race condition where the server sends the push notification before the
     * client has registered the future to receive it.
     *
     * @param lockKey lock key
     * @param owner lock owner identifier
     */
    public void registerForNotification(String lockKey, String owner) {
        if (closed.get()) {
            return;
        }
        String waitKey = buildWaitKey(lockKey, owner);
        CompletableFuture<LockNotificationType> oldFuture =
            notificationFutures.put(waitKey, new CompletableFuture<>());
        if (oldFuture != null && !oldFuture.isDone()) {
            oldFuture.complete(LockNotificationType.AVAILABLE);
        }
    }
    
    /**
     * Cancel a pending notification wait for the specified lock and owner.
     *
     * @param lockKey lock key
     * @param owner lock owner identifier
     */
    public void cancelWait(String lockKey, String owner) {
        String waitKey = buildWaitKey(lockKey, owner);
        CompletableFuture<LockNotificationType> future = notificationFutures.remove(waitKey);
        if (future != null) {
            future.cancel(false);
        }
    }
    
    /**
     * Cancel a pending notification wait locally and remove the server-side wait queue entry.
     *
     * @param lockKey lock key
     * @param lockType lock type
     * @param owner lock owner identifier
     */
    public void cancelWait(String lockKey, String lockType, String owner) {
        cancelWait(lockKey, owner);
        try {
            LockInstance instance = new LockInstance();
            instance.setKey(lockKey);
            instance.setLockType(lockType);
            instance.setOwner(owner);
            cancelWaitWithResult(instance);
        } catch (Exception e) {
            LOGGER.warn("Failed to cancel server-side lock waiter, key={}, owner={}", lockKey,
                owner, e);
        }
    }
    
    /**
     * Cancel a pending server-side wait queue entry.
     *
     * @param instance lock instance identifying the waiter
     * @return structured cancel result
     * @throws NacosException on server error
     */
    public LockResult cancelWaitWithResult(LockInstance instance) throws NacosException {
        if (!isAbilitySupportedByServer()) {
            throw new NacosRuntimeException(NacosException.SERVER_NOT_IMPLEMENTED,
                "Request Nacos server version is too low, not support lock feature.");
        }
        LockOperationRequest request = new LockOperationRequest();
        request.setLockInstance(instance);
        request.setLockOperationEnum(LockOperationEnum.CANCEL_WAIT);
        LockOperationResponse response = requestToServer(request, LockOperationResponse.class);
        LockResult lockResult = response.getLockResult();
        if (lockResult != null) {
            return lockResult;
        }
        Boolean raw = (Boolean) response.getResult();
        return new LockResult(raw != null && raw);
    }
    
    private String buildWaitKey(String lockKey, String owner) {
        return lockKey + ":" + owner;
    }
    
    @Override
    public void shutdown() throws NacosException {
        if (closed.compareAndSet(false, true)) {
            // Complete all pending futures exceptionally to unblock waiting threads
            CancellationException ex = new CancellationException("Client shutdown");
            notificationFutures.values().forEach(f -> f.completeExceptionally(ex));
            notificationFutures.clear();
            rpcClient.shutdown();
        }
    }
    
    private <T extends Response> T requestToServer(AbstractLockRequest request,
        Class<T> responseClass)
        throws NacosException {
        try {
            request.putAllHeader(getSecurityHeaders());
            long timeout = requestTimeout > 0 ? requestTimeout : 15000L;
            Response response = rpcClient.request(request, timeout);
            if (ResponseCode.SUCCESS.getCode() != response.getResultCode()) {
                throw new NacosException(response.getErrorCode(), response.getMessage());
            }
            if (responseClass.isAssignableFrom(response.getClass())) {
                return (T) response;
            }
        } catch (NacosException e) {
            throw e;
        } catch (Exception e) {
            throw new NacosException(NacosException.SERVER_ERROR, "Request nacos server failed: ",
                e);
        }
        throw new NacosException(NacosException.SERVER_ERROR, "Server return invalid response");
    }
    
    private boolean isAbilitySupportedByServer() {
        return rpcClient.getConnectionAbility(
            AbilityKey.SERVER_DISTRIBUTED_LOCK) == AbilityStatus.SUPPORTED;
    }
}
