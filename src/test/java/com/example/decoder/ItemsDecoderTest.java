package com.example.decoder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import reactor.test.StepVerifier.Step;

public class ItemsDecoderTest {

    private static final int MIN_CHUNK_SIZE = 10;
    private static final int MAX_CHUNK_SIZE = 60;

    private static Random rnd = new Random(0x0123456789abcdefL);

    private final DataBufferFactory dataBufferFactory = new DefaultDataBufferFactory();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testBulk() {
        List<Item> items = IntStream.range(0, 1000)
                .mapToObj(i -> UUID.randomUUID().toString())
                .map(Item::new)
                .collect(Collectors.toList());

        String json = items.stream()
                .map(this::toJson)
                .collect(Collectors.joining(",", "{\"items\":[", "]}"));

        Flux<String> flux = Flux.generate(
                () -> 0,
                (state, sink) -> {
                    int begin = state;
                    state = state + MIN_CHUNK_SIZE + rnd.nextInt(MAX_CHUNK_SIZE - MIN_CHUNK_SIZE);
                    int end = Math.min(json.length(), state);
                    sink.next(json.substring(begin, end));
                    if (state > json.length()) {
                        sink.complete();
                    }
                    return state;
                });
        Flux<DataBuffer> dataBuffers = flux
                .map(this::toBytes)
                .map(this.dataBufferFactory::wrap);

        Flux<Item> results = ItemsDecoder.transform(dataBuffers, this.objectMapper);

        Step<Item> verifier = StepVerifier.create(results);
        for (Item item : items) {
            verifier = verifier.expectNext(item);
        }
        verifier.expectComplete()
                .verify();
    }

    private String toJson(Item item) {
        try {
            return this.objectMapper.writeValueAsString(item);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    private byte[] toBytes(String input) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (OutputStreamWriter w = new OutputStreamWriter(baos, "UTF-8")) {
            w.write(input);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return baos.toByteArray();
    }

}
