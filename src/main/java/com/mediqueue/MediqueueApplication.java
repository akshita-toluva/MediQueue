package com.mediqueue;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class  MediqueueApplication {

	public static void main(String[] args) {

		SpringApplication.run(MediqueueApplication.class, args);
		System.out.println("==================================");
		System.out.println(" MediQueue running on port 8080 ");
		System.out.println("==================================");
	}

}
