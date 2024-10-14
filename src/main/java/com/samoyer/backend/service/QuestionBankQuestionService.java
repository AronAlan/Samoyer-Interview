package com.samoyer.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.samoyer.backend.model.dto.questionbankquestion.QuestionBankQuestionQueryRequest;
import com.samoyer.backend.model.entity.QuestionBankQuestion;
import com.samoyer.backend.model.entity.User;
import com.samoyer.backend.model.vo.QuestionBankQuestionVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 题库题目关联服务
 *
 * @author Samoyer
 */
public interface QuestionBankQuestionService extends IService<QuestionBankQuestion> {

    /**
     * 校验数据
     *
     * @param questionBankQuestion
     * @param add                  对创建的数据进行校验
     */
    void validQuestionBankQuestion(QuestionBankQuestion questionBankQuestion, boolean add);

    /**
     * 获取查询条件
     *
     * @param questionBankQuestionQueryRequest
     * @return
     */
    QueryWrapper<QuestionBankQuestion> getQueryWrapper(QuestionBankQuestionQueryRequest questionBankQuestionQueryRequest);

    /**
     * 根据questionId删除题库题目关联表中对应的数据
     *
     * @param questionId
     * @return
     */
    boolean removeByQuestionId(long questionId);

    /**
     * 获取题库题目关联封装
     *
     * @param questionBankQuestion
     * @param request
     * @return
     */
    QuestionBankQuestionVO getQuestionBankQuestionVO(QuestionBankQuestion questionBankQuestion, HttpServletRequest request);

    /**
     * 分页获取题库题目关联封装
     *
     * @param questionBankQuestionPage
     * @param request
     * @return
     */
    Page<QuestionBankQuestionVO> getQuestionBankQuestionVOPage(Page<QuestionBankQuestion> questionBankQuestionPage, HttpServletRequest request);

    /**
     * 批量向题库中添加题目（关联）
     *
     * @param questionIdList
     * @param questionBankId
     * @param loginUser
     */
    void batchAddQuestionToBank(List<Long> questionIdList, Long questionBankId, User loginUser);

    /**
     * 避免长事务问题，将batchAddQuestionToBank批量添加题目到题库中执行批量添加的操作独立出来
     *
     * @param questionBankQuestions
     */
    void batchAddQuestionToBankInner(List<QuestionBankQuestion> questionBankQuestions);

    /**
     * 批量从题库中移除题目（关联）
     *
     * @param questionIdList
     * @param questionBankId
     */
    void batchRemoveQuestionFromBank(List<Long> questionIdList, Long questionBankId);

    /**
     * 避免长事务问题，将batchRemoveQuestionFromBank批量从题库中移除题目的操作独立出来
     * @param questionIdList
     * @param questionBankId
     */
    void batchRemoveQuestionFromBankInner(List<Long> questionIdList, Long questionBankId);
}
