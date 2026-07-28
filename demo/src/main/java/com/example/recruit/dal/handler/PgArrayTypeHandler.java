package com.example.recruit.dal.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * PostgreSQL BIGINT[] ↔ Long[] 类型处理器 (复刻对齐清单 §6)。
 *
 * <p>处理 {@code consolidation_task.entry_ids} 等 {@code BIGINT[]} 列。
 * 通过 {@link java.sql.Connection#createArrayOf(String, Object[])} 写入 SQL ARRAY，
 * 通过 {@link ResultSet#getArray(String)} 读取为 {@code Long[]}。
 *
 * <p>{@link MappedTypes}({@code Long[].class}) 使 MyBatis-Plus 自动注册，
 * 含 BIGINT[] 列的 entity 无需逐字段显式声明 typeHandler。
 * TEXT[] 列仍由 {@link StringArrayTypeHandler} 处理。
 */
@MappedTypes(Long[].class)
@MappedJdbcTypes({JdbcType.ARRAY, JdbcType.OTHER})
public class PgArrayTypeHandler extends BaseTypeHandler<Long[]> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Long[] parameter, JdbcType jdbcType)
            throws SQLException {
        Array array = ps.getConnection().createArrayOf("bigint", parameter);
        ps.setArray(i, array);
    }

    @Override
    public Long[] getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toLongArray(rs.getArray(columnName));
    }

    @Override
    public Long[] getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toLongArray(rs.getArray(columnIndex));
    }

    @Override
    public Long[] getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toLongArray(cs.getArray(columnIndex));
    }

    private Long[] toLongArray(Array array) throws SQLException {
        if (array == null) {
            return null;
        }
        Object raw = array.getArray();
        if (raw == null) {
            return null;
        }
        if (raw instanceof Long[] longs) {
            return longs;
        }
        if (raw instanceof Object[] objects) {
            Long[] result = new Long[objects.length];
            for (int i = 0; i < objects.length; i++) {
                Number n = (Number) objects[i];
                result[i] = n == null ? null : n.longValue();
            }
            return result;
        }
        // 单元素非数组
        return new Long[]{((Number) raw).longValue()};
    }
}
