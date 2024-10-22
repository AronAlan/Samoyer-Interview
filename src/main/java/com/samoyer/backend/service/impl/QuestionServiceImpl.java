package com.samoyer.backend.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.samoyer.backend.common.ErrorCode;
import com.samoyer.backend.constant.CommonConstant;
import com.samoyer.backend.esdao.QuestionEsDao;
import com.samoyer.backend.exception.BusinessException;
import com.samoyer.backend.exception.ThrowUtils;
import com.samoyer.backend.manager.CounterManager;
import com.samoyer.backend.mapper.QuestionMapper;
import com.samoyer.backend.model.dto.question.QuestionEsDTO;
import com.samoyer.backend.model.dto.question.QuestionQueryRequest;
import com.samoyer.backend.model.entity.Question;
import com.samoyer.backend.model.entity.QuestionBank;
import com.samoyer.backend.model.entity.QuestionBankQuestion;
import com.samoyer.backend.model.entity.User;
import com.samoyer.backend.model.vo.QuestionSimpleVO;
import com.samoyer.backend.model.vo.QuestionVO;
import com.samoyer.backend.model.vo.UserVO;
import com.samoyer.backend.service.QuestionBankQuestionService;
import com.samoyer.backend.service.QuestionBankService;
import com.samoyer.backend.service.QuestionService;
import com.samoyer.backend.service.UserService;
import com.samoyer.backend.utils.EmailUtils;
import com.samoyer.backend.utils.SqlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.fasterxml.jackson.databind.type.LogicalType.Collection;

/**
 * 题目服务实现
 *
 * @author Samoyer
 */
@Service
@Slf4j
public class QuestionServiceImpl extends ServiceImpl<QuestionMapper, Question> implements QuestionService {

    @Resource
    private UserService userService;

    @Resource
    private QuestionBankQuestionService questionBankQuestionService;

    @Resource
    private QuestionBankService questionBankService;

    @Resource
    private ElasticsearchRestTemplate elasticsearchRestTemplate;

    @Resource
    private QuestionMapper questionMapper;

    @Resource
    private QuestionEsDao questionEsDao;

    @Resource
    private CounterManager counterManager;

    /**
     * 校验数据
     *
     * @param question
     * @param add      对创建的数据进行校验
     */
    @Override
    public void validQuestion(Question question, boolean add) {
        ThrowUtils.throwIf(question == null, ErrorCode.PARAMS_ERROR);
        // todo 从对象中取值
        String title = question.getTitle();
        // 创建数据时，参数不能为空
        if (add) {
            // todo 补充校验规则
            ThrowUtils.throwIf(StringUtils.isBlank(title), ErrorCode.PARAMS_ERROR);
        }
        // 修改数据时，有参数则校验
        // todo 补充校验规则
        if (StringUtils.isNotBlank(title)) {
            ThrowUtils.throwIf(title.length() > 80, ErrorCode.PARAMS_ERROR, "标题过长");
        }
    }

