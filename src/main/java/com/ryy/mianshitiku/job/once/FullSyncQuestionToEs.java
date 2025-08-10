package com.ryy.mianshitiku.job.once;

import cn.hutool.core.collection.CollUtil;
import com.ryy.mianshitiku.esdao.QuestionEsDao;
import com.ryy.mianshitiku.model.dto.question.QuestionEsDTO;
import com.ryy.mianshitiku.model.entity.Question;
import com.ryy.mianshitiku.service.QuestionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 全量同步数据库题目到 Es
 */
@Component
@Slf4j
public class FullSyncQuestionToEs implements CommandLineRunner {

    @Resource
    private QuestionService questionService;

    @Resource
    private QuestionEsDao questionEsDao;

    @Override
    public void run(String... args) throws Exception {
        // 从数据库中把题目全部取出
        List<Question> questionList = questionService.list();
        if(CollUtil.isEmpty(questionList)){
            return;
        }

        // 将题目转换为 Es 实体类
        List<QuestionEsDTO> questionEsDTOList = questionList.stream()
                .map(QuestionEsDTO::objToDto)
                .collect(Collectors.toList());
        // 将这些 Es 实体类分批插入到 Es 中
        final int pageSize = 500;  // 一次性插入500条数据
        for(int i = 0; i < questionEsDTOList.size(); i += pageSize){
            // 注意同步的数据下标不可以超过数据总量
            int end = Math.min(questionEsDTOList.size(), i + pageSize);
            log.info("sync from {} to {}", i, end);
            questionEsDao.saveAll(questionEsDTOList.subList(i, end));
        }
        log.info("FullSyncQuestionToEs end, total {}", questionEsDTOList.size());
    }
}
