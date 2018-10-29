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
package com.alibaba.nacos.client.naming.cache;

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.client.naming.utils.CollectionUtils;
import com.alibaba.nacos.client.naming.utils.LogUtils;
import com.alibaba.nacos.client.naming.utils.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author xuanyin
 */
public class DiskCache {

    public static void write(ServiceInfo dom, String dir) {

        try {
            makeSureCacheDirExists(dir);

            File file = new File(dir, dom.getKey());
            if (!file.exists()) {
                // add another !file.exists() to avoid conflicted creating-new-file from multi-instances
                if (!file.createNewFile() && !file.exists()) {
                    throw new IllegalStateException("failed to create cache file");
                }
            }

            StringBuilder keyContentBuffer = new StringBuilder("");

            String json = dom.getJsonFromServer();

            if (StringUtils.isEmpty(json)) {
                json = JSON.toJSONString(dom);
            }

            keyContentBuffer.append(json);

            //Use the concurrent API to ensure the consistency.
            ConcurrentDiskUtil.writeFileContent(file, keyContentBuffer.toString(), Charset.defaultCharset().toString());

        } catch (Throwable e) {
            LogUtils.LOG.error("NA", "failed to write cache for dom:" + dom.getName(), e);
        }
    }

    public static String getLineSeperator() {
        String lineSeparator = System.getProperty("line.separator");
        return lineSeparator;
    }

    public static Map<String, ServiceInfo> read(String cacheDir) {
        Map<String, ServiceInfo> domMap = new HashMap<String, ServiceInfo>(16);

        BufferedReader reader = null;
        try {
            File[] files = makeSureCacheDirExists(cacheDir).listFiles();
            if (files == null) {
                return domMap;
            }

            for (File file : files) {
                if (!file.isFile()) {
                    continue;
                }

                if (!(file.getName().endsWith(ServiceInfo.SPLITER + "meta") || file.getName().endsWith(ServiceInfo.SPLITER + "special-url"))) {
                    ServiceInfo dom = new ServiceInfo(file.getName());
                    List<Instance> ips = new ArrayList<Instance>();
                    dom.setHosts(ips);

                    ServiceInfo newFormat = null;

                    try {
                        String dataString = ConcurrentDiskUtil.getFileContent(file, Charset.defaultCharset().toString());
                        reader = new BufferedReader(new StringReader(dataString));

                        String json;
                        while ((json = reader.readLine()) != null) {
                            try {
                                if (!json.startsWith("{")) {
                                    continue;
                                }

                                newFormat = JSON.parseObject(json, ServiceInfo.class);

                                if (StringUtils.isEmpty(newFormat.getName())) {
                                    ips.add(JSON.parseObject(json, Instance.class));
                                }
                            } catch (Throwable e) {
                                LogUtils.LOG.error("NA", "error while parsing cache file: " + json, e);
                            }
                        }
                    } catch (Exception e) {
                        LogUtils.LOG.error("NA", "failed to read cache for dom: " + file.getName(), e);
                    } finally {
                        try {
                            if (reader != null) {
                                reader.close();
                            }
                        } catch (Exception e) {
                            //ignore
                        }
                    }
                    if (newFormat != null && !StringUtils.isEmpty(newFormat.getName()) && !CollectionUtils.isEmpty(newFormat.getHosts())) {
                        domMap.put(dom.getKey(), newFormat);
                    } else if (!CollectionUtils.isEmpty(dom.getHosts())) {
                        domMap.put(dom.getKey(), dom);
                    }
                }

            }
        } catch (Throwable e) {
            LogUtils.LOG.error("NA", "failed to read cache file", e);
        }

        return domMap;
    }

    private static File makeSureCacheDirExists(String dir) {
        File cacheDir = new File(dir);
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            throw new IllegalStateException("failed to create cache dir: " + dir);
        }

        return cacheDir;
    }
}
