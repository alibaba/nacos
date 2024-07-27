package com.alibaba.nacos.client.serverlist.utils;

import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.utils.UuidUtils;
import com.alibaba.nacos.common.utils.VersionUtils;

/**
 * sdk http util.
 *
 * @author xz
 * @since 2024/7/25 13:50
 */
public class HttpUtil {

    private static final String NAMING_MODULE = "Naming";

    private static final String CONFIG_MODULE = "Config";

    public static Header builderNamingHeader() {
        return builderHeaderByModule(NAMING_MODULE);
    }

    public static Header builderConfigHeader() {
        return builderHeaderByModule(CONFIG_MODULE);
    }

    /**
     * Build header by module.
     *
     * @param module client module name
     * @return header
     */
    public static Header builderHeaderByModule(String module) {
        Header header = Header.newInstance();
        header.addParam(HttpHeaderConsts.CLIENT_VERSION_HEADER, VersionUtils.version);
        header.addParam(HttpHeaderConsts.USER_AGENT_HEADER, VersionUtils.getFullClientVersion());
        header.addParam(HttpHeaderConsts.ACCEPT_ENCODING, "gzip,deflate,sdch");
        header.addParam(HttpHeaderConsts.CONNECTION, "Keep-Alive");
        header.addParam(HttpHeaderConsts.REQUEST_ID, UuidUtils.generateUuid());
        header.addParam(HttpHeaderConsts.REQUEST_MODULE, module);
        return header;
    }
}
