package com.example.recruit.dal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.recruit.dal.handler.FloatVectorTypeHandler;
import lombok.Data;

/**
 * 文档语义分块表 (schema.sql §3.1.6)。
 */
@Data
@TableName(value = "document_chunk", autoResultMap = true)
public class DocumentChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** resume/job */
    private String parentType;

    private Long parentId;

    private Integer chunkIndex;

    /** skill/experience/education/summary */
    private String chunkType;

    private String content;

    @TableField(typeHandler = FloatVectorTypeHandler.class)
    private float[] embedding;
}
