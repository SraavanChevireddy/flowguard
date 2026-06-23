package com.sraavan.flowguard.policy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Serializes {@link Policy} to/from the JSON stored in the database and exposed on the API.
 * {@code LocalTime} freeze-window bounds round-trip via Jackson's JSR-310 support.
 */
public class PolicyCodec {

    private final ObjectMapper mapper;

    public PolicyCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String toJson(Policy policy) {
        try {
            return mapper.writeValueAsString(policy);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not serialize policy: " + e.getMessage());
        }
    }

    public Policy fromJson(String json) {
        try {
            return mapper.readValue(json, Policy.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid policy JSON: " + e.getMessage());
        }
    }
}
