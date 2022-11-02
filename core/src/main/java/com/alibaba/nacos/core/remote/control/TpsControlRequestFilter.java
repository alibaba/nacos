/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.remote.control;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.core.remote.AbstractRequestFilter;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.plugin.control.ControlManagerFactory;
import com.alibaba.nacos.plugin.control.tps.TpsControlManager;
import com.alibaba.nacos.plugin.control.tps.key.MonitorKey;
import com.alibaba.nacos.plugin.control.tps.request.TpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * tps control point.
 *
 * @author liuzunfei
 * @version $Id: TpsControlRequestFilter.java, v 0.1 2021年01月09日 12:38 PM liuzunfei Exp $
 */
@Service
public class TpsControlRequestFilter extends AbstractRequestFilter {
    
    TpsControlManager tpsControlManager = ControlManagerFactory.getInstance().getTpsControlManager();
    
    @Override
    protected Response filter(Request request, RequestMeta meta, Class handlerClazz) {
        
        Method method = null;
        try {
            method = getHandleMethod(handlerClazz);
        } catch (NacosException e) {
            return null;
        }
        
        if (method.isAnnotationPresent(TpsControl.class) && TpsControlConfig.isTpsControlEnabled()) {
            
            TpsControl tpsControl = method.getAnnotation(TpsControl.class);
            
            String pointName = tpsControl.pointName();
            TpsCheckRequest tpsCheckRequest = null;
            RemoteTpsCheckParser parser = TpsCheckRequestParserRegistry.getParser(pointName);
            if (parser != null) {
                tpsCheckRequest = parser.parse(request, meta);
            } else {
                tpsCheckRequest = new TpsCheckRequest();
                tpsCheckRequest.setConnectionId(meta.getConnectionId());
                tpsCheckRequest.setClientIp(meta.getClientIp());
            }
            tpsCheckRequest.setPointName(pointName);
            
            TpsCheckResponse check = tpsControlManager.check(tpsCheckRequest);
            
            if (!check.isSuccess()) {
                Response response;
                try {
                    response = super.getDefaultResponseInstance(handlerClazz);
                    response.setErrorInfo(NacosException.OVER_THRESHOLD, "Tps Flow restricted:" + check.getMessage());
                    return response;
                } catch (Exception e) {
                    Loggers.TPS_CONTROL_DETAIL
                            .warn("Tps monitor fail , request: {},exception:{}", request.getClass().getSimpleName(), e);
                    return null;
                }
                
            }
        }
        
        return null;
    }
}
