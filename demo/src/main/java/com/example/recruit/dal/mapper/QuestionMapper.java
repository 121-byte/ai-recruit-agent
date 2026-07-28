package com.example.recruit.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.recruit.dal.entity.Question;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 面试题表 Mapper。含批量插入、HR 采纳与按面试删除。
 */
@Mapper
public interface QuestionMapper extends BaseMapper<Question> {

    /**
     * 批量插入面试题 (循环单条 insert)。
     */
    default int batchInsert(List<Question> questions) {
        if (questions == null || questions.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (Question q : questions) {
            n += insert(q);
        }
        return n;
    }

    /**
     * 标记面试题为 HR 采纳。
     */
    @Update("UPDATE question SET hr_adopted = true WHERE id = #{id}")
    int adoptQuestion(@Param("id") Long id);

    /**
     * 按面试 ID 删除全部面试题。
     */
    @Delete("DELETE FROM question WHERE interview_id = #{interviewId}")
    int deleteByInterviewId(@Param("interviewId") Long interviewId);
}
