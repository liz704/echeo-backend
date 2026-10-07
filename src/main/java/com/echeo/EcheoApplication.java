package com.echeo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class EcheoApplication {

	public static void main(String[] args) {
		// Fuseau métier Cameroun : évite les décalages LocalDate.now() / cron
		// quand le conteneur Render tourne en UTC.
		TimeZone.setDefault(TimeZone.getTimeZone("Africa/Douala"));
		SpringApplication.run(EcheoApplication.class, args);
	}

}
