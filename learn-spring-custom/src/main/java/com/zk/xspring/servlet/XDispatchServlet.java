package com.zk.xspring.servlet;

import com.zk.xspring.annotation.XAutowired;
import com.zk.xspring.annotation.XController;
import com.zk.xspring.annotation.XRequestMapping;
import com.zk.xspring.annotation.XService;

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
import java.util.*;


public class XDispatchServlet extends HttpServlet {

    /**
     * 属性配置文件
     */
    private Properties contextConfig = new Properties();

    private List<String> classNameList = new ArrayList<String>();

    /**
     * IOC 容器
     */
    Map<String,Object> iocMap = new HashMap<String,Object>();


    Map<String, Method>handlerMapping = new HashMap<String, Method>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        //7、运行阶段
        try{
            doDispatch(req,resp);
        }catch (Exception e){
            e.printStackTrace();
            resp.getWriter().write("500 Exception Detail:\n" + Arrays.toString(e.getStackTrace()));
        }
    }

    /**
     * 7、运行阶段，进行拦截，匹配
     *
     * @param req  请求
     * @param resp 响应
     */
    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws InvocationTargetException, IllegalAccessException {
        String url = req.getRequestURI();

        String contextPath = req.getContextPath();

        url = url.replaceAll(contextPath,"").replaceAll("/+","/");

        System.out.println("[INFO-7] request url-->" + url);

        if(!this.handlerMapping.containsKey(url)){
            try{
                resp.getWriter().write("404 NOT FOUND!!");
                return;
            }catch (IOException e){
                e.printStackTrace();
            }
        }

        Method method = this.handlerMapping.get(url);

        System.out.println("[INFO-7] method-->" + method);

        String beanName = toLowerFirstCase(method.getDeclaringClass().getSimpleName());

        System.out.println("[INFO-7] iocMap.get(beanName)->" + iocMap.get(beanName));

        // 第一个参数是获取方法，后面是参数，多个参数直接加，按顺序对应
        method.invoke(iocMap.get(beanName),req,resp);

        System.out.println("[INFO-7] method.invoke put {" + iocMap.get(beanName) + "}.");

    }


    @Override
    public void init(ServletConfig servletConfig) throws ServletException {
        //1、加载配置文件
        doLoadConfig(servletConfig.getInitParameter("contextConfigLocation"));

        //2、扫描相关的类
        doScanner(contextConfig.getProperty("scan-package"));

        //3、初始化 IOC 容器，将所有相关的类实例保存到 IOC 容器中
        doInstance();
        //4、依赖注入
        doAutowired();

        //5、初始化 HandlerMapping
        initHandlerMapping();

        System.out.println("XSpring FrameWork is init.");

        //6、打印数据
        doTestPrintData();
    }

    /**
     * 6、打印数据
     */
    private void doTestPrintData() {

        System.out.println("[INFO-6]----data------------------------");

        System.out.println("contextConfig.propertyNames()-->" + contextConfig.propertyNames());

        System.out.println("[classNameList]-->");
        for (String str : classNameList) {
            System.out.println(str);
        }

        System.out.println("[iocMap]-->");
        for (Map.Entry<String, Object> entry : iocMap.entrySet()) {
            System.out.println(entry);
        }

        System.out.println("[handlerMapping]-->");
        for (Map.Entry<String, Method> entry : handlerMapping.entrySet()) {
            System.out.println(entry);
        }

        System.out.println("[INFO-6]----done-----------------------");

        System.out.println("====启动成功====");
        System.out.println("测试地址：http://localhost:8080/test/query?username=xiaopengwei");
        System.out.println("测试地址：http://localhost:8080/test/listClassName");
    }

    /**
     * 5、初始化 HandlerMapping
     */
    private void initHandlerMapping() {

        if (iocMap.isEmpty()) {
            return;
        }

        for(Map.Entry<String,Object> entry : iocMap.entrySet()){
            Class<?> clazz = entry.getValue().getClass();

            if(!clazz.isAnnotationPresent(XController.class)){
                continue;
            }

            String baseUrl = "";
            if(clazz.isAnnotationPresent(XRequestMapping.class)){
                XRequestMapping xRequestMapping = clazz.getAnnotation(XRequestMapping.class);
                baseUrl = xRequestMapping.value();
            }

            for(Method method : clazz.getMethods()){
                if(!method.isAnnotationPresent(XRequestMapping.class)){
                    continue;
                }

                XRequestMapping xRequestMapping = method.getAnnotation(XRequestMapping.class);

                String url = ("/" + baseUrl + "/" + xRequestMapping.value()).replaceAll("/+", "/");

                handlerMapping.put(url,method);

                System.out.println("[INFO-5] handlerMapping put {" + url + "} - {" + method + "}.");

            }

        }



    }

    /**
     * 4、依赖注入
     */
    private void doAutowired() {
       if(iocMap.isEmpty()){
           return;
       }

       for(Map.Entry<String,Object> entry : iocMap.entrySet()){
           Field[] fields = entry.getValue().getClass().getDeclaredFields();

           for(Field field :fields){
               if(!field.isAnnotationPresent(XAutowired.class)){
                   continue;
               }

               System.out.println("[INFO-4] Existence XAutowired.");

               // 获取注解对应的类
               XAutowired xAutowired = field.getAnnotation(XAutowired.class);
               String beanName = xAutowired.value().trim();

               //获取 XAutowired 注解的值
               if("".equals(beanName)){
                   System.out.println("[INFO] xAutowired.value() is null");
                   beanName = field.getType().getName();
               }

               // 只要加了注解，都要加载，不管是 private 还是 protect
                field.setAccessible(true);

               try{
                   field.set(entry.getValue(),iocMap.get(beanName));
                   System.out.println("[INFO-4] field set {" + entry.getValue() + "} - {" + iocMap.get(beanName) + "}.");
               }catch (Exception e){
                   e.printStackTrace();
               }


           }

       }
    }


    /**
     * 3、初始化 IOC 容器，将所有相关的类实例保存到 IOC 容器中
     */
    private void doInstance() {
        if(classNameList.isEmpty()){
            return;
        }

        try{
            for(String className :classNameList){
                Class<?> clazz = Class.forName(className);

                //方法返回true，如果指定类型的注释存在于此元素上,否则返回false。这种方法的设计主要是为了方便访问标记注释.
                if(clazz.isAnnotationPresent(XController.class)){//判断是否是controller
                    String beanName = toLowerFirstCase(clazz.getSimpleName());
                    Object instance = clazz.newInstance();

                    // 保存在 ioc 容器
                    iocMap.put(beanName,instance);
                    System.out.println("[INFO-3] {" + beanName + "} has been saved in iocMap.");
                } else if (clazz.isAnnotationPresent(XService.class)){
                    String beanName = toLowerFirstCase(clazz.getSimpleName());

                    // 如果注解包含自定义名称 todo ?
                    XService xService = clazz.getAnnotation(XService.class);
                    if(!"".equals(xService.value())){
                        beanName = xService.value();
                    }

                    Object instance = clazz.newInstance();
                    iocMap.put(beanName,instance);
                    System.out.println("[INFO-3] {" + beanName + "} has been saved in iocMap.");

                    //找类的接口
                    for(Class<?> i :clazz.getInterfaces()){
                        if(iocMap.containsKey(i.getName())){
                            throw new Exception("The Bean Name Is Exist.");
                        }

                        iocMap.put(i.getName(),instance);
                        System.out.println("[INFO-3] {" + i.getName() + "} has been saved in iocMap.");
                    }
                }
            }

        }catch (Exception e){
            e.printStackTrace();
        }


    }

    /**
     * 获取类的首字母小写的名称
     *
     * @param className ClassName
     * @return java.lang.String
     */
    private String toLowerFirstCase(String className) {
        char[] charArray = className.toCharArray();
        charArray[0] += 32;
        return String.valueOf(charArray);
    }

    /**
     * 2、扫描相关的类
     * @param scanPackage properties --> scan-package
     */
    private void doScanner(String scanPackage) {
        URL resourcePath = this.getClass().getClassLoader().getResource("/"+scanPackage.replaceAll("\\.","/"));
        if(resourcePath == null){
            return;
        }

        File classPath = new File(resourcePath.getFile());
        for(File file : classPath.listFiles()){
            if(file.isDirectory()){
                System.out.println("[INFO-2] {" + file.getName() + "} is a directory.");

                // 子目录递归
                doScanner(scanPackage + "." + file.getName());
            }else {
                if (!file.getName().endsWith(".class")) {
                    System.out.println("[INFO-2] {" + file.getName() + "} is not a class file.");
                    continue;
                }

                String className = (scanPackage + "." + file.getName()).replace(".class", "");

                // 保存在内容
                classNameList.add(className);

                System.out.println("[INFO-2] {" + className + "} has been saved in classNameList.");
            }
        }
    }

    /**
     * 1、加载配置文件
     * @param contextConfigLocation web.xml --> servlet/init-param
     */
    private void doLoadConfig(String contextConfigLocation) {
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try {
            //保存在内存
            contextConfig.load(inputStream);
            System.out.println("[INFO-1] property file has been saved in contextConfig.");
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if(null != inputStream){
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
