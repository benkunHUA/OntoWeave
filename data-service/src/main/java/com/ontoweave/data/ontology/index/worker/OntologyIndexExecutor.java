package com.ontoweave.data.ontology.index.worker;

import com.ontoweave.data.ontology.common.SubjectKind;
import com.ontoweave.data.ontology.domain.OntologyIndexRunEntity;
import com.ontoweave.data.ontology.index.IndexBuildException;
import com.ontoweave.data.ontology.index.IndexCancelledException;
import com.ontoweave.data.ontology.index.IndexFailureCode;
import com.ontoweave.data.ontology.index.pipeline.ObjectBuildResult;
import com.ontoweave.data.ontology.index.pipeline.ObjectIndexPipeline;
import com.ontoweave.data.ontology.index.pipeline.RelationRunPipeline;
import com.ontoweave.data.ontology.index.pipeline.RelationRunResult;
import com.ontoweave.data.ontology.index.run.OntologyRunStateStore;
import com.ontoweave.data.ontology.index.switching.IndexSwitcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OntologyIndexExecutor {

    private static final Logger log = LoggerFactory.getLogger(OntologyIndexExecutor.class);

    private final ObjectIndexPipeline objectPipeline;
    private final RelationRunPipeline relationPipeline;
    private final IndexSwitcher switcher;
    private final OntologyRunStateStore stateStore;

    public OntologyIndexExecutor(
            ObjectIndexPipeline objectPipeline,
            RelationRunPipeline relationPipeline,
            IndexSwitcher switcher,
            OntologyRunStateStore stateStore
    ) {
        this.objectPipeline = objectPipeline;
        this.relationPipeline = relationPipeline;
        this.switcher = switcher;
        this.stateStore = stateStore;
    }

    public void execute(OntologyIndexRunEntity run, String worker) {
        try {
            if (run.getSubjectKind() == SubjectKind.OBJECT_TYPE) {
                ObjectBuildResult result = objectPipeline.execute(run, worker);
                switcher.switchObject(run, worker, result);
            } else {
                RelationRunResult result = relationPipeline.execute(
                        run,
                        () -> checkpoint(run, worker)
                );
                if (result.relation() == null) {
                    switcher.completeWithoutVersion(run, worker, result.stats());
                } else {
                    switcher.switchRelation(run, worker, result);
                }
            }
        } catch (IndexCancelledException exception) {
            stateStore.cancelExhausted(run, worker, Map.of("cancelled", true));
        } catch (IndexBuildException exception) {
            stateStore.fail(
                    run,
                    worker,
                    exception.errorCode(),
                    exception.getMessage(),
                    exception.details(),
                    Map.of()
            );
        } catch (RuntimeException exception) {
            log.error("ontology index run failed: {}", run.getId(), exception);
            stateStore.fail(
                    run,
                    worker,
                    IndexFailureCode.INDEX_FAILURE,
                    exception.getMessage() == null ? "index run failed" : exception.getMessage(),
                    Map.of(),
                    Map.of()
            );
        }
    }

    private void checkpoint(OntologyIndexRunEntity run, String worker) {
        if (stateStore.cancelRequested(run.getId())) {
            throw new IndexCancelledException();
        }
        if (!stateStore.renewLease(run.getId(), worker)) {
            throw new IndexBuildException(
                    IndexFailureCode.LEASE_LOST,
                    "index run lease is no longer owned by this worker"
            );
        }
    }
}
