package com.happymart.common.result;

import lombok.Data;

/**
 * 统一返回结果类
 * <p>
 * 后端所有接口都返回这个格式，前端用起来统一。
 * 相当于一个"信封"：后端把数据装在这个信封里，前端收到后拆开看。
 * <p>
 * 返回给前端的 JSON 格式：
 * <pre>
 * {
 *   "code": 200,          // 状态码：200=成功，其他=失败
 *   "message": "成功",    // 提示信息
 *   "data": { ... }       // 真正的数据（可能是对象、列表、分页等）
 * }
 * </pre>
 * <p>
 * 泛型 {@code <T>} 是什么意思？
 * T 是 Type（类型）的缩写，表示"任意类型"。
 * 这样设计的好处是：一个 Result 类可以装任何类型的数据。
 * 比如：
 *   Result&lt;UserVO&gt;     → data 里装的是 UserVO 对象
 *   Result&lt;List&gt;       → data 里装的是列表
 *   Result&lt;Page&gt;       → data 里装的是分页结果
 *   Result&lt;Void&gt;       → data 为 null（比如删除成功，不需要返回数据）
 * <p>
 * 使用方式（在 Controller 里）：
 * <pre>
 * // 成功，带数据
 * return Result.success(userVO);
 * // 成功，不带数据
 * return Result.success();
 * // 失败
 * return Result.fail(ResultCodeEnum.USER_EXIST);
 * // 失败，自定义提示
 * return Result.fail(400, "参数错误");
 * </pre>
 */
@Data  // Lombok → 自动生成 getter/setter/toString
public class Result<T> {

    /**
     * 状态码
     * <p>
     * 200 = 成功
     * 其他数字 = 各种失败原因（具体含义在 ResultCodeEnum 里定义）
     * 比如：
     *   401  → 未登录
     *   1001 → 用户已存在
     *   2001 → 库存不足
     *   5000 → 系统异常
     */
    private Integer code;

    /**
     * 提示信息
     * <p>
     * 成功时一般是"成功"，失败时说明原因，比如"用户名或密码错误"。
     * 前端可以根据这个信息弹提示框给用户看。
     */
    private String message;

    /**
     * 真正的数据
     * <p>
     * 这里用泛型 T，所以可以是任何类型：
     * - 单个对象：UserVO、ProductVO
     * - 列表：List&lt;CategoryVO&gt;
     * - 分页：Page&lt;ProductVO&gt;
     * - 登录结果：LoginVO（包含 token + 用户信息）
     * - 不需要返回数据时：null
     */
    private T data;

    /**
     * 私有构造方法
     * <p>
     * 不让外面直接 new Result()，必须通过静态方法 success() 或 fail() 来创建。
     * 这样可以保证 Result 对象一定是"格式正确"的。
     * 就像去麦当劳不能自己进厨房做汉堡，只能通过柜台点餐。
     */
    private Result() {}

    // ==================== 成功 ====================

    /**
     * 成功（不带返回数据）
     * <p>
     * 用于只需要告诉前端"操作成功"、不需要返回数据的场景。
     * 比如：删除商品成功、修改密码成功。
     * <p>
     * 返回给前端的数据：
     * { code: 200, message: "成功", data: null }
     */
    public static <T> Result<T> success() {
        return build(null, ResultCodeEnum.SUCCESS);
    }

    /**
     * 成功（带返回数据）
     * <p>
     * 最常用的方法，几乎所有查询接口都用它。
     * 比如查用户信息、查商品列表、查订单等。
     * <p>
     * 返回给前端的数据：
     * { code: 200, message: "成功", data: { ... } }
     *
     * @param data 要返回给前端的数据
     * @param <T>  数据类型
     */
    public static <T> Result<T> success(T data) {
        return build(data, ResultCodeEnum.SUCCESS);
    }

    // ==================== 失败 ====================

    /**
     * 失败（根据枚举返回错误）
     * <p>
     * 用于已知的业务错误，比如"用户名已存在""库存不足"。
     * 错误码和信息都定义在 ResultCodeEnum 里。
     * <p>
     * 返回给前端的数据：
     * { code: 1001, message: "用户已存在", data: null }
     *
     * @param codeEnum 错误码枚举（定义了 code + message）
     */
    public static <T> Result<T> fail(ResultCodeEnum codeEnum) {
        return build(null, codeEnum);
    }

    /**
     * 失败（覆盖提示信息）
     * <p>
     * 和上面的 fail(ResultCodeEnum) 类似，但可以自定义提示信息。
     * 当枚举里的默认信息不够具体时用。
     * <p>
     * 比如参数校验失败：
     * Result.fail(ResultCodeEnum.PARAM_ERROR, "用户名不能为空");
     * <p>
     * 返回给前端的数据：
     * { code: 400, message: "用户名不能为空", data: null }
     *
     * @param codeEnum 错误码枚举
     * @param message  自定义的错误提示（覆盖枚举里的默认信息）
     */
    public static <T> Result<T> fail(ResultCodeEnum codeEnum, String message) {
        Result<T> result = new Result<>();
        result.setCode(codeEnum.getCode());
        result.setMessage(message);
        return result;
    }

    /**
     * 失败（直接传 code + message）
     * <p>
     * 用于全局异常处理器里的兜底异常。
     * 当出现了枚举里没定义的错误时，直接传数字和文字。
     * <p>
     * 返回给前端的数据：
     * { code: 5000, message: "系统异常", data: null }
     *
     * @param code    错误码（数字）
     * @param message 错误描述
     */
    public static <T> Result<T> fail(Integer code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }

    // ==================== 内部构造方法 ====================

    /**
     * 构建 Result 对象（私有方法）
     * <p>
     * 这个方法是 success() 和 fail() 的内部工具，
     * 把数据 + 枚举里的 code + message 组装到一起。
     * 外面调 success() / fail() 就行，不需要直接调这个。
     *
     * @param data     要返回的数据
     * @param codeEnum 状态码枚举
     * @param <T>      数据类型
     * @return 组装好的 Result 对象
     */
    private static <T> Result<T> build(T data, ResultCodeEnum codeEnum) {
        Result<T> result = new Result<>();
        result.setCode(codeEnum.getCode());       // 从枚举取状态码
        result.setMessage(codeEnum.getMessage()); // 从枚举取提示信息
        result.setData(data);                     // 设置返回数据
        return result;
    }
}