package com.ecom.payments.auth;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** The user returned by the user service's /validate/{token} endpoint. */
@Getter
@Setter
public class AuthUserDto {
    private Long id;
    private String name;
    private String email;
    private List<String> roles;
}
