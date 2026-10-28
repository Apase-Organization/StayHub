package com.stayhub.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE)
@ControllerAdvice(annotations = Controller.class)
public class MvcExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ModelAndView handleResourceNotFoundException(ResourceNotFoundException exception,
                                                        HttpServletRequest request) {
        return errorView(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ModelAndView handleInvalidStateTransitionException(InvalidStateTransitionException exception,
                                                              HttpServletRequest request) {
        return errorView(HttpStatus.CONFLICT, exception.getMessage(), request);
    }

    @ExceptionHandler(BusinessException.class)
    public ModelAndView handleBusinessException(BusinessException exception, HttpServletRequest request) {
        return errorView(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ModelAndView handleMaxUploadSizeExceededException(MaxUploadSizeExceededException exception,
                                                             HttpServletRequest request) {
        return errorView(HttpStatus.PAYLOAD_TOO_LARGE,
                "Tệp tải lên vượt quá giới hạn dung lượng cho phép.", request);
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView handleUnexpectedException(Exception exception, HttpServletRequest request) {
        log.error("Unexpected MVC error", exception);
        return errorView(HttpStatus.INTERNAL_SERVER_ERROR, "Đã xảy ra lỗi không mong đợi.", request);
    }

    private ModelAndView errorView(HttpStatus status, String message, HttpServletRequest request) {
        ModelAndView modelAndView = new ModelAndView("error/error");
        modelAndView.setStatus(status);
        modelAndView.addObject("status", status.value());
        modelAndView.addObject("error", status.getReasonPhrase());
        modelAndView.addObject("message", displayMessage(message));
        modelAndView.addObject("path", request.getRequestURI());
        return modelAndView;
    }

    /** Translate only MVC display copy; service and API error contracts stay unchanged. */
    public static String displayMessage(String message) {
        if (message == null) return "Không thể hoàn tất yêu cầu.";
        return switch (message) {
            case "ERR_COMPLETION_TOO_EARLY" -> "Chỉ có thể hoàn tất từ 11:00 ngày trả phòng (giờ Việt Nam).";
            case "ERR_ACTUAL_STAY_REQUIRED" -> "Cần chứng từ lưu trú thực tế đã được xác minh cho tất cả các phòng trước khi hoàn tất. Vui lòng liên hệ bộ phận hỗ trợ.";
            case "ERR_UNRESOLVED_STAY_CASE" -> "Đặt phòng còn hồ sơ hoặc tranh chấp chưa được giải quyết. Vui lòng liên hệ bộ phận hỗ trợ trước khi hoàn tất.";
            case "The verification code has expired. Please register again." -> "Mã xác minh đã hết hạn. Vui lòng đăng ký lại.";
            case "The verification code has expired. Please request a new code." -> "Mã xác minh đã hết hạn. Vui lòng yêu cầu mã mới.";
            case "The verification code is invalid." -> "Mã xác minh không hợp lệ.";
            case "This username is already taken." -> "Tên đăng nhập này đã được sử dụng.";
            case "Password confirmation does not match." -> "Mật khẩu xác nhận không khớp.";
            case "Unable to send verification email. Please try again later." -> "Không thể gửi email xác minh. Vui lòng thử lại sau.";
            case "Email already exists", "Email already exists.", "This email is already registered.", "An account with this email already exists." -> "Email này đã được đăng ký.";
            case "Guest not found" -> "Không tìm thấy tài khoản khách.";
            case "User account was not found." -> "Không tìm thấy tài khoản người dùng.";
            case "Property not found" -> "Không tìm thấy chỗ nghỉ.";
            case "Booking not found" -> "Không tìm thấy đặt phòng.";
            case "Only pending bookings can be accepted." -> "Chỉ có thể chấp nhận đặt phòng đang chờ xác nhận.";
            case "Only pending bookings can be rejected." -> "Chỉ có thể từ chối đặt phòng đang chờ xác nhận.";
            case "Only confirmed bookings can be completed." -> "Chỉ có thể hoàn tất đặt phòng đã xác nhận.";
            case "Only pending, pending payment, or confirmed bookings can be cancelled." -> "Chỉ có thể hủy đặt phòng đang chờ xác nhận, chờ thanh toán hoặc đã xác nhận.";
            case "Guest count must be at least 1." -> "Số khách phải ít nhất là 1.";
            case "Guest count exceeds the property capacity." -> "Số khách vượt quá sức chứa của chỗ nghỉ.";
            case "The selected dates are no longer available." -> "Không còn phòng trong các ngày đã chọn.";
            case "Same-day bookings must be created before 20:00 local time." -> "Đặt phòng trong ngày phải được tạo trước 20:00 giờ địa phương.";
            case "Only completed bookings can be reviewed." -> "Chỉ có thể đánh giá đặt phòng đã hoàn tất.";
            case "The approved review window is closed or has not opened." -> "Thời hạn đánh giá đã kết thúc hoặc chưa bắt đầu.";
            case "Verified actual-stay evidence is required before review." -> "Cần xác minh chứng từ lưu trú thực tế trước khi đánh giá.";
            case "This booking has already been reviewed." -> "Đặt phòng này đã được đánh giá.";
            default -> message;
        };
    }
}
