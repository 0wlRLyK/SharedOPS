package dev.sharedops.smoke.openapi;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OpenApiSmokeController {

  @GetMapping("/api/_smoke/openapi")
  public ResponseEntity<OpenApiSmokeResponse> getSmokeResponse() {
    return ResponseEntity.ok(new OpenApiSmokeResponse("OK", 1));
  }
}
