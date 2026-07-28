package com.happymart.vo;                      // VO = 返回给前端的数据

import lombok.Data;                            // @Data：自动生成 getter/setter/toString

/**
 * 发起支付后返回的 VO
 * codeUrl 字段名沿用早期设计，语义是"二维码内容字符串"——
 * 装的是支付宝 alipay.trade.precreate 接口返回的 qr_code，不是微信的 code_url
 * 前端拿到这个字符串后用二维码生成组件渲染成图片给用户扫
 */
@Data
public class PayVO {

    private String codeUrl;                    // 支付宝 qr_code，前端渲染成二维码供沙箱钱包App扫码
}