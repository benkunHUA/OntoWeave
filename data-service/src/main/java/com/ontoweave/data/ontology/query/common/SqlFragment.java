package com.ontoweave.data.ontology.query.common;

import java.util.ArrayList;
import java.util.List;

public record SqlFragment(String sql, List<Object> arguments) {

    public SqlFragment {
        if (sql == null) {
            throw new IllegalArgumentException("sql must not be null");
        }
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
    }

    public static SqlFragment of(String sql, List<Object> arguments) {
        return new SqlFragment(sql, arguments);
    }

    public static SqlFragment of(String sql, Object... arguments) {
        return new SqlFragment(sql, List.of(arguments));
    }

    public SqlFragment append(String suffix) {
        return new SqlFragment(sql + suffix, arguments);
    }

    public static SqlFragment concat(SqlFragment... fragments) {
        StringBuilder sql = new StringBuilder();
        List<Object> arguments = new ArrayList<>();
        for (SqlFragment fragment : fragments) {
            sql.append(fragment.sql());
            arguments.addAll(fragment.arguments());
        }
        return new SqlFragment(sql.toString(), arguments);
    }
}
