package cn.xiaomo.breeze.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.PGobject;

/**
 * PostgreSQL JSONB 类型处理器。
 * <p>
 * 支持 {@link Map} 和 {@link List} 两种 JSON 结构的序列化/反序列化。
 * 使用 {@code Object} 泛型以兼容所有 JSONB 字段类型。
 * </p>
 */
public class JsonbTypeHandler extends BaseTypeHandler<Object> {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Object parameter, JdbcType jdbcType)
            throws SQLException {
        try {
            PGobject pg = new PGobject();
            pg.setType("jsonb");
            pg.setValue(mapper.writeValueAsString(parameter));
            ps.setObject(i, pg);
        } catch (Exception e) {
            throw new SQLException("Failed to serialize JSONB value", e);
        }
    }

    @Override
    public Object getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public Object getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public Object getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private Object parse(String json) throws SQLException {
        if (json == null) {
            return null;
        }
        try {
            String trimmed = json.trim();
            if (trimmed.startsWith("[")) {
                return mapper.readValue(json, List.class);
            }
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new SQLException("Failed to parse JSONB value", e);
        }
    }
}
