package com.ontoweave.data.ontology.edit.api;

import com.ontoweave.data.ontology.edit.model.EditSetPreview;
import com.ontoweave.data.ontology.edit.model.EditSetRequest;
import com.ontoweave.data.ontology.edit.model.EditSetResult;
import com.ontoweave.data.ontology.edit.service.EditSetApplyService;
import com.ontoweave.data.ontology.edit.service.EditSetPreviewService;
import com.ontoweave.data.ontology.query.common.QueryErrors;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/ontology/edit-sets")
public class OntologyEditController {

    private final EditSetPreviewService previewService;
    private final EditSetApplyService applyService;

    public OntologyEditController(
            EditSetPreviewService previewService,
            EditSetApplyService applyService
    ) {
        this.previewService = previewService;
        this.applyService = applyService;
    }

    @PostMapping("/preview")
    public EditSetPreview preview(@RequestBody EditSetRequest request) {
        if (request == null) {
            throw QueryErrors.invalidRequest("请求体不能为空");
        }
        return previewService.preview(request);
    }

    @PostMapping("/apply")
    public EditSetResult apply(@RequestBody EditSetRequest request) {
        return applyService.apply(request);
    }
}
