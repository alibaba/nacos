package com.alibaba.nacos.client.aliyun;

import com.alibaba.nacos.api.AbstractNacosExtension;
import com.alibaba.nacos.api.config.filter.IConfigFilter;

import java.util.Properties;

/**
 * the NacosExtension of Aliyun.
 *
 * @author luyanbo(RobberPhex)
 */
public class AliyunNacosExtension extends AbstractNacosExtension {

    @Override
    public String getExtensionName() {
        return "Aliyun";
    }

    @Override
    public IConfigFilter buildConfigFilter(Properties properties) {
        return new AliyunConfigFilter(properties);
    }
}
