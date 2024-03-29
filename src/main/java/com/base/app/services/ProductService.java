package com.base.app.services;

import com.base.app.documents.Category;
import com.base.app.documents.Product;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ProductService {

    public Flux<Product> findAll();

    public Flux<Product> findAllWithNameUpperCase();

    public Flux<Product> findAllWithNameUpperCaseRepeat();

    public Mono<Product> findById(String id);

    public Mono<Product> save(Product product);

    public Mono<Void> delete(Product product);

    public Flux<Category> findAllCategories();

    public Mono<Category> findCategoryById(String id);

    public Mono<Category> saveCategory(Category category);

}
