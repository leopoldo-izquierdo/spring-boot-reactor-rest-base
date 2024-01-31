package com.base.app;

import com.base.app.documents.Category;
import com.base.app.documents.Product;
import com.base.app.services.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import reactor.core.publisher.Flux;

import java.util.Date;


@SpringBootApplication
public class Main implements CommandLineRunner {


    private static final Logger log = LoggerFactory.getLogger(Main.class);

    @Autowired
    private ProductService productService;

    @Autowired
    private ReactiveMongoTemplate mongoTemplate;


    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
        System.out.println("Hello world!");
    }

    @Override
    public void run(String... args) throws Exception {

        mongoTemplate.dropCollection("products").subscribe();
        mongoTemplate.dropCollection("categories").subscribe();

        Category electronics = new Category("electronics");
        Category sport = new Category("sport");
        Category computation = new Category("computation");
        Category furniture = new Category("furniture");

        Flux.just(electronics, sport, computation, furniture)
                .flatMap(productService::saveCategory)
                .doOnNext(c -> {
                    log.info("Category created: " + c.getName() + ", Id: " + c.getId());
                }).thenMany(
                        Flux.just(new Product("TV Panasonic Pantalla LCD", 456.89, electronics),
                                        new Product("Sony Camara HD Digital", 177.89, electronics),
                                        new Product("Apple iPod", 46.89, electronics),
                                        new Product("Sony Notebook", 846.89, computation),
                                        new Product("Hewlett Packard Multifuncional", 200.89, computation),
                                        new Product("Bianchi Bicicleta", 70.89, sport),
                                        new Product("HP Notebook Omen 17", 2500.89, computation),
                                        new Product("Mica Cómoda 5 Cajones", 150.89, furniture),
                                        new Product("TV Sony Bravia OLED 4K Ultra HD", 2255.89, electronics)
                                )
                                .flatMap(product -> {
                                    product.setCreateAt(new Date());
                                    return productService.save(product);
                                })
                )
                .subscribe(product -> log.info("Insert: " + product.getId() + " " + product.getName()));

    }
}
