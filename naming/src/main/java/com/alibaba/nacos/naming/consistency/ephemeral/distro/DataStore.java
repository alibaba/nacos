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

package com.alibaba.nacos.naming.consistency.ephemeral.distro;

import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.core.Instances;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Store of data.
 *
 * @author nkorange
 * @since 1.0.0
 */
@Component
public class DataStore {


    /**
     * {
     * 	"dataMap": {
     * 		"com.alibaba.nacos.naming.iplist.ephemeral.public##DEFAULT_GROUP@@userProvide": {
     * 			"key": "com.alibaba.nacos.naming.iplist.ephemeral.public##DEFAULT_GROUP@@userProvide",
     * 			"timestamp": 1,
     * 			"value": {
     * 				"cachedChecksum": "",
     * 				"instanceList": [{
     * 					"app": "DEFAULT",
     * 					"clusterName": "TEST1",
     * 					"enabled": true,
     * 					"ephemeral": true,
     * 					"healthy": true,
     * 					"instanceHeartBeatInterval": 5000,
     * 					"instanceHeartBeatTimeOut": 15000,
     * 					"instanceId": "11.11.11.111#8887#TEST1#DEFAULT_GROUP@@userProvide",
     * 					"ip": "11.11.11.111",
     * 					"ipDeleteTimeout": 30000,
     * 					"lastBeat": 1568717072650,
     * 					"marked": false,
     * 					"metadata": {},
     * 					"port": 8887,
     * 					"serviceName": "DEFAULT_GROUP@@userProvide",
     * 					"tenant": "",
     * 					"weight": 1.0
     *                                }]* 			}
     *        },
     * 		"com.alibaba.nacos.naming.iplist.ephemeral.public##DEFAULT_GROUP@@videProvide": {
     * 			"key": "com.alibaba.nacos.naming.iplist.ephemeral.public##DEFAULT_GROUP@@videProvide",
     * 			"timestamp": 1,
     * 			"value": {
     * 				"cachedChecksum": "",
     * 				"instanceList": [{
     * 					"app": "DEFAULT",
     * 					"clusterName": "DEFAULT",
     * 					"enabled": true,
     * 					"ephemeral": true,
     * 					"healthy": true,
     * 					"instanceHeartBeatInterval": 5000,
     * 					"instanceHeartBeatTimeOut": 15000,
     * 					"instanceId": "2.2.2.21#9999#DEFAULT#DEFAULT_GROUP@@videProvide",
     * 					"ip": "2.2.2.21",
     * 					"ipDeleteTimeout": 30000,
     * 					"lastBeat": 1568717072679,
     * 					"marked": false,
     * 					"metadata": {},
     * 					"port": 9999,
     * 					"serviceName": "DEFAULT_GROUP@@videProvide",
     * 					"tenant": "",
     * 					"weight": 1.0
     *                }, {
     * 					"app": "DEFAULT",
     * 					"clusterName": "DEFAULT",
     * 					"enabled": true,
     * 					"ephemeral": true,
     * 					"healthy": true,
     * 					"instanceHeartBeatInterval": 5000,
     * 					"instanceHeartBeatTimeOut": 15000,
     * 					"instanceId": "5.5.5.5#9999#DEFAULT#DEFAULT_GROUP@@videProvide",
     * 					"ip": "5.5.5.5",
     * 					"ipDeleteTimeout": 30000,
     * 					"lastBeat": 1568717072683,
     * 					"marked": false,
     * 					"metadata": {},
     * 					"port": 9999,
     * 					"serviceName": "DEFAULT_GROUP@@videProvide",
     * 					"tenant": "",
     * 					"weight": 1.0
     *                }, {
     * 					"app": "DEFAULT",
     * 					"clusterName": "TEST1",
     * 					"enabled": true,
     * 					"ephemeral": true,
     * 					"healthy": true,
     * 					"instanceHeartBeatInterval": 5000,
     * 					"instanceHeartBeatTimeOut": 15000,
     * 					"instanceId": "11.11.11.111#8889#TEST1#DEFAULT_GROUP@@videProvide",
     * 					"ip": "11.11.11.111",
     * 					"ipDeleteTimeout": 30000,
     * 					"lastBeat": 1568717072674,
     * 					"marked": false,
     * 					"metadata": {},
     * 					"port": 8889,
     * 					"serviceName": "DEFAULT_GROUP@@videProvide",
     * 					"tenant": "",
     * 					"weight": 1.0
     *                }]
     *            }
     *        }* 	},
     * 	"instanceCount": 4
     * }
     */
    private Map<String, Datum> dataMap = new ConcurrentHashMap<>(1024);

    public void put(String key, Datum value) {
        dataMap.put(key, value);
    }

    public Datum remove(String key) {
        return dataMap.remove(key);
    }

    public Set<String> keys() {
        return dataMap.keySet();
    }

    public Datum get(String key) {
        return dataMap.get(key);
    }

    public boolean contains(String key) {
        return dataMap.containsKey(key);
    }

    /**
     * Batch get datum for a list of keys.
     * 批量获取key对应的Datum
     * @param keys of datum
     * @return list of datum
     */
    public Map<String, Datum> batchGet(List<String> keys) {
        Map<String, Datum> map = new HashMap<>(128);
        for (String key : keys) {
            Datum datum = dataMap.get(key);
            if (datum == null) {
                continue;
            }
            map.put(key, datum);
        }
        return map;
    }

    public int getInstanceCount() {
        int count = 0;
        for (Map.Entry<String, Datum> entry : dataMap.entrySet()) {
            try {
                Datum instancesDatum = entry.getValue();
                if (instancesDatum.value instanceof Instances) {
                    count += ((Instances) instancesDatum.value).getInstanceList().size();
                }
            } catch (Exception ignore) {
            }
        }
        return count;
    }

    public Map<String, Datum> getDataMap() {
        return dataMap;
    }
}
