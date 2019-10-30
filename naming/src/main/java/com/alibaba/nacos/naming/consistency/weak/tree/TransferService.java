/*
 * Copyright 1999-2019 Alibaba Group Holding Ltd.
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
package com.alibaba.nacos.naming.consistency.weak.tree;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.naming.boot.RunningConfig;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.misc.HttpClient;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.Set;

/**
 * @author satjd
 */
@Component
public class TransferService {
    @Autowired
    TreePeerSet treePeerSet;

    @Autowired
    TransferTaskProcessor transferTaskProcessor;

    @Autowired
    ProtocolConfig protocolConfig;

    public void transferNext(Datum datum, DatumType type) throws Exception {
        transferNext(datum, type, treePeerSet.getLocal());
    }

    public void transferNext(Datum datum, DatumType type, TreePeer source) throws Exception {
        if (protocolConfig.isBatchUpdateEnabled() && type.equals(DatumType.UPDATE)) {
            // 采用聚合更新
            transferUpdateBatch(datum, type, source);
            return;
        }

        JSONObject json = new JSONObject();
        json.put("datum", datum);
        json.put("source", source);

        String body = json.toJSONString();

        Set<TreePeer> curChild = treePeerSet.getCurrentChild(source);

        // todo change log level from INFO to DEBUG
        if (Loggers.TREE.isDebugEnabled()) {
            Loggers.TREE.debug("Forward to:" + curChild.toString());
        }

        for (TreePeer child : curChild) {
            String url = "http://" + child.ip
                + UtilsAndCommons.IP_PORT_SPLITER
                + child.port
                + RunningConfig.getContextPath();

            // build url for different types of message
            switch (type) {
                case UPDATE:
                    url += ProtocolConfig.TREE_API_ON_PUB_PATH;

                    break;
                case DELETE:
                    url += ProtocolConfig.TREE_API_ON_DELETE_PATH;
                default:
                    break;
            }

            HttpClient.asyncHttpPostLarge(url, Arrays.asList("key=" + datum.key), body, new AsyncCompletionHandler<Integer>() {
                @Override
                public Integer onCompleted(Response response) throws Exception {
                    if (response.getStatusCode() != HttpURLConnection.HTTP_OK) {
                        Loggers.TREE.warn("[TREE] failed to publish data to peer, datumId={}, peer={}, http code={}",
                            datum.key, child, response.getStatusCode());
                        return 1;
                    }
                    return 0;
                }

                @Override
                public STATE onContentWriteCompleted() {
                    return STATE.CONTINUE;
                }
            });
        }
    }

    private void transferUpdateBatch(Datum datum, DatumType type, TreePeer source) {
        transferTaskProcessor.addTask(new TransferTask(datum,type,source));
    }
}
