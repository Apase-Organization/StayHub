# StayHub

Nền tảng đặt chỗ lưu trú, sử dụng Java 21, Spring Boot, Thymeleaf và PostgreSQL.

## Yêu cầu

- Git, Docker đang chạy và Docker Compose (`docker compose`).
- Node.js/npm, Python 3 và Bash trên Linux/macOS hoặc WSL.
- Kết nối Internet để tải dependencies, Docker images và mở Cloudflare Tunnel.

Không cần cài Java/Maven trên máy: Docker đảm nhiệm build và chạy ứng dụng.

## Cài đặt và chạy

```bash
git clone https://github.com/Apase-Organization/StayHub.git
cd StayHub
cp .env.example .env
```

Chỉ sao chép `.env` ở lần thiết lập đầu tiên để tránh ghi đè cấu hình cũ.
Mở `.env`, đặt `DB_PASSWORD` thành mật khẩu riêng. Không chia sẻ hoặc commit file này.
Mặc định dùng cấu hình demo và dữ liệu mẫu; các tích hợp sau chỉ cần cấu hình khi sử dụng:

- Google: `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`.
- SMTP: đặt `MAIL_ENABLED=true`, cấu hình `MAIL_HOST`, `MAIL_PORT`,
  `MAIL_USERNAME`, `MAIL_PASSWORD` và `MAIL_FROM`.
- Cloudinary: đặt `UPLOAD_USE_CLOUDINARY=true`, điền `CLOUDINARY_CLOUD_NAME`,
  `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`; mặc định lưu ảnh cục bộ.
- VNPay sandbox: điền `VNPAY_TMN_CODE` và `VNPAY_HASH_SECRET` do VNPay cấp
  trước khi thử thanh toán.

```bash
./scripts/dev-up.sh
```

Script tự chạy `npm ci` nếu chưa có `node_modules`, build CSS và Docker image,
khởi động database, ứng dụng và Cloudflare Tunnel. Khi hoàn tất, script in địa chỉ
local (mặc định **http://localhost:8080**) và URL public.
Tunnel đưa ứng dụng ra Internet, chỉ dùng cho môi trường phát triển/demo.

Script cập nhật `VNPAY_RETURN_URL` (localhost) và `VNPAY_IPN_URL` (public) trong `.env`.
Để kiểm thử VNPay, sao chép **VNPay IPN URL** được in ra vào cấu hình IPN của
merchant sandbox và giữ tunnel chạy. URL quick tunnel thay đổi mỗi lần tạo lại,
vì vậy cần cập nhật lại cấu hình merchant tương ứng.

## Build riêng

`dev-up.sh` đã tự build; nếu chỉ muốn build mà chưa khởi động dịch vụ:

```bash
npm ci && npm run build:css && docker compose build app
```

## Xem log và dừng

```bash
docker compose logs -f app cloudflared
# Nhấn Ctrl+C để thoát xem log.
./scripts/dev-stop.sh
```

Lệnh dừng giữ lại dữ liệu PostgreSQL và ảnh trong Docker volumes.
**Không chạy `docker compose down -v` nếu muốn giữ dữ liệu.**
