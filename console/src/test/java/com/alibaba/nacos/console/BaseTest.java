package com.alibaba.nacos.console;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.modules.entity.ConfigInfoAggr;
import org.apache.commons.io.IOUtils;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

/**
 * @author zhangshun
 * @version $Id: BaseTest.java,v 0.1 2020年06月04日 11:22 $Exp
 */
public  class BaseTest {


    public static String readClassPath(String path) {
        ClassPathResource resource = new ClassPathResource(path);
        try {
            return IOUtils.toString(resource.getInputStream(), "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }


}
