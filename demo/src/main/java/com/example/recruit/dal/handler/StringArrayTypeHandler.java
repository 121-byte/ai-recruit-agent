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
 * PostgreSQL TEXT[] ↔ String[] 类型处理器。
 *
 * <p>实体中的 {@code String[]} 字段 (tags / riskTags / strengths / risks 等) 映射到 PG {@code text[]}。
 * 本处理器通过 {@link java.sql.Connection#createArrayOf(String, Object[])} 写入 SQL ARRAY，
 * 通过 {@link ResultSet#getArray(String)} 读取。
 *
 * <p>{@link MappedTypes}({@code String[].class}) 使 MyBatis-Plus 自动为本包注册，
 * 无需在每个字段上显式声明 {@code @TableField(typeHandler=...)}。
 */
@MappedTypes(String[].class)
@MappedJdbcTypes({JdbcType.ARRAY, JdbcType.OTHER})
public class StringArrayTypeHandler extends BaseTypeHandler<String[]> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String[] parameter, JdbcType jdbcType)
            throws SQLException {
        Array array = ps.getConnection().createArrayOf("text", parameter);
        ps.setArray(i, array);
    }

    @Override
    public String[] getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toStringArray(rs.getArray(columnName));
    }

    @Override
    public String[] getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toStringArray(rs.getArray(columnIndex));
    }

    @Override
    public String[] getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toStringArray(cs.getArray(columnIndex));
    }

    private String[] toStringArray(Array array) throws SQLException {
        if (array == null) {
            return null;
        }
        Object raw = array.getArray();
        if (raw == null) {
            return null;
        }
        if (raw instanceof String[] strs) {
            return strs;
        }
        if (raw instanceof Object[] objects) {
            String[] result = new String[objects.length];
            for (int i = 0; i < objects.length; i++) {
                result[i] = objects[i] == null ? null : objects[i].toString();
            }
            return result;
        }
        // 单元素非数组
        return new String[]{raw.toString()};
    }
}
