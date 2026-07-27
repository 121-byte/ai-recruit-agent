package com.example.recruit.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.recruit.dal.entity.CandidateMatch;
import org.apache.ibatis.annotations.Mapper;

/**
 * 候选人匹配表 Mapper。
 */
@Mapper
public interface CandidateMatchMapper extends BaseMapper<CandidateMatch> {
}
