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
import java.util.Arrays;

/**
 * pgvector VECTOR(1024) ↔ float[] 类型处理器。
 *
 * <p>pgvector 的文本表示为 {@code [0.1,0.2,0.3,...]}。本处理器将 Java {@code float[]}
 * 序列化为该格式写入 PreparedStatement，并从 ResultSet 反序列化为 {@code float[]}。
 *
 * <p>当底层数据源不是 PostgreSQL（如 H2 降级模式）时，写入按字符串处理、读取容错，
 * 不影响应用启动。
 */
@MappedJdbcTypes(JdbcType.OTHER)
@MappedTypes(float[].class)
public class FloatVectorTypeHandler extends BaseTypeHandler<float[]> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, float[] parameter, JdbcType jdbcType)
            throws SQLException {
        String vectorLiteral = toPgVectorLiteral(parameter);
        // 使用 PGobject 风格的字符串绑定；非 PG 驱动也能以字符串接收
        ps.setObject(i, vectorLiteral);
    }

    @Override
    public float[] getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public float[] getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public float[] getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    /** float[] → pgvector 字面量 {@code [v1,v2,...]}。 */
    public static String toPgVectorLiteral(float[] vector) {
        if (vector == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(vector.length * 8 + 2);
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            // 控制精度，避免浮点尾部噪声
            sb.append(Math.round(vector[i] * 1_000_000f) / 1_000_000f);
        }
        sb.append(']');
        return sb.toString();
    }

    /** pgvector 字面量 / JDBC Array → float[]，容错多种形态。 */
    private static float[] parse(Object raw) throws SQLException {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Array array) {
            Object arr = array.getArray();
            if (arr instanceof float[] floats) {
                return floats;
            }
            if (arr instanceof double[] doubles) {
                float[] result = new float[doubles.length];
                for (int i = 0; i < doubles.length; i++) {
                    result[i] = (float) doubles[i];
                }
                return result;
            }
            if (arr instanceof Object[] objects) {
                float[] result = new float[objects.length];
                for (int i = 0; i < objects.length; i++) {
                    result[i] = ((Number) objects[i]).floatValue();
                }
                return result;
            }
        }
        String s = raw.toString().trim();
        if (s.startsWith("[") && s.endsWith("]")) {
            s = s.substring(1, s.length() - 1);
        }
        if (s.isEmpty()) {
            return new float[0];
        }
        String[] parts = s.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }
        return result;
    }

    /** 便于其他模块复用：把任意 float[] 转 pgvector 字面量。 */
    public static String literal(float[] vector) {
        return toPgVectorLiteral(vector);
    }

    /** 工具：两个向量的余弦相似度（用于动态锚点匹配等场景）。 */
    public static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0.0;
        }
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        double denom = Math.sqrt(na) * Math.sqrt(nb);
        return denom == 0 ? 0.0 : dot / denom;
    }

    @Override
    public String toString() {
        return "FloatVectorTypeHandler" + Arrays.toString(new float[]{0});
    }
}
