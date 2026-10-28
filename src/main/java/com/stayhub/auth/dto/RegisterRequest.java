package com.stayhub.auth.dto;

import com.stayhub.user.EmailNormalizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "Vui lòng nhập họ và tên")
    @Size(max = 150, message = "Họ và tên không được quá 150 ký tự")
    private String fullName;

    @NotBlank(message = "Vui lòng nhập tên đăng nhập")
    @Size(min = 3, max = 50, message = "Tên đăng nhập phải có từ 3 đến 50 ký tự")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Tên đăng nhập chỉ gồm chữ cái không dấu, chữ số, dấu chấm, gạch dưới và gạch nối")
    private String username;
     
    @NotBlank(message = "Vui lòng nhập email")
    @Email(message = "Định dạng email không hợp lệ")
    @Size(max = 255, message = "Email không được quá 255 ký tự")
    private String email;

    @NotBlank(message = "Vui lòng nhập mật khẩu")
    @Size(min = 8, max = 72, message = "Mật khẩu phải có từ 8 đến 72 ký tự")
    private String password;

    @NotBlank(message = "Vui lòng xác nhận mật khẩu")
    private String confirmPassword;

    public void setEmail(String email) {
        this.email = EmailNormalizer.normalize(email);
    }

    public void setUsername(String username) {
        this.username = username == null ? null : username.trim().toLowerCase();
    }
}
