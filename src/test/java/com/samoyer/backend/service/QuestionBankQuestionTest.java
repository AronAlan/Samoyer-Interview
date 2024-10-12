package com.samoyer.backend.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.samoyer.backend.model.entity.Question;
import com.samoyer.backend.model.entity.QuestionBank;
import com.samoyer.backend.model.entity.QuestionBankQuestion;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * QuestionBankQuestion测试
 *
 * @author Samoyer
 */
@SpringBootTest
public class QuestionBankQuestionTest {

    @Resource
    private QuestionBankQuestionService questionBankQuestionService;

    @Resource
    private QuestionBankService questionBankService;

@Test
void test() {
    Set<Long> questionIdSet = List.of(1L, 2L, 3L).stream().collect(Collectors.toSet());

    // 根据questionId查询题库题目关联
    List<QuestionBankQuestion> questionBankQuestions = questionBankQuestionService.list(Wrappers.<QuestionBankQuestion>lambdaQuery().in(QuestionBankQuestion::getQuestionId, questionIdSet));

    // 将结果转换为Map<questionId, List<questionBankId>>
    Map<Long, List<Long>> result = questionBankQuestions.stream()
        .collect(Collectors.groupingBy(
            QuestionBankQuestion::getQuestionId, // 分组键
            Collectors.mapping(QuestionBankQuestion::getQuestionBankId, Collectors.toList()) // 收集值
        ));
    //result中的value是questionBankIds，查询questionBank表中对应的title，并创建为Map<Long, List<String>>
    Map<Long, List<String>> questionBankTitleMap = result.entrySet().stream().collect(Collectors.toMap(
        Map.Entry::getKey, // key
        entry -> entry.getValue().stream().map(questionBankId -> {
            QuestionBank questionBank = questionBankService.getById(questionBankId);
            return questionBank.getTitle();
        }).collect(Collectors.toList()) // value
    ));


    System.out.println(questionBankTitleMap);
}

}
