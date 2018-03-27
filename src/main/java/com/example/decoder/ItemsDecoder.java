package com.example.decoder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.async.ByteArrayFeeder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.util.TokenBuffer;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ItemsDecoder {

    public static Flux<Item> transform(Flux<DataBuffer> dataBuffers, ObjectMapper objectMapper) {
        ItemsDecoder decoder;
        try {
            decoder = new ItemsDecoder(objectMapper);
        } catch (IOException e) {
            return Flux.error(e);
        }

        return dataBuffers.flatMap(decoder::transform, Flux::error, decoder::endOfInput);
    }

    private final ObjectMapper objectMapper;
    private final ObjectReader itemsReader;
    private final ObjectReader errorReader;
    private final JsonParser parser;
    private final ByteArrayFeeder inputFeeder;

    private TokenBuffer tokenBuffer;
    private State state = State.START;
    private int depth;
    private ErrorDetail error;

    private ItemsDecoder(ObjectMapper objectMapper) throws IOException {
        this.objectMapper = objectMapper;
        this.itemsReader = this.objectMapper.readerFor(Item.class);
        this.errorReader = this.objectMapper.readerFor(ErrorDetail.class);

        JsonFactory jsonFactory = objectMapper.getFactory();
        this.parser = jsonFactory.createNonBlockingByteArrayParser();

        this.inputFeeder = (ByteArrayFeeder) this.parser.getNonBlockingInputFeeder();
        this.tokenBuffer = new TokenBuffer(this.parser);
    }

    private Flux<Item> transform(DataBuffer dataBuffer) {
        byte[] buffer = new byte[dataBuffer.readableByteCount()];
        dataBuffer.read(buffer);
        DataBufferUtils.release(dataBuffer);

        try {
            this.inputFeeder.feedInput(buffer, 0, buffer.length);
            return parseResponseData();
        } catch (JsonProcessingException e) {
            return Flux.error(new IllegalStateException(
                    "Could not parse message response: " + e.getOriginalMessage(), e));
        } catch (IOException e) {
            return Flux.error(e);
        }
    }

    private Flux<Item> endOfInput() {
        try {
            Flux<Item> items = parseResponseData();
            // items was prepared from List, so it's actually synchronous up to here
            if (this.state != State.FINISHED) {
                throw new IllegalStateException("incomplete JSON input");
            }
            return items;
        } catch (IOException e) {
            return Flux.error(e);
        }
    }

    private Flux<Item> parseResponseData() throws IOException {
        List<Item> result = new ArrayList<>();
        while (true) {
            JsonToken token = this.parser.nextToken();
            if (token == null || token == JsonToken.NOT_AVAILABLE) {
                break;
            }

            this.state.next(this, token, result);
        }

        Flux<Item> flux = Flux.fromIterable(result);
        if (this.error != null) {
            flux = flux.concatWith(Mono.error(new IllegalStateException(this.error.getMessage())));
        }
        return flux;
    }

    private void readEntity(List<Item> result) throws IOException {
        TokenBuffer buffer = this.tokenBuffer;
        this.tokenBuffer = new TokenBuffer(this.parser);

        if (this.state == State.ITEMS) {
            Item item = this.itemsReader.readValue(buffer.asParser(this.objectMapper));
            result.add(item);
        }
        else if (this.state == State.ERROR) {
            ErrorDetail errorDetail = this.errorReader
                    .readValue(buffer.asParser(this.objectMapper));
            this.error = errorDetail;
        }
        else {
            throw new IllegalStateException("unexpected parser state");
        }
    }

    private enum State {
        START {
            @Override
            void next(ItemsDecoder parent, JsonToken token, List<Item> result)
                    throws JsonParseException {
                if (token != JsonToken.START_OBJECT) {
                    throw new JsonParseException(parent.parser, "expected top-level object");
                }
                parent.state = TOPLEVEL;
            }
        },
        TOPLEVEL {
            @Override
            void next(ItemsDecoder parent, JsonToken token, List<Item> result)
                    throws JsonParseException, IOException {
                if (token == JsonToken.END_OBJECT) {
                    parent.state = FINISHED;
                }
                else if (token == JsonToken.FIELD_NAME) {
                    switch (parent.parser.getText()) {
                    case "items":
                        parent.state = ITEMS;
                        break;
                    case "error":
                        parent.state = ERROR;
                        break;
                    default:
                        throw new JsonParseException(parent.parser, "unknown field name");
                    }
                }
                else {
                    throw new JsonParseException(parent.parser, "expected field name");
                }
            }
        },
        ITEMS {
            @Override
            void next(ItemsDecoder parent, JsonToken token, List<Item> result) throws IOException {
                readObjects(parent, token, true, result);
            }

        },
        ERROR {
            @Override
            void next(ItemsDecoder parent, JsonToken token, List<Item> result) throws IOException {
                readObjects(parent, token, false, result);
            }
        },
        FINISHED {
            @Override
            void next(ItemsDecoder parent, JsonToken token, List<Item> result)
                    throws JsonParseException {
                throw new JsonParseException(parent.parser, "unexpected token");
            }
        };

        abstract void next(ItemsDecoder parent, JsonToken token, List<Item> result)
                throws IOException;

        private static void readObjects(ItemsDecoder parent, JsonToken token, boolean isArray,
                List<Item> result)
                throws IOException, JsonParseException {
            switch (token) {
            case START_OBJECT:
                parent.depth++;
                parent.tokenBuffer.copyCurrentEvent(parent.parser);
                break;
            case END_OBJECT:
                if (parent.depth == 0) {
                    if (isArray) {
                        parent.tokenBuffer.copyCurrentEvent(parent.parser);
                    }
                    else {
                        parent.state = TOPLEVEL;
                    }
                }
                else {
                    if (--parent.depth == 0) {
                        parent.tokenBuffer.copyCurrentEvent(parent.parser);
                        parent.readEntity(result);
                    }
                }
                break;
            case START_ARRAY:
                if (parent.depth == 0) {
                    if (!isArray) {
                        throw new JsonParseException(parent.parser, "unexpected start of array");
                    }
                }
                else {
                    parent.tokenBuffer.copyCurrentEvent(parent.parser);
                }
                break;
            case END_ARRAY:
                if (parent.depth == 0) {
                    if (isArray) {
                        parent.state = TOPLEVEL;
                    }
                    else {
                        throw new JsonParseException(parent.parser, "unexpected end of array");
                    }
                }
                else {
                    parent.tokenBuffer.copyCurrentEvent(parent.parser);
                }
                break;
            default:
                if (parent.depth == 0) {
                    throw new JsonParseException(parent.parser, "unexpected parser state");
                }
                parent.tokenBuffer.copyCurrentEvent(parent.parser);
                break;
            }
        }
    }

}
