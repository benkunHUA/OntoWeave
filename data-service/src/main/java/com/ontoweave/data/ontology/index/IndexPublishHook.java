package com.ontoweave.data.ontology.index;

import com.ontoweave.data.ontology.common.DatasetKind;
import com.ontoweave.data.ontology.store.IndexEnqueuer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

public final class IndexPublishHook {

    private static final Logger log = LoggerFactory.getLogger(IndexPublishHook.class);

    private IndexPublishHook() {
    }

    public static void enqueueAfterCommit(
            IndexEnqueuer enqueuer,
            DatasetKind kind,
            UUID datasetId,
            UUID versionId
    ) {
        if (enqueuer == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            enqueue(enqueuer, kind, datasetId, versionId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                enqueue(enqueuer, kind, datasetId, versionId);
            }
        });
    }

    private static void enqueue(
            IndexEnqueuer enqueuer,
            DatasetKind kind,
            UUID datasetId,
            UUID versionId
    ) {
        try {
            enqueuer.enqueueOnPublish(kind, datasetId, versionId);
        } catch (RuntimeException exception) {
            log.warn(
                    "failed to enqueue ontology index for {} dataset {} version {}",
                    kind,
                    datasetId,
                    versionId,
                    exception
            );
        }
    }
}
