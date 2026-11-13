package com.ncpl.sales.cashflow.controller;

import com.ncpl.sales.cashflow.config.StandaloneAdministratorBootstrap;
import java.util.Collections;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "cashflow.standalone", havingValue = "true")
public class StandaloneHealthController {
    private final StandaloneAdministratorBootstrap bootstrap;
    public StandaloneHealthController(StandaloneAdministratorBootstrap bootstrap) { this.bootstrap = bootstrap; }
    @GetMapping("/cashflow-health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.status(bootstrap.isReady() ? 200 : 503)
            .body(Collections.singletonMap("status", bootstrap.isReady() ? "ready" : "starting"));
    }
}
