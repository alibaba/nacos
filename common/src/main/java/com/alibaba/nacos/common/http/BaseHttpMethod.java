/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.common.http;

import com.alibaba.nacos.common.utils.HttpMethod;
import com.alibaba.nacos.common.utils.StringUtils;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.classic.methods.HttpPatch;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.classic.methods.HttpTrace;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;

import java.net.URI;

/**
 * Base http method.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public enum BaseHttpMethod {
    
    /**
     * get request.
     */
    GET(HttpMethod.GET) {
        @Override
        protected HttpUriRequestBase createRequest(String url) {
            return new HttpGet(url);
        }
    },
    
    GET_LARGE(HttpMethod.GET_LARGE) {
        @Override
        protected HttpUriRequestBase createRequest(String url) {
            return new HttpGetWithEntity(url);
        }
    },
    
    /**
     * post request.
     */
    POST(HttpMethod.POST) {
        @Override
        protected HttpUriRequestBase createRequest(String url) {
            return new HttpPost(url);
        }
    },
    
    /**
     * put request.
     */
    PUT(HttpMethod.PUT) {
        @Override
        protected HttpUriRequestBase createRequest(String url) {
            return new HttpPut(url);
        }
    },
    
    /**
     * delete request.
     */
    DELETE(HttpMethod.DELETE) {
        @Override
        protected HttpUriRequestBase createRequest(String url) {
            return new HttpDelete(url);
        }
    },
    
    /**
     * delete Large request.
     */
    DELETE_LARGE(HttpMethod.DELETE_LARGE) {
        @Override
        protected HttpUriRequestBase createRequest(String url) {
            return new HttpDeleteWithEntity(url);
        }
    },
    
    /**
     * head request.
     */
    HEAD(HttpMethod.HEAD) {
        @Override
        protected HttpUriRequestBase createRequest(String url) {
            return new HttpHead(url);
        }
    },
    
    /**
     * trace request.
     */
    TRACE(HttpMethod.TRACE) {
        @Override
        protected HttpUriRequestBase createRequest(String url) {
            return new HttpTrace(url);
        }
    },
    
    /**
     * patch request.
     */
    PATCH(HttpMethod.PATCH) {
        @Override
        protected HttpUriRequestBase createRequest(String url) {
            return new HttpPatch(url);
        }
    },
    
    /**
     * options request.
     */
    OPTIONS(HttpMethod.OPTIONS) {
        @Override
        protected HttpUriRequestBase createRequest(String url) {
            return new HttpTrace(url);
        }
    };
    
    private String name;
    
    BaseHttpMethod(String name) {
        this.name = name;
    }
    
    public HttpUriRequestBase init(String url) {
        return createRequest(url);
    }
    
    protected HttpUriRequestBase createRequest(String url) {
        throw new UnsupportedOperationException();
    }
    
    /**
     * Value of {@link BaseHttpMethod}.
     *
     * @param name method name
     * @return {@link BaseHttpMethod}
     */
    public static BaseHttpMethod sourceOf(String name) {
        for (BaseHttpMethod method : BaseHttpMethod.values()) {
            if (StringUtils.equalsIgnoreCase(name, method.name)) {
                return method;
            }
        }
        throw new IllegalArgumentException("Unsupported http method : " + name);
    }
    
    /**
     * get Large implemented.
     * <p>
     * Mainly used for GET request parameters are relatively large, can not be placed on the URL, so it needs to be
     * placed in the body.
     * </p>
     */
    public static class HttpGetWithEntity extends HttpUriRequestBase {
        
        public static final String METHOD_NAME = "GET";
        
        public HttpGetWithEntity(String url) {
            super(METHOD_NAME, URI.create(url));
        }
    }
    
    /**
     * delete Large implemented.
     * <p>
     * Mainly used for DELETE request parameters are relatively large, can not be placed on the URL, so it needs to be
     * placed in the body.
     * </p>
     */
    public static class HttpDeleteWithEntity extends HttpUriRequestBase {
        
        public static final String METHOD_NAME = "DELETE";
        
        public HttpDeleteWithEntity(String url) {
            super(METHOD_NAME, URI.create(url));
        }
    }
    
}
