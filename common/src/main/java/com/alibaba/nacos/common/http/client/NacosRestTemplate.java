package com.alibaba.nacos.common.http.client;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.common.http.handler.RequestHandler;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.RequestHttpEntity;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.utils.HttpMethod;
import com.alibaba.nacos.common.utils.UriUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * NacosRestTemplate, refer to the design of Spring's RestTemplate
 *
 * @author mai.jh
 * @date 2020/5/24
 */
public class NacosRestTemplate implements RestOperations {

    private static final Logger logger = LoggerFactory.getLogger(NacosRestTemplate.class);

    private HttpClientRequest requestClient;

    public NacosRestTemplate(HttpClientRequest requestClient) {
       this.requestClient = requestClient;
    }

    @Override
    public <T> RestResult<T> get(String url, Type responseType, Header header, Query query) throws Exception {
        ResponseExtractor<RestResult<T>> responseExtractor = responseEntityExtractor(responseType);
        return execute(url, HttpMethod.GET, new RequestHttpEntity(header, query), responseExtractor);
    }

    @Override
    public <T> RestResult<T> get(String url, Type responseType, List<String> headers, Map<String, String> paramValues) throws Exception {
        RequestHttpEntity requestHttpEntity = new RequestHttpEntity(
            Header.newInstance().addAll(headers),
            Query.newInstance().initParams(paramValues));

        ResponseExtractor<RestResult<T>> responseExtractor = responseEntityExtractor(responseType);
        return execute(url, HttpMethod.GET, requestHttpEntity, responseExtractor);
    }

    @Override
    public <T> RestResult<T> get(String url, Header header, Query query, Type responseType) throws Exception {
        ResponseExtractor<RestResult<T>> responseExtractor = responseEntityExtractor(responseType);
        return execute(url, HttpMethod.GET, new RequestHttpEntity(header, query), responseExtractor);
    }

    @Override
    public <T> RestResult<T> getLarge(String url, Header header, Query query, Object body, Type responseType) throws Exception {
        ResponseExtractor<RestResult<T>> responseExtractor = responseEntityExtractor(responseType);
        return execute(url, HttpMethod.GET_LARGE, new RequestHttpEntity(header, query, toByte(body)), responseExtractor);
    }

    @Override
    public <T> RestResult<T> delete(String url, Header header, Query query, Type responseType) throws Exception {
        ResponseExtractor<RestResult<T>> responseExtractor = responseEntityExtractor(responseType);
        return execute(url, HttpMethod.DELETE, new RequestHttpEntity(header, query), responseExtractor);
    }

    @Override
    public <T> RestResult<T> put(String url, Header header, Query query, Object body, Type responseType) throws Exception {
        ResponseExtractor<RestResult<T>> responseExtractor = responseEntityExtractor(responseType);
        return execute(url, HttpMethod.PUT, new RequestHttpEntity(header, query, toByte(body)), responseExtractor);
    }

    @Override
    public <T> RestResult<T> putJson(String url, List<String> headers, Map<String, String> paramValues, String body, Type responseType) throws Exception {
        RequestHttpEntity requestHttpEntity = new RequestHttpEntity(
            Header.newInstance().addAll(headers),
            Query.newInstance().initParams(paramValues),
            toByte(body));

        ResponseExtractor<RestResult<T>> responseExtractor = responseEntityExtractor(responseType);
        return execute(url, HttpMethod.PUT, requestHttpEntity, responseExtractor);
    }

    @Override
    public <T> RestResult<T> putFrom(String url, List<String> headers, Map<String, String> paramValues, Map<String, String> bodyValues, Type responseType) throws Exception {
        RequestHttpEntity requestHttpEntity = new RequestHttpEntity(
            Header.newInstance().addAll(headers),
            Query.newInstance().initParams(paramValues),
            toByte(bodyValues));

        ResponseExtractor<RestResult<T>> responseExtractor = responseEntityExtractor(responseType);
        return execute(url, HttpMethod.PUT, requestHttpEntity, responseExtractor);
    }

    @Override
    public <T> RestResult<T> post(String url, Header header, Query query, Object body, Type responseType) throws Exception {
        ResponseExtractor<RestResult<T>> responseExtractor = responseEntityExtractor(responseType);
        return execute(url, HttpMethod.POST, new RequestHttpEntity(header, query, toByte(body)), responseExtractor);
    }

    @Override
    public <T> RestResult<T> postJson(String url, List<String> headers, Map<String, String> paramValues, String body, Type responseType) throws Exception {
        RequestHttpEntity requestHttpEntity = new RequestHttpEntity(
            Header.newInstance().addAll(headers),
            Query.newInstance().initParams(paramValues),
            toByte(body));

        ResponseExtractor<RestResult<T>> responseExtractor = responseEntityExtractor(responseType);
        return execute(url, HttpMethod.POST, requestHttpEntity, responseExtractor);
    }

    @Override
    public <T> RestResult<T> postFrom(String url, List<String> headers, Map<String, String> paramValues, Map<String, String> bodyValues, Type responseType) throws Exception {
        RequestHttpEntity requestHttpEntity = new RequestHttpEntity(
            Header.newInstance().addAll(headers),
            Query.newInstance().initParams(paramValues),
            toByte(bodyValues));

        ResponseExtractor<RestResult<T>> responseExtractor = responseEntityExtractor(responseType);
        return execute(url, HttpMethod.POST, requestHttpEntity, responseExtractor);
    }


    private <T> T execute(String url, String httpMethod, RequestHttpEntity requestEntity,
                          ResponseExtractor<T> responseExtractor) throws Exception {
        URI uri = UriUtils.buildUri(url, requestEntity.getQuery());
        if (logger.isDebugEnabled()) {
            logger.debug("HTTP " + httpMethod + " " + url);
        }
        HttpClientResponse response = null;
        try {
            response = requestClient.execute(uri, httpMethod, requestEntity);
            return responseExtractor.extractData(response);
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    private <T> ResponseExtractor<RestResult<T>> responseEntityExtractor(Type responseType) {
        return new ResponseEntityExtractor<>(responseType);
    }


    private byte[] toByte(Object param) throws Exception {
        return RequestHandler.parse(param).getBytes(Constants.ENCODE);
    }

}
