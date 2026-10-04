package com.campus.event.api;

import java.io.IOException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

/** Optimistic versions must be integer JSON tokens; coercion changes the caller's precondition. */
public class EventVersionDeserializer extends JsonDeserializer<Long> {
    @Override public Long deserialize(JsonParser parser,DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) return (Long)context.handleUnexpectedToken(Long.class,parser);
        return parser.getLongValue();
    }
}
