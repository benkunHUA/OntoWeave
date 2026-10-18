package com.ontoweave.data.sync.iceberg;

import java.util.List;
import java.util.Map;

public record IcebergPreview(List<String> columns, List<Map<String, Object>> rows, boolean truncated) {
}
