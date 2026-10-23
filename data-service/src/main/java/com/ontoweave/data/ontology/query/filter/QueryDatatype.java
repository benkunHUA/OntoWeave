package com.ontoweave.data.ontology.query.filter;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import static com.ontoweave.data.ontology.query.filter.FilterOperator.CONTAINS;
import static com.ontoweave.data.ontology.query.filter.FilterOperator.EQ;
import static com.ontoweave.data.ontology.query.filter.FilterOperator.GT;
import static com.ontoweave.data.ontology.query.filter.FilterOperator.GTE;
import static com.ontoweave.data.ontology.query.filter.FilterOperator.IS_NOT_NULL;
import static com.ontoweave.data.ontology.query.filter.FilterOperator.IS_NULL;
import static com.ontoweave.data.ontology.query.filter.FilterOperator.LT;
import static com.ontoweave.data.ontology.query.filter.FilterOperator.LTE;
import static com.ontoweave.data.ontology.query.filter.FilterOperator.NE;

public enum QueryDatatype {
    TEXT("text_value", EnumSet.of(EQ, NE, CONTAINS, IS_NULL, IS_NOT_NULL), "primary_key_value"),
    ENUM("text_value", EnumSet.of(EQ, NE, CONTAINS, IS_NULL, IS_NOT_NULL), "primary_key_value"),
    INTEGER("num_value", EnumSet.of(EQ, NE, GT, GTE, LT, LTE, IS_NULL, IS_NOT_NULL),
            "primary_key_num"),
    DECIMAL("num_value", EnumSet.of(EQ, NE, GT, GTE, LT, LTE, IS_NULL, IS_NOT_NULL),
            "primary_key_num"),
    BOOLEAN("bool_value", EnumSet.of(EQ), "primary_key_value"),
    DATE("date_value", EnumSet.of(EQ, NE, GT, GTE, LT, LTE, IS_NULL, IS_NOT_NULL),
            "primary_key_date"),
    TIMESTAMP("ts_value", EnumSet.of(EQ, NE, GT, GTE, LT, LTE, IS_NULL, IS_NOT_NULL),
            "primary_key_ts");

    private final String valueColumn;
    private final Set<FilterOperator> operators;
    private final String sortColumn;

    QueryDatatype(
            String valueColumn,
            Set<FilterOperator> operators,
            String sortColumn
    ) {
        this.valueColumn = valueColumn;
        this.operators = Set.copyOf(operators);
        this.sortColumn = sortColumn;
    }

    public String valueColumn() {
        return valueColumn;
    }

    public String sortColumn() {
        return sortColumn;
    }

    public boolean supports(FilterOperator operator) {
        return operators.contains(operator);
    }

    public boolean isTextual() {
        return this == TEXT || this == ENUM;
    }

    public static QueryDatatype from(String datatype) {
        String normalized = normalize(datatype);
        return switch (normalized) {
            case "text" -> TEXT;
            case "enum" -> ENUM;
            case "integer" -> INTEGER;
            case "decimal" -> DECIMAL;
            case "boolean" -> BOOLEAN;
            case "date" -> DATE;
            case "timestamp", "timestamptz" -> TIMESTAMP;
            default -> throw new IllegalArgumentException("unsupported datatype: " + datatype);
        };
    }

    private static String normalize(String datatype) {
        if (datatype == null) {
            return "";
        }
        String normalized = datatype.trim().toLowerCase(Locale.ROOT);
        int parenthesis = normalized.indexOf('(');
        if (parenthesis >= 0) {
            normalized = normalized.substring(0, parenthesis).trim();
        }
        return switch (normalized) {
            case "timestamp with time zone", "timestamp_tz", "timestamp with zone" ->
                    "timestamptz";
            case "timestamp without time zone", "timestamp_ntz", "timestamp without zone" ->
                    "timestamp";
            default -> normalized;
        };
    }
}
