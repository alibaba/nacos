package com.alibaba.nacos.config.server.service.sql;

import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

public class ExternalStorageUtils {
    public static KeyHolder createKeyHolder(){
        return new GeneratedKeyHolder();
    }
}
