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
                holdUser(user, request); // 存放user信息，供业务系统使用
                chain.doFilter(request, response);
            } else {
                CookieUtil.deleteCookie("xtoken", response, "/");
                // 重定向到服务端执行登录校验
                loginCheck(request, response);
            }
        } else {
            String xtokenParam = pasrextokenParam(request);
            if (xtokenParam == null) {
                // 请求中中没有xtokenParam, 重定向到服务端执行登录校验
                loginCheck(request, response);
            } else if (xtokenParam.length() == 0) {
                response.sendError(403);
            } else {
                redirectToSelf(xtokenParam, request, response);
            }
        }
    }

    // 从参数中获取服务端传来的xtoken后，重定向将xtoken写入cookie
    private void redirectToSelf(String xtoken, HttpServletRequest request, HttpServletResponse response) throws IOException {
        final String PARANAME = "__xtoken_param__=";
        StringBuffer location = request.getRequestURL();
       
        String qstr = request.getQueryString();
        int index = qstr.indexOf(PARANAME);
        if (index > 0) {
            qstr = "?" + qstr.substring(0, qstr.indexOf(PARANAME) - 1);
        } else {
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

    // 重定向到服务端执行登录校验
    private void loginCheck(HttpServletRequest request, HttpServletResponse response) throws IOException {
        //jsonp
        if ("XMLHttpRequest".equals(request.getHeader("x-requested-with"))) {
            response.sendError(400);
        } else {
            String qstr = makeQueryString(request); // 组装queryString
            String backUrl = request.getRequestURL() + qstr;
            String location = ssoServerUrl + "/login?backUrl=" + URLEncoder.encode(backUrl, "utf-8");
            if (notLoginOnFail) {
                location += "&notLogin=true";
            }
            response.sendRedirect(location);
        }

    }

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
