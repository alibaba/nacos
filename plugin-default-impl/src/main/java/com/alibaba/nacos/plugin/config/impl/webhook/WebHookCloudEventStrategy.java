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

package com.alibaba.nacos.plugin.config.impl.webhook;

import com.alibaba.nacos.config.server.utils.ConfigExecutor;
import com.alibaba.nacos.plugin.config.model.ConfigChangeNotifyInfo;
import com.alibaba.nacos.plugin.config.util.ConfigPropertyUtil;
import com.google.gson.Gson;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.http.vertx.VertxMessageFactory;
import io.cloudevents.jackson.JsonFormat;
import io.netty.util.internal.StringUtil;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * WebHookCloudEventStrategy.
 *
 * @author liyunfei
 */
public class WebHookCloudEventStrategy implements WebHookNotifyStrategy {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(WebHookCloudEventStrategy.class);
    
    private static final String ACCESS_KEY_ID = ConfigPropertyUtil.getWebHookAccessKeyId();
    
    private static final String ACCESS_KEY_SECRET = ConfigPropertyUtil.getWebHookAccessKeySecret();
    
    private static final String ENDPOINT = ConfigPropertyUtil.getWebHookEndpoint();
    
    private static final String EVENT_BUS = ConfigPropertyUtil.getWebHookEventBus();
    
    private static final String SOURCE = ConfigPropertyUtil.getWebHookSource();
    
    private static final String PROTOCOL_HTTP = "http://";
    
    private static final String PROTOCOL_HTTPS = "https://";
    
    private static final String PUT_EVENT_API = "/openapi/putEvents";
    
    private static final int SUCCESS_CODE = 200;
    
    @Override
    public void notifyConfigChange(ConfigChangeNotifyInfo configChangeNotifyInfo, String httpUrl) {
        CloudEventBuilder eventTemplate = CloudEventBuilder.v1().withSource(URI.create("app.microservice"))
                .withType("webhook.notify");
        String id = UUID.randomUUID().toString();
        Gson gson = new Gson();
        CloudEvent event;
        String url = PROTOCOL_HTTP + ENDPOINT + PUT_EVENT_API;
        if (httpUrl != null) {
            url = httpUrl;
            event = eventTemplate.newBuilder().withId(id)
                    .withData("application/json", gson.toJson(configChangeNotifyInfo).getBytes())
                    .withSource(URI.create("webhook.source")).withType("webhook.notify").build();
        } else {
            event = eventTemplate.newBuilder().withId(id)
                    .withData("application/json", gson.toJson(configChangeNotifyInfo).getBytes())
                    .withExtension("aliyuneventbusname", EVENT_BUS).withSource(URI.create(SOURCE))
                    .withType("webhook.notify").build();
        }
        ConfigExecutor.executeAsyncNotify(new WebhookCloudEventSingleTask(event, url));
    }
    
    @Override
    public String getNotifyStrategyName() {
        return "eventcloud";
    }
    
    class WebhookCloudEventSingleTask implements Runnable {
        
        private CloudEvent event;
        
        private String url;
        
        private int retry = 0;
        
        private final int maxRetry = 6;
        
        public WebhookCloudEventSingleTask(CloudEvent event, String url) {
            this.event = event;
            this.url = url;
        }
        
        @Override
        public void run() {
            final Vertx vertx = Vertx.vertx();
            final HttpClient httpClient = vertx.createHttpClient();
            final HttpClientRequest request = httpClient.postAbs(url).handler(response -> {
                if (response.statusCode() != SUCCESS_CODE) {
                    LOGGER.warn("push fail {},ready to retry", response.statusMessage());
                    retryRequest();
                }
            }).exceptionHandler(throwable -> {
                LOGGER.warn("push fail {},ready to retry", throwable.getMessage());
                retryRequest();
            });
            request.putHeader("content-type", "application/cloudevents+json");
            try {
                request.putHeader("authorization",
                        "acs" + ":" + ACCESS_KEY_ID + ":" + getSignature(getStringToSign(request), ACCESS_KEY_SECRET)
                                + "");
            } catch (Exception e) {
                LOGGER.error("signature failed {}", e.getMessage());
            }
            VertxMessageFactory.createWriter(request).writeStructured(event, new JsonFormat());
        }
        
        private long getDelay() {
            return (long) retry * retry;
        }
        
        private void retryRequest() {
            retry++;
            if (retry > maxRetry) {
                LOGGER.error("retry to much,give up to push");
                return;
            }
            ConfigExecutor.scheduleAsyncNotify(this, getDelay(), TimeUnit.SECONDS);
        }
    }
    
    String getStringToSign(HttpClientRequest request) {
        String method = request.method().name();
        String pathname = request.path();
        MultiMap headers = request.headers();
        Map<String, String> query = buildQueryMap(request.query());
        String contentMD5 = headers.get("content-md5") == null ? "" : (String) headers.get("content-md5");
        String contentType = headers.get("content-type") == null ? "" : (String) headers.get("content-type");
        String date = headers.get("date") == null ? "null" : (String) headers.get("date");
        String header = method + "\n" + contentMD5 + "\n" + contentType + "\n" + date + "\n";
        String canonicalizedHeaders = getCanonicalizedHeaders(headers);
        String canonicalizedResource = getCanonicalizedResource(pathname, query);
        String stringToSign = header + canonicalizedHeaders + canonicalizedResource;
        return stringToSign;
    }
    
    Map<String, String> buildQueryMap(String query) {
        Map<String, String> map = new HashMap<>(8);
        if (!StringUtil.isNullOrEmpty(query)) {
            String[] params = query.split("&");
            Arrays.stream(params).forEach(param -> {
                String[] kv = param.split("=");
                map.put(kv[0], kv[1]);
            });
        }
        return map;
    }
    
    String getCanonicalizedHeaders(MultiMap headers) {
        String prefix = "x-acs";
        Set<String> keys = headers.names();
        List<String> canonicalizedKeys = new ArrayList();
        Iterator var4 = keys.iterator();
        
        while (var4.hasNext()) {
            String key = (String) var4.next();
            if (key.startsWith(prefix)) {
                canonicalizedKeys.add(key);
            }
        }
        String[] canonicalizedKeysArray = (String[]) canonicalizedKeys.toArray(new String[canonicalizedKeys.size()]);
        Arrays.sort(canonicalizedKeysArray);
        StringBuilder result = new StringBuilder();
        
        for (int i = 0; i < canonicalizedKeysArray.length; ++i) {
            String key = canonicalizedKeysArray[i];
            result.append(key);
            result.append(":");
            result.append(((String) headers.get(key)).trim());
            result.append("\n");
        }
        
        return result.toString();
    }
    
    String getCanonicalizedResource(String pathname, Map<String, String> query) {
        String[] keys = (String[]) query.keySet().toArray(new String[query.size()]);
        if (keys.length <= 0) {
            return pathname;
        } else {
            Arrays.sort(keys);
            StringBuilder result = new StringBuilder(pathname);
            result.append("?");
            
            for (int i = 0; i < keys.length; ++i) {
                String key = keys[i];
                result.append(key);
                String value = (String) query.get(key);
                if (!StringUtil.isNullOrEmpty(value) && !"".equals(value.trim())) {
                    result.append("=");
                    result.append(value);
                }
                
                result.append("&");
            }
            return result.deleteCharAt(result.length() - 1).toString();
        }
    }
    
    String getSignature(String stringToSign, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(secret.getBytes("UTF-8"), "HmacSHA1"));
        byte[] signData = mac.doFinal(stringToSign.getBytes("UTF-8"));
        return Base64.getEncoder().encodeToString(signData);
    }
    
}
