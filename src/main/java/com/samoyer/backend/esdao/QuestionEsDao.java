package com.samoyer.backend.esdao;

import com.samoyer.backend.model.dto.question.QuestionEsDTO;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * 题目ES操作
 * 统一存放对ES的操作
 * 继承ElasticsearchRepository，类似于mybatis，提供了一些crud的方法
 *
 * @author Samoyer
 * @since 2024-10-11
 */
public interface QuestionEsDao extends ElasticsearchRepository<QuestionEsDTO,Long> {
}
