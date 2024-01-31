package com.base.app.controllers;

import com.base.app.documents.Product;
import com.base.app.services.ProductService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.net.URI;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);

    @Autowired
    private ProductService productService;

    @Value("${config.uploads.path}")
    private String uploadFilesPath;

    @GetMapping()
    public Mono<ResponseEntity<Flux<Product>>> getAllProducts() {
        Flux<Product> products = productService.findAll()
        .map(p -> {
            p.setName(p.getName().toUpperCase());
            return p;
        })
        .doOnNext(p -> log.info(p.getName()));
        return Mono.just(
        ResponseEntity
        .ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body(products)
        );
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Mono<Product>>> getProductById(@PathVariable String id) {
        Mono<Product> product = productService.findById(id);
        return product.map(p -> ResponseEntity
        .ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body(Mono.just(p))
        ).defaultIfEmpty(ResponseEntity.notFound().build());
    }


    @PostMapping()
    public Mono<ResponseEntity<Map<String, Object>>> createProduct(@Valid @RequestBody Mono<Product> monoProduct) {
        Map<String, Object> response = new HashMap<String, Object>();
        return monoProduct.flatMap(product -> {
            if (product.getCreateAt() == null) {
                product.setCreateAt(new Date());
            }
            return productService.save(product).map(p -> {
                response.put("product", p);
                response.put("message", "Product created successfully.");
                response.put("timestamp", new Date());
                response.put("status", HttpStatus.CREATED.value());
                return ResponseEntity
                .created(URI.create("/api/products/".concat(p.getId())))
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
            });
        }).onErrorResume(t -> {
            return Mono.just(t).cast(WebExchangeBindException.class)
            .flatMap(e -> Mono.just(e.getFieldErrors()))
            .flatMapMany(errors -> Flux.fromIterable(errors))
            .map(fieldError -> "The field " + fieldError.getField() + " " + fieldError.getDefaultMessage())
            .collectList()
            .flatMap(list -> {
                response.put("errors", list);
                response.put("timestamp", new Date());
                response.put("status", HttpStatus.BAD_REQUEST.value());
                return Mono.just(ResponseEntity.badRequest().body(response));
            });
        });
    }

    @PostMapping("v2")
    public Mono<ResponseEntity<Mono<Product>>> createProductWithPicture(Product product, @RequestPart FilePart file) {
        if (product.getCreateAt() == null) {
            product.setCreateAt(new Date());
        }
        product.setPicture(UUID.randomUUID() + "-" + file.filename()
        .replace(" ", "")
        .replace(":", "")
        .replace("\\", ""));

        log.info("path to save files: ".concat(uploadFilesPath + product.getPicture()));

        return file.transferTo(new File(uploadFilesPath + product.getPicture()))
        .then(productService.save(product))
        .map(p -> ResponseEntity
        .created(URI.create("/api/products/".concat(p.getId())))
        .contentType(MediaType.APPLICATION_JSON)
        .body(Mono.just(p))
        );
    }


    @PostMapping("/upload/{id}")
    public Mono<ResponseEntity<Mono<Product>>> uploadFile(@PathVariable String id, @RequestPart FilePart file) {
        return productService.findById(id).flatMap(p -> {
            p.setPicture(UUID.randomUUID() + "-" + file.filename()
            .replace(" ", "")
            .replace(":", "")
            .replace("\\", ""));
            log.info("path to save files: ".concat(uploadFilesPath + p.getPicture()));
            return file.transferTo(new File(uploadFilesPath + p.getPicture()))
            .then(productService.save(p));
        }).map(p -> ResponseEntity.ok(Mono.just(p)))
        .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<Mono<Product>>> editProduct(@RequestBody Product product, @PathVariable String id) {
        return productService.findById(id).flatMap(p -> {
            p.setName(product.getName());
            p.setPrice(product.getPrice());
            p.setCategory(product.getCategory());
            return productService.save(p);
        }).map(p -> ResponseEntity
        .created(URI.create("/api/products/".concat(p.getId())))
        .contentType(MediaType.APPLICATION_JSON)
        .body(Mono.just(p))
        ).defaultIfEmpty(ResponseEntity.notFound().build());
    }


    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteProduct(@PathVariable String id) {
        return productService.findById(id).flatMap(p -> {
            return productService.delete(p).then(Mono.just(new ResponseEntity<Void>(HttpStatus.NO_CONTENT)));
        }).defaultIfEmpty(new ResponseEntity<Void>(HttpStatus.NOT_FOUND));
    }
}
