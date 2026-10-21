package com.ontoweave.data.pipeline.execution.build;

import com.ontoweave.data.pipeline.execution.output.OutputSnapshot;

import java.util.List;

public interface ExpectationChecker {

    List<ExpectationResult> check(ExpectationSet expectations, OutputSnapshot snapshot);
}
