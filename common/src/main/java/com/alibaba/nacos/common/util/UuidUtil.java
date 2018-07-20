package com.alibaba.nacos.common.util;

import java.util.UUID;

/**
 * @author dungu.zpf
 */
public class UuidUtil {

    public static String generateUuid() {
        return UUID.randomUUID().toString();
    }
}
