package com.samoyer.backend.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jd.platform.hotkey.client.callback.JdHotKeyStore;
import com.samoyer.backend.annotation.AuthCheck;
import com.samoyer.backend.common.BaseResponse;
import com.samoyer.backend.common.DeleteRequest;
import com.samoyer.backend.common.ErrorCode;
import com.samoyer.backend.common.ResultUtils;
import com.samoyer.backend.constant.UserConstant;
import com.samoyer.backend.exception.BusinessException;
import com.samoyer.backend.exception.ThrowUtils;
import com.samoyer.backend.model.dto.question.QuestionAddRequest;
import com.samoyer.backend.model.dto.question.QuestionEditRequest;
import com.samoyer.backend.model.dto.question.QuestionQueryRequest;
import com.samoyer.backend.model.dto.question.QuestionUpdateRequest;
import com.samoyer.backend.model.dto.questionbankquestion.QuestionBatchDeleteRequest;
import com.samoyer.backend.model.entity.Question;
import com.samoyer.backend.model.entity.User;
import com.samoyer.backend.model.vo.QuestionSimpleVO;
import com.samoyer.backend.model.vo.QuestionVO;
import com.samoyer.backend.sentinel.SentinelConstant;
import com.samoyer.backend.service.QuestionBankQuestionService;
import com.samoyer.backend.service.QuestionService;
import com.samoyer.backend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 题目接口
 *
 * @author Samoyer
 */
@RestController
@RequestMapping("/question")
@Slf4j
public class QuestionController {

    @Resource
    private QuestionService questionService;

    @Resource
    private UserService userService;

    @Resource
    private QuestionBankQuestionService questionBankQuestionService;

