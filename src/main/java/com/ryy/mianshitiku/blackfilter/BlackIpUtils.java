package com.ryy.mianshitiku.blackfilter;

import cn.hutool.bloomfilter.BitMapBloomFilter;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;

@Slf4j
public class BlackIpUtils {

    // 创建布隆过滤器，过滤黑名单用户
    private static BitMapBloomFilter bloomFilter;

    // 判断 ip 是否在黑名单内
    public static boolean isBlackIp(String ip){
        return bloomFilter.contains(ip);
    }

    // 重建 ip 黑名单，使之能够动态修改
    public static void rebuildBlackIp(String configInfo){
        if(StrUtil.isBlank(configInfo)){
            configInfo = "{}";
        }
        // 解析 yaml 文件
        Yaml yaml = new Yaml();
        // 将 configInfo 转换成 Map 对象
        Map map = yaml.loadAs(configInfo, Map.class);
        // 拿取黑名单中的值
        List<String> blackIpList = (List<String>) map.get("blackIpList");

        // 构造布隆过滤器
        synchronized (BlackIpUtils.class){
            // 如果拿到的黑名单不为空
            if(CollectionUtil.isNotEmpty(blackIpList)){
                // 确定布隆过滤器内部的位数组长度
                BitMapBloomFilter bitMapBloomFilter = new BitMapBloomFilter(958506);
                for(String ip : blackIpList){
                    bitMapBloomFilter.add(ip);
                }
                bloomFilter = bitMapBloomFilter;
            }else{
                // 黑名单为空，减少布隆过滤器为数组的长度
                bloomFilter = new BitMapBloomFilter(100);
            }
        }
    }
}
