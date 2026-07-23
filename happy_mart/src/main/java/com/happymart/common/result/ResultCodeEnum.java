package com.happymart.common.result;

import lombok.Getter;

@Getter
public enum ResultCodeEnum {

    SUCCESS(200, "成功"),
    FAIL(500, "失败"),

    // 认证
    UNAUTHORIZED(401, "未登录或 token 已过期"),
    FORBIDDEN(403, "无权限访问"),
    LOGIN_FAIL(402, "用户名或密码错误"),

    // 参数
    PARAM_ERROR(400, "参数错误"),
    PARAM_MISSING(400, "缺少必要参数"),

    // 通用
    NOT_FOUND(404, "资源不存在"),

    // 业务
    USER_EXIST(1001, "用户已存在"),
    USER_NOT_EXIST(1002, "用户不存在"),
    STOCK_NOT_ENOUGH(2001, "库存不足"),
    ORDER_NOT_FOUND(3001, "订单不存在"),
    ORDER_STATUS_ERROR(3002, "订单状态异常"),
    PAY_FAIL(4001, "支付失败"),
    PAY_SIGN_ERROR(4002, "支付签名验证失败"),

    // 系统
    SYSTEM_ERROR(5000, "系统异常"),
    DB_ERROR(5001, "数据库异常"),
    RPC_ERROR(5002, "远程调用失败");

    private final Integer code;
    private final String message;

    ResultCodeEnum(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}