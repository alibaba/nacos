/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.controller;

import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller to redirect root path to the default console UI.
 *
 * <p>The default UI version is controlled by the property {@code nacos.console.ui.default}.
 * Supported values: "next" (new UI) and "legacy" (old UI). Defaults to "next".
 *
 * @author zhuoguang
 */
@Controller
public class ConsoleRedirectController {
    
    private static final String PROPERTY_DEFAULT_UI = "nacos.console.ui.default";
    
    private static final String UI_LEGACY = "legacy";
    
    private static final String UI_NEXT = "next";
    
    @Since("3.2.0")
    @GetMapping("/")
    public String index() {
        String defaultUi = EnvUtil.getProperty(PROPERTY_DEFAULT_UI, UI_NEXT);
        return UI_LEGACY.equals(defaultUi) ? "redirect:/legacy/" : "redirect:/next/";
    }
    
    @Since("3.2.0")
    @GetMapping("/next/")
    public String next() {
        return "forward:/next/index.html";
    }
    
    @Since("3.2.0")
    @GetMapping("/legacy/")
    public String legacy() {
        return "forward:/legacy/index.html";
    }
}
