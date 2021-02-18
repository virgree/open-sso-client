package com.chenfeng.ssoclient.service;

import com.chenfeng.ssoclient.model.LoginUser;

import com.chenfeng.ssoclient.util.HttpUtil;
import org.json.JSONObject;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;


/**
 * xtoken管理
 */
public class TokenManager {


    private static class Token {
        private LoginUser user;
        private Date lastAccessTime;
    }

    // 缓存Map
    private final static Map<String, Token> LOCAL_CACHE = new HashMap<String, TokenManager.Token>();

    public static  String ssoServerUrl = "http:localhost:8080";

    private TokenManager() {
    }

    /**
     * 验证xtoken有效性
     *
     * @param xtoken
     * @return
     * @throws Exception
     */
    public static LoginUser validate(String xtoken) throws Exception {

        LoginUser user = localValidate(xtoken);

        if (user == null) {
            user = remoteValidate(xtoken);
        }

        return user;
    }

    // 在本地缓存验证有效性
    private static LoginUser localValidate(String xtoken) {

        // 从缓存中查找数据
        Token token = LOCAL_CACHE.get(xtoken);

        if (token != null) { // 用户数据存在
            // 更新最后访问时间
            token.lastAccessTime = new Date();
            // 返回结果
            return token.user;
        }

        return null;
    }

    // 远程验证成功后将信息写入本地缓存
    private static void cacheUser(String xtoken, LoginUser user) {
        Token token = new Token();
        token.user = user;
        token.lastAccessTime = new Date();
        LOCAL_CACHE.put(xtoken, token);
    }

    // 远程验证xtoken有效性
    private static LoginUser remoteValidate(String xtoken) throws Exception {

        JSONObject obj = HttpUtil.httpGet(ssoServerUrl + "/token/validateTocken?xtoken=" + xtoken);
        LoginUser user = new LoginUser();
        if(obj != null) {
            user.setLoginName(obj.get("loginName").toString());
            cacheUser(xtoken, user);
        }
        return user;
    }

    /**
     * 处理服务端发送的timeout通知
     *
     * @param xtoken
     * @param tokenTimeout
     * @return
     */
    public static Date timeout(String xtoken, int tokenTimeout) {

        Token token = LOCAL_CACHE.get(xtoken);

        if (token != null) {
            Date lastAccessTime = token.lastAccessTime;
            // 最终过期时间
            Date expires = new Date(lastAccessTime.getTime() + tokenTimeout * 60 * 1000);
            Date now = new Date();

            if (expires.compareTo(now) < 0) { // 已过期
                // 从本地缓存移除
                LOCAL_CACHE.remove(xtoken);
                // 返回null表示此客户端缓存已过期
                return null;
            } else {
                return expires;
            }
        } else {
            return null;
        }
    }

    /**
     * 用户退出时失效对应缓存
     *
     * @param xtoken
     */
    public static void invalidate(String xtoken) {
        // 从本地缓存移除
        LOCAL_CACHE.remove(xtoken);
    }

    /**
     * 服务端应用关闭时清空本地缓存，失效所有信息
     */
    public static void destroy() {
        LOCAL_CACHE.clear();
    }

}
