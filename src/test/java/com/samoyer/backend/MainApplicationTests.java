package com.samoyer.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 主类测试
 *
 * @author Samoyer

 */
@SpringBootTest
class MainApplicationTests {

    @Test
    void contextLoads() throws UnknownHostException {
        System.out.println(InetAddress.getLocalHost().getHostName());
    }

}
