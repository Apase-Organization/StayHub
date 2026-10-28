package com.stayhub.booking;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Query("""
            select b from Booking b
            join fetch b.property p
            join fetch p.host
            join fetch b.guest
            left join fetch b.payment
            where p.host.id = :hostId and b.status = :status
            order by b.createdAt desc, b.id desc
            """)
    List<Booking> findRecentBookingsByHost(@Param("hostId") Long hostId,
                                           @Param("status") BookingStatus status,
                                           org.springframework.data.domain.Pageable pageable);

    @Query("""
            select b from Booking b
            join fetch b.property p
            join fetch p.host
            join fetch b.guest
            left join fetch b.payment
            where b.id = :bookingId
            """)
    Optional<Booking> findDetailedById(@Param("bookingId") Long bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b from Booking b
            join fetch b.property p
            join fetch p.host
            join fetch b.guest
            left join fetch b.payment
            where b.id = :bookingId
            """)
    Optional<Booking> findDetailedByIdForUpdate(@Param("bookingId") Long bookingId);

    @Query("""
            select b from Booking b
            join fetch b.property p
            join fetch p.host
            join fetch b.guest
            left join fetch b.payment
            where p.id = :propertyId
              and b.status in :statuses
              and b.checkInDate < :checkOutDate
              and b.checkOutDate > :checkInDate
            order by b.checkInDate asc
            """)
    List<Booking> findConflictingBookings(@Param("propertyId") Long propertyId,
                                          @Param("checkInDate") LocalDate checkInDate,
                                          @Param("checkOutDate") LocalDate checkOutDate,
                                          @Param("statuses") Collection<BookingStatus> statuses);

    @Query("""
            select b from Booking b
            join fetch b.property p
            join fetch p.host
            join fetch b.guest
            left join fetch b.payment
            where b.guest.id = :guestId
            order by b.checkInDate desc, b.createdAt desc
            """)
    List<Booking> findByGuestId(@Param("guestId") Long guestId);

    @Query("""
            select b from Booking b
            join fetch b.property p
            join fetch p.host
            join fetch b.guest
            left join fetch b.payment
            where p.id = :propertyId and b.status = :status
            order by b.checkInDate asc
            """)
    List<Booking> findByPropertyIdAndStatus(@Param("propertyId") Long propertyId,
                                            @Param("status") BookingStatus status);

    @Query("""
            select b from Booking b
            join fetch b.property p
            join fetch p.host
            join fetch b.guest
            left join fetch b.payment
            where p.host.id = :hostId and b.status = :status
            order by b.createdAt desc
            """)
    List<Booking> findBookingRequestsByHost(@Param("hostId") Long hostId,
                                            @Param("status") BookingStatus status);

    long countByGuestIdAndDiscountCodeId(Long guestId, Long discountCodeId);

    @Query("""
            select b from Booking b
            join fetch b.property p
            join fetch p.host
            join fetch b.guest
            left join fetch b.payment
            where p.host.id = :hostId
            order by b.createdAt desc
            """)
    List<Booking> findByPropertyHostIdOrderByCreatedAtDesc(@Param("hostId") Long hostId);

    List<Booking> findByStatusAndCreatedAtBefore(BookingStatus status, java.time.Instant threshold);

    @Query("select count(b) from Booking b where b.property.host.id = :hostId and b.status = :status")
    long countByHostIdAndStatus(@Param("hostId") Long hostId, @Param("status") BookingStatus status);

    @Query("select count(b) from Booking b where b.property.host.id = :hostId")
    long countByHostId(@Param("hostId") Long hostId);

    @Query("""
            select coalesce(sum(b.totalPrice), 0) from Booking b
            where b.property.host.id = :hostId
              and b.status in ('CONFIRMED', 'COMPLETED')
            """)
    java.math.BigDecimal sumGrossRevenueByHostId(@Param("hostId") Long hostId);

    @Query("""
            select coalesce(sum(b.serviceFee), 0) from Booking b
            where b.property.host.id = :hostId
              and b.status in ('CONFIRMED', 'COMPLETED')
            """)
    java.math.BigDecimal sumCommissionByHostId(@Param("hostId") Long hostId);
}
