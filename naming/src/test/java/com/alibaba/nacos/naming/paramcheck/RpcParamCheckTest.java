/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.paramcheck;

import com.alibaba.nacos.api.naming.remote.request.InstanceRequest;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.core.remote.grpc.RemoteParamCheckFilter;
import com.alibaba.nacos.naming.remote.rpc.handler.InstanceRequestHandler;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * RpcParamCheckTest.
 *
 * @author 985492783@qq.com
 * @date 2023/11/7 21:44
 */
@ExtendWith(MockitoExtension.class)
class RpcParamCheckTest {
    
    @Test
    void testFilter() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        MockedStatic<EnvUtil> mockedStatic = Mockito.mockStatic(EnvUtil.class);
        mockedStatic.when(() -> EnvUtil.getProperty(Mockito.any(), Mockito.any(), Mockito.any())).thenAnswer((k) -> k.getArgument(2));
        RemoteParamCheckFilter filter = new RemoteParamCheckFilter();
        Method method = filter.getClass().getDeclaredMethod("filter", Request.class, RequestMeta.class, Class.class);
        method.setAccessible(true);
        InstanceRequest request = new InstanceRequest();
        request.setNamespace("test111");
        Response response = (Response) method.invoke(filter, request, null, InstanceRequestHandler.class);
        assertNull(response);
        request.setNamespace("test@@@");
        Response response2 = (Response) method.invoke(filter, request, null, InstanceRequestHandler.class);
        assertEquals(400, response2.getErrorCode());
        mockedStatic.close();
    }
}
