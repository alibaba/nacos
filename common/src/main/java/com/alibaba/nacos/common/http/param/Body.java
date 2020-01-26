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

package com.alibaba.nacos.common.http.param;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class Body {

    public static final Body EMPTY = Body.newInstance();

    private final Map<String, Object> body;

    private boolean dataInit = false;

    private boolean bodyInit = false;

    private Object data;

    private Body() {
        body = new HashMap<String, Object>();
    }

    public static Body newInstance() {
        return new Body();
    }

    public Body addParam(String key, Object value) {
        if (!dataInit) {
            bodyInit = true;
            body.put(key, value);
            return this;
        }
        else {
            throw new IllegalStateException(
                "class Body field data has been initialized, and may not add other data");
        }
    }

    public void initBody(Map body) {
        bodyInit = true;
        this.body.putAll(body);
    }

    public void initBody(List<Object> list) {
        if ((list.size() & 1) != 0) {
            throw new IllegalArgumentException("list size must be a multiple of 2");
        }
        for (int i = 0; i < list.size();) {
            addParam(String.valueOf(list.get(i++)), list.get(i++));
        }
    }

    public Object getValue(String key) {
        return body.get(key);
    }

    public Map<String, Object> getBody() {
        return body;
    }

    public Object getData() {
        return data == null ? body : data;
    }

    public Iterator<Map.Entry<String, Object>> iterator() {
        return body.entrySet().iterator();
    }

    public static Body objToBody(Object obj) {
        Body body = Body.newInstance();
        body.dataInit = true;
        body.data = obj;
        return body;
    }

    public void clear() {
        data = null;
        body.clear();
    }

}
