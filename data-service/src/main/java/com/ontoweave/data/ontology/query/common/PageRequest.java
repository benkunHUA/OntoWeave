package com.ontoweave.data.ontology.query.common;

public record PageRequest(int page, int pageSize, int offset) {

    public static PageRequest of(Integer page, Integer pageSize) {
        int resolvedPage = page == null ? 1 : page;
        int resolvedSize = pageSize == null ? QueryConstants.DEFAULT_PAGE_SIZE : pageSize;
        if (resolvedPage < 1) {
            throw QueryErrors.invalidRequest("page 必须从 1 开始");
        }
        if (resolvedSize < 1 || resolvedSize > QueryConstants.MAX_PAGE_SIZE) {
            throw QueryErrors.invalidRequest("pageSize 必须在 1 到 200 之间");
        }
        try {
            int offset = Math.multiplyExact(resolvedPage - 1, resolvedSize);
            return new PageRequest(resolvedPage, resolvedSize, offset);
        } catch (ArithmeticException exception) {
            throw QueryErrors.invalidRequest("分页参数超出范围");
        }
    }
}
