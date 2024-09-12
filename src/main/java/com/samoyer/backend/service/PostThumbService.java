package com.samoyer.backend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.samoyer.backend.model.entity.PostThumb;
import com.samoyer.backend.model.entity.User;

/**
 * 帖子点赞服务
 *
 * @author Samoyer

 */
public interface PostThumbService extends IService<PostThumb> {

    /**
     * 点赞
     *
     * @param postId
     * @param loginUser
     * @return
     */
    int doPostThumb(long postId, User loginUser);

    /**
     * 帖子点赞（内部服务）
     *
     * @param userId
     * @param postId
     * @return
     */
    int doPostThumbInner(long userId, long postId);
}
