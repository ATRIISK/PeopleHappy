package com.happymart.common.exception;

import com.happymart.common.result.Result;
import com.happymart.common.result.ResultCodeEnum;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * <p>
 * 这个类的作用是：替所有 Controller"兜底"处理异常。
 * 没有它的话，每个 Controller 都得自己 try-catch，代码就炸了。
 * <p>
 * 它的原理：
 * {@code @RestControllerAdvice} = @ControllerAdvice + @ResponseBody
 * 意思是：监听所有 Controller 抛出的异常，统一处理，返回 JSON。
 * <p>
 * 工作流程：
 * Controller/Service 抛异常
 *   ↓
 * 全局异常处理器根据异常类型找到对应的处理方法
 *   ↓
 * 把异常信息包装成 Result.fail() 返回给前端
 * <p>
 * 前端永远收到统一的 JSON 格式：
 * { code: xxx, message: "xxx", data: null }
 * 而不是乱七八糟的 HTML 错误页面。
 * <p>
 * 覆盖了 5 种异常：
 * 1. BusinessException        → 业务异常（用户名已存在、库存不足等）
 * 2. MethodArgumentNotValidException → 参数校验失败（@Valid 校验）
 * 3. ConstraintViolationException    → 参数校验失败（@RequestParam 校验）
 * 4. DataAccessException      → 数据库异常
 * 5. Exception（兜底）        → 其他所有没预料到的异常
 */
@Slf4j  // Lombok → 自动生成 log 变量
@RestControllerAdvice  // 监听所有 Controller 的异常，并返回 JSON
public class GlobalExceptionHandler {

    /**
     * 处理业务异常
     * <p>
     * BusinessException 是我们在 Service 层手动抛的异常。
     * 比如：
     * - 注册时用户名已存在 → throw new BusinessException(USER_EXIST)
     * - 登录时密码错误 → throw new BusinessException(LOGIN_FAIL)
     * - 下单时库存不足 → throw new BusinessException(STOCK_NOT_ENOUGH)
     * <p>
     * 处理方式：用 warn 级别记日志（因为是预期的错误，不是系统错误），
     * 然后返回对应的错误码和信息给前端。
     * <p>
     * 注意：状态码是 200（正常返回），但 code 是业务错误码。
     * 前端判断逻辑：code === 200 是成功，code !== 200 是失败。
     *
     * @param e 业务异常（包含 code 和 message）
     * @return Result.fail(业务错误码, 错误描述)
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        // 用 warn 级别：业务异常是"预期的错误"，不用报 error
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 处理参数校验异常（来自 @Valid 注解）
     * <p>
     * 当 DTO 中加了 @NotBlank、@Size、@Pattern 等校验注解，
     * 前端传的参数不合法时，Spring 会抛出 MethodArgumentNotValidException。
     * <p>
     * 比如 RegisterDTO 里：
     * {@code @NotBlank(message = "用户名不能为空")}
     * private String username;
     * <p>
     * 如果前端没传 username，就会触发这个异常。
     * 我们把所有字段的错误信息收集起来，用逗号拼接返回。
     * <p>
     * 返回给前端：
     * { code: 400, message: "用户名不能为空, 密码不能为空", data: null }
     *
     * @param e 参数校验异常（包含哪个字段错了、错在哪）
     * @return Result.fail(PARAM_ERROR, 拼接的错误信息)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        // 把每个字段的错误信息取出来，用逗号连成一句话
        // 比如 "用户名不能为空, 密码至少6位"
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)  // 取每个字段的校验提示文字
                .collect(Collectors.joining(", "));  // 用逗号拼接
        log.warn("参数校验失败: {}", message);
        return Result.fail(ResultCodeEnum.PARAM_ERROR, message);
    }

    /**
     * 处理参数校验异常（来自 @RequestParam / @PathVariable）
     * <p>
     * 和上面的区别：
     * - MethodArgumentNotValidException → DTO 对象的 @Valid 校验失败
     * - ConstraintViolationException → 单个参数的校验失败
     * <p>
     * 比如：
     * {@code @RequestParam @Min(1) Integer page}
     * 如果前端传了 page=0，就会触发这个异常。
     *
     * @param e 参数校验异常
     * @return Result.fail(PARAM_ERROR, 错误信息)
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolationException(ConstraintViolationException e) {
        log.warn("参数校验失败: {}", e.getMessage());
        return Result.fail(ResultCodeEnum.PARAM_ERROR, e.getMessage());
    }

    /**
     * 处理数据库异常
     * <p>
     * 当 MyBatis、MyBatis-Plus 或 JDBC 操作数据库时出错，会抛出 DataAccessException。
     * 比如：
     * - SQL 语法错误
     * - 表不存在
     * - 违反唯一约束（插入了重复数据）
     * - 数据库连接失败
     * <p>
     * 处理方式：用 error 级别记日志（因为这是系统错误，需要开发人员关注），
     * 然后返回"数据库异常"给前端。
     * <p>
     * 注意：不把具体错误信息返回给前端（比如"Duplicate entry 'xxx' for key"），
     * 因为这样会暴露数据库结构，不安全。
     *
     * @param e 数据库异常
     * @return Result.fail(DB_ERROR)
     */
    @ExceptionHandler(DataAccessException.class)
    public Result<Void> handleDataAccessException(DataAccessException e) {
        // 用 error 级别：数据库异常是系统错误，需要开发人员排查
        log.error("数据库异常: ", e);
        return Result.fail(ResultCodeEnum.DB_ERROR);
    }

    /**
     * 处理所有未捕获的异常（兜底）
     * <p>
     * 这是最后一道防线。
     * 如果抛出的异常不属于上面任何一种，就由这个方法处理。
     * <p>
     * 比如：
     * - NullPointerException（空指针）
     * - ArrayIndexOutOfBoundsException（数组越界）
     * - 其他意料之外的运行时异常
     * <p>
     * 处理方式：
     * - HTTP 状态码设为 500（Internal Server Error）
     * - 用 error 级别记日志（需要开发人员紧急排查）
     * - 返回"系统异常"给前端（不暴露具体错误，防黑客）
     * <p>
     * 注意：这个方法的 @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
     * 会让 HTTP 响应状态码变成 500。
     * 和上面几个方法不同，上面都是 200（业务错误码在 JSON body 里）。
     * 因为到了这里说明真是系统出问题了，不是业务逻辑的问题。
     *
     * @param e 未捕获的异常
     * @return Result.fail(SYSTEM_ERROR)
     */
    /**
     * 处理上传文件超过大小限制（v1.10 商品图片本地上传）
     * <p>
     * 为什么必须单独接住？
     * multipart 解析发生在 DispatcherServlet.checkMultipart（先于拦截器/Controller），
     * 文件超过 spring.servlet.multipart.max-file-size 时会抛 MaxUploadSizeExceededException。
     * 如果不单独处理，会掉进下面的兜底 Exception → HTTP 500「系统异常」，体验差。
     * <p>
     * 这里返回参数错误（400）+"图片大小不能超过 5MB"，前端友好提示。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        log.warn("上传文件超过大小限制: {}", e.getMessage());
        return Result.fail(ResultCodeEnum.PARAM_ERROR, "图片大小不能超过 5MB");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)  // HTTP 状态码设为 500
    public Result<Void> handleException(Exception e) {
        // 用 error 级别打印完整堆栈，方便排查问题
        log.error("系统异常: ", e);
        return Result.fail(ResultCodeEnum.SYSTEM_ERROR);
    }
}
