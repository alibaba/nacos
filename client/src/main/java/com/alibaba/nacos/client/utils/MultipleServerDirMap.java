package com.alibaba.nacos.client.utils;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.common.utils.StringUtils;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * @author ggggg
 */

public class MultipleServerDirMap {

    public static final Logger LOGGER = LogUtils.logger(EnvUtil.class);

    private static final String DEFAULT_SERVER = "localhost:8888";

    private static final String SERVER_DIR_MAP = "serverDirMap";

    private static final String SERVER_ADDRESS_DELIMITER = ",";

    public static String convertBaseDir(String prefix, String serverAddress) {
        assert !StringUtils.isBlank(prefix) : "the prefix of the baseAddress can not be empty";
        if (StringUtils.isBlank(serverAddress)) {
            serverAddress = DEFAULT_SERVER;
        }
        File mapFile = new File(prefix + File.separator + SERVER_DIR_MAP);
        List<String> addressList = formattedAddress(serverAddress);
        Map<String, String> keyMap = readCacheKey(mapFile);
        String key = null;
        for (String add : addressList) {
            key = keyMap.get(add);
            if (!StringUtils.isBlank(key)) {
                break;
            }
        }
        key = initKey(addressList, keyMap, key);
        try {
            FileUtils.write(mapFile, JacksonUtils.toJson(keyMap), StandardCharsets.UTF_8, false);
        } catch (IOException e) {
            LOGGER.error("write address map key file fail", e);
        }
        return prefix + File.separator + key;

    }

    private static String initKey(List<String> addressList, Map<String, String> map,
                                  String defaultKey) {
        String key = defaultKey == null ? MD5Utils.encodeHexString(addressList.get(0).getBytes()) : defaultKey;
        for (String add : addressList) {
            map.put(add, key);
        }
        return key;
    }

    private static List<String> formattedAddress(String serverAddress) {
        String[] addressStrArr = serverAddress.split(SERVER_ADDRESS_DELIMITER);
        ArrayList<String> results = new ArrayList<>();
        for (String address : addressStrArr) {
            if (!StringUtils.isBlank(address)) {
                results.add(address.replace(":", "_"));
            }
        }
        Collections.sort(results);
        return results;
    }

    private static Map<String, String> readCacheKey(File mapFile) {
        HashMap<String, String> map = new HashMap<>();
        if (mapFile.exists()) {
            try {
                String context = FileUtils.readFileToString(mapFile, StandardCharsets.UTF_8);
                map = JacksonUtils.toObj(context, HashMap.class);
            } catch (IOException e) {
                LOGGER.error("read address map key file fail", e);
            }
        }
        return map;
    }

}