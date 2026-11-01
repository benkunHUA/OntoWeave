package com.ontoweave.data.ontology.query.common;

import java.util.ArrayList;
import java.util.List;

public final class EffectiveQuerySql {

    private EffectiveQuerySql() {
    }

    public static SqlFragment with(CteDefinition... ctes) {
        StringBuilder sql = new StringBuilder("WITH ");
        List<Object> arguments = new ArrayList<>();
        for (int index = 0; index < ctes.length; index++) {
            CteDefinition cte = ctes[index];
            if (index > 0) {
                sql.append(",\n");
            }
            sql.append(cte.name()).append(" AS (\n")
                    .append(cte.body())
                    .append("\n)");
            arguments.addAll(cte.arguments());
        }
        return new SqlFragment(sql.toString(), arguments);
    }
}
