package com.example.decoder.controller;

import java.io.IOException;
import java.io.Writer;
import java.util.concurrent.atomic.AtomicLong;

import com.example.decoder.ErrorDetail;
import com.example.decoder.Item;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonGenerator.Feature;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ItemsResponseEncoder {

    private final JsonFactory jsonFactory;
    private final AtomicLong count = new AtomicLong();
    private volatile String error;
    private volatile boolean finished;

    public ItemsResponseEncoder(ObjectMapper objectMapper) {
        JsonFactory jsonFactory = new JsonFactory(objectMapper);
        jsonFactory.disable(Feature.AUTO_CLOSE_TARGET);
        this.jsonFactory = jsonFactory;
    }

    public boolean isStarted() {
        return this.finished || this.error != null || this.count.get() != 0L;
    }

    public boolean isFinished() {
        return this.finished;
    }

    public void setError(String message) {
        this.error = message;
    }

    public void addItem(Writer w, Item item) throws IOException {
        if (this.count.getAndIncrement() == 0L) {
            w.append("{\"items\":[");
        }
        else {
            w.append(',');
        }

        try (JsonGenerator gen = this.jsonFactory.createGenerator(w)) {
            gen.writeObject(item);
        }
    }

    public void finish(Writer w) throws IOException {
        this.finished = true;

        long resultSize = this.count.get();
        if (resultSize == 0L) {
            w.append('{');
        }
        else {
            w.append("]");
        }

        String message = this.error;
        if (message != null) {
            ErrorDetail errorDetail = new ErrorDetail(message);
            if (resultSize != 0L) {
                w.append(",");
            }
            w.append("\"error\":");
            try (JsonGenerator gen = this.jsonFactory.createGenerator(w)) {
                gen.writeObject(errorDetail);
            }
        }

        w.append('}');
    }

}
