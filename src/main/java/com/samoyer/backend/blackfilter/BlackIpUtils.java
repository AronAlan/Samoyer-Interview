package com.samoyer.backend.blackfilter;

import cn.hutool.bloomfilter.BitMapBloomFilter;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;

/**
 * 黑名单过滤工具类
 *
 * @author Samoyer
 * @since 2024-10-18
 */
@Slf4j
public class BlackIpUtils {
    /**
     * 黑名单
     */
    private static BitMapBloomFilter bloomFilter;
    ;

    /**
     * 判断ip是否在黑名单内
     *
     * @param ip
     * @return
     */
    public static boolean isBlackIp(String ip) {
        return bloomFilter.contains(ip);
    }

    /**
     * nacos知晓配置变更，则重建黑名单（监听粒度粗，无法知晓新增删除还是修改）
     * 重建ip黑名单
     *
     * @param configInfo
     */
    public static void rebuildBlackIp(String configInfo) {
        log.info("重建黑名单：{}", configInfo);
        if (StrUtil.isBlank(configInfo)) {
            configInfo = "{}";
        }

        //解析yaml文件
        Yaml yaml = new Yaml();
        Map map = yaml.loadAs(configInfo, Map.class);
        //获取ip黑名单
        List<String> blackIpList = (List<String>) map.get("blackIpList");
        log.info("获取到ip黑名单：{}", blackIpList);
        //加锁防止并发
        synchronized (BlackIpUtils.class) {
            //重建ip黑名单
            if (CollUtil.isNotEmpty(blackIpList)) {
                BitMapBloomFilter bitMapBloomFilter = new BitMapBloomFilter(1000);
                for (String ip : blackIpList) {
                    bitMapBloomFilter.add(ip);
                }
                bloomFilter = bitMapBloomFilter;
            } else {
                bloomFilter = new BitMapBloomFilter(1000);
            }
        }
    }
}
