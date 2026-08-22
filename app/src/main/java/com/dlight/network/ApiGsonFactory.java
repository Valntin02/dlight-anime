package com.dlight.network;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class ApiGsonFactory {

    private ApiGsonFactory() {
    }

    public static Gson create() {
        ExclusionStrategy localRoomFieldStrategy = new ExclusionStrategy() {
            @Override
            public boolean shouldSkipField(FieldAttributes field) {
                return field.getAnnotation(LocalOnly.class) != null;
            }

            @Override
            public boolean shouldSkipClass(Class<?> type) {
                return false;
            }
        };
        return new GsonBuilder()
            .addSerializationExclusionStrategy(localRoomFieldStrategy)
            .addDeserializationExclusionStrategy(localRoomFieldStrategy)
            .create();
    }
}
