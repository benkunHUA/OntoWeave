package com.ontoweave.data.ontology.query.repository;

/** 查询端点一侧的主键与对端主键；方向由调用方解释。 */
public record RelatedLinkRow(
        String currentPrimaryKey,
        String peerPrimaryKey,
        String peerTitle
) {
}
