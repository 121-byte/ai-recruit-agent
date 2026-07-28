package com.example.recruit.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.recruit.dal.entity.HrPreference;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * HR 偏好表 Mapper。含 PostgreSQL UPSERT 与过期清理。
 */
@Mapper
public interface HrPreferenceMapper extends BaseMapper<HrPreference> {

    /**
     * UPSERT HR 偏好 (按 hr_id 冲突时更新)。preferenceJson 以 jsonb 写入。
     */
    @Insert("INSERT INTO hr_preference (hr_id, preference_json, expire_at, updated_at) " +
            "VALUES (#{hrId}, " +
            "#{preferenceJson, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler}::jsonb, " +
            "#{expireAt}, now()) " +
            "ON CONFLICT (hr_id) DO UPDATE SET " +
            "preference_json = EXCLUDED.preference_json, " +
            "expire_at = EXCLUDED.expire_at, " +
            "updated_at = now()")
    int upsert(HrPreference preference);

    /**
     * 删除已过期的 HR 偏好记录。
     */
    @Delete("DELETE FROM hr_preference WHERE expire_at IS NOT NULL AND expire_at &lt; now()")
    int deleteExpired();
}
