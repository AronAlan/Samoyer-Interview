package com.samoyer.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.samoyer.backend.model.entity.Question;
import org.apache.ibatis.annotations.Select;

import java.util.Date;
import java.util.List;

/**
* @author xuzhichao
* @description 针对表【question(题目)】的数据库操作Mapper
* @createDate 2024-09-11 20:54:10
* @Entity com.samoyer.backend.model.entity.Question
*/
public interface QuestionMapper extends BaseMapper<Question> {

    /**
     * 查询过去某时刻之后更新的数据。用于将这些更新的数据增量同步到ES
     * 定时查询题目列表
     * 用于ES数据同步
     * 全量查询，包括逻辑删除的
     *
     * @param minUpdateTime
     * @return
     */
    @Select("select id, title, content, tags, answer, userId, editTime, createTime, updateTime, isDelete from mianshiya.question where updateTime>=#{minUpdateTime}")
    List<Question> listQuestionWithDelete(Date minUpdateTime);

}




