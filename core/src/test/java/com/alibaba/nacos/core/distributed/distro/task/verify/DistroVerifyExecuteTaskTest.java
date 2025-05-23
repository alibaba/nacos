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

package com.alibaba.nacos.core.distributed.distro.task.verify;

import com.alibaba.nacos.core.distributed.distro.DistroConfig;
import com.alibaba.nacos.core.distributed.distro.component.DistroTransportAgent;
import com.alibaba.nacos.core.distributed.distro.entity.DistroData;
import com.alibaba.nacos.core.distributed.distro.entity.DistroKey;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.env.MockEnvironment;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

class DistroVerifyExecuteTaskTest {

    @InjectMocks
    private DistroVerifyExecuteTask distroVerifyExecuteTask;

    @Mock
    private DistroTransportAgent transportAgent;

    @Mock
    private DistroConfig distroConfig;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        EnvUtil.setEnvironment(new MockEnvironment());
        // 初始化测试数据
        List<DistroData> verifyData = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            DistroKey key = new DistroKey("testKey" + i, "testResource");
            DistroData data = new DistroData(key, ("data" + i).getBytes());
            verifyData.add(data);
        }

        // 创建被测任务实例
        distroVerifyExecuteTask = new DistroVerifyExecuteTask(
                transportAgent, verifyData, "targetServer", "testResource"
        );
    }

    @Test
    void testRunWithSmallBatchSize() throws InterruptedException {
        // 准备测试数据
        when(distroConfig.getVerifyBatchSize()).thenReturn(2);
        when(distroConfig.getVerifyIntervalMillis()).thenReturn(50L);
        try (MockedStatic<DistroConfig> mockedStatic = mockStatic(DistroConfig.class)) {
            mockedStatic.when(DistroConfig::getInstance).thenReturn(distroConfig);

            // 执行测试
            distroVerifyExecuteTask.run();

            // 验证全部被调用
            Thread.sleep(100L);
            verify(transportAgent, times(10)).syncVerifyData(any(), any());
        }
    }

    @Test
    void testRunWithLargeBatchSize() throws InterruptedException {
        // 准备测试数据
        when(distroConfig.getVerifyBatchSize()).thenReturn(20);
        when(distroConfig.getVerifyIntervalMillis()).thenReturn(50L);
        try (MockedStatic<DistroConfig> mockedStatic = mockStatic(DistroConfig.class)) {
            mockedStatic.when(DistroConfig::getInstance).thenReturn(distroConfig);

            // 执行测试
            distroVerifyExecuteTask.run();
            // 验证全部被调用
            Thread.sleep(100L);
            verify(transportAgent, times(10)).syncVerifyData(any(), any());
        }
    }
}
