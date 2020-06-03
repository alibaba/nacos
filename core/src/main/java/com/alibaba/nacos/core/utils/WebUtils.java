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
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.context.request.async.DeferredResult;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * @author nkorange
 */
public class WebUtils {

    public static String required(final HttpServletRequest req, final String key) {
        String value = req.getParameter(key);
        if (StringUtils.isEmpty(value)) {
            throw new IllegalArgumentException("Param '" + key + "' is required.");
        }
        String encoding = req.getParameter("encoding");
        return resolveValue(value, encoding);
    }

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

    private static String resolveValue(String value, String encoding) {
        if (StringUtils.isEmpty(encoding)) {
            encoding = StandardCharsets.UTF_8.name();
        }
        try {
            value = HttpUtils.decode(new String(value.getBytes(StandardCharsets.UTF_8), encoding), encoding);
        } catch (UnsupportedEncodingException ignore) { }
        return value.trim();
    }

    public static String getAcceptEncoding(HttpServletRequest req) {
        String encode = StringUtils.defaultIfEmpty(req.getHeader("Accept-Charset"), StandardCharsets.UTF_8.name());
        encode = encode.contains(",") ? encode.substring(0, encode.indexOf(",")) : encode;
        return encode.contains(";") ? encode.substring(0, encode.indexOf(";")) : encode;
    }

    /**
     * Returns the value of the request header "user-agent" as a <code>String</code>.
     *
     * @param request HttpServletRequest
     * @return the value of the request header "user-agent", or the value of the
     *         request header "client-version" if the request does not have a
     *         header of "user-agent"
     */
    public static String getUserAgent(HttpServletRequest request) {
        String userAgent = request.getHeader(HttpHeaderConsts.USER_AGENT_HEADER);
        if (StringUtils.isEmpty(userAgent)) {
            userAgent = StringUtils.defaultIfEmpty(request.getHeader(HttpHeaderConsts.CLIENT_VERSION_HEADER), StringUtils.EMPTY);
        }
        return userAgent;
    }

    public static void response(HttpServletResponse response, String body, int code) throws
            IOException {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(body);
        response.setStatus(code);
    }

    public static  <T> void process(DeferredResult<T> deferredResult, CompletableFuture<T> future, Function<Throwable, T> errorHandler) {

        deferredResult.onTimeout(future::join);

        future.whenComplete((t, throwable) -> {
            if (Objects.nonNull(throwable)) {
                deferredResult.setResult(errorHandler.apply(throwable));
                return;
            }
            deferredResult.setResult(t);
        });
    }

    public static  <T> void process(DeferredResult<T> deferredResult, CompletableFuture<T> future, Runnable success, Function<Throwable, T> errorHandler) {

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
