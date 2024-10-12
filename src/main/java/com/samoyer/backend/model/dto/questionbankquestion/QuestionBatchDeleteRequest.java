package com.samoyer.backend.model.dto.questionbankquestion;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 批量删除题目。并移除相应的题库题目关联
 *
 * @author Samoyer

 */
@Data
public class QuestionBatchDeleteRequest implements Serializable {

    /**
     * 题目 id列表
     */
    private List<Long> questionIdList;

    private static final long serialVersionUID = 1L;
}