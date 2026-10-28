package com.stayhub.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UiDemoSmokeTest {
    @Test
    void reviewFixturesRequireBothDemoProfileAndEnabledFlag() {
        var runner = new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withUserConfiguration(DemoReviewSeedRunner.class)
                .withBean(org.springframework.jdbc.core.JdbcTemplate.class,
                        () -> org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class));
        runner.withInitializer(c -> c.getEnvironment().setActiveProfiles("prod"))
                .withPropertyValues("app.seed-data.enabled=true")
                .run(c -> assertThat(c).doesNotHaveBean(DemoReviewSeedRunner.class));
        runner.withInitializer(c -> c.getEnvironment().setActiveProfiles("demo"))
                .withPropertyValues("app.seed-data.enabled=false")
                .run(c -> assertThat(c).doesNotHaveBean(DemoReviewSeedRunner.class));
        runner.withInitializer(c -> c.getEnvironment().setActiveProfiles("demo"))
                .withPropertyValues("app.seed-data.enabled=true")
                .run(c -> assertThat(c).hasSingleBean(DemoReviewSeedRunner.class));
    }

    private String template(String name) throws Exception {
        return Files.readString(Path.of("src/main/resources/templates/" + name + ".html"));
    }

    @Test
    void compactStatusAndInventoryNotice() throws Exception {
        assertThat(template("host/dashboard")).contains("min-w-0 flex-1", "th:title=\"${property.status}\"",
                "aria-label=${property.status}", "' bg-green-500' : ' bg-red-500'");
        assertThat(template("host/property-inventory")).doesNotContain("Tồn kho được quản lý theo")
                .contains("inventory-message", "Đặt phòng này không có bản chụp dữ liệu tài chính");
    }

    @Test
    void performancePrecedesAnomalies() throws Exception {
        String html = template("admin/dashboard");
        assertThat(html).contains("th:fragment=\"hostPerformanceContent\"", "Hiệu suất & doanh thu từng chủ chỗ nghỉ");
        assertThat(html.indexOf("<!-- Host Performance Matrix Section -->"))
                .isLessThan(html.indexOf("<!-- Anomaly Alert Center Banner -->"));
    }

    @Test
    void verifiedBookingStatusSelectsTabWithoutTreatingFailuresAsConfirmed() throws Exception {
        String html = template("payment/vnpay-return");
        String function = html.substring(html.indexOf("function setBookingsTab("), html.indexOf("    setBookingsTab(null);"));
        String script = "const assert=require('node:assert/strict');"
                + "const bookingsUrl='http://localhost:8080/bookings?tab=pending';const bookingLink={};"
                + function
                + "for(const [status,tab] of [['CONFIRMED','upcoming'],['PENDING','pending'],"
                + "['PENDING_PAYMENT','pending'],[null,'pending'],['CANCELLED','cancelled'],"
                + "['REJECTED','cancelled'],['COMPLETED','completed']]){setBookingsTab(status);"
                + "assert.equal(new URL(bookingLink.href).searchParams.get('tab'),tab);} ";
        Process process = new ProcessBuilder("node", "-e", script).inheritIO().start();
        assertThat(process.waitFor()).isZero();
        assertThat(html).contains("setBookingsTab(data.bookingStatus)");
        assertThat(template("fragments/navbar")).contains("@{/bookings(tab='upcoming')}");
        assertThat(Files.readString(Path.of("src/main/java/com/stayhub/booking/BookingController.java")))
                .contains("@RequestParam(defaultValue = \"upcoming\") String tab");
    }
}
