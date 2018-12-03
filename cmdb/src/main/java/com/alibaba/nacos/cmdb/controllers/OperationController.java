package com.alibaba.nacos.cmdb.controllers;

import com.alibaba.nacos.cmdb.core.SwitchAndOptions;
import com.alibaba.nacos.cmdb.utils.UtilsAndCommons;
import com.alibaba.nacos.common.util.WebUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 */
@RestController
@RequestMapping(UtilsAndCommons.NACOS_CMDB_CONTEXT + "/ops")
public class OperationController {

    @Autowired
    private SwitchAndOptions switches;

    @RequestMapping(value = "/updateSwitch", method = RequestMethod.POST)
    public String updateSwitch(HttpServletRequest request) throws Exception {

        String entry = WebUtils.required(request, "entry");
        String value = WebUtils.required(request, "value");

        switch (entry) {
            case "dumpTaskInterval":
                switches.setDumpTaskInterval(Integer.parseInt(value));
                break;
            case "eventTaskInterval":
                switches.setEventTaskInterval(Integer.parseInt(value));
                break;
            case "loadDataAtStart":
                switches.setLoadDataAtStart(Boolean.parseBoolean(value));
                break;
            default:
                break;
        }
        return "ok";
    }
}