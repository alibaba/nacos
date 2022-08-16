/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.controller.v2;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.console.model.Namespace;
import com.alibaba.nacos.console.model.vo.NamespaceVo;
import com.alibaba.nacos.console.service.NamespaceOperationService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * NamespaceControllerV2Test.
 * @author dongyafei
 * @date 2022/8/16
 */
@RunWith(MockitoJUnitRunner.class)
public class NamespaceControllerV2Test {
    
    private NamespaceControllerV2 namespaceControllerV2;
    
    @Mock
    private NamespaceOperationService namnamespaceOperationService;
    
    private static final String TEST_NAMESPACE_ID = "testId";
    
    private static final String TEST_NAMESPACE_NAME = "testName";
    
    private static final String TEST_NAMESPACE_DESC = "testDesc";
    
    @Before
    public void setUp() throws Exception {
        namespaceControllerV2 = new NamespaceControllerV2(namnamespaceOperationService);
    }
    
    @Test
    public void testGetNamespaceList() {
        Namespace namespace = new Namespace();
        namespace.setNamespace(TEST_NAMESPACE_ID);
        namespace.setNamespaceShowName(TEST_NAMESPACE_NAME);
        namespace.setNamespaceDesc(TEST_NAMESPACE_DESC);
        List<Namespace> namespaceList = Collections.singletonList(namespace);
        when(namnamespaceOperationService.getNamespaceList()).thenReturn(namespaceList);
        
        Result<List<Namespace>> actualResult = namespaceControllerV2.getNamespaceList();
        verify(namnamespaceOperationService).getNamespaceList();
        assertEquals(ErrorCode.SUCCESS.getCode(), actualResult.getCode());
        
        List<Namespace> actualList = actualResult.getData();
        Namespace actualNamespace = actualList.get(0);
        assertEquals(namespaceList.size(), actualList.size());
        assertEquals(namespace.getNamespace(), actualNamespace.getNamespace());
        assertEquals(namespace.getNamespaceShowName(), actualNamespace.getNamespaceShowName());
        assertEquals(namespace.getNamespaceDesc(), actualNamespace.getNamespaceDesc());
        
    }
    
    @Test
    public void testCreateNamespace() throws NacosException {
        when(namnamespaceOperationService
                .createNamespace(TEST_NAMESPACE_ID, TEST_NAMESPACE_NAME, TEST_NAMESPACE_DESC, true)).thenReturn(true);
        Result<Boolean> result = namespaceControllerV2
                .createNamespace(new NamespaceVo(TEST_NAMESPACE_ID, TEST_NAMESPACE_NAME, TEST_NAMESPACE_DESC));
        
        verify(namnamespaceOperationService).createNamespace(TEST_NAMESPACE_ID, TEST_NAMESPACE_NAME, TEST_NAMESPACE_DESC, true);
        
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(true, result.getData());
    }
    
    @Test
    public void testEditNamespace() throws NacosException {
        when(namnamespaceOperationService.editNamespace(TEST_NAMESPACE_ID, TEST_NAMESPACE_NAME, TEST_NAMESPACE_DESC)).thenReturn(true);
        Result<Boolean> result = namespaceControllerV2
                .editNamespace(new NamespaceVo(TEST_NAMESPACE_ID, TEST_NAMESPACE_NAME, TEST_NAMESPACE_DESC));
        
        verify(namnamespaceOperationService).editNamespace(TEST_NAMESPACE_ID, TEST_NAMESPACE_NAME, TEST_NAMESPACE_DESC);
        
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(true, result.getData());
    }
    
    @Test
    public void testDeleteNamespace() {
        when(namnamespaceOperationService.removeNamespace(TEST_NAMESPACE_ID)).thenReturn(true);
        Result<Boolean> result = namespaceControllerV2.deleteNamespace(TEST_NAMESPACE_ID);
        
        verify(namnamespaceOperationService).removeNamespace(TEST_NAMESPACE_ID);
        
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(true, result.getData());
    }
}
