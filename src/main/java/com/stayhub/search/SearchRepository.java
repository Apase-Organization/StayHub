package com.stayhub.search;

import com.stayhub.booking.Booking;
import com.stayhub.booking.BookingStatus;
import com.stayhub.property.Amenity;
import com.stayhub.property.Property;
import com.stayhub.property.PropertyRepository;
import com.stayhub.property.PropertyStatus;
import com.stayhub.search.dto.SearchCriteria;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SearchRepository {

    private static final String VIETNAMESE_LETTERS = "áàảãạăắằẳẵặâấầẩẫậéèẻẽẹêếềểễệíìỉĩịóòỏõọôốồổỗộơớờởỡợúùủũụưứừửữựýỳỷỹỵđ";
    private static final String ASCII_LETTERS = VIETNAMESE_LETTERS.codePoints()
            .mapToObj(codePoint -> {
                String letter = new String(Character.toChars(codePoint));
                if (letter.equals("đ")) {
                    return "d";
                }
                return Normalizer.normalize(letter, Normalizer.Form.NFD)
                        .replaceAll("\\p{M}+", "");
            })
            .reduce("", String::concat);

    private final PropertyRepository propertyRepository;

    public Page<Property> search(SearchCriteria criteria, Pageable pageable) {
        return propertyRepository.findAll(toSpecification(criteria), pageable);
    }

    private Specification<Property> toSpecification(SearchCriteria criteria) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("status"), PropertyStatus.ACTIVE));

            if (criteria != null) {
                if (hasText(criteria.getLocation())) {
                    String keyword = "%" + normalizeLocation(criteria.getLocation()) + "%";
                    predicates.add(builder.or(
                            builder.like(normalizeDatabaseText(builder, root.get("city")), keyword),
                            builder.like(normalizeDatabaseText(builder, root.get("address")), keyword),
                            builder.like(normalizeDatabaseText(builder, root.get("title")), keyword)
                    ));
                }
                if (criteria.getGuests() != null) {
                    predicates.add(builder.greaterThanOrEqualTo(root.get("maxGuests"), criteria.getGuests()));
                }
                if (criteria.getRooms() != null) {
                    predicates.add(builder.greaterThanOrEqualTo(root.get("bedrooms"), criteria.getRooms()));
                }
                if (criteria.getCheckIn() != null && criteria.getCheckOut() != null) {
                    Subquery<Long> conflictSubquery = query.subquery(Long.class);
                    Root<Booking> bookingRoot = conflictSubquery.from(Booking.class);
                    conflictSubquery.select(bookingRoot.get("property").get("id"));

                    Predicate activeBooking = bookingRoot.get("status").in(List.of(
                            BookingStatus.CONFIRMED,
                            BookingStatus.PENDING,
                            BookingStatus.PENDING_PAYMENT
                    ));
                    Predicate overlapDates = builder.and(
                            builder.lessThan(bookingRoot.get("checkInDate"), criteria.getCheckOut()),
                            builder.greaterThan(bookingRoot.get("checkOutDate"), criteria.getCheckIn())
                    );
                    conflictSubquery.where(builder.and(activeBooking, overlapDates));

                    predicates.add(builder.not(root.get("id").in(conflictSubquery)));
                }
                if (criteria.getMinPrice() != null) {
                    predicates.add(builder.greaterThanOrEqualTo(root.get("pricePerNight"), criteria.getMinPrice()));
                }
                if (criteria.getMaxPrice() != null) {
                    predicates.add(builder.lessThanOrEqualTo(root.get("pricePerNight"), criteria.getMaxPrice()));
                }
                if (criteria.getType() != null && !criteria.getType().isEmpty()) {
                    predicates.add(root.get("propertyType").in(criteria.getType()));
                }
                if (criteria.getBedrooms() != null) {
                    predicates.add(builder.greaterThanOrEqualTo(root.get("bedrooms"), criteria.getBedrooms()));
                }
                if (criteria.getBeds() != null) {
                    predicates.add(builder.greaterThanOrEqualTo(root.get("beds"), criteria.getBeds()));
                }
                if (criteria.getBathrooms() != null) {
                    predicates.add(builder.greaterThanOrEqualTo(root.get("bathrooms"), criteria.getBathrooms()));
                }
                if (criteria.getRating() != null) {
                    predicates.add(builder.greaterThanOrEqualTo(root.get("ratingAvg"), criteria.getRating()));
                }
                if (criteria.getAmenities() != null && !criteria.getAmenities().isEmpty()) {
                    Join<Property, Amenity> amenityJoin = root.join("amenities", JoinType.INNER);
                    predicates.add(amenityJoin.get("id").in(criteria.getAmenities()));
                }
            }

            query.distinct(true);
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String normalizeLocation(String value) {
        return Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd');
    }

    private jakarta.persistence.criteria.Expression<String> normalizeDatabaseText(
            jakarta.persistence.criteria.CriteriaBuilder builder,
            jakarta.persistence.criteria.Expression<String> expression) {
        return builder.function("translate", String.class,
                builder.lower(expression),
                builder.literal(VIETNAMESE_LETTERS),
                builder.literal(ASCII_LETTERS));
    }
}
