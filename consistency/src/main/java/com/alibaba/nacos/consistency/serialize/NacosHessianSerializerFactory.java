/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

import com.caucho.hessian.io.SerializerFactory;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Nacos Hessian Serializer Factory.
 *
 * @author xiweng.yy
 */
public class NacosHessianSerializerFactory extends SerializerFactory {
    
    NacosHessianSerializerFactory() {
        super();
        super.getClassFactory().setWhitelist(true);
        allowBasicType();
        allowCollections();
        allowConcurrent();
        allowTime();
        super.getClassFactory().allow("com.alibaba.nacos.*");
    }
    
    private void allowBasicType() {
        super.getClassFactory().allow(boolean.class.getCanonicalName());
        super.getClassFactory().allow(byte.class.getCanonicalName());
        super.getClassFactory().allow(char.class.getCanonicalName());
        super.getClassFactory().allow(double.class.getCanonicalName());
        super.getClassFactory().allow(float.class.getCanonicalName());
        super.getClassFactory().allow(int.class.getCanonicalName());
        super.getClassFactory().allow(long.class.getCanonicalName());
        super.getClassFactory().allow(short.class.getCanonicalName());
        super.getClassFactory().allow(Boolean.class.getCanonicalName());
        super.getClassFactory().allow(Byte.class.getCanonicalName());
        super.getClassFactory().allow(Character.class.getCanonicalName());
        super.getClassFactory().allow(Double.class.getCanonicalName());
        super.getClassFactory().allow(Float.class.getCanonicalName());
        super.getClassFactory().allow(Integer.class.getCanonicalName());
        super.getClassFactory().allow(Long.class.getCanonicalName());
        super.getClassFactory().allow(Short.class.getCanonicalName());
        
        super.getClassFactory().allow(Number.class.getCanonicalName());
        super.getClassFactory().allow(Class.class.getCanonicalName());
        super.getClassFactory().allow(String.class.getCanonicalName());
    }
    
    private void allowCollections() {
        super.getClassFactory().allow(List.class.getCanonicalName());
        super.getClassFactory().allow(ArrayList.class.getCanonicalName());
        super.getClassFactory().allow(LinkedList.class.getCanonicalName());
        
        super.getClassFactory().allow(Set.class.getCanonicalName());
        super.getClassFactory().allow(HashSet.class.getCanonicalName());
        super.getClassFactory().allow(LinkedHashSet.class.getCanonicalName());
        super.getClassFactory().allow(TreeSet.class.getCanonicalName());
        
        super.getClassFactory().allow(Map.class.getCanonicalName());
        super.getClassFactory().allow(HashMap.class.getCanonicalName());
        super.getClassFactory().allow(LinkedHashMap.class.getCanonicalName());
        super.getClassFactory().allow(TreeMap.class.getCanonicalName());
        super.getClassFactory().allow(WeakHashMap.class.getCanonicalName());
        
        super.getClassFactory().allow("java.util.Arrays$ArrayList");
        super.getClassFactory().allow("java.util.Collections$EmptyList");
        super.getClassFactory().allow("java.util.Collections$EmptyMap");
        super.getClassFactory().allow("java.util.Collections$SingletonSet");
        super.getClassFactory().allow("java.util.Collections$SingletonList");
        super.getClassFactory().allow("java.util.Collections$UnmodifiableCollection");
        super.getClassFactory().allow("java.util.Collections$UnmodifiableList");
        super.getClassFactory().allow("java.util.Collections$UnmodifiableMap");
        super.getClassFactory().allow("java.util.Collections$UnmodifiableNavigableMap");
        super.getClassFactory().allow("java.util.Collections$UnmodifiableNavigableSet");
        super.getClassFactory().allow("java.util.Collections$UnmodifiableRandomAccessList");
        super.getClassFactory().allow("java.util.Collections$UnmodifiableSet");
        super.getClassFactory().allow("java.util.Collections$UnmodifiableSortedMap");
        super.getClassFactory().allow("java.util.Collections$UnmodifiableSortedSet");
    }
    
    private void allowConcurrent() {
        super.getClassFactory().allow(AtomicBoolean.class.getCanonicalName());
        super.getClassFactory().allow(AtomicInteger.class.getCanonicalName());
        super.getClassFactory().allow(AtomicLong.class.getCanonicalName());
        super.getClassFactory().allow(AtomicReference.class.getCanonicalName());
        
        super.getClassFactory().allow(ConcurrentMap.class.getCanonicalName());
        super.getClassFactory().allow(ConcurrentHashMap.class.getCanonicalName());
        super.getClassFactory().allow(ConcurrentSkipListMap.class.getCanonicalName());
        super.getClassFactory().allow(CopyOnWriteArrayList.class.getCanonicalName());
    }
    
    private void allowTime() {
        super.getClassFactory().allow(SimpleDateFormat.class.getCanonicalName());
        super.getClassFactory().allow(DateTimeFormatter.class.getCanonicalName());
        super.getClassFactory().allow(Instant.class.getCanonicalName());
        super.getClassFactory().allow(LocalDate.class.getCanonicalName());
        super.getClassFactory().allow(LocalDateTime.class.getCanonicalName());
        super.getClassFactory().allow(LocalTime.class.getCanonicalName());
        super.getClassFactory().allow(TimeUnit.class.getCanonicalName());
        super.getClassFactory().allow(Date.class.getCanonicalName());
        super.getClassFactory().allow(Calendar.class.getCanonicalName());
    }
}
