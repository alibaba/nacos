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

package com.alibaba.nacos.client.lock;

import com.alibaba.nacos.api.lock.model.LockResult;
import com.alibaba.nacos.client.lock.remote.grpc.LockGrpcClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * 验证 lockInterruptibly() 中断路径正确清理服务端等待队列。
 * @author DHX
 * @date 2026/06/06
 */
@ExtendWith(MockitoExtension.class)
class NacosLockInterruptTest {
    
    @Mock
    private LockGrpcClient grpcClient;
    
    private NacosLock nacosLock;
    
    @BeforeEach
    void setUp() {
        NacosLockWatchdog watchdog = new NacosLockWatchdog();
        nacosLock = new NacosLock("test-key", "mutex", grpcClient, watchdog, "test-client");
    }
    
    /**
     * 验证：lockInterruptibly() 中断路径调用 cancelWait(3-arg) 清理服务端等待队列，
     * 且中断不被阻塞。
     *
     * <p>由于 @Mock 替换了方法体（CompletableFuture.runAsync 不会执行），
     * 此测试验证中断路径调用了正确的 cancelWait 重载。
     */
    @Test
    void testInterruptCallsServerSideCancelWait() throws Exception {
        // lockWithResult 返回 waiting（锁被别人持有，客户端进入等待队列）
        doAnswer(invocation -> LockResult.waiting(0))
            .when(grpcClient).lockWithResult(any());
        
        // waitForNotification 用 Thread.sleep 真正阻塞，直到线程被中断
        doAnswer(invocation -> {
            Thread.sleep(60000);
            return null;
        }).when(grpcClient).waitForNotification(anyString(), anyString(), anyLong());
        
        // cancelWait(key, owner) 仅清理本地 future（lenient: 可能不被调用）
        lenient().doAnswer(invocation -> null)
            .when(grpcClient).cancelWait(anyString(), anyString());
        
        // cancelWait(key, lockType, owner) 异步清理服务端（mock 下同步执行）
        doAnswer(invocation -> null)
            .when(grpcClient).cancelWait(anyString(), anyString(), anyString());
        
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean(false);
        
        // 在子线程中调用 lockInterruptibly()
        Thread t = new Thread(() -> {
            try {
                started.countDown();
                nacosLock.lockInterruptibly();
            } catch (InterruptedException e) {
                interrupted.set(true);
            }
        });
        
        t.start();
        started.await();
        // 等待子线程进入 waitForNotification 阻塞
        Thread.sleep(200);
        
        // 中断子线程
        t.interrupt();
        t.join(5000);
        
        // 子线程应该已经收到中断
        assertTrue(interrupted.get(), "线程应收到 InterruptedException");
        
        // 验证中断路径调用了 cancelWait(3-arg) 清理服务端等待队列
        verify(grpcClient, timeout(3000).atLeastOnce())
            .cancelWait(anyString(), anyString(), anyString());
    }
}
