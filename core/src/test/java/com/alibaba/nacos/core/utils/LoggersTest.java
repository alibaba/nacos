/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class LoggersTest {
    
    @Test
    void loggersAreNotNull() {
        assertNotNull(Loggers.AUTH);
        assertNotNull(Loggers.CORE);
        assertNotNull(Loggers.RAFT);
        assertNotNull(Loggers.DISTRO);
        assertNotNull(Loggers.CLUSTER);
        assertNotNull(Loggers.REMOTE);
        assertNotNull(Loggers.REMOTE_PUSH);
        assertNotNull(Loggers.REMOTE_DIGEST);
    }
    
    @Test
    void setLogLevelCoreAuth() {
        Loggers.setLogLevel("core-auth", "DEBUG");
    }
    
    @Test
    void setLogLevelCore() {
        Loggers.setLogLevel("core", "DEBUG");
    }
    
    @Test
    void setLogLevelCoreRaft() {
        Loggers.setLogLevel("core-raft", "DEBUG");
    }
    
    @Test
    void setLogLevelCoreDistro() {
        Loggers.setLogLevel("core-distro", "DEBUG");
    }
    
    @Test
    void setLogLevelCoreCluster() {
        Loggers.setLogLevel("core-cluster", "DEBUG");
    }
    
    @Test
    void setLogLevelDefaultBranch() {
        Loggers.setLogLevel("other-name", "INFO");
    }
}
