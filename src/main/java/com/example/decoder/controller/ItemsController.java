package com.example.decoder.controller;

import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.example.decoder.Item;
import com.example.decoder.ItemsEntity;

import reactor.core.publisher.Mono;

@RestController
public class ItemsController {

    @Autowired
    private ItemsEntity testValueHolder;

    @GetMapping(value = "/items/{count}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ItemsEntity> getItems(@PathVariable Integer count) {
        this.testValueHolder.setItems(IntStream.range(0, count)
                .mapToObj(i -> UUID.randomUUID().toString())
                .map(Item::new)
                .collect(Collectors.toList()));
        return Mono.just(this.testValueHolder);
    }

}