    /**
     * 创建题目
     *
     * @param questionAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addQuestion(@RequestBody QuestionAddRequest questionAddRequest,
                                          HttpServletRequest request) {
        ThrowUtils.throwIf(questionAddRequest == null, ErrorCode.PARAMS_ERROR);
        // todo 在此处将实体类和 DTO 进行转换
        Question question = new Question();
        BeanUtils.copyProperties(questionAddRequest, question);
        List<String> tags = questionAddRequest.getTags();
        if (CollUtil.isNotEmpty(tags)) {
            //这里增加题目时，传进来的tags标签不是json类型的，需要转换一下
            question.setTags(JSONUtil.toJsonStr(tags));
        }
        // 数据校验
        questionService.validQuestion(question, true);
        // todo 填充默认值
        User loginUser = userService.getLoginUser(request);
        question.setUserId(loginUser.getId());
        // 写入数据库
        boolean result = questionService.save(question);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        // 返回新写入的数据 id
        long newQuestionId = question.getId();
        //主动增量同步到ES
        questionService.incrementalEs();
        return ResultUtils.success(newQuestionId);
    }

    /**
     * 删除题目
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteQuestion(@RequestBody DeleteRequest deleteRequest,
                                                HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldQuestion.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 删除题目
        boolean result = questionService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        // 删除题库题目关联表中所有questionId为id的数据
        questionBankQuestionService.removeByQuestionId(id);
        //主动增量同步到ES
        questionService.incrementalEs();
        return ResultUtils.success(true);
    }

    /**
     * 更新题目（仅管理员可用）
     *
     * @param questionUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateQuestion(@RequestBody QuestionUpdateRequest questionUpdateRequest) {
        if (questionUpdateRequest == null || questionUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 将实体类和 DTO 进行转换
        Question question = new Question();
        BeanUtils.copyProperties(questionUpdateRequest, question);
        List<String> tags = questionUpdateRequest.getTagsList();
        if (CollUtil.isNotEmpty(tags)) {
            //这里增加题目时，传进来的tags标签不是json类型的，需要转换一下
            question.setTags(JSONUtil.toJsonStr(tags));
        }

        // 数据校验
        questionService.validQuestion(question, false);
        // 判断是否存在
        long id = questionUpdateRequest.getId();
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);

        // 操作数据库
        boolean result = questionService.updateById(question);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        //主动增量同步到ES
        questionService.incrementalEs();
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取题目（封装类）
     *
     * @param id
     * @return
     */
    @GetMapping("/get/vo")
    public BaseResponse<QuestionVO> getQuestionVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);

        User loginUser = userService.getLoginUser(request);
        // 处理爬虫和告警
        questionService.crawlerDetect(loginUser.getId());

        /* 热key缓存 */
        //生成key
        String key = "question_detail_" + id;
        if (JdHotKeyStore.isHotKey(key)) {
            //从本地缓存中获取缓存值
            Object cachedQuestionVO = JdHotKeyStore.get(key);
            //如果缓存中有值的话，直接返回缓存的值
            if (cachedQuestionVO != null) {
                return ResultUtils.success((QuestionVO) cachedQuestionVO);
            }
        }

        /* 热key缓存 */
        // 缓存中值为空的话，则先正常查询数据库
        Question question = questionService.getById(id);
        ThrowUtils.throwIf(question == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        QuestionVO questionVO = questionService.getQuestionVO(question, request);

        //将查询到的数据设置本地缓存
        JdHotKeyStore.smartSet(key, questionVO);

        return ResultUtils.success(questionVO);
    }

    /**
     * 分页获取题目列表（仅管理员可用）
     *
     * @param questionQueryRequest
     * @return
     */
    @PostMapping("/list/page")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Question>> listQuestionByPage(@RequestBody QuestionQueryRequest questionQueryRequest) {
        ThrowUtils.throwIf(questionQueryRequest == null, ErrorCode.PARAMS_ERROR);
        //根据题库id和查询条件（若有）查询题目列表
        Page<Question> questionPage = questionService.listQuestionByPage(questionQueryRequest);
        return ResultUtils.success(questionPage);
    }

    /**
     * 分页获取题目列表（封装类）
     *
     * @param questionQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<QuestionVO>> listQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                               HttpServletRequest request) {
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);

        //基于IP限流
        String remoteAddr = request.getRemoteAddr();
        Entry entry = null;
        try {
            //上报资源
            entry = SphU.entry(SentinelConstant.listQuestionVOByPage, EntryType.IN, 1, remoteAddr);
            //如果上报没有异常，则正常执行
            //查询数据库
            Page<Question> questionPage = questionService.page(new Page<>(current, size),
                    questionService.getQueryWrapper(questionQueryRequest));
            //获取封装类
            return ResultUtils.success(questionService.getQuestionVOPage(questionPage, request));
        } catch (Throwable ex) {
            //上报有异常
            //业务异常
            if (!BlockException.isBlockException(ex)) {
                Tracer.trace(ex);
                return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
            }

            //熔断降级操作
            if (ex instanceof DegradeException) {
                return handleFallback(questionQueryRequest, request, ex);
            }

            //限流操作
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "访问过于频繁，请稍后再试");
        } finally {
            if (entry != null) {
                //释放entry
                entry.exit(1, remoteAddr);
            }
        }
    }

    /**
     * 分页获取题目列表（封装类）（限流版测试）
     *
     * @param questionQueryRequest
     * @param request
     * @return
     */
    /*@PostMapping("/list/page/vo/sentinel")
    public BaseResponse<Page<QuestionVO>> listQuestionVOByPageSentinel(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                                       HttpServletRequest request) {
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);

        //基于IP限流
        String remoteAddr = request.getRemoteAddr();
        Entry entry = null;
        try {
            //上报资源（传热点参数IP）（如某IP占用几个我的资源）(batchCount=1就是这个IP每次上报占用几个资源）
            entry = SphU.entry("listQuestionVOByPage", EntryType.IN, 1, remoteAddr);
            //如果上报没有异常，则正常执行
            // 查询数据库
            Page<Question> questionPage = questionService.page(new Page<>(current, size),
                    questionService.getQueryWrapper(questionQueryRequest));
            // 获取封装类
            return ResultUtils.success(questionService.getQuestionVOPage(questionPage, request));
        } catch (Throwable ex) {
            //上报有异常。捕捉业务异常和Block异常
            //这里不是使用注解（自动统计业务异常，如果是业务异常就自动走fallback降级x）
            //需要手动记录业务异常

            //不是BlockException，即业务异常
            if (!BlockException.isBlockException(ex)) {
                Tracer.trace(ex);
                return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
            }

            //熔断降级操作
            if (ex instanceof DegradeException) {
                return handleFallback(questionQueryRequest, request, ex);
            }

            //限流操作
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "访问过于频繁，请稍后再试");
        } finally {
            if (entry != null) {
                //释放entry
                entry.exit(1, remoteAddr);
            }
        }
    }*/

    /**
     * 降级逻辑
     */
    public BaseResponse<Page<QuestionVO>> handleFallback(QuestionQueryRequest questionQueryRequest,
                                                         HttpServletRequest request, Throwable ex) {
        // 可以返回本地数据或空数据
        return ResultUtils.success(null);
    }

    /**
     * 分页获取题目列表（封装类）
     * 返回题目的简化版VO，不返回这么多详细的信息
     *
     * @param questionQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/svo")
    public BaseResponse<Page<QuestionSimpleVO>> listQuestionSimpleVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                                           HttpServletRequest request) {
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Page<Question> questionPage = questionService.page(new Page<>(current, size),
                questionService.getQueryWrapper(questionQueryRequest));
        // 获取封装类
        return ResultUtils.success(questionService.getQuestionSimpleVOPage(questionPage, request));
    }

    /**
     * 分页获取当前登录用户创建的题目列表
     *
     * @param questionQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<QuestionVO>> listMyQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                                 HttpServletRequest request) {
        ThrowUtils.throwIf(questionQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 补充查询条件，只查询当前登录用户的数据
        User loginUser = userService.getLoginUser(request);
        questionQueryRequest.setUserId(loginUser.getId());
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Page<Question> questionPage = questionService.page(new Page<>(current, size),
                questionService.getQueryWrapper(questionQueryRequest));
        // 获取封装类
        return ResultUtils.success(questionService.getQuestionVOPage(questionPage, request));
    }

    /**
     * 编辑题目
     *
     * @param questionEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> editQuestion(@RequestBody QuestionEditRequest questionEditRequest,
                                              HttpServletRequest request) {
        if (questionEditRequest == null || questionEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // todo 在此处将实体类和 DTO 进行转换
        Question question = new Question();
        BeanUtils.copyProperties(questionEditRequest, question);
        List<String> tags = questionEditRequest.getTags();
        if (CollUtil.isNotEmpty(tags)) {
            question.setTags(JSONUtil.toJsonStr(tags));
        }
        // 数据校验
        questionService.validQuestion(question, false);
        User loginUser = userService.getLoginUser(request);
        // 判断是否存在
        long id = questionEditRequest.getId();
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldQuestion.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库
        boolean result = questionService.updateById(question);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        //主动增量同步到ES
        questionService.incrementalEs();
        return ResultUtils.success(true);
    }

    /**
     * 搜索ES
     * 用户。（返回的无答案）
     *
     * @param questionQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/search/page/svo")
    public BaseResponse<Page<QuestionSimpleVO>> searchQuestionSimpleVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                                             HttpServletRequest request) {
        long size = questionQueryRequest.getPageSize();
        //限制爬虫
        ThrowUtils.throwIf(size > 200, ErrorCode.PARAMS_ERROR);
        Page<Question> questionPage = questionService.searchFromEs(questionQueryRequest);
        return ResultUtils.success(questionService.getQuestionSimpleVOPage(questionPage, request));
    }

    /**
     * 搜索ES
     * 管理员。（返回的QuestionVO）
     *
     * @param questionQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/search/page/vo")
    public BaseResponse<Page<QuestionVO>> searchQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                                 HttpServletRequest request) {
        long size = questionQueryRequest.getPageSize();
        //限制爬虫
        ThrowUtils.throwIf(size > 200, ErrorCode.PARAMS_ERROR);
        Page<Question> questionPage = questionService.searchFromEs(questionQueryRequest);
        // 转换VO包装类中包含了根据questionId查题目对应的题库名字，所以返回的VO中的题库名字在前端是改动即能看到更新。
        // 因为题库名字是重新查的数据库并在返回前端前一步set的
        // 其余的都是定时任务更新的es
        return ResultUtils.success(questionService.getQuestionVOPage(questionPage, request));
    }

    /**
     * 批量删除题目
     * 并移除相应的题库题目关联
     *
     * @param questionBatchDeleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete/batch")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> batchDeleteQuestions(@RequestBody QuestionBatchDeleteRequest questionBatchDeleteRequest,
                                                      HttpServletRequest request) {
        //参数校验
        ThrowUtils.throwIf(questionBatchDeleteRequest == null, ErrorCode.PARAMS_ERROR);
        questionService.batchDeleteQuestions(questionBatchDeleteRequest.getQuestionIdList());
        //主动增量同步到ES
        questionService.incrementalEs();
        return ResultUtils.success(true);
    }

}
