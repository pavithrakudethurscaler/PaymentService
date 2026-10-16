package com.ecom.payments.clients;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

/** The fields the Payment Service needs from the Order Service's order response. */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderDto {
    private Long id;
    private String status;
    /** Order total in paise */
    private long totalAmount;
    private String currency;
}
