package com.ryy.mianshitiku.config;

import com.alibaba.fastjson.JSON;
import com.jd.platform.hotkey.client.ClientStarter;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

@Configuration
@ConfigurationProperties(prefix = "hotkey")
@Data
public class HotKeyConfig {

    /**
     * Etcd 服务器完整地址
     */
    private String etcdServer = "http://127.0.0.1:2379";

    /**
     * 应用名称
     */
    private String appName = "mianshitiku";

//    /**
//     * 本地缓存最大数量
//     */
//    private int caffeineSize = 10000;

    /**
     * 批量推送 key 的间隔时间
     */
    private long pushPeriod = 1000L;

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 初始化 hotkey
     */
    @Bean
    public void initHotkey() {
        ClientStarter.Builder builder = new ClientStarter.Builder();
        ClientStarter starter = builder.setAppName(appName)
                // 设置分布式缓存 Redis ，不需要设置本地缓存大小了
//                .setCaffeineSize(caffeineSize)
                .setPushPeriod(pushPeriod)
                .setEtcdServer(etcdServer)
                .build();
        starter.startPipeline();
    }

    public Object getFromCache(String key){
        return redisTemplate.opsForValue().get(key);
    }

    public void setToCache(String key, Object value, long ttlSeconds){
        redisTemplate.opsForValue().set(key, JSON.toJSONString(value), ttlSeconds, TimeUnit.SECONDS);
    }
}

