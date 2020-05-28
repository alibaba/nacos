package com.alibaba.nacos.common.http.client;

import java.io.IOException;

/**
 *
 * Generic callback interface used by {@link NacosRestTemplate}'s retrieval methods
 * Implementations of this interface perform the actual work of extracting data
 *
 * @author mai.jh
 * @date 2020/5/27
 */
public interface ResponseExtractor<T> {

    /**
     * Extract data from the given {@code ClientHttpResponse} and return it.
     * @param clientHttpResponse http response
     * @return the extracted data
     * @throws Exception ex
     */
    T extractData(HttpClientResponse clientHttpResponse) throws Exception;
}
