package com.happymart.common.result;

import lombok.Data;

@Data
public class Result<T> {

    private Integer code;
    private String message;
    private T data;

    private Result() {}

    public static <T> Result<T> success() {
        return build(null, ResultCodeEnum.SUCCESS);
    }

    public static <T> Result<T> success(T data) {
        return build(data, ResultCodeEnum.SUCCESS);
    }

    public static <T> Result<T> fail(ResultCodeEnum codeEnum) {
        return build(null, codeEnum);
    }

    public static <T> Result<T> fail(ResultCodeEnum codeEnum, String message) {
        Result<T> result = new Result<>();
        result.setCode(codeEnum.getCode());
        result.setMessage(message);
        return result;
    }

    public static <T> Result<T> fail(Integer code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }

    private static <T> Result<T> build(T data, ResultCodeEnum codeEnum) {
        Result<T> result = new Result<>();
        result.setCode(codeEnum.getCode());
        result.setMessage(codeEnum.getMessage());
        result.setData(data);
        return result;
    }
}