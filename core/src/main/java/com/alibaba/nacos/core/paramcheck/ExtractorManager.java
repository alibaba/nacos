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

package com.alibaba.nacos.core.paramcheck;

import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.common.paramcheck.ParamInfo;
import com.alibaba.nacos.common.spi.NacosServiceLoader;

import javax.servlet.http.HttpServletRequest;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * param checker to manager Extractor.
 *
 * @author 985492783@qq.com
 * @date 2023/11/7 16:29
 */

public class ExtractorManager {
    
    /**
     * ParamChecker will first look for the Checker annotation in the handler method, and if that annotation is null, it
     * will try to find the Checker annotation on the class where the handler method is located, and then load in the
     * target ParamExtractor in the Checker annotation.
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    public @interface Extractor {
        
        /**
         * Configure a Class to locate a specific Extractor, which takes effect only on the @Controller annotated class
         * or method.
         *
         * @return Class<? extends AbstractHttpParamExtractor>
         */
        Class<? extends AbstractHttpParamExtractor> httpExtractor() default DefaultHttpExtractor.class;
        
        /**
         * Configure a Class to locate a specific Extractor, which takes effect only on grpcHandler.
         *
         * @return Class<? extends AbstractRpcParamExtractor>
         */
        Class<? extends AbstractRpcParamExtractor> rpcExtractor() default DefaultGrpcExtractor.class;
    }
    
    public static class DefaultHttpExtractor extends AbstractHttpParamExtractor {
        
        @Override
        public List<ParamInfo> extractParam(HttpServletRequest params) {
            return Collections.emptyList();
        }
    }
    
    public static class DefaultGrpcExtractor extends AbstractRpcParamExtractor {
        
        @Override
        public List<ParamInfo> extractParam(Request request) {
            return Collections.emptyList();
        }
    }
    
    private static HashMap<Class<? extends AbstractRpcParamExtractor>, AbstractRpcParamExtractor> rpcManager = new HashMap<>();
    
    private static HashMap<Class<? extends AbstractHttpParamExtractor>, AbstractHttpParamExtractor> httpManager = new HashMap<>();
    
    static {
        NacosServiceLoader.load(AbstractHttpParamExtractor.class).forEach(checker -> {
            httpManager.put(checker.getClass(), checker);
        });
        NacosServiceLoader.load(AbstractRpcParamExtractor.class).forEach(checker -> {
            rpcManager.put(checker.getClass(), checker);
        });
    }
    
    public static AbstractRpcParamExtractor getRpcExtractor(Extractor extractor) {
        return rpcManager.computeIfAbsent(extractor.rpcExtractor(), (key) -> new DefaultGrpcExtractor());
    }
    
    public static AbstractHttpParamExtractor getHttpExtractor(Extractor extractor) {
        return httpManager.computeIfAbsent(extractor.httpExtractor(), (key) -> new DefaultHttpExtractor());
    }
}
