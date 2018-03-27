package com.example.decoder.controller;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.config.WebFluxConfigurer;

import com.example.decoder.ItemsEntity;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
@EnableWebFlux
@ComponentScan
public class ItemsConfiguration implements WebFluxConfigurer {

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder
            .json()
            .serializationInclusion(Include.NON_EMPTY)
            .build();

    @Bean
    public ItemsEntity testValueHolder() {
        return new ItemsEntity();
    }

    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        configurer.defaultCodecs().jackson2JsonDecoder(
                new Jackson2JsonDecoder(this.objectMapper));
        configurer.defaultCodecs().jackson2JsonEncoder(
                new Jackson2JsonEncoder(this.objectMapper));
    }

}
