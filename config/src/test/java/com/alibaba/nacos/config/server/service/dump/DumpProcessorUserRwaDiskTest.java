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

package com.alibaba.nacos.config.server.service.dump;

import com.alibaba.nacos.config.server.service.dump.disk.ConfigDiskService;
import com.alibaba.nacos.config.server.service.dump.disk.ConfigRawDiskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

@ExtendWith(MockitoExtension.class)
class DumpProcessorUserRwaDiskTest extends DumpProcessorTest {
    
    @BeforeEach
    public void init() throws Exception {
        super.init();
    }
    
    @Override
    protected ConfigDiskService createDiskService() {
        return new ConfigRawDiskService();
    }
    
    @AfterEach
    public void after() {
        super.after();
    }
    
    @Test
    public void testDumpNormalAndRemove() throws IOException {
        super.testDumpNormalAndRemove();
        
    }
    
    @Test
    public void testDumpBetaAndRemove() throws IOException {
        super.testDumpBetaAndRemove();
    }
    
    @Test
    public void testDumpTagAndRemove() throws IOException {
        super.testDumpTagAndRemove();
    }
}
