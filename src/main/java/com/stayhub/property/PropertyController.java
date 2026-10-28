package com.stayhub.property;

import com.stayhub.auth.UserPrincipal;
import com.stayhub.search.SearchService;
import com.stayhub.search.dto.SearchCriteria;
import com.stayhub.review.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@RequiredArgsConstructor
public class PropertyController {

    private final PropertyService propertyService;
    private final SearchService searchService;
    private final ReviewService reviewService;

    @GetMapping("/properties")
    public String search(SearchCriteria criteria,
                         @PageableDefault(size = 12, sort = "createdAt") Pageable pageable,
                         Model model) {
        model.addAttribute("criteria", criteria);
        model.addAttribute("properties", searchService.search(criteria, pageable));
        model.addAttribute("propertyTypes", PropertyType.values());
        model.addAttribute("amenities", propertyService.getAmenities());
        return "property/search-results";
    }

    @GetMapping("/properties/{id}")
    public String showProperty(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal viewer, Model model) {
        var property=propertyService.getPublicProperty(id);
        model.addAttribute("property", property);
        model.addAttribute("reviews", reviewService.getPropertyReviews(id));
        boolean self=viewer!=null&&property.getHostId()!=null&&property.getHostId().equals(viewer.getId());
        boolean guestOrHost=viewer!=null&&(viewer.getAuthorities().stream().anyMatch(authority->
                authority.getAuthority().equals("ROLE_GUEST")||authority.getAuthority().equals("ROLE_HOST")));
        String viewerRole=viewer==null?null:viewer.getAuthorities().stream()
                .map(Object::toString)
                .filter(authority->authority.startsWith("ROLE_"))
                .map(authority->authority.substring("ROLE_".length()))
                .findFirst().orElse(null);
        model.addAttribute("canBookProperty",guestOrHost&&!self);
        model.addAttribute("selfBookingBlocked",self);
        model.addAttribute("viewerRole",viewerRole);
        return "property/property-detail";
    }
}
