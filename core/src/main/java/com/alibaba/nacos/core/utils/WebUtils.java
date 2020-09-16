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

package com.alibaba.nacos.core.utils;

import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.http.HttpUtils;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.model.RestResultUtils;
import com.alibaba.nacos.sys.utils.DiskUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * web utils.
 *
 * @author nkorange
 */
public class WebUtils {
    
    /**
     * get target value from parameterMap, if not found will throw {@link IllegalArgumentException}.
     *
     * @param req {@link HttpServletRequest}
     * @param key key
     * @return value
     */
    public static String required(final HttpServletRequest req, final String key) {
        String value = req.getParameter(key);
        if (StringUtils.isEmpty(value)) {
            throw new IllegalArgumentException("Param '" + key + "' is required.");
        }
        String encoding = req.getParameter("encoding");
        return resolveValue(value, encoding);
    }
    
    /**
     * get target value from parameterMap, if not found will return default value.
     *
     * @param req          {@link HttpServletRequest}
     * @param key          key
     * @param defaultValue default value
     * @return value
     */
    public static String optional(final HttpServletRequest req, final String key, final String defaultValue) {
        if (!req.getParameterMap().containsKey(key) || req.getParameterMap().get(key)[0] == null) {
            return defaultValue;
        }
        String value = req.getParameter(key);
        if (StringUtils.isBlank(value)) {
            return defaultValue;
        }
        String encoding = req.getParameter("encoding");
        return resolveValue(value, encoding);
    }
    
    /**
     * decode target value.
     *
     * @param value    value
     * @param encoding encode
     * @return Decoded data
     */
    private static String resolveValue(String value, String encoding) {
        if (StringUtils.isEmpty(encoding)) {
            encoding = StandardCharsets.UTF_8.name();
        }
        try {
            value = HttpUtils.decode(new String(value.getBytes(StandardCharsets.UTF_8), encoding), encoding);
        } catch (UnsupportedEncodingException ignore) {
        }
        return value.trim();
    }
    
    /**
     * get accept encode from request.
     *
     * @param req {@link HttpServletRequest}
     * @return accept encode
     */
    public static String getAcceptEncoding(HttpServletRequest req) {
        String encode = StringUtils.defaultIfEmpty(req.getHeader("Accept-Charset"), StandardCharsets.UTF_8.name());
        encode = encode.contains(",") ? encode.substring(0, encode.indexOf(",")) : encode;
        return encode.contains(";") ? encode.substring(0, encode.indexOf(";")) : encode;
    }
    
    /**
     * Returns the value of the request header "user-agent" as a <code>String</code>.
     *
     * @param request HttpServletRequest
     * @return the value of the request header "user-agent", or the value of the request header "client-version" if the
     *         request does not have a header of "user-agent".
     */
    public static String getUserAgent(HttpServletRequest request) {
        String userAgent = request.getHeader(HttpHeaderConsts.USER_AGENT_HEADER);
        if (StringUtils.isEmpty(userAgent)) {
            userAgent = StringUtils
                    .defaultIfEmpty(request.getHeader(HttpHeaderConsts.CLIENT_VERSION_HEADER), StringUtils.EMPTY);
        }
        return userAgent;
    }
    
    /**
     * response data to client.
     *
     * @param response {@link HttpServletResponse}
     * @param body     body
     * @param code     http code
     * @throws IOException IOException
     */
    public static void response(HttpServletResponse response, String body, int code) throws IOException {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(body);
        response.setStatus(code);
    }
    
    /**
     * Handle file upload operations.
     *
     * @param multipartFile file
     * @param consumer post processor
     * @param response {@link DeferredResult}
     */
    public static void onFileUpload(MultipartFile multipartFile, Consumer<File> consumer,
            DeferredResult<RestResult<String>> response) {
        
        if (Objects.isNull(multipartFile) || multipartFile.isEmpty()) {
            response.setResult(RestResultUtils.failed("File is empty"));
            return;
        }
        File tmpFile = null;
        try {
            tmpFile = DiskUtils.createTmpFile(multipartFile.getName(), ".tmp");
            multipartFile.transferTo(tmpFile);
            consumer.accept(tmpFile);
        } catch (Throwable ex) {
            if (!response.isSetOrExpired()) {
                response.setResult(RestResultUtils.failed(ex.getMessage()));
            }
        } finally {
            DiskUtils.deleteQuietly(tmpFile);
        }
    }
    
    /**
     * Register DeferredResult in the callback of CompletableFuture.
     *
     * @param deferredResult {@link DeferredResult}
     * @param future         {@link CompletableFuture}
     * @param errorHandler   {@link Function}
     * @param <T>            target type
     */
    public static <T> void process(DeferredResult<T> deferredResult, CompletableFuture<T> future,
            Function<Throwable, T> errorHandler) {
        
        deferredResult.onTimeout(future::join);
        
        future.whenComplete((t, throwable) -> {
            if (Objects.nonNull(throwable)) {
                deferredResult.setResult(errorHandler.apply(throwable));
                return;
            }
            deferredResult.setResult(t);
        });
    }
    
    /**
     * Register DeferredResult in the callback of CompletableFuture.
     *
     * @param deferredResult {@link DeferredResult}
     * @param future         {@link CompletableFuture}
     * @param success        if future success, callback runnable
     * @param errorHandler   {@link Function}
     * @param <T>            target type
     */
    public static <T> void process(DeferredResult<T> deferredResult, CompletableFuture<T> future, Runnable success,
            Function<Throwable, T> errorHandler) {
        
        deferredResult.onTimeout(future::join);
        
        future.whenComplete((t, throwable) -> {
            if (Objects.nonNull(throwable)) {
                deferredResult.setResult(errorHandler.apply(throwable));
                return;
            }
            success.run();
            deferredResult.setResult(t);
        });
    }
}