    /**
     * 获取查询条件
     *
     * @param questionQueryRequest
     * @return
     */
    @Override
    public QueryWrapper<Question> getQueryWrapper(QuestionQueryRequest questionQueryRequest) {
        QueryWrapper<Question> queryWrapper = new QueryWrapper<>();
        if (questionQueryRequest == null) {
            return queryWrapper;
        }
        // todo 从对象中取值
        Long id = questionQueryRequest.getId();
        Long notId = questionQueryRequest.getNotId();
        String title = questionQueryRequest.getTitle();
        String content = questionQueryRequest.getContent();
        String searchText = questionQueryRequest.getSearchText();
        String sortField = questionQueryRequest.getSortField();
        String sortOrder = questionQueryRequest.getSortOrder();
        List<String> tagList = questionQueryRequest.getTags();
        Long userId = questionQueryRequest.getUserId();
        String answer = questionQueryRequest.getAnswer();
        // todo 补充需要的查询条件
        // 从多字段中搜索
        if (StringUtils.isNotBlank(searchText)) {
            // 需要拼接查询条件
            queryWrapper.and(qw -> qw.like("title", searchText).or().like("content", searchText));
        }
        // 模糊查询
        queryWrapper.like(StringUtils.isNotBlank(title), "title", title);
        queryWrapper.like(StringUtils.isNotBlank(content), "content", content);
        queryWrapper.like(StringUtils.isNotBlank(answer), "answer", answer);
        // JSON 数组查询
        if (CollUtil.isNotEmpty(tagList)) {
            for (String tag : tagList) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        // 精确查询
        queryWrapper.ne(ObjectUtils.isNotEmpty(notId), "id", notId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        // 排序规则
        queryWrapper.orderBy(SqlUtils.validSortField(sortField),
                sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

    /**
     * 获取题目封装
     *
     * @param question
     * @param request
     * @return
     */
    @Override
    public QuestionVO getQuestionVO(Question question, HttpServletRequest request) {
        // 对象转封装类
        QuestionVO questionVO = QuestionVO.objToVo(question);

        // todo 可以根据需要为封装对象补充值，不需要的内容可以删除
        // region 可选
        // 1. 关联查询用户信息
        Long userId = question.getUserId();
        User user = null;
        if (userId != null && userId > 0) {
            user = userService.getById(userId);
        }
        UserVO userVO = userService.getUserVO(user);
        questionVO.setUser(userVO);
        // 2. 已登录，获取用户点赞、收藏状态
        /*long questionId = question.getId();
        User loginUser = userService.getLoginUserPermitNull(request);
        if (loginUser != null) {
            // 获取点赞
            QueryWrapper<QuestionThumb> questionThumbQueryWrapper = new QueryWrapper<>();
            questionThumbQueryWrapper.in("questionId", questionId);
            questionThumbQueryWrapper.eq("userId", loginUser.getId());
            QuestionThumb questionThumb = questionThumbMapper.selectOne(questionThumbQueryWrapper);
            questionVO.setHasThumb(questionThumb != null);
            // 获取收藏
            QueryWrapper<QuestionFavour> questionFavourQueryWrapper = new QueryWrapper<>();
            questionFavourQueryWrapper.in("questionId", questionId);
            questionFavourQueryWrapper.eq("userId", loginUser.getId());
            QuestionFavour questionFavour = questionFavourMapper.selectOne(questionFavourQueryWrapper);
            questionVO.setHasFavour(questionFavour != null);
        }*/
        // endregion

        return questionVO;
    }

    /**
     * 分页获取题目封装
     *
     * @param questionPage
     * @param request
     * @return
     */
    @Override
    public Page<QuestionVO> getQuestionVOPage(Page<Question> questionPage, HttpServletRequest request) {
        List<Question> questionList = questionPage.getRecords();
        Page<QuestionVO> questionVOPage = new Page<>(questionPage.getCurrent(), questionPage.getSize(), questionPage.getTotal());
        if (CollUtil.isEmpty(questionList)) {
            return questionVOPage;
        }
        // 对象列表 => 封装对象列表
        List<QuestionVO> questionVOList = questionList.stream().map(question -> {
            return QuestionVO.objToVo(question);
        }).collect(Collectors.toList());

        // 可以根据需要为封装对象补充值，不需要的内容可以删除
        // 1. 关联查询用户信息
        Set<Long> userIdSet = questionList.stream().map(Question::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 填充信息
        questionVOList.forEach(questionVO -> {
            Long userId = questionVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            questionVO.setUser(userService.getUserVO(user));
        });

        // 2.关联查询题目所属的题库
        Set<Long> questionIdSet = questionList.stream().map(Question::getId).collect(Collectors.toSet());
        // 根据questionId查询题库题目关联
        List<QuestionBankQuestion> questionBankQuestions = questionBankQuestionService.list(
                Wrappers.<QuestionBankQuestion>lambdaQuery()
                        .in(QuestionBankQuestion::getQuestionId, questionIdSet)
        );
        // 题目对应（多个）题库,将结果转换为Map<questionId, List<questionBankId>>
        Map<Long, List<Long>> questionIdToBankIdsMap = questionBankQuestions.stream()
                .collect(Collectors.groupingBy(
                        QuestionBankQuestion::getQuestionId,
                        Collectors.mapping(QuestionBankQuestion::getQuestionBankId, Collectors.toList())
                ));
        //questionIdToBankIdsMap中的value是questionBankIds，查询questionBank表中对应的title，并创建为Map<Long, List<String>>
        Map<Long, List<String>> questionBankTitleMap = questionIdToBankIdsMap.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().stream().map(questionBankId -> {
                    QuestionBank questionBank = questionBankService.getById(questionBankId);
                    return questionBank.getTitle();
                }).collect(Collectors.toList())
        ));

        // 填充信息
        questionVOList.forEach(questionVO -> {
            Long questionId = questionVO.getId();
            List<String> questionBankTitles = null;
            if (questionBankTitleMap.containsKey(questionId)) {
                questionBankTitles = questionBankTitleMap.get(questionId);
            }
            questionVO.setQuestionBankTitles(questionBankTitles);
        });

        questionVOPage.setRecords(questionVOList);
        return questionVOPage;
    }

    /**
     * 分页获取题目简化版封装
     *
     * @param questionPage
     * @param request
     * @return
     */
    @Override
    public Page<QuestionSimpleVO> getQuestionSimpleVOPage(Page<Question> questionPage, HttpServletRequest request) {
        List<Question> questionList = questionPage.getRecords();
        Page<QuestionSimpleVO> questionSimpleVOPage = new Page<>(questionPage.getCurrent(), questionPage.getSize(), questionPage.getTotal());
        if (CollUtil.isEmpty(questionList)) {
            return questionSimpleVOPage;
        }
        // 对象列表 => 封装对象列表
        List<QuestionSimpleVO> questionVOList = questionList.stream().map(question -> {
            return QuestionSimpleVO.objToVo(question);
        }).collect(Collectors.toList());
        questionSimpleVOPage.setRecords(questionVOList);
        return questionSimpleVOPage;
    }

    /**
     * 分页获取题目列表
     *
     * @param questionQueryRequest
     * @return
     */
    @Override
    public Page<Question> listQuestionByPage(QuestionQueryRequest questionQueryRequest) {
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();

        //题目表的查询条件
        QueryWrapper<Question> queryWrapper = this.getQueryWrapper(questionQueryRequest);

        //根据题库id查询题目列表
        Long questionBankId = questionQueryRequest.getQuestionBankId();
        if (questionBankId != null) {
            LambdaQueryWrapper<QuestionBankQuestion> lambdaQueryWrapper = Wrappers.lambdaQuery(QuestionBankQuestion.class)
                    //只查询题目ids，优化查询
                    .select(QuestionBankQuestion::getQuestionId)
                    .eq(QuestionBankQuestion::getQuestionBankId, questionBankId);
            //查询到题库id下所有题目的ids
            List<QuestionBankQuestion> questionList = questionBankQuestionService.list(lambdaQueryWrapper);
            //题库下的题目不为空
            if (CollUtil.isNotEmpty(questionList)) {
                //取出题目id集合
                Set<Long> questionIdSet = questionList.stream().map(QuestionBankQuestion::getQuestionId)
                        .collect(Collectors.toSet());
                //增加查询条件到queryWrapper
                queryWrapper.in("id", questionIdSet);
            } else {
                //如果题库为空的话，返回空列表
                //逻辑是根据题库id查询题目ids，然后返回题库下的所有题目
                //但是如果不返回空列表的话，就变成下面查数据库时的查询条件（为空）就没有限制了，也就全量查询了
                return new Page<>(current, size, 0);
            }
        }

        // 查询数据库
        Page<Question> questionPage = this.page(new Page<>(current, size),
                queryWrapper);
        return questionPage;
    }

    /**
     * 从ES中分页查询题目
     * 使用ElasticsearchRestTemplate
     *
     * @param questionQueryRequest
     * @return
     */
    @Override
    public Page<Question> searchFromEs(QuestionQueryRequest questionQueryRequest) {
        Long id = questionQueryRequest.getId();
        Long notId = questionQueryRequest.getNotId();
        String searchText = questionQueryRequest.getSearchText();
        List<String> tags = questionQueryRequest.getTags();
//        Long questionBankId = questionQueryRequest.getQuestionBankId();
        Long userId = questionQueryRequest.getUserId();
        //ES的起始页为0，需要减一
        int current = questionQueryRequest.getCurrent() - 1;
        int pageSize = questionQueryRequest.getPageSize();
        String sortField = questionQueryRequest.getSortField();
        String sortOrder = questionQueryRequest.getSortOrder();

        //构造查询条件
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        //过滤.过滤出符合termQuery条件的数据
        boolQueryBuilder.filter(QueryBuilders.termQuery("isDelete", 0));
        if (id != null) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("id", id));
        }
        if (notId != null) {
            boolQueryBuilder.mustNot(QueryBuilders.termQuery("id", notId));
        }
        if (userId != null) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("userId", userId));
        }
        //题目与题库关联不在题目表中，暂不设置根据questionBankId查询
//        if (questionBankId != null) {
//            boolQueryBuilder.filter(QueryBuilders.termQuery("questionBankId", questionBankId));
//        }

