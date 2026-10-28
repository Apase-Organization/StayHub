package com.stayhub.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

class DemoReviewSeedIntegrationTest {
    @Test
    void persistsTwoMarkedReviewsPerPropertyIdempotentlyWithoutNotifications() {
        // Dedicated disposable DB, never application/local connection settings.
        try (var postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            var ds = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            Flyway.configure().dataSource(ds).load().migrate();
            var jdbc = new JdbcTemplate(ds);
            var tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
            jdbc.execute("TRUNCATE users CASCADE");
            jdbc.execute("""
                    INSERT INTO users(id,email,password_hash,full_name,role,status) VALUES
                    (90001,'host.fixture@example.com','test','Host','HOST','ACTIVE'),
                    (90002,'real.fixture@example.com','test','Real guest','GUEST','ACTIVE');
                    INSERT INTO properties(id,host_id,title,description,address,city,price_per_night,
                        max_guests,bedrooms,beds,bathrooms,property_type,rating_avg) VALUES
                    (90001,90001,'Fixture','Fixture','Fixture','Fixture',100000,1,1,1,1,'HOUSE',4.82),
                    (90002,90001,'Fixture 2','Fixture','Fixture','Fixture',200000,1,1,1,1,'HOUSE',4.82);
                    INSERT INTO bookings(id,property_id,guest_id,check_in_date,check_out_date,guests,
                        nightly_price,cleaning_fee,service_fee,subtotal_price,total_price,status)
                    VALUES(90001,90001,90002,'2019-01-01','2019-01-02',1,100000,0,0,100000,100000,'COMPLETED');
                    INSERT INTO reviews(booking_id,property_id,guest_id,rating,comment)
                    VALUES(90001,90001,90002,2,'Real review must remain unchanged');
                    """);
            var runner = new DemoReviewSeedRunner(jdbc);
            // Missing demo guest is a safe no-op, never impersonate an arbitrary guest.
            tx.executeWithoutResult(s -> runner.run(null));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM reviews", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT rating_avg FROM properties WHERE id=90002", java.math.BigDecimal.class))
                    .isEqualByComparingTo("0.00");
            jdbc.execute("""
                    INSERT INTO users(id,email,password_hash,full_name,role,status)
                    VALUES(90003,'guest@stayhub.com','test','Demo guest','GUEST','ACTIVE')
                    """);
            tx.executeWithoutResult(s -> runner.run(null));
            tx.executeWithoutResult(s -> runner.run(null));
            assertThat(jdbc.queryForList("""
                    SELECT count(*) FROM reviews WHERE guest_id=90003 AND rating=5
                        AND comment LIKE '[Đánh giá demo]%' GROUP BY property_id ORDER BY property_id
                    """, Integer.class)).containsExactly(2, 2);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM bookings", Integer.class)).isEqualTo(5);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM booking_notification_outbox", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM payments", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT comment FROM reviews WHERE booking_id=90001", String.class))
                    .isEqualTo("Real review must remain unchanged");
            assertThat(jdbc.queryForObject("SELECT rating_avg FROM properties WHERE id=90001", java.math.BigDecimal.class))
                    .isEqualByComparingTo("4.00");
            assertThat(jdbc.queryForObject("SELECT rating_avg FROM properties WHERE id=90002", java.math.BigDecimal.class))
                    .isEqualByComparingTo("5.00");
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM reviews r JOIN bookings b ON b.id=r.booking_id
                    WHERE r.guest_id=90003 AND b.status='COMPLETED' AND b.check_out_date<CURRENT_DATE
                        AND b.guest_id=r.guest_id AND b.property_id=r.property_id
                    """, Integer.class)).isEqualTo(4);
        }
    }
}
