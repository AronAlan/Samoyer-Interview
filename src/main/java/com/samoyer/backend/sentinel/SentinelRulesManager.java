package com.samoyer.backend.sentinel;

import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker.CircuitBreakerStrategy;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Collections;

/**
 * 单IP查看题目列表的限流规则、降级规则
 *
 * @author Samoyer
 * @since 2024-10-18
 */
@Component
public class SentinelRulesManager {

    @PostConstruct
    public void initRules() {
        initFlowRules();
        initDegradeRules();
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
}

