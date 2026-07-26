package com.happymart.common.exception;

import com.happymart.common.result.ResultCodeEnum;
import lombok.Getter;

/**
 * 业务异常类
 * <p>/
 * 这个类专门用来抛"业务上的错误"。
 * 什么是"业务错误"？就是代码没写错，但是因为业务规则不允许而报错。
 * <p>
 * 比如：
 * - 用户注册时，用户名已经被别人用了 → 抛 BusinessException(USER_EXIST)
 * - 用户登录时，密码输错了 → 抛 BusinessException(LOGIN_FAIL)
 * - 下单时，库存不够了 → 抛 BusinessException(STOCK_NOT_ENOUGH)
 * <p>
 * 它是一个 RuntimeException（运行时异常），也就是说：
 * 你不必在方法上写 throws BusinessException，它自动往上抛。
 * 全局异常处理器 GlobalExceptionHandler 会接住它，返回给前端。
 * <p>
 * 流程：
 * Service 层抛异常 → 往上抛到 Controller → 全局异常处理器接住
 * → 封装成 Result.fail(code, message) → 返回给前端
 * <p>
 * 为什么要自定义异常类？
 * 因为 Java 自带的异常（如 IllegalArgumentException）没有"错误码"这个概念。
 * 我们想要的是：{ code: 1001, message: "用户已存在" }
 * BusinessException 可以同时带 code（数字）和 message（文字）。
 */
@Getter  // Lombok → 自动生成 getter（getCode() / getMessage()），但 message 是从父类 RuntimeException 继承的
public class BusinessException extends RuntimeException {

    /**
     * 业务错误码
     * <p>
     * 对应 ResultCodeEnum 里定义的数字。
     * 比如：
     *   200  → 成功（BusinessException 不会用这个）
     *   401  → 未登录
     *   1001 → 用户已存在
     *   2001 → 库存不足
     *   3001 → 订单不存在
     * <p>
     * 前端拿到这个 code，可以做不同的处理（比如 401 跳登录页）。
     */
    private final Integer code;

    /**
     * 构造方法：从枚举创建异常
     * <p>
     * 最常用的方式。枚举里同时定义了 code 和 message，直接传进来。
     * <p>
     * 用法：
     * throw new BusinessException(ResultCodeEnum.USER_EXIST);
     * // 效果：code=1001, message="用户已存在"
     *
     * @param codeEnum 错误码枚举
     */
    public BusinessException(ResultCodeEnum codeEnum) {
        super(codeEnum.getMessage());  // 把枚举里的 message 传给父类 RuntimeException
        this.code = codeEnum.getCode();  // 把枚举里的 code 存到自己字段里
    }

    /**
     * 构造方法：自定义 code 和 message
     * <p>
     * 当枚举里没有你想要的错误码时，直接传数字和字符串。
     * <p>
     * 用法：
     * throw new BusinessException(400, "参数xxx不合法");
     *
     * @param code    错误码
     * @param message 错误描述
     */
    public BusinessException(Integer code, String message) {
        super(message);     // 传给父类
        this.code = code;   // 存下来
    }

    /**
     * 构造方法：枚举 + 自定义 message
     * <p>
     * 用枚举的 code，但 message 想自己写。
     * 比如枚举里 PARAM_ERROR 的 message 是"参数错误"，
     * 但你想说"用户名不能为空"，就用这个。
     * <p>
     * 用法：
     * throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "用户名不能为空");
     * // 效果：code=400, message="用户名不能为空"
     *
     * @param codeEnum 错误码枚举（只取它的 code）
     * @param message  自定义的错误提示
     */
    public BusinessException(ResultCodeEnum codeEnum, String message) {
        super(message);     // 用自己的 message
        this.code = codeEnum.getCode();  // 用枚举的 code
    }
}