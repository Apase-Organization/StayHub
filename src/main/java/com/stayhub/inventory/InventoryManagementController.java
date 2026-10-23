package com.stayhub.inventory;

import com.stayhub.auth.UserPrincipal;
import com.stayhub.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/host/properties/{propertyId}/inventory")
public class InventoryManagementController {
    private final InventoryManagementService service;

    @GetMapping("/room-types")
    public ApiResponse<?> types(@AuthenticationPrincipal UserPrincipal user, @PathVariable long propertyId) {
        return ApiResponse.success(service.listTypes(user.getId(), propertyId), "Room types");
    }
    @PostMapping("/room-types")
    public ApiResponse<?> createType(@AuthenticationPrincipal UserPrincipal user, @PathVariable long propertyId,
                                     @Valid @RequestBody RoomTypeRequest request) {
        return ApiResponse.success(service.createType(user.getId(), propertyId, request), "Room type created");
    }
    @PutMapping("/room-types/{typeId}")
    public ApiResponse<?> updateType(@AuthenticationPrincipal UserPrincipal user, @PathVariable long propertyId,
                                     @PathVariable long typeId, @Valid @RequestBody RoomTypeUpdateRequest request) {
        service.updateType(user.getId(), propertyId, typeId, request);
        return ApiResponse.success(null, "Room type updated");
    }
    @DeleteMapping("/room-types/{typeId}")
    public ApiResponse<?> archiveType(@AuthenticationPrincipal UserPrincipal user, @PathVariable long propertyId,
                                      @PathVariable long typeId, @RequestParam long expectedVersion) {
        service.archiveType(user.getId(), propertyId, typeId, expectedVersion);
        return ApiResponse.success(null, "Room type archived");
    }
    @GetMapping("/room-types/{typeId}/rate-plans")
    public ApiResponse<?> plans(@AuthenticationPrincipal UserPrincipal user, @PathVariable long propertyId,
                                @PathVariable long typeId) {
        return ApiResponse.success(service.listPlans(user.getId(), propertyId, typeId), "Rate plans");
    }
    @PostMapping("/room-types/{typeId}/rate-plans")
    public ApiResponse<?> createPlan(@AuthenticationPrincipal UserPrincipal user, @PathVariable long propertyId,
                                     @PathVariable long typeId, @Valid @RequestBody RatePlanRequest request) {
        return ApiResponse.success(service.createPlan(user.getId(), propertyId, typeId, request), "Rate plan created");
    }
    @PutMapping("/room-types/{typeId}/rate-plans/{planId}")
    public ApiResponse<?> updatePlan(@AuthenticationPrincipal UserPrincipal user, @PathVariable long propertyId,
                                     @PathVariable long typeId, @PathVariable long planId,
                                     @Valid @RequestBody RatePlanUpdateRequest request) {
        service.updatePlan(user.getId(), propertyId, typeId, planId, request);
        return ApiResponse.success(null, "Rate plan updated");
    }
    @DeleteMapping("/room-types/{typeId}/rate-plans/{planId}")
    public ApiResponse<?> archivePlan(@AuthenticationPrincipal UserPrincipal user, @PathVariable long propertyId,
                                      @PathVariable long typeId, @PathVariable long planId,
                                      @RequestParam long expectedVersion) {
        service.archivePlan(user.getId(), propertyId, typeId, planId, expectedVersion);
        return ApiResponse.success(null, "Rate plan archived");
    }
    @GetMapping("/room-types/{typeId}/calendar")
    public ApiResponse<?> calendar(@AuthenticationPrincipal UserPrincipal user, @PathVariable long propertyId,
                                   @PathVariable long typeId,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate through) {
        return ApiResponse.success(service.listCalendar(user.getId(), propertyId, typeId, from, through), "Inventory calendar");
    }
    @PutMapping("/room-types/{typeId}/calendar")
    public ApiResponse<?> updateDay(@AuthenticationPrincipal UserPrincipal user, @PathVariable long propertyId,
                                    @PathVariable long typeId, @Valid @RequestBody InventoryDayRequest request) {
        service.updateCalendar(user.getId(), propertyId, typeId, request);
        return ApiResponse.success(null, "Inventory day saved");
    }
}
