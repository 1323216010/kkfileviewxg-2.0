package cn.keking.utils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;

/**
 * 方法工具类
 *
 * @author yan160100
 **/
public class MethodUtils {

    /**
     * Map转String(Content-Type：application/x-www-form-urlencoded)
     *
     * @param data
     * @return
     */
    public static String urlencode(Map<String, ?> data) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, ?> i : data.entrySet()) {
            try {
                sb.append(i.getKey()).append("=").append(URLEncoder.encode(i.getValue() + "", "UTF-8")).append("&");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        String result = sb.toString();
        result = result.substring(0, result.lastIndexOf("&"));
        return result;
    }

    /*指定路径下创建文件*/
    public static String createFile(String filePath) {
        File file = new File(filePath);
        try {
            file.createNewFile();
            return filePath;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return filePath;
    }

    /*post方式发送x-www-form-urlencoded格式数据*/
    public static String sendForm(String path, String postData) {
        String line;
        String result = "未知错误";
        try {
            URL url = new URL(path);
            /*String postData = "grant_type=authorization_code&client_id=ahxt&client_secret=bfa0dc0934ec15516248d00ddd99538f&code=" + code
            + "&redirect_uri=http://172.18.29.125/single_point";*/
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Content-Length", Integer.toString(postData.length()));
            conn.setUseCaches(false);

            try (DataOutputStream dos = new DataOutputStream(conn.getOutputStream())) {
                dos.writeBytes(postData);
            }

            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    conn.getInputStream()))) {
                while ((line = br.readLine()) != null) {
                    result = line;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    /*对象字符串转换为json字符串*/
    public static String strToStr(String str) {
        char[] s = str.replaceAll(" ", "").toCharArray();
        str = "";
        for (int i = 0; i < s.length; ++i) {
            if (s[i] == '{' ) {
                str = str + s[i] + "\"";
            } else if (s[i] == '}') {
                str = str + "\"" + s[i];
            } else if (s[i] == '=') {
                str = str + "\"" + ":" + "\"";
            } else if (s[i] == ',') {
                str = str + "\"" + s[i] + "\"";
            } else {
                str = str + s[i];
            }
        }
        return str;
    }

    /*判断是否为公司*/
    public static boolean isCompany(String name) {

        String a = "分公司";
        char[] b = a.toCharArray();
        char f = b[0];
        char g = b[1];
        char s = b[2];
        char[] ch = name.toCharArray();
        if (ch.length >= 3) {
            if (ch[ch.length - 3] != f && ch[ch.length - 2] == g && ch[ch.length - 1] == s) {
                return true;
            }
        } else if (ch.length == 2) {
            if (ch[ch.length - 2] == g && ch[ch.length - 1] == s) {
                return true;
            }
        }
        return false;
    }

    /*String转Url字符串*/
    public static String stringToUrl(String url) {
        char[] a = url.toCharArray();
        url = "";
        for (int i = 0; i < a.length; i++) {
            if (a[i] == '+') {
                url = url + "%2B";
            }else if (a[i] == '=') {
                url = url + "%3D";
            }else if (a[i] == '/') {
                url = url + "%2F";
            }else if (a[i] == '?') {
                url = url + "%3F";
            }
            else {
                url = url + a[i];
            }
        }
        return url;
    }

}
