package com.chenfeng.ssoclient.service;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.chenfeng.ssoclient.util.StringUtil;

/**
 * 服务端向各系统写cookie
 * 
 * @author Administrator
 *
 */
@WebServlet("/setCookie")
public class CookieSetServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        String xtoken = req.getParameter("xtoken");
        if (!StringUtil.isEmpty(xtoken)) {
            // P3P信息
            resp.addHeader("P3P", "CP=CURa ADMa DEVa PSAo PSDo OUR BUS UNI PUR INT DEM STA PRE COM NAV OTC NOI DSP COR");
            Cookie cookie = new Cookie("xtoken", xtoken);
            cookie.setPath("/");
            cookie.setHttpOnly(true);;
            resp.addCookie(cookie);
        }
    }

}
