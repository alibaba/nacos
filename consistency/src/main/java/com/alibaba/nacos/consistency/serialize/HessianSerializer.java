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

package com.alibaba.nacos.consistency.serialize;

import com.alibaba.nacos.common.utils.ByteUtils;
import com.alibaba.nacos.consistency.Serializer;
import com.caucho.hessian.io.Hessian2Input;
import com.caucho.hessian.io.Hessian2Output;
import com.caucho.hessian.io.SerializerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;

/**
 * hessian serializer.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public class HessianSerializer implements Serializer {
    
    private static final String NAME = "Hessian";
    
    private SerializerFactory serializerFactory = new SerializerFactory();
    
    public HessianSerializer() {
    }
    
    @Override
    public <T> T deserialize(byte[] data) {
        return deseiralize0(data);
    }
    
    @Override
    public <T> T deserialize(byte[] data, Class<T> cls) {
        return deserialize(data);
    }
    
    @Override
    public <T> T deserialize(byte[] data, Type type) {
        return deserialize(data);
    }
    
    private <T> T deseiralize0(byte[] data) {
        if (ByteUtils.isEmpty(data)) {
            return null;
        }
    
        Hessian2Input input = new Hessian2Input(new ByteArrayInputStream(data));
        input.setSerializerFactory(serializerFactory);
        Object resultObject;
        try {
            resultObject = input.readObject();
            input.close();
        } catch (IOException e) {
            throw new RuntimeException("IOException occurred when Hessian serializer decode!", e);
        }
        return (T) resultObject;
    }
    
    @Override
    public <T> byte[] serialize(T obj) {
        ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
        Hessian2Output output = new Hessian2Output(byteArray);
        output.setSerializerFactory(serializerFactory);
        try {
            output.writeObject(obj);
            output.close();
        } catch (IOException e) {
            throw new RuntimeException("IOException occurred when Hessian serializer encode!", e);
        }
        
        return byteArray.toByteArray();
    }
    
    @Override
    public String name() {
        return NAME;
    }
    
}
