package com.chenfeng.ssoclient.filter;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Enumeration;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.chenfeng.ssoclient.model.LoginUser;
import com.chenfeng.ssoclient.service.TokenManager;
import com.chenfeng.ssoclient.service.UserHolder;
import com.chenfeng.ssoclient.util.CookieUtil;
import com.chenfeng.ssoclient.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 登录验证拦截器
 *
 */
public class SSOFilter implements Filter {

    private Logger logger = LoggerFactory.getLogger(SSOFilter.class);

    private String excludes; // 不需要拦截的URI模式，以正则表达式表示
    private String ssoServerUrl; //
    private boolean notLoginOnFail; // 当授权失败时是否让浏览器跳转到服务端登录页

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        excludes = filterConfig.getInitParameter("excludes");
        ssoServerUrl = filterConfig.getInitParameter("ssoServerUrl");

        notLoginOnFail = Boolean.parseBoolean(filterConfig.getInitParameter("notLoginOnFail"));

        if (ssoServerUrl == null) {
            throw new ServletException("ssoServerUrl不能为空!");
        }
        TokenManager.ssoServerUrl = ssoServerUrl;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException,
            ServletException {

        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) resp;

        // 如果是不需要拦截的请求，直接通过
        if (requestIsExclude(request)) {
            chain.doFilter(request, response);
            return;
        }

        logger.info("SSOFilter==>>url: {}", request.getRequestURL());

        // 进行登录状态验证
        String xtoken = CookieUtil.getCookie("xtoken", request);
        if (xtoken != null) {
            LoginUser user = null;

            try {
                user = TokenManager.validate(xtoken);
            } catch (Exception e) {
                throw new ServletException(e);
            }

            if (user != null) {   
                holdUser(user, request); // 将user存放，供业务系统使用
                chain.doFilter(request, response); // 请求继续向下执行
            } else {
                // 删除无效的xtoken cookie
                CookieUtil.deleteCookie("xtoken", response, "/");
                // 引导浏览器重定向到服务端执行登录校验
                loginCheck(request, response);
            }
        } else {
            String xtokenParam = pasrextokenParam(request);
            if (xtokenParam == null) {
                // 请求中中没有xtokenParam，引导浏览器重定向到服务端执行登录校验
                loginCheck(request, response);
            } else if (xtokenParam.length() == 0) {
                // 有xtokenParam，但内容为空，表示到服务端loginCheck后，得到的结果是未登录
                response.sendError(403);
            } else {
                // 让浏览器向本链接发起一次重定向，此过程去除xtokenParam，将xtoken写入cookie
                redirectToSelf(xtokenParam, request, response);
            }
        }
    }

    // 从参数中获取服务端传来的xtoken后，执行一个到本链接的重定向，将xtoken写入cookie
    // 重定向后再发来的请求就存在有效xtoken参数了
    private void redirectToSelf(String xtoken, HttpServletRequest request, HttpServletResponse response) throws IOException {
        final String PARANAME = "__xtoken_param__="; 
        // 此处拼接redirect的url，去除xtoken参数部分
        StringBuffer location = request.getRequestURL();
       
        String qstr = request.getQueryString();
        int index = qstr.indexOf(PARANAME);
        if (index > 0) { // 还有其它参数，para1=param1&param2=param2&__xtoken_param__=xxx是最后一个参数
            qstr = "?" + qstr.substring(0, qstr.indexOf(PARANAME) - 1);
        } else { // 没有其它参数 qstr = __xtoken_param__=xxx
            qstr = "";
        }

        location.append(qstr);
        Cookie cookie = new Cookie("xtoken", xtoken);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        response.addCookie(cookie);

        response.sendRedirect(location.toString());
    }

    // 从请求参数中解析xtoken
    private String pasrextokenParam(HttpServletRequest request) {
        
        final String PARANAME = "__xtoken_param__=";
        
        String qstr = request.getQueryString();
      // a=2&b=xxx&__xtoken_param__=xxxxxxx
        if (qstr == null) {
            return null;
        }
        
        int index = qstr.indexOf(PARANAME);
        if (index > -1) {
            return qstr.substring(index + PARANAME.length());
        } else {
            return null;
        }
    }

    // 引导浏览器重定向到服务端执行登录校验
    private void loginCheck(HttpServletRequest request, HttpServletResponse response) throws IOException {
        //jsonp
        if ("XMLHttpRequest".equals(request.getHeader("x-requested-with"))) {
            // 400 状态表示请求格式错误，服务器没有理解请求，此处返回400状态表示未登录时服务器拒绝此ajax请求
            response.sendError(400);
        } else {
            String qstr = makeQueryString(request); // 将所有请求参数重新拼接成queryString
            String backUrl = request.getRequestURL() + qstr; // 回调url
            String location = ssoServerUrl + "/login?backUrl=" + URLEncoder.encode(backUrl, "utf-8");
            if (notLoginOnFail) {
                location += "&notLogin=true";
            }
            response.sendRedirect(location);
        }

    }

    // 将所有请求参数重新拼接成queryString
    private String makeQueryString(HttpServletRequest request) throws UnsupportedEncodingException {
        StringBuilder builder = new StringBuilder();
        Enumeration<String> paraNames = request.getParameterNames();
        while (paraNames.hasMoreElements()) {
            String paraName = paraNames.nextElement();
            String[] paraVals = request.getParameterValues(paraName);
            for (String paraVal : paraVals) {
                builder.append("&").append(paraName).append("=").append(URLEncoder.encode(paraVal, "utf-8"));
            }
        }

        if (builder.length() > 0) {
            builder.replace(0, 1, "?");
        }

        return builder.toString();
    }

    // 将user存入threadLocal和request，供业务系统使用
    private void holdUser(LoginUser user, ServletRequest request) {
        UserHolder.set(user, request);
    }

    // 判断请求是否不需要拦截
    private boolean requestIsExclude(ServletRequest request) {

        // 没有设定excludes时，所以经过filter的请求都需要被处理
        if (StringUtil.isEmpty(excludes)) {
            return false;
        }

        // 获取去除context path后的请求路径
        String contextPath = request.getServletContext().getContextPath();
        String uri = ((HttpServletRequest) request).getRequestURI();
        uri = uri.substring(contextPath.length());

        // 正则模式匹配的uri被排除，不需要拦截
        boolean isExcluded = uri.matches(excludes);

        if (isExcluded) {
            logger.debug("request path: {} is excluded!", uri);
        }

        return isExcluded;
    }

    @Override
    public void destroy() {
        // DO nothing
    }
}
