package com.ryy.mianshitiku.manager;

import cn.hutool.core.collection.CollUtil;
import com.ryy.mianshitiku.common.ErrorCode;
import com.ryy.mianshitiku.exception.BusinessException;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionChoice;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import com.volcengine.ark.runtime.service.ArkService;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

public class AiManager {

    @Resource
    private ArkService aiService;

    private final String DEFAULT_MODEL = "deepseek-v3-250324";

    public String doChat(String userPrompt){
        return doChat("", userPrompt, DEFAULT_MODEL);
    }

    public String doChat(String systemPrompt, String userPrompt){
        return doChat(systemPrompt, userPrompt, DEFAULT_MODEL);
    }

    /**
     * 调用 AI 接口，获取 AI 的响应字符串
     * @return
     */
    public String doChat(String systemPrompt, String userPrompt, String model){
        // 构造消息列表
        final List<ChatMessage> messages = new ArrayList<>();
        final ChatMessage systemMessage = ChatMessage.builder().role(ChatMessageRole.SYSTEM).content(systemPrompt).build();
        final ChatMessage userMessage = ChatMessage.builder().role(ChatMessageRole.USER).content(userPrompt).build();
        messages.add(systemMessage);
        messages.add(userMessage);

        // 构造发送请求的请求体
        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
//                .model("")
                .model(model)
                .messages(messages)
                .build();

        // 遍历每个响应得到输出
        List<ChatCompletionChoice> choices = aiService.createChatCompletion(chatCompletionRequest).getChoices();
        if(CollUtil.isEmpty(choices)){
            return (String) choices.get(0).getMessage().getContent();
        }
        throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 调用失败，没有返回结果");

//        aiService.shutdownExecutor();
    }
}
