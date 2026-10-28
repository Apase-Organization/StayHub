package com.stayhub.user;


public enum UserStatus {
    ACTIVE,
    LOCKED,
    INACTIVE;

    public String getDisplayName() {
        return switch (this) {
            case ACTIVE -> "Đang hoạt động";
            case LOCKED -> "Đã khóa";
            case INACTIVE -> "Ngừng hoạt động";
        };
    }
}
