package com.alibaba.nacos.console.constant;

import java.util.HashMap;
import java.util.Map;

/**
 * Description:
 *
 * @author: zhenxianyimeng
 * @date: 2020-01-15
 * @time: 20:02
 */
public class ConsoleConstants {
    public static Map<String, String> sortFieldMap = new HashMap<>();

    static {
        sortFieldMap.put("namespaceShowName", "tenant_name");
        sortFieldMap.put("namespace", "tenant_id");
        sortFieldMap.put("gmt_create", "gmt_create");
    }
}
