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

package com.alibaba.nacos.core.distributed.distro.core;

import com.alibaba.fastjson.TypeReference;
import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.http.HttpClientManager;
import com.alibaba.nacos.common.http.NSyncHttpClient;
import com.alibaba.nacos.common.http.param.Body;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.HttpResResult;
import com.alibaba.nacos.common.model.ResResult;
import com.alibaba.nacos.common.utils.VersionUtils;
import com.alibaba.nacos.core.cluster.NodeManager;
import com.alibaba.nacos.core.distributed.distro.utils.DistroExecutor;
import com.alibaba.nacos.core.utils.ExceptionUtil;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.core.utils.Serializer;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.alibaba.nacos.core.utils.Constants.NACOS_SERVER_HEADER;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
class DistroClient {

    private static final String DATA_ON_SYNC_URL = "/distro/datum";

    private static final String DATA_GET_URL = "/distro/datum";

    private static final String ALL_DATA_GET_URL = "/distro/datums";

    private static final String TIMESTAMP_SYNC_URL = "/distro/checksum";

    private final NodeManager nodeManager;
    private final NSyncHttpClient httpClient;
    private final Serializer serializer;

    public DistroClient(NodeManager nodeManager, Serializer serializer) {
        this.nodeManager = nodeManager;
        this.serializer = serializer;
        this.httpClient = HttpClientManager.newHttpClient(DataSyncer.class.getCanonicalName());
    }

    public void syncCheckSums(Map<String, Map<String, String>> checksumMap, String server) {

        try {

            final String url = "http://" + server + TIMESTAMP_SYNC_URL + "?source=" + nodeManager.self().address();

            final Header header = Header.newInstance()
                    .addParam(HttpHeaderConsts.CLIENT_VERSION_HEADER, VersionUtils.VERSION)
                    .addParam(HttpHeaderConsts.USER_AGENT_HEADER, "Nacos-Server:" + VersionUtils.VERSION)
                    .addParam("Connection", "Keep-Alive");

            final Body body = Body.objToBody(checksumMap);

            DistroExecutor.executeByGlobal(() -> {
                try {
                    HttpResResult<String> result = (HttpResResult<String>) httpClient.put(url, header, Query.EMPTY, body, new TypeReference<ResResult<String>>() {
                    });

                    if (!result.ok()) {
                        Loggers.DISTRO.error("failed to req API: {}, code: {}, msg: {}",
                                url, result.getHttpCode() , result.getData());
                    }

                } catch (Throwable t) {
                    Loggers.DISTRO.error("failed to req API:" + url, t);
                }
            });

        } catch (Exception e) {
            Loggers.DISTRO.warn("NamingProxy", e);
        }
    }

    byte[] getAllData(final String serverAddr) throws Exception {
        final String url = "http://" + serverAddr + nodeManager.getContextPath()
                + ALL_DATA_GET_URL;

        HttpResResult<String> result = (HttpResResult<String>) httpClient.get(url, Header.EMPTY, Query.EMPTY, new TypeReference<ResResult<String>>() {
        });

        if (result.ok()) {
            return result.getData().getBytes();
        }

        throw new IOException("failed to req API: " + url + ". code: "
                + result.getHttpCode() + " msg: " + result.getData());
    }

    public byte[] getData(List<String> keys, String server) throws Exception {

        Map<String, String> params = new HashMap<>(8);
        params.put("keys", StringUtils.join(keys, ","));

        final String url = "http://" + server + DATA_GET_URL;

        final Body body = Body.objToBody(params);

        HttpResResult<String> result = (HttpResResult<String>) httpClient.getLarge(url, Header.EMPTY, Query.EMPTY, body, new TypeReference<ResResult<String>>() {
        });

        if (result.ok()) {
            return result.getData().getBytes();
        }

        throw new IOException("failed to req API: " + url + ". code: "
                + result.getHttpCode() + " msg: " + result.getData());
    }

    boolean syncData(Map<String, byte[]> data, String curServer) {
        final Header header = Header.newInstance()
                .addParam(HttpHeaderConsts.CLIENT_VERSION_HEADER, VersionUtils.VERSION)
                .addParam(HttpHeaderConsts.USER_AGENT_HEADER, NACOS_SERVER_HEADER + ":" + VersionUtils.VERSION)
                .addParam("Accept-Encoding", "gzip,deflate,sdch")
                .addParam("Connection", "Keep-Alive")
                .addParam("Content-Encoding", "gzip");

        try {
            final String url = "http://" + curServer + nodeManager.getContextPath() + DATA_ON_SYNC_URL;
            HttpResResult<String> result = (HttpResResult<String>) httpClient.post(url, header, Query.EMPTY, Body.objToBody(data), new TypeReference<ResResult<String>>() {
            });
            if (HttpURLConnection.HTTP_OK == result.getHttpCode()) {
                return true;
            }
            if (HttpURLConnection.HTTP_NOT_MODIFIED == result.getHttpCode()) {
                return true;
            }
            throw new IOException("failed to req API:" + url + ". code:"
                    + result.getHttpCode() + " msg: " + result.getData());
        } catch (Exception e) {
            Loggers.DISTRO.warn("distro sync data has error : {}", ExceptionUtil.getAllExceptionMsg(e));
        }
        return false;
    }

}
