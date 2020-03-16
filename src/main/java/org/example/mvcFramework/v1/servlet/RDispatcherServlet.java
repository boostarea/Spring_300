package org.example.mvcFramework.v1.servlet;

import org.example.mvcFramework.annotation.RAutowired;
import org.example.mvcFramework.annotation.RController;
import org.example.mvcFramework.annotation.RRequestMapping;
import org.example.mvcFramework.annotation.RService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

/**
 * @Description TODO
 * @Author ooooor
 * @Date 2020/3/4 23:34
 **/
public class RDispatcherServlet extends HttpServlet {

    private Map<String, Object> mapping = new HashMap<>();

    private Map<String, Object> UrlMapping = new HashMap<>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            this.doDispatch(req, resp);
        } catch (InvocationTargetException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        InputStream is = null;
        try {
            Properties configContext = new Properties();
            is = this.getClass().getClassLoader().getResourceAsStream(config.getInitParameter("contextConfigLocation"));
            configContext.load(is);

            String scanPackage = configContext.getProperty("scanPackage");
            doScanner(scanPackage);
            Iterator<Map.Entry<String, Object>> iterator = mapping.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Object> entry = iterator.next();
                String className = entry.getKey();
                if (!className.contains(".")) {
                    continue;
                }
                Class<?> clazz = Class.forName(className);

                if (clazz.isAnnotationPresent(RController.class)) {
                    entry.setValue(clazz.newInstance());
                    // mapping.put(className, clazz.newInstance());
                    String baseUrl = "";

                    if (clazz.isAnnotationPresent(RRequestMapping.class)) {
                        RRequestMapping requestMapping = clazz.getAnnotation(RRequestMapping.class);
                        baseUrl = requestMapping.value();
                    }

                    Method[] methods = clazz.getMethods();
                    for (Method method : methods) {
                        if (!method.isAnnotationPresent(RRequestMapping.class)) {
                            continue;
                        }
                        RRequestMapping requestMapping = method.getAnnotation(RRequestMapping.class);
                        String url = (baseUrl + "/" + requestMapping.value()).replaceAll("/+", "/");

                        // entry.setValue(method);
                        UrlMapping.put(url, method);
                        System.out.println("Mapped " + url + "," + method);
                    }
                } else if (clazz.isAnnotationPresent(RService.class)) {
                    RService service = clazz.getAnnotation(RService.class);
                    String beanName = service.value();
                    if ("".equals(beanName)) {
                        beanName = clazz.getName();
                    }
                    Object instance = clazz.newInstance();
                    entry.setValue(instance);
                    // mapping.put(beanName, instance);
                    for (Class<?> i : clazz.getInterfaces()) {
                        mapping.put(i.getName(), instance);
                    }
                } else {
                    continue;
                }
            }

            if (!UrlMapping.isEmpty()) {
                mapping.putAll(UrlMapping);
            }


            for (Object object : mapping.values()) {
                if (null == object) {
                    continue;
                }
                Class clazz = object.getClass();
                if (clazz.isAnnotationPresent(RController.class)) {
                    Field[] fields = clazz.getDeclaredFields();
                    for (Field field : fields) {
                        if (!field.isAnnotationPresent(RAutowired.class)) {
                            continue;
                        }
                        RAutowired autowired = field.getAnnotation(RAutowired.class);
                        String beanName = autowired.value();

                        if ("".equals(beanName)) {
                            beanName = field.getType().getName();
                        }
                        field.setAccessible(true);
                        try {
                            field.set(mapping.get(clazz.getName()), mapping.get(beanName));
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        System.out.println("R MVC Framework is init!");
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws IOException, InvocationTargetException, IllegalAccessException {
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();

        url = url.replace(contextPath, "").replaceAll("/+", "/");
        if (!this.mapping.containsKey(url)) {
            resp.getWriter().write("404 Not Found");
        }

        Method method = (Method) this.mapping.get(url);
        Map<String, String[]> params = req.getParameterMap();
        method.invoke(this.mapping.get(method.getDeclaringClass().getName()), new Object[]{req, resp, params.get("name")[0]});

    }

    private void doScanner(String scanPackage) {
        URL url = this.getClass().getClassLoader().getResource("/" +  scanPackage.replaceAll("\\.", "/"));
        File classDir = new File(url.getFile());
        for (File file : classDir.listFiles()) {
            if (file.isDirectory()) {
                doScanner(scanPackage + "." + file.getName());
            } else {

                if (!file.getName().endsWith(".class")) {
                    continue;
                }
                String clazzName = (scanPackage + "." + file.getName().replaceAll(".class", ""));
                mapping.put(clazzName, null);
            }
        }
    }
}
