package cn.keking.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

/**
 * @author sxwh
 **/
@Component
public class StaticGetPrivate {

    private static StaticGetPrivate staticGetPrivate;

    @Resource
    private RestTemplate restTemplate;

    @Value("${vod.address}")
    private String vodAddress;

    @Value("${my.address}")
    private String myAddress;

    @PostConstruct
    public void init() {
        staticGetPrivate = this;
        staticGetPrivate.restTemplate = this.restTemplate;
        staticGetPrivate.vodAddress = this.vodAddress;
        staticGetPrivate.myAddress = this.myAddress;
    }

    public static RestTemplate getTemplates() {
        return staticGetPrivate.restTemplate;
    }

    public static String getvodAddress() {
        return staticGetPrivate.vodAddress;
    }

    public static String getmyAddress() {
        return staticGetPrivate.myAddress;
    }
}
