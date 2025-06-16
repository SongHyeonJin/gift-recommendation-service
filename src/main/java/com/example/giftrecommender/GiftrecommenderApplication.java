package com.example.giftrecommender;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class GiftrecommenderApplication {

	public static void main(String[] args) {
		SpringApplication.run(GiftrecommenderApplication.class, args);
	}

}
