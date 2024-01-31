package com.base.app.handlers;


import com.base.app.documents.Category;
import com.base.app.documents.Product;
import com.base.app.services.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.net.URI;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.web.reactive.function.BodyInserters.fromValue;

@Component
public class ProductHandler {

    @Autowired
    private ProductService service;

    @Value("${config.uploads.path}")
    private String path;

    @Autowired
    private Validator validator;


    public Mono<ServerResponse> getAllPProducts(ServerRequest request) {
        return ServerResponse.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body(service.findAll(), Product.class);
    }

    public Mono<ServerResponse> getProductById(ServerRequest request) {
        String id = request.pathVariable("id");
        return service.findById(id).flatMap(p -> ServerResponse
        .ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body(fromValue(p)))
        .switchIfEmpty(ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> create(ServerRequest request) {
        Mono<Product> product = request.bodyToMono(Product.class);
        return product.flatMap(p -> {
            Errors errors = new BeanPropertyBindingResult(p, Product.class.getName());
            validator.validate(p, errors);
            if (errors.hasErrors()) {
                return Flux.fromIterable(errors.getFieldErrors())
                .map(fieldError -> "El campo " + fieldError.getField() + " " + fieldError.getDefaultMessage())
                .collectList()
                .flatMap(list -> ServerResponse.badRequest().body(fromValue(list)));
            } else {
                if (p.getCreateAt() == null) {
                    p.setCreateAt(new Date());
                }
                return service.save(p).flatMap(pdb -> ServerResponse
                .created(URI.create("/api/v2/productos/".concat(pdb.getId())))
                .contentType(MediaType.APPLICATION_JSON)
                .body(fromValue(pdb)));
            }
        });
    }


    public Mono<ServerResponse> edit(ServerRequest request) {
        Mono<Product> product = request.bodyToMono(Product.class);
        String id = request.pathVariable("id");
        Mono<Product> productDb = service.findById(id);
        return productDb.zipWith(product, (db, req) -> {
            db.setName(req.getName());
            db.setPrice(req.getPrice());
            db.setCategory(req.getCategory());
            return db;
        }).flatMap(p -> ServerResponse.created(URI.create("/api/v2/products/".concat(p.getId())))
        .contentType(MediaType.APPLICATION_JSON)
        .body(service.save(p), Product.class))
        .switchIfEmpty(ServerResponse.notFound().build());

    }

    public Mono<ServerResponse> delete(ServerRequest request) {
        String id = request.pathVariable("id");
        Mono<Product> productDb = service.findById(id);
        return productDb.flatMap(p -> service.delete(p).then(ServerResponse.noContent().build()))
        .switchIfEmpty(ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> createWithPicture(ServerRequest request) {
        Mono<Product> product = request.multipartData().map(multipart -> {
            FormFieldPart name = (FormFieldPart) multipart.toSingleValueMap().get("name");
            FormFieldPart price = (FormFieldPart) multipart.toSingleValueMap().get("price");
            FormFieldPart categoryId = (FormFieldPart) multipart.toSingleValueMap().get("category.id");
            FormFieldPart categoryName = (FormFieldPart) multipart.toSingleValueMap().get("category.name");
            Category category = new Category(categoryName.value());
            category.setId(categoryId.value());
            return new Product(name.value(), Double.parseDouble(price.value()), category);
        });

        return request.multipartData().map(multipart -> multipart.toSingleValueMap().get("file"))
        .cast(FilePart.class)
        .flatMap(file -> product
        .flatMap(p -> {
            p.setPicture(UUID.randomUUID().toString() + "-" + file.filename()
            .replace(" ", "-")
            .replace(":", "")
            .replace("\\", ""));
            p.setCreateAt(new Date());
            return file.transferTo(new File(path + p.getPicture())).then(service.save(p));
        })).flatMap(p -> ServerResponse.created(URI.create("/api/v2/products/".concat(p.getId())))
        .contentType(MediaType.APPLICATION_JSON)
        .body(fromValue(p)));
    }

    public Mono<ServerResponse> upload(ServerRequest request) {
        String id = request.pathVariable("id");
        return request.multipartData().map(multipart -> multipart.toSingleValueMap().get("file"))
        .cast(FilePart.class)
        .flatMap(file -> service.findById(id)
        .flatMap(p -> {
            p.setPicture(UUID.randomUUID().toString() + "-" + file.filename()
            .replace(" ", "-")
            .replace(":", "")
            .replace("\\", ""));
            return file.transferTo(new File(path + p.getPicture())).then(service.save(p));
        })).flatMap(p -> ServerResponse.created(URI.create("/api/v2/products/".concat(p.getId())))
        .contentType(MediaType.APPLICATION_JSON)
        .body(fromValue(p)))
        .switchIfEmpty(ServerResponse.notFound().build());
    }


    public Mono<ServerResponse> createv2(ServerRequest request) {
        Map<String, Object> response = new HashMap<String, Object>();
        Mono<Product> product = request.bodyToMono(Product.class);
        return product.flatMap(p -> {
            Errors errors = new BeanPropertyBindingResult(p, Product.class.getName());
            validator.validate(p, errors);
            if (errors.hasErrors()) {
                return Flux.fromIterable(errors.getFieldErrors())
                .map(fieldError -> "El campo " + fieldError.getField() + " " + fieldError.getDefaultMessage())
                .collectList()
                .flatMap(list -> {
                    response.put("errors", list);
                    response.put("timestamp", new Date());
                    response.put("status", HttpStatus.BAD_REQUEST.value());
                    return ServerResponse.badRequest().body(fromValue(response));
                });
            } else {
                if (p.getCreateAt() == null) {
                    p.setCreateAt(new Date());
                }
                return service.save(p).flatMap(savedProd -> {
                    response.put("product", savedProd);
                    response.put("message", "Product created successfully.");
                    response.put("timestamp", new Date());
                    response.put("status", HttpStatus.CREATED.value());
                    return ServerResponse
                    .created(URI.create("/api/v2/products/".concat(savedProd.getId())))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(fromValue(response));
                });
            }
        });
    }

    public String getMultipartStringValue(MultiValueMap<String, Part> multipart, String key) {
        try {
            FormFieldPart part = (FormFieldPart) multipart.toSingleValueMap().get(key);
            if (part != null) {
                return part.value();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
    public Double getMultipartDoubleValue(MultiValueMap<String, Part> multipart, String key) {
        try {
            FormFieldPart part = (FormFieldPart) multipart.toSingleValueMap().get(key);
            if (part != null) {
                return Double.parseDouble(part.value());
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public Mono<ServerResponse> createWithPicturev2(ServerRequest request) {
        Map<String, Object> response = new HashMap<String, Object>();
        Mono<Product> product = request.multipartData().map(multipart -> {
            String name = getMultipartStringValue(multipart, "name");
            Double price = getMultipartDoubleValue(multipart, "price");
            String categoryId = getMultipartStringValue(multipart, "category.id");
            String categoryName = getMultipartStringValue(multipart, "category.name");
            Category category = new Category(categoryName);
            category.setId(categoryId);
            return new Product(name, price, category);
        });
        return product.flatMap(p -> {
            Errors errors = new BeanPropertyBindingResult(p, Product.class.getName());
            validator.validate(p, errors);
            if (errors.hasErrors()) {
                return Flux.fromIterable(errors.getFieldErrors())
                .map(fieldError -> "El campo " + fieldError.getField() + " " + fieldError.getDefaultMessage())
                .collectList()
                .flatMap(list -> {
                    response.put("errors", list);
                    response.put("timestamp", new Date());
                    response.put("status", HttpStatus.BAD_REQUEST.value());
                    return ServerResponse.badRequest().body(fromValue(response));
                });
            } else {
                return request.multipartData().map(multipart -> multipart.toSingleValueMap().get("file"))
                .cast(FilePart.class)
                .flatMap(file -> {
                    p.setPicture(UUID.randomUUID().toString() + "-" + file.filename()
                    .replace(" ", "-")
                    .replace(":", "")
                    .replace("\\", ""));
                    p.setCreateAt(new Date());
                    return file.transferTo(new File(path + p.getPicture()));
                }).then(service.save(p))
                .flatMap(savedProd -> {
                    response.put("product", savedProd);
                    response.put("message", "Product created successfully.");
                    response.put("timestamp", new Date());
                    response.put("status", HttpStatus.CREATED.value());
                    return ServerResponse
                    .created(URI.create("/api/v2/products/".concat(savedProd.getId())))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(fromValue(response));
                });
            }
        });


    }

}
