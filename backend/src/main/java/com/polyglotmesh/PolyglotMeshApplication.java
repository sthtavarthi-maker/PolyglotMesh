package com.polyglotmesh;

import com.polyglotmesh.config.GraalVmProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(GraalVmProperties.class)
public class PolyglotMeshApplication {

    public static void main(String[] args) {
        SpringApplication.run(PolyglotMeshApplication.class, args);
    }
}
