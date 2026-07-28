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

package com.alibaba.nacos.plugin.auth.impl.controller.v3;

import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.visibility.VisibilityGrantService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class VisibilityGrantControllerV3Test {
    
    @Test
    void grantShouldDelegateToService() throws Exception {
        VisibilityGrantService service = mock(VisibilityGrantService.class);
        VisibilityGrantControllerV3 controller = new VisibilityGrantControllerV3(service);
        
        Result<String> result = controller.grant("public", "skill", "demo-skill", "bob", "r");
        
        verify(service).grant("public", "skill", "demo-skill", "bob", "r");
        assertEquals("grant visibility permission ok!", result.getData());
    }
    
    @Test
    void revokeShouldDelegateToService() throws Exception {
        VisibilityGrantService service = mock(VisibilityGrantService.class);
        VisibilityGrantControllerV3 controller = new VisibilityGrantControllerV3(service);
        
        Result<String> result = controller.revoke("public", "skill", "demo-skill", "bob", "w");
        
        verify(service).revoke("public", "skill", "demo-skill", "bob", "w");
        assertEquals("revoke visibility permission ok!", result.getData());
    }
    
    @Test
    void grantAndRevokeShouldUseAdminApiSecuredMetadata() throws Exception {
        assertWriteAdminApiSecured(VisibilityGrantControllerV3.class.getDeclaredMethod("grant",
            String.class, String.class, String.class, String.class, String.class));
        assertWriteAdminApiSecured(VisibilityGrantControllerV3.class.getDeclaredMethod("revoke",
            String.class, String.class, String.class, String.class, String.class));
    }
    
    @Test
    void visibilityGrantControllerShouldUseNacosApiExceptionHandling() {
        assertNotNull(VisibilityGrantControllerV3.class.getAnnotation(NacosApi.class));
    }
    
    private void assertWriteAdminApiSecured(Method method) {
        Secured secured = method.getAnnotation(Secured.class);
        assertNotNull(secured);
        assertEquals(AuthConstants.VISIBILITY_RESOURCE, secured.resource());
        assertEquals(ActionTypes.WRITE, secured.action());
        assertEquals(ApiType.ADMIN_API, secured.apiType());
        assertArrayEquals(new String[] {Constants.Tag.ONLY_IDENTITY}, secured.tags());
    }
}
