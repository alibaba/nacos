/*
 *  Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.alibaba.nacos.naming.core.v2.cleaner;

import com.alibaba.nacos.common.utils.ConcurrentHashSet;
import com.alibaba.nacos.naming.core.v2.metadata.ExpiredMetadataInfo;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataManager;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataOperateService;
import com.alibaba.nacos.sys.env.EnvUtil;
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.env.MockEnvironment;

import java.util.Set;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ExpiredMetadataCleanerTest extends TestCase {

    private ExpiredMetadataCleaner expiredMetadataCleaner;

    @Mock
    private NamingMetadataManager metadataManagerMock;

    @Mock
    private NamingMetadataOperateService metadataOperateServiceMock;

    private Set<ExpiredMetadataInfo> set = new ConcurrentHashSet<>();

    @Mock
    private ExpiredMetadataInfo expiredMetadataInfoMock;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        EnvUtil.setEnvironment(new MockEnvironment());
        expiredMetadataCleaner = new ExpiredMetadataCleaner(metadataManagerMock, metadataOperateServiceMock);

        set.add(expiredMetadataInfoMock);

        when(metadataManagerMock.getExpiredMetadataInfos()).thenReturn(set);
        when(expiredMetadataInfoMock.getCreateTime()).thenReturn(0L);
        when(metadataManagerMock.containServiceMetadata(expiredMetadataInfoMock.getService())).thenReturn(true);
    }

    @Test
    public void testDoClean() {
        expiredMetadataCleaner.doClean();
        verify(metadataManagerMock).getExpiredMetadataInfos();
        verify(metadataOperateServiceMock).deleteServiceMetadata(expiredMetadataInfoMock.getService());
    }
}