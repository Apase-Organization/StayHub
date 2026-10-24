package com.stayhub.inventory;

import com.stayhub.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/properties/{propertyId}/availability")
public class InventoryOfferController {
    private final InventoryOfferService service;

    @PostMapping
    public ApiResponse<InventoryOfferService.OfferSearchResult> offers(
            @PathVariable long propertyId, @Valid @RequestBody InventoryOfferRequest request) {
        return ApiResponse.success(service.search(propertyId, request), "Eligible room offers retrieved.");
    }
}
