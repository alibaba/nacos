package com.alibaba.nacos.config.server.utils;

import org.apache.commons.lang3.StringUtils;

/**
 * Tenant(namespace) Util.
 * Because config and naming treat tenant(namespace) differently,
 * this tool class can only be used by the config module.
 * @author klw(213539 @ qq.com)
 * @ClassName: TenantUtil
 * @date 2020/10/12 17:56
 */
public class TenantUtil {
    
    private static final String NAMESPACE_PUBLIC_KEY = "public";
    
    private static final String NAMESPACE_NULL_KEY = "null";
    
    /**
     * Treat the tenant parameters with values of "public" and "null" as an empty string.
     * @author klw(213539@qq.com)
     * 2020/10/12 17:59
     * @param tenant tenant(namespace) id
     * @return java.lang.String
     */
    public static String processTenantParameter(String tenant) {
        if (StringUtils.isBlank(tenant) || NAMESPACE_PUBLIC_KEY.equalsIgnoreCase(tenant) || NAMESPACE_NULL_KEY
                .equalsIgnoreCase(tenant)) {
            return "";
        }
        return tenant.trim();
    }

}
