package com.ryy.mianshitiku.model.dto.questionBankQuestion;

import lombok.Value;

import java.io.Serializable;

/**
 *  移除题库与题目关系请求
 */
@Value
public class QuestionBankQuestionRemoveRequest implements Serializable {

    /**
     * 题库 id
     */
    private Long questionBankId;

    /**
     * 题目 id
     */
    private Long questionId;

    private static final long serialVersionUID = 1L;
}
