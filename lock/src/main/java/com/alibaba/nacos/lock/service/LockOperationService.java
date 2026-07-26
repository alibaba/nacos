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

package com.alibaba.nacos.lock.service;

import com.alibaba.nacos.api.lock.model.LockInstance;
import com.alibaba.nacos.api.lock.model.LockResult;

/**
 * lock operator service.
 *
 * @author 985492783@qq.com
 * @date 2023/6/28 2:38
 */
public interface LockOperationService {
    
    /**
     * Acquire lock via Raft consensus.
     *
     * @param lockInstance lock instance with owner
     * @param connectionId gRPC connection ID (for push notifications)
     * @return structured lock result
     */
    LockResult lock(LockInstance lockInstance, String connectionId);
    
    /**
     * Release lock via Raft consensus.
     *
     * @param lockInstance lock instance with owner
     * @return structured lock result with remaining reentrant count
     */
    LockResult unLock(LockInstance lockInstance);
    
    /**
     * Renew lock lease time via Raft consensus (watchdog heartbeat).
     *
     * @param lockInstance lock instance with owner and new expiry
     * @return true if renewed successfully
     */
    Boolean renew(LockInstance lockInstance);
    
    /**
     * Cancel a pending wait queue entry via Raft consensus.
     *
     * @param lockInstance lock instance with owner
     * @param connectionId gRPC connection ID of the waiter
     * @return structured cancel result
     */
    LockResult cancelWait(LockInstance lockInstance, String connectionId);
    
    /**
     * Expire lock via Raft consensus.
     *
     * <p>This method atomically checks and expires a lock inside the Raft onApply() path,
     * avoiding TOCTOU races between the expire scanner and concurrent renewals.
     *
     * @param lockInstance lock instance with owner
     * @return structured lock result
     */
    LockResult expire(LockInstance lockInstance);
    
    /**
     * Force release all locks held by the specified connection and clean up wait queue entries.
     *
     * <p>This method is called when a client disconnects to ensure locks are not
     * held indefinitely by dead connections.
     *
     * @param connectionId the gRPC connection ID of the disconnected client
     */
    void releaseLocksByConnection(String connectionId);
}
