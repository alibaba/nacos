package com.alibaba.nacos.test.base;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URL;

/**
 * Http client for test module
 *
 * @author nkorange
 * @since 1.2.0
 */
public class HttpClient4Test {

    protected URL base;

    @Autowired
    protected TestRestTemplate restTemplate;

    protected <T> ResponseEntity<T> request(String path, MultiValueMap<String, String> params, Class<T> clazz) {

        HttpHeaders headers = new HttpHeaders();

        HttpEntity<?> entity = new HttpEntity<T>(headers);

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(this.base.toString() + path)
            .queryParams(params);

        return this.restTemplate.exchange(
            builder.toUriString(), HttpMethod.GET, entity, clazz);
    }

    protected <T> ResponseEntity<T> request(String path, MultiValueMap<String, String> params, Class<T> clazz, HttpMethod httpMethod) {

        HttpHeaders headers = new HttpHeaders();

        HttpEntity<?> entity = new HttpEntity<T>(headers);

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(this.base.toString() + path)
            .queryParams(params);

        return this.restTemplate.exchange(
            builder.toUriString(), httpMethod, entity, clazz);
    }
}
