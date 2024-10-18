package com.samoyer.backend.sentinel;

import cn.hutool.core.io.FileUtil;
import com.alibaba.csp.sentinel.datasource.*;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker.CircuitBreakerStrategy;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import com.alibaba.csp.sentinel.transport.util.WritableDataSourceRegistry;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 单IP查看题目列表的限流规则、降级规则
 *
 * @author Samoyer
 * @since 2024-10-18
 */
@Component
public class SentinelRulesManager {

    @PostConstruct
    public void initRules() throws Exception {
        initFlowRules();
        initDegradeRules();
        listenRules();
    }

    /**
     * 限流规则
     */

    public void initFlowRules() {
        // 单 IP 查看题目列表限流规则
        // 被限流后并不是一定要再等一个周期，而是基于令牌桶和滑动窗口，类似于会搁一会儿又准发一次
        ParamFlowRule rule = new ParamFlowRule("listQuestionVOByPage")
                // 对第 0 个参数限流，即 IP 地址
                .setParamIdx(0)
                // 每分钟最多 60 次
                .setCount(60)
                // 规则的统计周期为 60 秒
                .setDurationInSec(60);
        ParamFlowRuleManager.loadRules(Collections.singletonList(rule));
    }

    /**
     * 降级规则
     */
    public void initDegradeRules() {
        // 单 IP 查看题目列表熔断规则
        DegradeRule slowCallRule = new DegradeRule("listQuestionVOByPage")
                .setGrade(CircuitBreakerStrategy.SLOW_REQUEST_RATIO.getType())
                // 慢调用比例大于 20%
                .setCount(0.2)
                // 熔断持续时间 60 秒
                .setTimeWindow(60)
                // 统计时长 30 秒
                .setStatIntervalMs(30 * 1000)
                // 最小请求数
                .setMinRequestAmount(10)
                // 响应时间超过 3 秒
                .setSlowRatioThreshold(3);

        DegradeRule errorRateRule = new DegradeRule("listQuestionVOByPage")
                .setGrade(CircuitBreakerStrategy.ERROR_RATIO.getType())
                // 异常率大于 10%
                .setCount(0.1)
                // 熔断持续时间 60 秒
                .setTimeWindow(60)
                // 统计时长 30 秒
                .setStatIntervalMs(30 * 1000)
                // 最小请求数
                .setMinRequestAmount(10);

        // 加载规则
        DegradeRuleManager.loadRules(Arrays.asList(slowCallRule, errorRateRule));
    }

    /**
     * 持久化配置为本地文件
     */
    public void listenRules() throws Exception {
        // 获取项目根目录
        String rootPath = System.getProperty("user.dir");
        // sentinel 目录路径
        File sentinelDir = new File(rootPath, "sentinel");
        // 目录不存在则创建
        if (!FileUtil.exist(sentinelDir)) {
            FileUtil.mkdir(sentinelDir);
        }
        // 规则文件路径
        String flowRulePath = new File(sentinelDir, "FlowRule.json").getAbsolutePath();
        String degradeRulePath = new File(sentinelDir, "DegradeRule.json").getAbsolutePath();

        // Data source for FlowRule
        ReadableDataSource<String, List<FlowRule>> flowRuleDataSource = new FileRefreshableDataSource<>(flowRulePath, flowRuleListParser);
        // Register to flow rule manager.
        FlowRuleManager.register2Property(flowRuleDataSource.getProperty());
        WritableDataSource<List<FlowRule>> flowWds = new FileWritableDataSource<>(flowRulePath, this::encodeJson);
        // Register to writable data source registry so that rules can be updated to file
        WritableDataSourceRegistry.registerFlowDataSource(flowWds);

        // Data source for DegradeRule
        FileRefreshableDataSource<List<DegradeRule>> degradeRuleDataSource
                = new FileRefreshableDataSource<>(
                degradeRulePath, degradeRuleListParser);
        DegradeRuleManager.register2Property(degradeRuleDataSource.getProperty());
        WritableDataSource<List<DegradeRule>> degradeWds = new FileWritableDataSource<>(degradeRulePath, this::encodeJson);
        // Register to writable data source registry so that rules can be updated to file
        WritableDataSourceRegistry.registerDegradeDataSource(degradeWds);
    }

    private Converter<String, List<FlowRule>> flowRuleListParser = source -> JSON.parseObject(source,
            new TypeReference<List<FlowRule>>() {
            });
    private Converter<String, List<DegradeRule>> degradeRuleListParser = source -> JSON.parseObject(source,
            new TypeReference<List<DegradeRule>>() {
            });

    private <T> String encodeJson(T t) {
        return JSON.toJSONString(t);
    }


}

