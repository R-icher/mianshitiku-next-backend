package com.ryy.mianshitiku.job.cycle;

import cn.hutool.core.collection.CollUtil;
import com.ryy.mianshitiku.esdao.QuestionEsDao;
import com.ryy.mianshitiku.mapper.QuestionMapper;
import com.ryy.mianshitiku.model.dto.question.QuestionEsDTO;
import com.ryy.mianshitiku.model.entity.Question;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 增量同步数据库题目到 Es
 */
@Component
@Slf4j
public class IncSyncQuestionToEs {
    @Resource
    private QuestionMapper questionMapper;

    @Resource
    private QuestionEsDao questionEsDao;

    /**
     * 每分钟执行一次，实时监听数据库的变化，将变化的内容同步到 Es 中
     */
    @Scheduled(fixedRate = 60 * 1000)
    public void run(){
        // 查询近 5 分钟内的数据
        long FIVE_MINUTES = 5 * 60 * 1000L;
        // 拿到五分钟内的数据
        Date fiveMinutesAgoDate = new Date(new Date().getTime() - FIVE_MINUTES);
        // 由于MP无法把被逻辑删除的字段同步到 ES，因此需要自己加一个方法把被逻辑删除的字段加入到 ES
        List<Question> questionList = questionMapper.listQuestionWithDelete(fiveMinutesAgoDate);
        if(CollUtil.isEmpty(questionList)){
            log.info("no inc question");
            return;
        }

        // 再次执行全量更新操作
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
