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
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.http.HttpClientManager;
import com.alibaba.nacos.common.http.NSyncHttpClient;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.HttpRestResult;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.utils.VersionUtils;
import com.alibaba.nacos.consistency.store.KVStore;
import com.alibaba.nacos.core.cluster.MemberManager;
import com.alibaba.nacos.core.distributed.distro.utils.DistroExecutor;
import com.alibaba.nacos.core.utils.Commons;
import com.alibaba.nacos.core.utils.ExceptionUtil;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.core.utils.RestResultUtils;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

import static com.alibaba.nacos.core.utils.Constants.NACOS_SERVER_HEADER;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
class DistroClient {

    private static final String DATA_ON_SYNC_URL = "/distro/items";
    private static final String DATA_GET_URL = "/distro/items";
    private static final String ALL_DATA_GET_URL = "/distro/all/items";
    private static final String TIMESTAMP_SYNC_URL = "/distro/checksum";
    private final TypeReference<RestResult<String>> reference = new TypeReference<RestResult<String>>() {
    };
    private final MemberManager memberManager;
    private final NSyncHttpClient httpClient;
    private final Serializer serializer;

    public DistroClient(MemberManager memberManager, Serializer serializer) {
        this.memberManager = memberManager;
        this.serializer = serializer;
        this.httpClient = HttpClientManager.newHttpClient(DataSyncer.class.getCanonicalName());
    }

    public void syncCheckSums(Map<String, Map<String, String>> checksumMap, String server) {

        try {

            final String url = buildUrl(TIMESTAMP_SYNC_URL, server);

            final Header header = Header.newInstance()
                    .addParam(HttpHeaderConsts.CLIENT_VERSION_HEADER, VersionUtils.VERSION)
                    .addParam(HttpHeaderConsts.USER_AGENT_HEADER, "Nacos-Server:" + VersionUtils.VERSION)
                    .addParam("Connection", "Keep-Alive");

            final Query query = Query.newInstance()
                    .addParam("source", memberManager.self().address());

            DistroExecutor.executeByGlobal(() -> {
                try {
                    HttpRestResult<String> result = (HttpRestResult<String>) httpClient.put(url, header, query, checksumMap, reference);

                    if (!result.ok()) {
                        Loggers.DISTRO.error("failed to req API: {}, code: {}, msg: {}",
                                url, result.getHttpCode(), result.getData());
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
        final String url = buildUrl(ALL_DATA_GET_URL, serverAddr);

        HttpRestResult<String> result = (HttpRestResult<String>) httpClient.get(url, Header.EMPTY, Query.EMPTY, reference);

        Loggers.DISTRO.info("get all data from server-addr : {} is : {}", serverAddr, result);

        if (result.ok()) {
            return result.getData().getBytes();
        }

        throw new IOException("failed to req API: " + url + ". code: "
                + result.getHttpCode() + " msg: " + result.getData());
    }

    public byte[] getData(String storeName, List<String> keys, String server) throws Exception {

        Map<String, String> params = new HashMap<>(8);
        params.put("keys", StringUtils.join(keys, ","));
        params.put("storeName", storeName);

        final String url = buildUrl(DATA_GET_URL, server);

        HttpRestResult<String> result = (HttpRestResult<String>) httpClient.getLarge(url, Header.EMPTY, Query.EMPTY, params, reference);

        if (result.ok()) {
            return result.getData().getBytes();
        }

        throw new IOException("failed to req API: " + url + ". code: "
                + result.getHttpCode() + " msg: " + result.getData());
    }

    boolean syncData(Map<String, Map<String, KVStore.Item>> data, String curServer) {
        final Header header = Header.newInstance()
                .addParam(HttpHeaderConsts.CLIENT_VERSION_HEADER, VersionUtils.VERSION)
                .addParam(HttpHeaderConsts.USER_AGENT_HEADER, NACOS_SERVER_HEADER + ":" + VersionUtils.VERSION)
                .addParam("Accept-Encoding", "gzip,deflate,sdch")
                .addParam("Connection", "Keep-Alive")
                .addParam("Content-Encoding", "gzip");

        try {
            final String url = buildUrl(DATA_ON_SYNC_URL, curServer);
            HttpRestResult<String> result = (HttpRestResult<String>) httpClient.put(url, header, Query.EMPTY, data, reference);
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

    private String buildUrl(String path, String server) {
        return "http://" + server + memberManager.getContextPath() + Commons.NACOS_CORE_CONTEXT + path;
    }
}
