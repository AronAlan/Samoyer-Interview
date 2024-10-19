package com.samoyer.backend.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.Header;
import cn.hutool.http.useragent.UserAgent;
import cn.hutool.http.useragent.UserAgentUtil;
import com.samoyer.backend.common.ErrorCode;
import com.samoyer.backend.exception.ThrowUtils;

import javax.servlet.http.HttpServletRequest;

/**
 * 设备工具类
 * 从请求头中获取用户使用的设备信息
 *
 * @author Samoyer
 * @since 2024-10-19
 */
public class DeviceUtils {

    public static String getRequestDevice(HttpServletRequest request) {
        String userAgentStr = request.getHeader(Header.USER_AGENT.toString());
        UserAgent userAgent = UserAgentUtil.parse(userAgentStr);
        ThrowUtils.throwIf(userAgent == null, ErrorCode.OPERATION_ERROR, "非法请求");

        //默认值是pc
        String device = "pc";
        if (isMiniProgram(userAgentStr)) {
            //是否为小程序
            device = "miniProgram";
        } else if (isPad(userAgentStr)) {
            //是否为平板设备
            device = "pad";
        } else if (userAgent.isMobile()) {
            //是否为收集
            device = "mobile";
        }
        return device;
    }

    /**
     * 判断是否是小程序
     *
     * @param userAgentStr
     * @return
     */
    private static boolean isMiniProgram(String userAgentStr) {
        //判断User-Agent是否是微信环境
        return StrUtil.containsIgnoreCase(userAgentStr, "MicroMessenger")
                && StrUtil.containsIgnoreCase(userAgentStr, "MiniProgram");
    }

    /**
     * 检查是否为平板设备
     *
     * @param userAgentStr
     * @return
     */
    private static boolean isPad(String userAgentStr) {
        //苹果平板
        boolean isPad = StrUtil.containsIgnoreCase(userAgentStr, "iPad");
        //安卓平板
        boolean isAndroidPad = StrUtil.containsIgnoreCase(userAgentStr, "Android")
                && !StrUtil.containsIgnoreCase(userAgentStr, "Mobile");
        return isPad || isAndroidPad;
    }
}
