package com.alibaba.nacos.common.http.client;

import com.alibaba.nacos.common.model.RestResult;

import java.lang.reflect.Type;

/**
 * Response extractor for
 *
 * @author mai.jh
 * @date 2020/5/27
 */
@SuppressWarnings({"unchecked", "rawtypes", "resource"})
public class ResponseEntityExtractor<T> implements ResponseExtractor<T> {

    private Type responseType;

    private final HttpMessageConverterExtractor<T> delegate;

    public ResponseEntityExtractor(Type responseType) {
        this.responseType = responseType;
        this.delegate = new HttpMessageConverterExtractor(responseType);
    }

    @Override
    public T extractData(HttpClientResponse response) throws Exception {
        T body = this.delegate.extractData(response);
        if (body instanceof RestResult) {
            return body;
        }
        return (T) new RestResult<>(response.getHeaders(), response.getStatusCode(), body);
    }
}
