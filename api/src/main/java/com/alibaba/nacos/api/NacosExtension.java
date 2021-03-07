package com.alibaba.nacos.api;

import com.alibaba.nacos.api.config.filter.IConfigFilter;

import java.util.Properties;

/**
 * The interface of Nacos Extension
 *
 * <p>DO NOT implement this interface directly, you should extend <code>AbstractNacosExtension</code>.
 *
 * @see AbstractNacosExtension
 * @author luyanbo(RobberPhex)
 */
public interface NacosExtension {
    String getExtensionName();

    IConfigFilter buildConfigFilter(Properties properties);
}
