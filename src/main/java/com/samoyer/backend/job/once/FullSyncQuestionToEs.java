package com.samoyer.backend.job.once;

import cn.hutool.core.collection.CollUtil;
import com.samoyer.backend.esdao.QuestionEsDao;
import com.samoyer.backend.model.dto.question.QuestionEsDTO;
import com.samoyer.backend.model.entity.Question;
import com.samoyer.backend.service.QuestionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 单次任务，将数据库中题目表的数据，全量写入到ES中
 *
 * @author Samoyer
 * @since 2024-10-11
 */
//@Component
@Slf4j
public class FullSyncQuestionToEs implements CommandLineRunner {
    @Resource
    private QuestionService questionService;

    @Resource
    private QuestionEsDao questionEsDao;

    @Override
    public void run(String... args) throws Exception {
        //全量获取题目
        List<Question> questionList = questionService.list();
        if (CollUtil.isEmpty(questionList)) {
            log.info("数据库中题目表为空");
            return;
        }

        //转为ES实体类
        List<QuestionEsDTO> questionEsDTOList = questionList.stream()
                .map(QuestionEsDTO::objToDto)
                .collect(Collectors.toList());
        //分页批量插入到ES
        final int pageSize = 500;
        int total = questionEsDTOList.size();
        log.info("全量插入开始，total:{}", total);
        for (int i = 0; i < total; i += pageSize) {
            //注意同步的数据下标不能超过总数据量
            int end = Math.min(i + pageSize, total);
            log.info("插入 from:{} to {}", i, end);
            questionEsDao.saveAll(questionEsDTOList.subList(i, end));
        }
        log.info("全量插入结束");
    }
}
