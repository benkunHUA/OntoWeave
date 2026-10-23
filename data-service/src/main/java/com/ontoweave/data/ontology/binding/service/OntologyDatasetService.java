package com.ontoweave.data.ontology.binding.service;

import com.ontoweave.data.ontology.binding.api.dto.BindableDatasetPageResponse;
import com.ontoweave.data.ontology.binding.api.dto.BindableDatasetResponse;
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

    private BindableDatasetResponse response(DatasetRef dataset) {
        return new BindableDatasetResponse(
                dataset.kind(),
                dataset.datasetId(),
                name(dataset),
                dataset.versionId(),
                dataset.rowCount()
        );
    }

    private String name(DatasetRef dataset) {
        String table = dataset.kind() == DatasetKind.SYNC
                ? "data.dataset"
                : "data.pipeline_dataset";
        List<String> names = jdbc.queryForList(
                "SELECT name FROM " + table + " WHERE id = ?",
                String.class,
                dataset.datasetId()
        );
        return names.isEmpty() ? dataset.table() : names.get(0);
    }
}
