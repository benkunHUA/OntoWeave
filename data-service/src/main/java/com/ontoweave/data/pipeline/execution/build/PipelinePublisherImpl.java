package com.ontoweave.data.pipeline.execution.build;

import com.ontoweave.data.pipeline.execution.output.PipelineDatasetWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PipelinePublisherImpl implements PipelinePublisher {

    private static final Logger log = LoggerFactory.getLogger(PipelinePublisherImpl.class);

    private final PipelinePublicationTransaction transaction;
    private final PipelineDatasetWriter writer;

    public PipelinePublisherImpl(
            PipelinePublicationTransaction transaction,
            PipelineDatasetWriter writer
    ) {
        this.transaction = transaction;
        this.writer = writer;
    }

    @Override
    public void publish(UUID buildId) {
        PipelinePublicationTransaction.Publication publication = transaction.publish(buildId);
        for (PipelinePublicationTransaction.PublishedDataset dataset : publication.datasets()) {
            try {
                writer.fastForwardMain(dataset.datasetId(), dataset.snapshotId());
            } catch (RuntimeException error) {
                log.warn("failed to fast-forward iceberg main for dataset {} snapshot {}",
                        dataset.datasetId(), dataset.snapshotId(), error);
                continue;
            }
            try {
                writer.dropBranch(dataset.datasetId(), dataset.branch());
            } catch (RuntimeException error) {
                log.warn("failed to drop iceberg branch {} for dataset {}",
                        dataset.branch(), dataset.datasetId(), error);
            }
        }
    }
}
