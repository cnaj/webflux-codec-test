package com.example.decoder;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilderFactory;

import com.example.decoder.controller.ItemsConfiguration;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Flux;
import reactor.ipc.netty.NettyContext;
import reactor.ipc.netty.http.server.HttpServer;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { ItemsConfiguration.class })
public class ItemsDecoderHttpTest {

    private static final int ITEM_COUNT = Integer
            .parseInt(System.getProperty("itemCount", "100000"));

    @Autowired
    private ApplicationContext context;

    @Autowired
    private ItemsEntity testValueHolder;

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder
            .json()
            .serializationInclusion(Include.NON_EMPTY)
            .build();
    private NettyContext server;
    private WebClient webClient;

    @Before
    public void before() {
        HttpHandler handler = WebHttpHandlerBuilder
                .applicationContext(this.context).build();
        this.server = HttpServer.create(0)
                .newHandler(new ReactorHttpHandlerAdapter(handler))
                .block();

        UriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory(
                "http://localhost:" + this.server.address().getPort());

        this.webClient = WebClient.builder()
                .uriBuilderFactory(uriBuilderFactory)
                .build();
    }

    @After
    public void after() {
        this.server.dispose();
    }

    @Test
    public void testBulkHttp() {
        Flux<Item> response = this.webClient.get() //
                .uri("/items/" + ITEM_COUNT)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .flatMapMany(this::handleItemsResponse);
        List<Item> result = response.collectList().block();
        assertEquals(this.testValueHolder.getItems(), result);
    }

    private Flux<Item> handleItemsResponse(ClientResponse response) {
        return response
                .body((inputMessage, context) -> ItemsDecoder.transform(inputMessage.getBody(),
                        this.objectMapper));
    }

}
