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
import org.springframework.beans.factory.annotation.Autowired;
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
    
    @Autowired
    private TpsMonitorManager tpsMonitorManager;
    
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
            Class[] parsers = tpsControl.parsers();
            List<MonitorKey> monitorKeys = new ArrayList<>();
            monitorKeys.add(new ClientIpMonitorKey(meta.getClientIp()));
            if (parsers != null) {
                for (Class clazz : parsers) {
                    try {
                        if (MonitorKeyParser.class.isAssignableFrom(clazz)) {
                            MonitorKey parseKey = ((MonitorKeyParser) (clazz.newInstance())).parse(request, meta);
                            if (parseKey != null) {
                                monitorKeys.add(parseKey);
                            }
                        }
                    } catch (Throwable throwable) {
                        //ignore
                    }
                }
            }
            
            boolean pass = tpsMonitorManager.applyTps(pointName, meta.getConnectionId(), monitorKeys);
            
            if (!pass) {
                Response response;
                try {
                    response = super.getDefaultResponseInstance(handlerClazz);
                    response.setErrorInfo(NacosException.OVER_THRESHOLD, "Tps Flow restricted");
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
