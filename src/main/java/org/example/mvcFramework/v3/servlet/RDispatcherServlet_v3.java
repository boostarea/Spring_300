package org.example.mvcFramework.v3.servlet;

import org.example.mvcFramework.annotation.RAutowired;
import org.example.mvcFramework.annotation.RController;
import org.example.mvcFramework.annotation.RRequestMapping;
import org.example.mvcFramework.annotation.RRequestParam;
import org.example.mvcFramework.annotation.RService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Description TODO
 * @Author ooooor
 * @Date 2020/3/10 21:47
 **/
public class RDispatcherServlet_v3 extends HttpServlet {

    //application.properties配置内容
    private Properties contextConfig = new Properties();
    //扫描所有的类名
    private List<String> classNames = new ArrayList<>();

    private Map<String, Object> ioc = new HashMap<>();
    //保存url和Method的对应关系
    private List<Handler> handlerMapping = new ArrayList<>();


    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            //委派模式
            this.doDispatch(req, resp);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        //STEP1 加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));

        //STEP2 扫描相关类
        doScanner(contextConfig.getProperty("scanPackage"));

        //STEP3 初始化扫描到的类，并且将他们放入IoC容器中
        doInstance();

        //STEP4 完成依赖注入
        doAutowired();

        //STEP5 初始化HandlerMapping
        initHandlerMapping();

