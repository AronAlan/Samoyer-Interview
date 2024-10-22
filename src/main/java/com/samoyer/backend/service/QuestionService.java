package com.samoyer.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.samoyer.backend.model.dto.question.QuestionQueryRequest;
import com.samoyer.backend.model.entity.Question;
import com.samoyer.backend.model.vo.QuestionSimpleVO;
import com.samoyer.backend.model.vo.QuestionVO;
import org.springframework.web.bind.annotation.PostMapping;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;

/**
 * 题目服务
 *
 * @author Samoyer
 */
public interface QuestionService extends IService<Question> {

    /**
     * 校验数据
     *
     * @param question
     * @param add      对创建的数据进行校验
     */
    void validQuestion(Question question, boolean add);

    /**
     * 获取查询条件
     *
     * @param questionQueryRequest
     * @return
     */
    QueryWrapper<Question> getQueryWrapper(QuestionQueryRequest questionQueryRequest);

    /**
     * 获取题目封装
     *
     * @param question
     * @param request
     * @return
     */
    QuestionVO getQuestionVO(Question question, HttpServletRequest request);

    /**
     * 分页获取题目封装
     *
     * @param questionPage
     * @param request
     * @return
     */
    Page<QuestionVO> getQuestionVOPage(Page<Question> questionPage, HttpServletRequest request);

    /**
     * 分页获取题目简化版封装
     *
     * @param questionPage
     * @param request
     * @return
     */
    Page<QuestionSimpleVO> getQuestionSimpleVOPage(Page<Question> questionPage, HttpServletRequest request);

    /**
     * 分页获取题目列表（仅管理员可用）
     *
     * @param questionQueryRequest
     * @return
     */
    Page<Question> listQuestionByPage(QuestionQueryRequest questionQueryRequest);

    /**
     * 从ES查询题目
     *
     * @param questionQueryRequest
     * @return
     */
    Page<Question> searchFromEs(QuestionQueryRequest questionQueryRequest);

    /**
     * 批量删除题目
     * 并移除相应的题库题目关联
     *
     * @param questionIdList
     */
    void batchDeleteQuestions(List<Long> questionIdList);

    /**
     * 避免长事务问题，将batchDeleteQuestions批量删除题目的操作独立出来
     *
     * @param questionIdList
     * @param validQuestionsIdList
     */
    void batchDeleteQuestionsInner(List<Long> questionIdList, Set<Long> validQuestionsIdList);

    /**
     * 用于删除或增加时主动增量同步到ES
     */
    void incrementalEs();

    /**
     * 检测爬虫并告警或封号
     *
     * @param loginUserId
     */
    void crawlerDetect(long loginUserId);
}
