package com.ontoweave.data.ontology.binding.service;

import com.ontoweave.data.ontology.binding.api.dto.BindableDatasetPageResponse;
import com.ontoweave.data.ontology.binding.api.dto.BindableDatasetResponse;
import com.ontoweave.data.ontology.binding.api.dto.BindableDatasetDetailResponse;
import com.ontoweave.data.ontology.binding.api.dto.DatasetColumnResponse;
import com.ontoweave.data.ontology.binding.support.BindingErrors;
import com.ontoweave.data.ontology.common.DatasetKind;
import com.ontoweave.data.ontology.dataset.DatasetPage;
import com.ontoweave.data.ontology.dataset.DatasetRef;
import com.ontoweave.data.ontology.dataset.DatasetUnavailableException;
import com.ontoweave.data.ontology.dataset.OntologyDatasetCatalog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class OntologyDatasetService {

    private final OntologyDatasetCatalog catalog;
    private final JdbcTemplate jdbc;

    public OntologyDatasetService(OntologyDatasetCatalog catalog, JdbcTemplate jdbc) {
        this.catalog = catalog;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public BindableDatasetPageResponse list(
            UUID ownerId,
            String keyword,
            int page,
            int size
    ) {
        DatasetPage datasets = catalog.listBindable(ownerId, keyword, page, size);
        List<BindableDatasetResponse> items = datasets.items().stream()
                .map(this::response)
                .toList();
        return new BindableDatasetPageResponse(
                items,
                datasets.total(),
                datasets.page(),
                datasets.size()
        );
    }

    @Transactional(readOnly = true)
    public List<DatasetColumnResponse> columns(DatasetKind kind, UUID datasetId, UUID ownerId) {
        try {
            return catalog.listColumns(kind, datasetId, ownerId).stream()
                    .map(DatasetColumnResponse::from)
                    .toList();
        } catch (DatasetUnavailableException exception) {
            throw BindingErrors.datasetNotFound(kind, datasetId);
        }
    }

    @Transactional(readOnly = true)
    public BindableDatasetDetailResponse detail(
            DatasetKind kind,
            UUID datasetId,
            UUID ownerId,
            UUID versionId
    ) {
        try {
            DatasetRef dataset = ownerId == null
                    ? catalog.resolveCurrent(kind, datasetId)
                    : catalog.requireCurrent(kind, datasetId, ownerId);
            if (versionId != null && !versionId.equals(dataset.versionId())) {
                throw new DatasetUnavailableException(kind, datasetId);
            }
            DatasetMetadata metadata = metadata(dataset);
            List<DatasetColumnResponse> columns = catalog
                    .listColumns(dataset)
                    .stream()
                    .map(DatasetColumnResponse::from)
                    .toList();
            return new BindableDatasetDetailResponse(
                    kind,
                    dataset.datasetId(),
                    metadata.name(),
                    dataset.versionId(),
                    metadata.versionNumber(),
                    dataset.rowCount(),
                    dataset.ownerId(),
                    metadata.publishedAt(),
                    columns
            );
        } catch (DatasetUnavailableException exception) {
            throw BindingErrors.datasetNotFound(kind, datasetId);
        }
    }

    private BindableDatasetResponse response(DatasetRef dataset) {
        DatasetMetadata metadata = metadata(dataset);
        return new BindableDatasetResponse(
                dataset.kind(),
                dataset.datasetId(),
                metadata.name(),
                dataset.versionId(),
                metadata.versionNumber(),
                dataset.rowCount()
        );
    }

    private DatasetMetadata metadata(DatasetRef dataset) {
        return jdbc.queryForObject("""
                SELECT d.name, v.version_number, v.published_at
                FROM data.dataset_catalog d
                JOIN data.dataset_version_catalog v
                  ON v.dataset_kind = d.dataset_kind
                 AND v.dataset_id = d.dataset_id
                WHERE d.dataset_kind = ?
                  AND d.dataset_id = ?
                  AND v.version_id = ?
                """, (row, rowNumber) -> {
                    var publishedAt = row.getTimestamp("published_at");
                    return new DatasetMetadata(
                            row.getString("name"),
                            row.getInt("version_number"),
                            publishedAt == null ? null : publishedAt.toInstant()
                    );
                },
                viewKind(dataset.kind()), dataset.datasetId(), dataset.versionId());
    }

    private String viewKind(DatasetKind kind) {
        return kind == DatasetKind.SYNC ? "sync" : "pipeline";
    }

    private record DatasetMetadata(
            String name,
            int versionNumber,
            java.time.Instant publishedAt
    ) {
    }
}
