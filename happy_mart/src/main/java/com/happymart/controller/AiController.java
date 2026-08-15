package com.happymart.controller;

import com.happymart.common.exception.BusinessException;
import com.happymart.common.result.Result;
import com.happymart.dto.response.AiChatVO;
import com.happymart.dto.response.KbSearchResult;
import com.happymart.service.impl.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AI 购物助手接口（v1.11，阶段四，见开发文档 §14）
 * <p>
 * 提供前台右下角悬浮助手的对话接口：POST /api/ai/chat
 * <p>
 * 鉴权说明：本接口对【游客开放】（WebMvcConfig 已放行 /api/ai/**），
 * 任何人都能问商品，不需要登录。理由：逛商城看商品是游客基本需求，
 * 且第一版不做会话记忆，不存在越权访问他人数据的问题。
 * <p>
 * 请求体：{"message": "推荐适合运动的耳机"}
 * 返回体：{ code: 200, message: "成功", data: { response: "AI 回答", sources: [商品卡片...] } }
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor   // Lombok → 为 final 字段自动生成构造器
@Slf4j                      // Lombok → 自动生成 log 对象
public class AiController {

    private final ChatClient chatClient;              // AI 对话客户端（AiConfig 提供）
    private final KnowledgeBaseService knowledgeBaseService; // 商品知识库（RAG 检索）

    /**
     * AI 对话：混合检索商品知识库 → 拼 system prompt → 通义千问非流式回答
     */
    @PostMapping("/chat")
    public Result<AiChatVO> chat(@RequestBody Map<String, String> request) {
        String userMessage = request.get("message");
        // 参数校验：消息非空 + 长度上限（code-review F9）
        // 长度上限防两件事：① 超大消息原样转发给付费模型（token 成本爆炸）；② 全量写进日志（日志膨胀/隐私）。
        // 500 字足够问清楚一个导购问题。
        if (userMessage == null || userMessage.trim().isEmpty()) {
            throw new BusinessException(400, "消息内容不能为空");
        }
        if (userMessage.length() > 500) {
            throw new BusinessException(400, "消息内容过长，请控制在 500 字以内");
        }
        // 日志只记前 80 字（防长消息刷日志），完整消息不落盘
        log.info("AI 购物助手请求 - 消息: {}",
                userMessage.length() > 80 ? userMessage.substring(0, 80) + "…" : userMessage);

        // 第一步（RAG）：根据用户问题检索商品知识库，作为回答的参考依据
        KbSearchResult kb = knowledgeBaseService.hybridSearch(userMessage);

        // 第二步：构造 system prompt —— 约束 AI 只基于商城真实商品信息回答，不编造
        String systemPrompt = "你是众乐电子商城的 AI 购物助手，帮用户推荐、介绍商城里的商品。"
                + "请仅基于下方【商城商品信息】回答用户问题；"
                + "若商品信息中没有相关商品，如实说明“商城暂无相关商品”，不要编造。"
                + "回答时在句末标注引用，如 [1]。";
        // 检索到商品才拼参考资料，否则 AI 只能如实说没有
        if (kb.context() != null && !kb.context().isEmpty()) {
            systemPrompt += "\n\n【商城商品信息】\n" + kb.context();
        }

        try {
            // 第三步：非流式调用通义千问，一次性返回回答文本
            String response = chatClient.prompt()
                    .system(systemPrompt)   // 系统提示（角色 + 参考资料）
                    .user(userMessage)      // 用户问题
                    .call()                 // 同步阻塞调用（非流式）
                    .content();             // 取回答文本

            return Result.success(new AiChatVO(response, kb.sources()));
        } catch (Exception e) {
            // 第四步（兜底）：API Key 未配置 / 模型超时 / 网络异常等，统一友好错误，不让 500 裸奔
            log.error("AI 购物助手调用失败", e);
            return Result.fail(500, "AI 服务暂时不可用，请稍后再试");
        }
    }
}
