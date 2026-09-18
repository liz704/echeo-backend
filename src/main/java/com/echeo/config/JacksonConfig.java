package com.echeo.config;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean
    public Module hibernateModule() {
        Hibernate6Module module = new Hibernate6Module();
        // Ne force PAS le chargement des associations LAZY (évite les N+1)
        module.disable(Hibernate6Module.Feature.FORCE_LAZY_LOADING);
        // Ignore les proxies non initialisés au lieu de planter
        module.configure(Hibernate6Module.Feature.SERIALIZE_IDENTIFIER_FOR_LAZY_NOT_LOADED_OBJECTS, true);
        return module;
    }
}