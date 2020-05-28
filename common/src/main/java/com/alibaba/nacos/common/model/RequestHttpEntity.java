package com.alibaba.nacos.common.model;

import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.http.handler.RequestHandler;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.ByteUtils;
import com.alibaba.nacos.common.utils.IoUtils;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.util.Arrays;
import java.util.Map;

/**
 *
 * Represents an HTTP request , consisting of headers and body.
 *
 * @author mai.jh
 * @date 2020/5/23
 */
public class RequestHttpEntity {

    private final Header headers = Header.newInstance();

    private Query query;

    private byte[] body;

    public RequestHttpEntity(Header header, Query query) {
        handleHeader(header);
        this.query = query;
    }

    public RequestHttpEntity(Header header, Query query, byte[] body) throws Exception {
        handleHeader(header);
        this.query = query;
        this.body = body;
    }

    private void handleHeader(Header header) {
        if (header != null && !header.getHeader().isEmpty()) {
            Map<String, String> headerMap = header.getHeader();
            headers.addAll(headerMap);
        }
    }

    public Header getHeaders() {
        return headers;
    }

    public Query getQuery() {
        return query;
    }

    public byte[] getBody() {
        return body;
    }

    public boolean isEmptyBody() {
        return body == null || body.length == 0;
    }


}
