package com.alibaba.nacos.common.http.client;

import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.http.handler.ResponseHandler;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.MediaType;
import com.alibaba.nacos.common.utils.IoUtils;

import java.lang.reflect.Type;

/**
 * HTTP Message Converter
 * to convert the response into a type {@code T}.
 *
 * @author mai.jh
 * @date 2020/5/27
 */
@SuppressWarnings({"unchecked", "rawtypes", "resource"})
public class HttpMessageConverterExtractor<T> implements ResponseExtractor<T>{


    private Type responseType;

    public HttpMessageConverterExtractor(Type responseType) {
        this.responseType = responseType;
    }

    @Override
    public T extractData(ClientHttpResponse clientHttpResponse) throws Exception {
        Header headers = clientHttpResponse.getHeaders();
        String value = headers.getValue(HttpHeaderConsts.CONTENT_TYPE);
        String body = IoUtils.toString(clientHttpResponse.getBody(), headers.getCharset());
        if (MediaType.APPLICATION_JSON.equals(value)) {
            return ResponseHandler.convert(body, responseType);
        }
        return (T) body;
    }


}
