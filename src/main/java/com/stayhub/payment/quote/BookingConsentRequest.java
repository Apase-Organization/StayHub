package com.stayhub.payment.quote;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BookingConsentRequest(@NotBlank @Size(max=36) String quoteId,
                                    @NotBlank String acceptedPolicyVersion,
                                    @NotBlank String acceptedFixtureVersion,
                                    @NotBlank @Pattern(regexp="[a-f0-9]{64}") String acceptedQuoteHash) {}
