package com.samoyer.backend.sentinel;

/**
 * Sentinel限流熔断常量
 *
 * @author Samoyer
 * @since 2024-10-18
 */
public interface SentinelConstant {
    /**
     * 分页获取题库列表接口限流常量
     */
    String listQuestionBankVOByPage="listQuestionBankVOByPage";

    /**
     * 分页获取题目列表接口限流常量
     */
    String listQuestionVOByPage="listQuestionVOByPage";
}
