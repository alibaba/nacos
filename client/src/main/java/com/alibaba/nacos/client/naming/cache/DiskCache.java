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

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.client.naming.utils.CollectionUtils;
import com.alibaba.nacos.client.utils.ConcurrentDiskUtil;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.alibaba.nacos.client.utils.LogUtils.NAMING_LOGGER;

/**
 * Disk cache.
 *
 * @author xuanyin
 */
public class DiskCache {
    
    /**
     * Write service info to dir.
     *
     * @param dom service info
     * @param dir directory
     */
    public static void write(ServiceInfo dom, String dir) {
        
        try {
            makeSureCacheDirExists(dir);
            
            File file = new File(dir, dom.getKeyEncoded());
            createFileIfAbsent(file, false);
            
            StringBuilder keyContentBuffer = new StringBuilder();
            
            String json = dom.getJsonFromServer();
            
            if (StringUtils.isEmpty(json)) {
                json = JacksonUtils.toJson(dom);
            }
            
            keyContentBuffer.append(json);
            
            //Use the concurrent API to ensure the consistency.
            ConcurrentDiskUtil.writeFileContent(file, keyContentBuffer.toString(), Charset.defaultCharset().toString());
            
        } catch (Throwable e) {
            NAMING_LOGGER.error("[NA] failed to write cache for dom:" + dom.getName(), e);
        }
    }
    
    public static String getLineSeparator() {
        return System.getProperty("line.separator");
    }
    
    /**
     * Read service info from disk.
     *
     * @param cacheDir cache file dir
     * @return service infos
     */
    public static Map<String, ServiceInfo> read(String cacheDir) {
        Map<String, ServiceInfo> domMap = new HashMap<>(16);
        try {
            File[] files = makeSureCacheDirExists(cacheDir).listFiles();
            if (files == null || files.length == 0) {
                return domMap;
            }
            
            for (File file : files) {
                if (!file.isFile()) {
                    continue;
                }
                domMap.putAll(parseServiceInfoFromCache(file));
            }
        } catch (Throwable e) {
            NAMING_LOGGER.error("[NA] failed to read cache file", e);
        }
        
        return domMap;
    }
    
    /**
     * Parse Service info from cache file or failover file.
     *
     * @param file cache file or failover file
     * @return Service info
     * @throws UnsupportedEncodingException if the file is not encoded in UTF-8
     */
    @SuppressWarnings("PMD.UndefineMagicConstantRule")
    public static Map<String, ServiceInfo> parseServiceInfoFromCache(File file) throws UnsupportedEncodingException {
        Map<String, ServiceInfo> result = new HashMap<>(1);
        String fileName = URLDecoder.decode(file.getName(), "UTF-8");
        if (!(fileName.endsWith(Constants.SERVICE_INFO_SPLITER + "meta") || fileName
                .endsWith(Constants.SERVICE_INFO_SPLITER + "special-url"))) {
            ServiceInfo dom = new ServiceInfo(fileName);
            List<Instance> ips = new ArrayList<>();
            dom.setHosts(ips);
            ServiceInfo newFormat = null;
            try (BufferedReader reader = new BufferedReader(
                    new StringReader(ConcurrentDiskUtil.getFileContent(file, Charset.defaultCharset().toString())))) {
                
                String json;
                while ((json = reader.readLine()) != null) {
                    try {
                        if (!json.startsWith("{")) {
                            continue;
                        }
                        
                        newFormat = JacksonUtils.toObj(json, ServiceInfo.class);
                        
                        if (StringUtils.isEmpty(newFormat.getName())) {
                            ips.add(JacksonUtils.toObj(json, Instance.class));
                        }
                    } catch (Throwable e) {
                        NAMING_LOGGER.error("[NA] error while parsing cache file: " + json, e);
                    }
                }
            } catch (Exception e) {
                NAMING_LOGGER.error("[NA] failed to read cache for dom: " + file.getName(), e);
            }
            if (newFormat != null && !StringUtils.isEmpty(newFormat.getName()) && !CollectionUtils
                    .isEmpty(newFormat.getHosts())) {
                result.put(dom.getKey(), newFormat);
            } else if (!CollectionUtils.isEmpty(dom.getHosts())) {
                result.put(dom.getKey(), dom);
            }
        }
        return result;
    }
    
    /**
     * Create file if absent.
     *
     * @param file  file
     * @param isDir is dir
     * @throws IOException if any io exception during create.
     */
    public static void createFileIfAbsent(File file, boolean isDir) throws IOException {
        if (file.exists()) {
            return;
        }
        boolean createResult = isDir ? file.mkdirs() : file.createNewFile();
        if (!createResult && !file.exists()) {
            throw new IllegalStateException("failed to create cache : " + (isDir ? "dir" : file) + file.getPath());
        }
    }
    
    private static File makeSureCacheDirExists(String dir) throws IOException {
        File cacheDir = new File(dir);
        createFileIfAbsent(cacheDir, true);
        return cacheDir;
    }
}
