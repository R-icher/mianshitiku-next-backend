package com.ryy.mianshitiku.model.dto.question;

import lombok.Data;

/**
 * AI 生成题目请求
 */
@Data
public class QuestionAIGenerateRequest {

    private String questionType;
    private int number = 10;

}
