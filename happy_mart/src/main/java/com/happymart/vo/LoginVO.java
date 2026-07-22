package com.happymart.vo;                      // VO = 返回给前端的数据

import lombok.Data;                            // @Data：自动生成 getter/setter/toString

/**
 * 登录成功返回的 VO
 * 包含 JWT token 和用户基本信息
 * 前端登录后拿到的就是这两个东西：token（后续请求带在 Header 里）和用户信息
 */
@Data
public class LoginVO {

    private String token;                      // JWT token → 前端后续请求放在 Authorization 请求头里

    private UserVO userInfo;                   // 用户信息（不含密码）
}