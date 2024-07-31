package com.alibaba.nacos.client.serverlist.utils;

import com.alibaba.nacos.api.common.Constants;
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

    private static final String OLD_NAMING_MODULE_HEADER = "Naming";

    public static Header buildNamingHeader() {
        return buildHeaderByModule(OLD_NAMING_MODULE_HEADER);
    }

    public static Header buildConfigHeader() {
        return buildHeaderByModule(Constants.Config.CONFIG_MODULE);
    }

    /**
     * Build header by module.
     *
     * @param module client module name
     * @return header
     */
    public static Header buildHeaderByModule(String module) {
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
