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

package com.alibaba.nacos.core.distributed.raft.storage;

import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.entity.PeerId;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileRaftClusterDiagnosticsTest {
    
    @Test
    void timeoutReportsCurrentStateAndAcceptedRequest() {
        PeerId target = new PeerId("127.0.0.1", 12345);
        AtomicReference<String> state = new AtomicReference<>("term=10 applied=100");
        AssertionError failure = assertThrows(AssertionError.class,
            () -> FileRaftClusterTest.awaitLeadership(() -> {
                state.set("term=12 applied=105");
                return false;
            }, target, Status.OK(), state::get, 0));
        
        assertTrue(failure.getMessage().contains("target=" + target));
        assertTrue(failure.getMessage().contains("lastStatus=" + Status.OK()));
        assertTrue(failure.getMessage().contains("term=12 applied=105"));
        assertFalse(failure.getMessage().contains("term=10 applied=100"));
    }
    
    @Test
    void successfulLeadershipDoesNotCollectFailureDiagnostics() throws Exception {
        FileRaftClusterTest.awaitLeadership(() -> true, new PeerId("127.0.0.1", 12345),
            Status.OK(), () -> {
                throw new AssertionError("Successful waits must not collect failure diagnostics");
            }, 0);
    }
}
