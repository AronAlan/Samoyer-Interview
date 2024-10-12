package com.samoyer.backend.job.cycle;

import cn.hutool.core.collection.CollUtil;
import com.samoyer.backend.esdao.QuestionEsDao;
import com.samoyer.backend.mapper.QuestionMapper;
import com.samoyer.backend.model.dto.question.QuestionEsDTO;
import com.samoyer.backend.model.entity.Question;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

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
    private QuestionMapper questionMapper;

    @Resource
    private QuestionEsDao questionEsDao;

    /**
     * 每分钟执行一次
     */
    @Scheduled(fixedRate = 60 * 1000)
    public void run() {
        //查询过去5分钟内的数据
        long FIVE_MINUTES = 5 * 60 * 1000L;
        Date theFiveMinutes = new Date(new Date().getTime() - FIVE_MINUTES);
        List<Question> questionList = questionMapper.listQuestionWithDelete(theFiveMinutes);
        if (CollUtil.isEmpty(questionList)) {
            log.info("数据库中近五分钟无更新题目");
            return;
        }

        List<QuestionEsDTO> questionEsDTOList = questionList.stream()
                .map(QuestionEsDTO::objToDto)
                .collect(Collectors.toList());
        final int pageSize = 500;
        int total = questionEsDTOList.size();
        log.info("近五分钟，有{}条数据更新，开始增量同步到ES", total);
        for (int i = 0; i < total; i += pageSize) {
            int end = Math.min(i + pageSize, total);
            questionEsDao.saveAll(questionEsDTOList.subList(i, end));
        }
        log.info("增量同步到ES完成");
    }
}
