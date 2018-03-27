package com.example.decoder.controller;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.example.decoder.Item;
import com.example.decoder.ItemsEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
public class ItemsController {

    @Autowired
    private ItemsEntity testValueHolder;

    @Autowired
    private ObjectMapper objectMapper;

    @GetMapping(value = "/items/{count}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Void> getItems(@PathVariable Integer count, ServerHttpResponse response) {
        List<Item> items = IntStream.range(0, count)
                .mapToObj(i -> UUID.randomUUID().toString())
                .map(Item::new)
                .collect(Collectors.toList());
        this.testValueHolder.setItems(items);
        return toResponse(response, Flux.fromIterable(items));
    }

    private Mono<Void> toResponse(ServerHttpResponse response, Flux<Item> items) {
        ItemsResponseEncoder builder = new ItemsResponseEncoder(
                this.objectMapper);

        Flux<DataBuffer> body = items //
                .map(item -> writeJsonToBuffer(response.bufferFactory(),
                        w -> builder.addItem(w, item)))
                .concatWith(Mono.fromSupplier(() -> writeJsonToBuffer(
                        response.bufferFactory(), builder::finish)))
                .onErrorResume(t -> {
                    if (!builder.isStarted()) {
                        // not yet started, so response status isn't set yet
                        return Mono.error(t);
                    }
                    if (builder.isFinished()) {
                        // too late to write error to response
                        return Mono.error(t);
                    }
                    builder.setError(t.getMessage());
                    return Mono
                            .just(writeJsonToBuffer(response.bufferFactory(), builder::finish))
                            .concatWith(Mono.error(t));
                });

        return response.writeWith(body);
    }

    private DataBuffer writeJsonToBuffer(DataBufferFactory bufferFactory,
            JsonBuilder jsonBuilder) {
        DataBuffer buffer = bufferFactory.allocateBuffer();
        OutputStream stream = buffer.asOutputStream();

        try (OutputStreamWriter w = new OutputStreamWriter(stream, "UTF-8")) {
            jsonBuilder.accept(w);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Could not generate JSON: " + e.getOriginalMessage(), e);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Unexpected error while writing to memory", e);
        }
        return buffer;
    }

    private interface JsonBuilder {
        void accept(Writer w) throws IOException;
    }

}
