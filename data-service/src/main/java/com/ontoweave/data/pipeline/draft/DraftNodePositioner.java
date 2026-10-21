package com.ontoweave.data.pipeline.draft;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import static com.ontoweave.data.pipeline.draft.DraftCommandValidation.requirePosition;
import static com.ontoweave.data.pipeline.draft.DraftConstants.DEFAULT_NODE_POSITION_X;
import static com.ontoweave.data.pipeline.draft.DraftConstants.DEFAULT_NODE_POSITION_Y;
import static com.ontoweave.data.pipeline.draft.DraftConstants.FIELD_POSITION;
import static com.ontoweave.data.pipeline.draft.DraftConstants.FIELD_X;
import static com.ontoweave.data.pipeline.draft.DraftConstants.FIELD_Y;
import static com.ontoweave.data.pipeline.draft.DraftConstants.NODE_HORIZONTAL_SPACING;

final class DraftNodePositioner {

    private final ObjectMapper mapper;

    DraftNodePositioner(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    JsonNode resolve(ArrayNode nodes, JsonNode requested) {
        if (requested != null) {
            return requirePosition(requested);
        }
        double rightmostX = Double.NEGATIVE_INFINITY;
        double rightmostY = DEFAULT_NODE_POSITION_Y;
        for (JsonNode node : nodes) {
            JsonNode position = node.path(FIELD_POSITION);
            if (position.path(FIELD_X).isNumber()
                    && position.path(FIELD_Y).isNumber()
                    && position.path(FIELD_X).asDouble() > rightmostX) {
                rightmostX = position.path(FIELD_X).asDouble();
                rightmostY = position.path(FIELD_Y).asDouble();
            }
        }
        if (!Double.isFinite(rightmostX)) {
            return position(DEFAULT_NODE_POSITION_X, DEFAULT_NODE_POSITION_Y);
        }
        return position(rightmostX + NODE_HORIZONTAL_SPACING, rightmostY);
    }

    private ObjectNode position(double x, double y) {
        ObjectNode position = mapper.createObjectNode();
        putCoordinate(position, FIELD_X, x);
        putCoordinate(position, FIELD_Y, y);
        return position;
    }

    private void putCoordinate(ObjectNode position, String field, double value) {
        if (value == Math.rint(value) && value >= Long.MIN_VALUE && value <= Long.MAX_VALUE) {
            position.put(field, (long) value);
        } else {
            position.put(field, value);
        }
    }
}
