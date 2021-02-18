package com.chenfeng.ssoclient.util;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

public class HttpUtil {

    public static JSONObject httpGet(String url){
        DefaultHttpClient httpClient = new DefaultHttpClient();
        try
        {
            HttpGet getRequest = new HttpGet("http://localhost:8080/token/validateTocken?xtoken=643a302467c04ca0bda809db6940cf69");
            getRequest.addHeader("accept", "application/json");
            HttpResponse response = httpClient.execute(getRequest);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                throw new RuntimeException("Failed with HTTP error code : " + statusCode);
            }
            HttpEntity httpEntity = response.getEntity();
            String apiOutput = EntityUtils.toString(httpEntity);
            if(!StringUtil.isEmpty(apiOutput)) {
                JSONObject object = new JSONObject(apiOutput);
                return object;
            }
            return null;
        }catch (Exception e){
            e.printStackTrace();
        }
        finally
        {
            httpClient.getConnectionManager().shutdown();
        }
        return null;
    }
    public static void main(String[] args) {

       JSONObject obj = httpGet("http://localhost:8080/token/validateTocken?xtoken=643a302467c04ca0bda809db6940cf69");
       System.out.println(obj.get("loginName"));
    }
}
