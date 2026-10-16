package com.ecom.payments.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/** Validates login tokens by calling the user service. */
@Service
public class AuthService {
    private final RestTemplate restTemplate;
    private final String userServiceBaseUrl;

    public AuthService(RestTemplate restTemplate, @Value("${userservice.base-url}") String userServiceBaseUrl) {
        this.restTemplate = restTemplate;
        this.userServiceBaseUrl = userServiceBaseUrl;
    }

    public AuthUserDto validateToken(String token) {
        try {
            AuthUserDto user = restTemplate.getForObject(
                    userServiceBaseUrl + "/api/v1/users/validate/{token}", AuthUserDto.class, token);
            if (user == null) {
                throw new UnauthorizedException("Invalid or expired token");
            }
            return user;
        } catch (HttpClientErrorException e) {
            throw new UnauthorizedException("Invalid or expired token");
        } catch (RestClientException e) {
            throw new AuthServiceUnavailableException("User service is unavailable", e);
        }
    }
}