        //必须包含所有查询的标签
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                boolQueryBuilder.filter(QueryBuilders.termQuery("tags", tag));
            }
        }

        //按关键词检索
        if (StrUtil.isNotBlank(searchText)) {
            boolQueryBuilder.should(QueryBuilders.matchQuery("title", searchText));
            boolQueryBuilder.should(QueryBuilders.matchQuery("content", searchText));
            boolQueryBuilder.should(QueryBuilders.matchQuery("answer", searchText));
            //至少有一个should匹配
            boolQueryBuilder.minimumShouldMatch(1);
        }

        //排序
        SortBuilder<?> sortBuilder = SortBuilders.scoreSort();
        if (StrUtil.isNotBlank(sortField)) {
            sortBuilder = SortBuilders.fieldSort(sortField);
            sortBuilder.order(CommonConstant.SORT_ORDER_ASC.equals(sortOrder) ? SortOrder.ASC : SortOrder.DESC);
        }

        //分页
        PageRequest pageRequest = PageRequest.of(current, pageSize);
        //构造查询
        NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
                .withQuery(boolQueryBuilder)
                .withPageable(pageRequest)
                .withSorts(sortBuilder)
                .build();
        SearchHits<QuestionEsDTO> searchHits = elasticsearchRestTemplate.search(searchQuery, QuestionEsDTO.class);

        //复用mysql的分页对象，封装返回结果
        Page<Question> page = new Page<>();
        page.setTotal(searchHits.getTotalHits());
        List<Question> resourceList = new ArrayList<>();
        if (searchHits.hasSearchHits()) {
            List<SearchHit<QuestionEsDTO>> searchHitList = searchHits.getSearchHits();
            for (SearchHit<QuestionEsDTO> questionEsDtoSearchHit : searchHitList) {
                resourceList.add(QuestionEsDTO.dtoToObj(questionEsDtoSearchHit.getContent()));
            }
        }

        page.setRecords(resourceList);
        return page;
    }

    /**
     * 批量删除题目 并移除相应的题库题目关联
     *
     * @param questionIdList
     */
    @Override
    public void batchDeleteQuestions(List<Long> questionIdList) {
        //参数校验
        ThrowUtils.throwIf(CollUtil.isEmpty(questionIdList), ErrorCode.PARAMS_ERROR, "批量删除题目列表为空");
        //查询批量删除题目列表中的与题库相关联着的有效题目
        LambdaQueryWrapper<QuestionBankQuestion> lambdaQueryWrapper = Wrappers.lambdaQuery(QuestionBankQuestion.class)
                .in(QuestionBankQuestion::getQuestionId, questionIdList);
        List<QuestionBankQuestion> validQuestions = questionBankQuestionService.list(lambdaQueryWrapper);
        //与题库关联着的题目idList
        Set<Long> validQuestionsIdList = validQuestions.stream().map(QuestionBankQuestion::getQuestionId).collect(Collectors.toSet());

        //分批次处理添加操作，避免长事务
        int batchSize = 1000;
        int totalQuestionListSize = questionIdList.size();
        for (int i = 0; i < totalQuestionListSize; i += batchSize) {
            //生成每批次的数据
            List<Long> subQuestionIdList = questionIdList.subList(i, Math.min(i + batchSize, totalQuestionListSize));

            //使用事务处理每批次数据
            QuestionService questionService = (QuestionService) AopContext.currentProxy();
            //执行添加
            questionService.batchDeleteQuestionsInner(subQuestionIdList, validQuestionsIdList);
        }


    }

    /**
     * 避免长事务问题，将batchDeleteQuestions批量删除题目的操作独立出来
     *
     * @param questionIdList
     * @param validQuestionsIdList
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDeleteQuestionsInner(List<Long> questionIdList, Set<Long> validQuestionsIdList) {
        //删除题目
        boolean result = this.removeBatchByIds(questionIdList);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "删除题目失败");
        for (Long questionId : questionIdList) {
            //该题目若与题库关联着，就移除关联，没有则忽略
            if (validQuestionsIdList.contains(questionId)) {
                //移除题库题目关联。只删除题库题目表中存在的数据。（因为有可能批量删除的题目不与任何题库关联）
                LambdaQueryWrapper<QuestionBankQuestion> lambdaQueryWrapper = Wrappers.lambdaQuery(QuestionBankQuestion.class)
                        .eq(QuestionBankQuestion::getQuestionId, questionId);
                result = questionBankQuestionService.remove(lambdaQueryWrapper);
                ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "移除题库题目关联失败");
            }
        }
    }

    /**
     * 用于删除或增加时主动增量同步到ES
     */
    @Override
    public void incrementalEs() {
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

    /**
     * 检测爬虫并告警或封号
     *
     * @param loginUserId
     */
    @Override
    public void crawlerDetect(long loginUserId) {
        //调用多少次时告警
        final int WARN_COUNT = 10;
        //超过多少次时封号
        final int BAN_COUNT = 20;
        //key
        String key = String.format("user:access:%s", loginUserId);
        //一分钟内访问次数，redis中180秒过期
        long count = counterManager.incrAndGetCounter(key, 1, TimeUnit.MINUTES, 180);
        log.info("count: " + count);
        //是否告警
        String time = DateUtil.format(new Date(), "yyyy-MM-dd HH:mm");
        if (count == WARN_COUNT + 1) {
            //向管理员发送邮件告警通知
            EmailUtils.sendEmailToAdmin(loginUserId, time, count, "爬虫检测-警告", "请及时处理");
        }

        //是否封号
        if (count > BAN_COUNT) {
            //所有设备都踢下线
            StpUtil.kickout(loginUserId);
            //封号
            User user = new User();
            user.setId(loginUserId);
            user.setUserRole("ban");
            userService.updateById(user);
            //向管理员发送邮件封禁通知
            EmailUtils.sendEmailToAdmin(loginUserId, time, count, "爬虫检测-封禁", "已被封禁");
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "访问过于频繁，已被封号");
        }
    }

}
