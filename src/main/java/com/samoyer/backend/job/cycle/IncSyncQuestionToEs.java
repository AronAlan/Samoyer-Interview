package com.samoyer.backend.job.cycle;

import com.samoyer.backend.service.QuestionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 定时循环任务，增量同步题目数据到ES
 *
 * @author Samoyer
 * @since 2024-10-11
 */
@Component
@Slf4j
public class IncSyncQuestionToEs {

    @Resource
    private QuestionService questionService;

    /**
     * 每分钟执行一次
     */
    @Scheduled(fixedRate = 60 * 1000)
    public void run() {
        questionService.incrementalEs();
    }
}
