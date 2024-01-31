package com.base.app;


import com.base.app.handlers.ProductHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class RouterFunctionConfig {

    @Bean
    public RouterFunction<ServerResponse> routes(ProductHandler handler) {
        return route(GET("/api/v2/products").or(GET("/api/v3/products")), handler::getAllPProducts)
        .andRoute(GET("/api/v2/products/{id}"), handler::getProductById)
        .andRoute(POST("/api/v2/products"), handler::create)
        .andRoute(PUT("/api/v2/products/{id}"), handler::edit)
        .andRoute(DELETE("/api/v2/products/{id}"), handler::delete)
        .andRoute(POST("/api/v2/products/upload/{id}"), handler::upload)
        .andRoute(POST("/api/v2/products/create"), handler::createWithPicture)
        .andRoute(POST("/api/v2/products/v2"), handler::createv2)
        .andRoute(POST("/api/v2/products/create/v2"), handler::createWithPicturev2);
    }

}
