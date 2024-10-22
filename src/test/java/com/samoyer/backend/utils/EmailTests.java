package com.samoyer.backend.utils;

import cn.hutool.core.date.DateUtil;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Date;
import java.util.Properties;
import java.util.concurrent.Executor;


/**
 * 邮件发送测试
 *
 * @author Samoyer
 */
@SpringBootTest
class EmailTests {

    @Test
    void sendEmail() {
//        long loginUserId = 1;
//        String time = DateUtil.format(new Date(), "yyyy-MM-dd HH:mm");
//        long count = 11;
//        EmailUtils.sendEmailToAdmin(loginUserId, time, count, "爬虫检测-警告", "请及时处理");
    }

}
