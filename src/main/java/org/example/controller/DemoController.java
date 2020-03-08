package org.example.controller;

import org.example.mvcFramework.annotation.RAutowired;
import org.example.mvcFramework.annotation.RController;
import org.example.mvcFramework.annotation.RRequestMapping;
import org.example.mvcFramework.annotation.RRequestParam;
import org.example.service.DemoService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @Description TODO
 * @Author ooooor
 * @Date 2020/3/4 23:17
 **/
@RController
@RRequestMapping("/demo")
public class DemoController {
    @RAutowired
    private DemoService demoService;

    @RRequestMapping("/query")
    public void query(HttpServletRequest request, HttpServletResponse response, @RRequestParam("name") String name) throws IOException {
        String res = demoService.get(name);
        response.getWriter().write(res);
    }
}
