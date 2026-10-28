package com.stayhub.user.dto;

import com.stayhub.user.EmailNormalizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @NotBlank(message = "Vui lòng nhập email")
    @Email(message = "Định dạng email không hợp lệ")
    @Size(max = 255, message = "Email không được quá 255 ký tự")
    private String email;
    
    @NotBlank(message = "Vui lòng nhập họ và tên")
    @Size(max = 150, message = "Họ và tên không được quá 150 ký tự")
    private String fullName;

    @Size(max = 30, message = "Số điện thoại không được quá 30 ký tự")
    private String phone;

    public void setEmail(String email) {
        this.email = EmailNormalizer.normalize(email);
    }
}
