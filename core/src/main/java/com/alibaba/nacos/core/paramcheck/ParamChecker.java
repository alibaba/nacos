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

public class ParamChecker {
    
    /**
     * ParamChecker will first look for the Checker annotation in the handler method, and if that annotation is null, it
     * will try to find the Checker annotation on the class where the handler method is located, and then load in the
     * target ParamExtractor in the Checker annotation.
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    public @interface Checker {
        
        /**
         * Configure a Class to locate a specific Extractor, which takes effect only on the @Controller annotated class
         * or method.
         *
         * @return Class<? extends AbstractHttpParamExtractor>
         */
        Class<? extends AbstractHttpParamExtractor> httpChecker() default DefaultHttpChecker.class;
        
        /**
         * Configure a Class to locate a specific Extractor, which takes effect only on grpcHandler.
         *
         * @return Class<? extends AbstractRpcParamExtractor>
         */
        Class<? extends AbstractRpcParamExtractor> rpcChecker() default DefaultGrpcChecker.class;
    }
    
    public static class DefaultHttpChecker extends AbstractHttpParamExtractor {
        
        @Override
        public List<ParamInfo> extractParam(HttpServletRequest params) throws Exception {
            return Collections.emptyList();
        }
    }
    
    public static class DefaultGrpcChecker extends AbstractRpcParamExtractor {
        
        @Override
        public List<ParamInfo> extractParam(Request request) throws Exception {
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
    
    public static AbstractRpcParamExtractor getRpcChecker(Checker checker) {
        return rpcManager.computeIfAbsent(checker.rpcChecker(), (key) -> new DefaultGrpcChecker());
    }
    
    public static AbstractHttpParamExtractor getHttpChecker(Checker checker) {
        return httpManager.computeIfAbsent(checker.httpChecker(), (key) -> new DefaultHttpChecker());
    }
}
