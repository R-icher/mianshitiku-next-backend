package com.ryy.mianshitiku.blackfilter;

import cn.hutool.bloomfilter.BitMapBloomFilter;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.WordTree;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;

@Slf4j
public class BlackIpUtils {

//    // 创建布隆过滤器，过滤黑名单用户
//    private static BitMapBloomFilter bloomFilter;

    // DFA 树结构
    private static WordTree wordTree = new WordTree();

    // 判断 ip 是否在黑名单内
    public static boolean isBlackIp(String ip){
        if(StrUtil.isBlank(ip)){
            return false;
        }
        // DFA匹配规则查看是否完全匹配黑名单中的 ip
        return wordTree.match(ip) != null;
    }

    // 重建 ip 黑名单，使之能够动态修改，监听器的存在会让黑名单的值一发生变化，DFA树就对应修改
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
//            // 如果拿到的黑名单不为空
//            if(CollectionUtil.isNotEmpty(blackIpList)){
//                // 确定布隆过滤器内部的位数组长度
//                BitMapBloomFilter bitMapBloomFilter = new BitMapBloomFilter(958506);
//                for(String ip : blackIpList){
//                    bitMapBloomFilter.add(ip);
//                }
//                bloomFilter = bitMapBloomFilter;
//            }else{
//                // 黑名单为空，减少布隆过滤器为数组的长度
//                bloomFilter = new BitMapBloomFilter(100);
//            }

            // 如果拿到的黑名单中的 ip 不为空，就更新DFA树
            WordTree newTree = new WordTree();
            if(CollUtil.isNotEmpty(blackIpList)){
                newTree.addWords(blackIpList);
            }
            wordTree = newTree;
        }
    }
}
