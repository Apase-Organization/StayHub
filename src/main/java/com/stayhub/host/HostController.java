package com.stayhub.host;

import com.stayhub.auth.UserPrincipal;
import com.stayhub.booking.BookingService;
import com.stayhub.property.PropertyStatus;
import com.stayhub.property.PropertyService;
import com.stayhub.property.PropertyType;
import com.stayhub.property.dto.PropertyCreateRequest;
import com.stayhub.property.dto.PropertyUpdateRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/host")
@RequiredArgsConstructor
public class HostController {

    private final PropertyService propertyService;
    private final BookingService bookingService;

    @GetMapping({"/dashboard", "/properties"})
    public String showProperties(@AuthenticationPrincipal UserPrincipal principal, Model model) {
        var properties = propertyService.getHostProperties(principal.getId());
        model.addAttribute("properties", properties);
        model.addAttribute("totalProperties", properties.size());
        model.addAttribute("activeProperties", properties.stream().filter(property -> property.getStatus() == PropertyStatus.ACTIVE).count());
        model.addAttribute("draftProperties", properties.stream().filter(property -> property.getStatus() == PropertyStatus.DRAFT).count());
        model.addAttribute("archivedProperties", properties.stream().filter(property -> property.getStatus() == PropertyStatus.INACTIVE).count());
        var bookingRequests = bookingService.getBookingRequestsByHost(principal.getId());
        model.addAttribute("bookingRequests", bookingRequests);
        model.addAttribute("pendingBookingRequests", bookingRequests.size());
        return "host/dashboard";
    }

    @PostMapping("/bookings/{id}/accept")
    public String acceptBooking(@AuthenticationPrincipal UserPrincipal principal,
                                @PathVariable Long id,
                                RedirectAttributes redirectAttributes) {
        bookingService.acceptBooking(principal.getId(), id);
        redirectAttributes.addFlashAttribute("message", "Booking request accepted.");
        return "redirect:/host/dashboard";
    }

    @PostMapping("/bookings/{id}/reject")
    public String rejectBooking(@AuthenticationPrincipal UserPrincipal principal,
                                @PathVariable Long id,
                                RedirectAttributes redirectAttributes) {
        bookingService.rejectBooking(principal.getId(), id);
        redirectAttributes.addFlashAttribute("message", "Booking request rejected.");
        return "redirect:/host/dashboard";
    }

    @PostMapping("/bookings/{id}/complete")
    public String completeBooking(@AuthenticationPrincipal UserPrincipal principal,
                                  @PathVariable Long id,
                                  RedirectAttributes redirectAttributes) {
        bookingService.completeBooking(principal.getId(), id);
        redirectAttributes.addFlashAttribute("message", "Booking marked as completed.");
        return "redirect:/host/bookings/" + id;
    }

    @GetMapping("/properties/new")
    public String showCreateForm(Model model) {
        if (!model.containsAttribute("propertyRequest")) {
            model.addAttribute("propertyRequest", new PropertyCreateRequest());
        }
        addFormOptions(model);
        model.addAttribute("editing", false);
        return "host/property-form";
    }

    @PostMapping("/properties")
    public String createProperty(@AuthenticationPrincipal UserPrincipal principal,
                                 @Valid @ModelAttribute("propertyRequest") PropertyCreateRequest request,
                                 BindingResult bindingResult,
                                 @RequestParam(name = "images", required = false) List<MultipartFile> images,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            addFormOptions(model);
            model.addAttribute("editing", false);
            return "host/property-form";
        }

        var property = propertyService.createProperty(principal.getId(), request, images);
        redirectAttributes.addFlashAttribute("message", "Property created as a draft.");
        return "redirect:/host/properties/" + property.getId() + "/edit";
    }

    @GetMapping("/properties/{id}/edit")
    public String showEditForm(@AuthenticationPrincipal UserPrincipal principal,
                               @PathVariable Long id,
                               Model model) {
        if (!model.containsAttribute("propertyRequest")) {
            model.addAttribute("propertyRequest", propertyService.getHostPropertyForEdit(principal.getId(), id));
        }
        model.addAttribute("property", propertyService.getHostProperty(principal.getId(), id));
        model.addAttribute("propertyId", id);
        model.addAttribute("editing", true);
        addFormOptions(model);
        return "host/property-form";
    }

    @PostMapping("/properties/{id}")
    public String updateProperty(@AuthenticationPrincipal UserPrincipal principal,
                                 @PathVariable Long id,
                                 @Valid @ModelAttribute("propertyRequest") PropertyUpdateRequest request,
                                 BindingResult bindingResult,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("property", propertyService.getHostProperty(principal.getId(), id));
            model.addAttribute("propertyId", id);
            model.addAttribute("editing", true);
            addFormOptions(model);
            return "host/property-form";
        }
        propertyService.updateProperty(principal.getId(), id, request);
        redirectAttributes.addFlashAttribute("message", "Property updated successfully.");
        return "redirect:/host/properties/" + id + "/edit";
    }

    @PostMapping("/properties/{id}/archive")
    public String archiveProperty(@AuthenticationPrincipal UserPrincipal principal,
                                  @PathVariable Long id,
                                  RedirectAttributes redirectAttributes) {
        propertyService.archiveProperty(principal.getId(), id);
        redirectAttributes.addFlashAttribute("message", "Property archived successfully.");
        return "redirect:/host/dashboard";
    }

    @PostMapping("/properties/{id}/images")
    public String uploadImages(@AuthenticationPrincipal UserPrincipal principal,
                               @PathVariable Long id,
                               @RequestParam("images") List<MultipartFile> images,
                               RedirectAttributes redirectAttributes) {
        propertyService.uploadImages(principal.getId(), id, images);
        redirectAttributes.addFlashAttribute("message", "Images uploaded successfully.");
        return "redirect:/host/properties/" + id + "/edit";
    }

    @PostMapping("/properties/{propertyId}/images/{imageId}/cover")
    public String setCoverImage(@AuthenticationPrincipal UserPrincipal principal,
                                @PathVariable Long propertyId,
                                @PathVariable Long imageId) {
        propertyService.setCoverImage(principal.getId(), propertyId, imageId);
        return "redirect:/host/properties/" + propertyId + "/edit";
    }

    @PostMapping("/properties/{propertyId}/images/{imageId}/delete")
    public String deleteImage(@AuthenticationPrincipal UserPrincipal principal,
                              @PathVariable Long propertyId,
                              @PathVariable Long imageId) {
        propertyService.deleteImage(principal.getId(), propertyId, imageId);
        return "redirect:/host/properties/" + propertyId + "/edit";
    }

    private void addFormOptions(Model model) {
        model.addAttribute("propertyTypes", PropertyType.values());
        model.addAttribute("amenities", propertyService.getAmenities());
    }
}
