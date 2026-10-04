package com.campus.event.api;

import java.io.IOException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

/** Capacity is a count: reject fractional, string and overflowing JSON input rather than coercing it. */
public class EventCapacityDeserializer extends JsonDeserializer<Integer> {
    @Override public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT))
            return (Integer) context.handleUnexpectedToken(Integer.class,parser);
        return parser.getIntValue();
    }
}
