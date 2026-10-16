package com.ecom.payments.paymentgateways;

/**
 * @param linkId gateway's id for the link (e.g. "plink_..."), used to match webhook events
 * @param url    short URL the customer opens to pay
 */
public record PaymentLinkResult(String linkId, String url) {
}
