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
package com.alibaba.nacos.test.naming;

import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.naming.NamingApp;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URL;
import java.util.Arrays;

/**
 * Auth related cases
 *
 * @author nkorange
 * @since 1.2.0
 */
//@RunWith(SpringRunner.class)
//@SpringBootTest(classes = NamingApp.class, properties = {"server.servlet.context-path=/nacos"},
//    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class Auth_ITCase extends NamingBase {

//    private NamingService naming;
//
//    @LocalServerPort
//    private int port;
//
//    @Before
//    public void init() throws Exception {
//        NamingBase.prepareServer(port);
//
//        if (naming == null) {
//            naming = NamingFactory.createNamingService("127.0.0.1" + ":" + port);
//        }
//        while (true) {
//            if (!"UP".equals(naming.getServerStatus())) {
//                Thread.sleep(1000L);
//                continue;
//            }
//            break;
//        }
//        String url = String.format("http://localhost:%d/", port);
//        this.base = new URL(url);
//    }

    @Test
    public void testAuth() {


    }

    public int numSimilarGroups(String[] A) {
        char[][] css = new char[A.length][];
        int i = 0;
        for (String a : A) {
            css[i++] = a.toCharArray();
        }
        boolean[] used = new boolean[A.length];
        int res=0;
        for (i = 0; i < A.length; i++) {
            if (!used[i]) {
                res++;
                used[i] = true;
                bfs(used, css, i);
            }
        }
        return res;
    }

    public void bfs(boolean[] used, char[][] css, int start) {
        int[] nexts = new int[]{start};
        int size = 1;
        while (size > 0) {
            int[] tmp = new int[used.length];
            int size2 = 0;
            for (int j = 0; j < size; j++) {
                for (int i = 0; i < used.length; i++) {
                    if (!used[i] && similar(css[nexts[j]], css[i])) {
                        tmp[size2++] = i;
                        used[i] = true;
                    }
                }
            }
            nexts = tmp;
            size = size2;
        }
    }

    public boolean similar(char[] cs1, char[] cs2) {
        int dif = 0;
        for (int i = 0; i < cs1.length && dif <= 2; i++) {
            if (cs1[i] != cs2[i]) {
                dif++;
            }
        }
        return dif <= 2;
    }
}
