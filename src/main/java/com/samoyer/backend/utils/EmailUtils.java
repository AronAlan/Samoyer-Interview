package com.samoyer.backend.utils;

import cn.hutool.extra.mail.MailUtil;

/**
 * 发送邮件工具类
 *
 * @author Samoyer
 * @since 2024-10-20
 */
public class EmailUtils {
    private static final String EMAIL_ADDRESS = "x_zhichao@outlook.com";

    /**
     * 发送邮件
     *
     * @param content
     */
    public static void send(String subject, String content) {
        //向管理员发送邮件通知
        MailUtil.send(EMAIL_ADDRESS, subject, content, false);
    }

    /**
     * 向管理员发送邮件通知
     *
     * @param loginUserId
     * @param time
     * @param count
     */
    public static void sendEmailToAdmin(long loginUserId, String time, long count, String subject, String type) {
        //向管理员发送邮件告警通知
        //获取当前时间
        String content = "用户id为" + loginUserId + "的账号在 " + time + " 的前1分钟内访问了" + count + "次，" + type;
        // TODO 改为延时发送
        EmailUtils.send(subject, content);
    }
}