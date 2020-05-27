package com.alibaba.nacos.common.utils;

import com.alibaba.nacos.common.http.param.Query;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * URI build utils
 *
 * @author mai.jh
 * @date 2020/5/24
 */
public class UriUtils {

    private final static UriUtils URI_UTILS = new UriUtils();

    private UriUtils() {
    }


    public static UriUtils newInstance() {
        return URI_UTILS;
    }

    /**
     * build URI By url and query
     * @param url url
     * @param query query param {@link Query}
     * @return
     */
    public static URI buildUri(String url, Query query) throws URISyntaxException {
        if (!query.isEmpty()) {
            url = url + "?" + query.toQueryUrl();
        }
        return new URI(url);
    }
}
