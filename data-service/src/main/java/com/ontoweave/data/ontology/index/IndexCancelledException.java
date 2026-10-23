package com.ontoweave.data.ontology.index;

public class IndexCancelledException extends RuntimeException {

    public IndexCancelledException() {
        super("index run cancellation requested");
    }
}
