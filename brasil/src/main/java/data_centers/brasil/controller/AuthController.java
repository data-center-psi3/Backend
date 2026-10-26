package data_centers.brasil.controller;

import java.security.Principal;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {
    @GetMapping("/api/auth/me")
    public ResponseEntity<UserResponse> me(Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new UserResponse(principal.getName()));
    }

    public record UserResponse(String username) {}
}
