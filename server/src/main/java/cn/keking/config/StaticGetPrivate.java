package cn.keking.config;

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

    @PostConstruct
    public void init() {
        staticGetPrivate = this;
        staticGetPrivate.restTemplate = this.restTemplate;
    }

    public static RestTemplate getTemplates() {
        return staticGetPrivate.restTemplate;
    }
}
