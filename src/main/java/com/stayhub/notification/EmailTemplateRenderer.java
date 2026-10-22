package com.stayhub.notification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class EmailTemplateRenderer {

    private static final String LOGO_URL = "https://github.com/user-attachments/assets/dad8185a-1590-4f73-b1da-01d90ce87989";
    private static final String PRIMARY = "#ff5a5f";
    private static final String TEXT = "#111827";
    private static final String MUTED = "#6b7280";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM d, yyyy");

    private EmailTemplateRenderer() {
    }

    public static String otpEmail(String fullName, String otp) {
        return layout("Verify your email", "Use this code to finish creating your StayHub account.",
                """
                <p style="margin:0 0 18px;color:%s;font-size:16px;line-height:1.6;">Hello %s,</p>
                <p style="margin:0 0 20px;color:%s;font-size:16px;line-height:1.6;">Enter this verification code to create your StayHub account. The code expires in 10 minutes.</p>
                <div style="margin:28px 0;padding:22px;border-radius:20px;background:#fff1f2;text-align:center;border:1px solid #ffe4e6;">
                    <div style="color:%s;font-size:12px;font-weight:800;letter-spacing:.18em;text-transform:uppercase;margin-bottom:10px;">Verification code</div>
                    <div style="font-size:36px;font-weight:900;letter-spacing:.22em;color:%s;font-family:ui-monospace,SFMono-Regular,Menlo,Monaco,Consolas,monospace;">%s</div>
                </div>
                <p style="margin:0;color:%s;font-size:14px;line-height:1.6;">If you did not request this code, you can safely ignore this email.</p>
                """.formatted(MUTED, escape(fullName), MUTED, PRIMARY, TEXT, escape(otp), MUTED));
    }

    public static String bookingStatusEmail(String fullName,
                                            String propertyTitle,
                                            String status,
                                            LocalDate checkIn,
                                            LocalDate checkOut,
                                            Integer guests,
                                            BigDecimal totalPrice) {
        String statusColor = switch (status) {
            case "CONFIRMED" -> "#16a34a";
            case "COMPLETED" -> "#2563eb";
            case "REJECTED", "CANCELLED" -> "#dc2626";
            default -> PRIMARY;
        };
        String statusBackground = switch (status) {
            case "CONFIRMED" -> "#dcfce7";
            case "COMPLETED" -> "#dbeafe";
            case "REJECTED", "CANCELLED" -> "#fee2e2";
            default -> "#fff1f2";
        };
        String title = switch (status) {
            case "CONFIRMED" -> "Your booking is confirmed";
            case "COMPLETED" -> "Thank you for staying! Leave a review";
            case "REJECTED" -> "Your booking was rejected";
            case "CANCELLED" -> "Your booking was cancelled";
            default -> "Your booking was updated";
        };
        String footerNote = switch (status) {
            case "COMPLETED" -> "Your trip is completed! We hope you enjoyed your stay. Please log in to your account and leave a review to help future guests.";
            case "CANCELLED" -> "Your booking has been cancelled. If applicable, refund processing will follow the cancellation and refund policy.";
            case "CONFIRMED" -> "Your booking is confirmed. Please review your check-in time and host contact details on your StayHub account.";
            default -> "You can review your trip details from your StayHub bookings page.";
        };
        return layout(title, "StayHub booking update for " + propertyTitle + ".",
                """
                <p style="margin:0 0 18px;color:%s;font-size:16px;line-height:1.6;">Hello %s,</p>
                <p style="margin:0 0 20px;color:%s;font-size:16px;line-height:1.6;">Your booking for <strong style="color:%s;">%s</strong> is now:</p>
                <div style="display:inline-block;margin:0 0 24px;padding:10px 16px;border-radius:999px;background:%s;color:%s;font-size:13px;font-weight:900;letter-spacing:.08em;text-transform:uppercase;">%s</div>
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="margin:8px 0 24px;border-collapse:separate;border-spacing:0 10px;">
                    %s
                    %s
                    %s
                    %s
                </table>
                <div style="margin-top:22px;padding:18px;border-radius:18px;background:#f9fafb;border:1px solid #e5e7eb;color:%s;font-size:14px;line-height:1.6;">
                    %s
                </div>
                """.formatted(MUTED, escape(fullName), MUTED, TEXT, escape(propertyTitle), statusBackground, statusColor, escape(status),
                        detailRow("Check-in", DATE_FORMATTER.format(checkIn)),
                        detailRow("Check-out", DATE_FORMATTER.format(checkOut)),
                        detailRow("Guests", String.valueOf(guests)),
                        detailRow("Total", totalPrice.stripTrailingZeros().toPlainString() + " VND"), MUTED, escape(footerNote)));
    }

    private static String layout(String heading, String preheader, String body) {
        return """
                <!doctype html>
                <html>
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
                <body style="margin:0;padding:0;background:#f6f7fb;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:%s;">
                    <div style="display:none;max-height:0;overflow:hidden;opacity:0;color:transparent;">%s</div>
                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f6f7fb;padding:28px 12px;">
                        <tr><td align="center">
                            <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:620px;background:#ffffff;border-radius:28px;overflow:hidden;border:1px solid #e5e7eb;box-shadow:0 18px 48px rgba(15,23,42,.08);">
                                <tr>
                                    <td style="padding:30px 34px 22px;background:linear-gradient(135deg,#fff7f7 0%%,#ffffff 60%%);border-bottom:1px solid #f1f5f9;">
                                        <table role="presentation" width="100%%"><tr>
                                            <td style="vertical-align:middle;">
                                                <img src="%s" width="44" height="44" alt="StayHub" style="display:inline-block;border-radius:14px;vertical-align:middle;margin-right:12px;">
                                                <span style="vertical-align:middle;color:%s;font-size:20px;font-weight:900;letter-spacing:-.02em;">StayHub</span>
                                            </td>
                                        </tr></table>
                                        <h1 style="margin:24px 0 0;color:%s;font-size:30px;line-height:1.18;letter-spacing:-.04em;">%s</h1>
                                    </td>
                                </tr>
                                <tr><td style="padding:30px 34px 34px;">%s</td></tr>
                                <tr><td style="padding:22px 34px;background:#111827;color:#d1d5db;font-size:13px;line-height:1.6;">StayHub System<br><span style="color:#9ca3af;">Beautiful stays, simple bookings.</span></td></tr>
                            </table>
                        </td></tr>
                    </table>
                </body>
                </html>
                """.formatted(TEXT, escape(preheader), LOGO_URL, PRIMARY, TEXT, escape(heading), body);
    }

    private static String detailRow(String label, String value) {
        return """
                <tr>
                    <td style="padding:14px 16px;border:1px solid #e5e7eb;border-right:0;border-radius:16px 0 0 16px;background:#ffffff;color:%s;font-size:13px;font-weight:800;">%s</td>
                    <td align="right" style="padding:14px 16px;border:1px solid #e5e7eb;border-left:0;border-radius:0 16px 16px 0;background:#ffffff;color:%s;font-size:15px;font-weight:900;">%s</td>
                </tr>
                """.formatted(MUTED, escape(label), TEXT, escape(value));
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