        System.out.println("R MVC Framework is init!");
    }

    private void doLoadConfig(String contextConfigLocation) {
        //将配置保存到内存中
        try (InputStream is = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation)) {
            contextConfig.load(is);
        } catch (IOException e) {
            e.printStackTrace();
        }
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
                classNames.add(clazzName);
            }
        }
    }

    private void doInstance() {
        if (classNames.isEmpty()) {
            return;
        }

        try {
            for (String className: classNames) {
                Class<?> clazz = Class.forName(className);

                if (clazz.isAnnotationPresent(RController.class)) {
                    Object instance = clazz.newInstance();
                    //Spring默认类名首字母小写
                    String beanName = toLowerFirstCase(clazz.getSimpleName());
                    ioc.put(beanName, instance);
                } else if (clazz.isAnnotationPresent(RService.class)) {
                    RService service = clazz.getAnnotation(RService.class);
                    String beanName = service.value();
                    if ("".equals(beanName.trim())) {
                        beanName = toLowerFirstCase(clazz.getSimpleName());
                    }

                    Object instance = clazz.newInstance();
                    ioc.put(beanName, instance);
                    // 根据类型自动赋值
                    for (Class<?> i : clazz.getInterfaces()) {
                        if (ioc.containsKey(i.getName())) {
                            throw new Exception("the " + i.getName() + " is exists");
                        }
                        ioc.put(i.getName(), instance);
                    }
                } else {
                    continue;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String toLowerFirstCase(String simpleName) {
        char[] chars = simpleName.toCharArray();
        // 小写字母比大写字母相差32
        // char算术运算实际就是ASCII码运算
        chars[0] += 32;
        return String.valueOf(chars);
    }

    private void doAutowired() {
        if (ioc.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Field[] fields =  entry.getValue().getClass().getDeclaredFields();
            for (Field field: fields) {
                if (!field.isAnnotationPresent(RAutowired.class)) {
                    continue;
                }
                RAutowired autowired = field.getAnnotation(RAutowired.class);

                //若没有自定义beanName，默认按类型注入
                String beanName = autowired.value().trim();
                if ("".equals(beanName)) {
                    beanName = field.getType().getName();
                }

                //强制赋值，暴力访问
                field.setAccessible(true);

                try {
                    //动态给字段赋值
                    field.set(entry.getValue(), ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void initHandlerMapping() {
        if (ioc.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            if (!clazz.isAnnotationPresent(RController.class)) {
                continue;
            }

            String baseUrl = "";
            if (clazz.isAnnotationPresent(RRequestMapping.class)) {
                RRequestMapping requestMapping = clazz.getAnnotation(RRequestMapping.class);
                baseUrl = requestMapping.value();
            }

            //默认获取所有public方法
            for (Method method : clazz.getMethods()) {
                if (!method.isAnnotationPresent(RRequestMapping.class)) {
                    continue;
                }
                RRequestMapping requestMapping = method.getAnnotation(RRequestMapping.class);
                String regex = ("/" + baseUrl + "/" + requestMapping.value()).replaceAll("/+", "/");

                Pattern pattern = Pattern.compile(regex);
                handlerMapping.add(new Handler(pattern, entry.getValue(), method));
                System.out.println("Mapped: " + regex + "," + method);
            }
        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {

        Handler handle = getHandler(req);
        if (handle == null) {
            resp.getWriter().write("404 Not Found");
            return;
        }

        Class<?>[] parameterTypes = handle.method.getParameterTypes();
        // URL参数列表
        Map<String, String[]> params = req.getParameterMap();
        Object[] paramValues = new Object[parameterTypes.length];

        for (Map.Entry<String, String[]> param : params.entrySet()){
            String value = Arrays.toString(param.getValue())
                    .replaceAll("\\[|\\]", "")
                    .replaceAll("\\s", ",");
            if (!handle.paramIndexMapping.containsKey(param.getKey())) {
                continue;
            }
            int index = handle.paramIndexMapping.get(param.getKey());
            paramValues[index] = convert(parameterTypes[index], value);
        }

        if (handle.paramIndexMapping.containsKey(HttpServletRequest.class.getName())) {
            int reqIndex = handle.paramIndexMapping.get(HttpServletRequest.class.getName());
            paramValues[reqIndex] = req;
        }
        if (handle.paramIndexMapping.containsKey(HttpServletResponse.class.getName())) {
            int reqIndex = handle.paramIndexMapping.get(HttpServletResponse.class.getName());
            paramValues[reqIndex] = resp;
        }

        Object returnValue = handle.method.invoke(handle.controller, paramValues);
        if (null == returnValue || returnValue instanceof Void) {
            return;
        }
        resp.getWriter().write(returnValue.toString());
    }


    private Handler getHandler(HttpServletRequest request) {
        if (handlerMapping.isEmpty()) {
            return null;
        }
        String url = request.getRequestURI();
        String contextPah = request.getContextPath();
        url = url.replace(contextPah, "").replaceAll("/+", "/");

        for (Handler handler : handlerMapping) {
            try {
                Matcher matcher = handler.pattern.matcher(url);
                if (!matcher.matches()) {
                    continue;
                }
                return handler;
            } catch (Exception e) {
                throw e;
            }
        }
        return null;
    }

    private Object convert(Class<?> type, String value) {
        if (Integer.class == type) {
            return Integer.valueOf(value);
        }
        return value;
    }

    /**
     * 记录Controller中的RequestMapping和Method的对应关系
     */
    public class Handler {
        // 保存方法对应的实例
        protected Object controller;
        // 映射的方法
        protected Method method;
        protected Pattern pattern;
        // 参数顺序
        protected Map<String, Integer> paramIndexMapping;

        protected Handler(Pattern pattern, Object controller, Method method) {
            this.controller = controller;
            this.method = method;
            this.pattern = pattern;
            paramIndexMapping = new HashMap<>();
            putParamIndexMapping(method);
        }

        private void putParamIndexMapping(Method method) {
            //提取方法中加了注解的参数
            Annotation[][] pa = method.getParameterAnnotations();
            for (int i = 0; i < pa.length; i++) {
                for (Annotation a : pa[i]) {
                    if (a instanceof RRequestParam) {
                        String paramName = ((RRequestParam) a).value();
                        if (!"".equals(paramName.trim())) {
                            paramIndexMapping.put(paramName, i);
                        }
                    }
                }
            }
            // 提取方法中的request和response参数
            Class<?>[] paramsTypes = method.getParameterTypes();
            for (int i = 0; i < paramsTypes.length; i++) {
                Class<?> type = paramsTypes[i];
                if (type == HttpServletRequest.class || type == HttpServletResponse.class) {
                    paramIndexMapping.put(type.getName(), i);
                }
            }
        }
    }
}
