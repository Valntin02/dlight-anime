package com.dlight.network;

import com.dlight.data.local.MyStarRecord;
import com.dlight.data.local.PlayRecord;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class ApiGsonFactory {

    private static final String LOCAL_STRUCTURED_DATA_FIELD = "vod_play_data";

    private ApiGsonFactory() {
    }

    public static Gson create() {
        ExclusionStrategy localRoomFieldStrategy = new ExclusionStrategy() {
            @Override
            public boolean shouldSkipField(FieldAttributes field) {
                Class<?> declaringClass = field.getDeclaringClass();
                return LOCAL_STRUCTURED_DATA_FIELD.equals(field.getName())
                    && (declaringClass == PlayRecord.class
                        || declaringClass == MyStarRecord.class);
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
