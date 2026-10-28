-- ==============================================================================
-- Seed Data: Demo Bookings for Presentation (Account: guest@stayhub.com)
-- Scenarios:
-- 1. Đặt phòng thành công sắp tới (Upcoming - CONFIRMED)
-- 2. Chuyến đi đang diễn ra ngay hôm nay (Active Stay - CONFIRMED)
-- 3. Đã đặt phòng nhưng chủ động hủy (Cancelled - CANCELLED)
-- 4. Đã nghỉ ngơi nhưng CHƯA ĐÁNH GIÁ (Completed - COMPLETED, ready for live review demo)
-- 5. Đã nghỉ ngơi và ĐÃ ĐÁNH GIÁ (Completed - COMPLETED, 5-star review included)
-- 6. Đơn mới tạo đang chờ thanh toán qua VNPay (Pending - PENDING_PAYMENT)
-- 7. Yêu cầu đặt phòng bị Host từ chối (Cancelled - REJECTED)
-- ==============================================================================

DO $$
DECLARE
    v_guest_id BIGINT;
    v_host_id BIGINT;
    v_b1 BIGINT;
    v_b2 BIGINT;
    v_b3 BIGINT;
    v_b4 BIGINT;
    v_b5 BIGINT;
    v_b6 BIGINT;
    v_b7 BIGINT;
BEGIN
    SELECT id INTO v_guest_id FROM users WHERE email = 'guest@stayhub.com';
    SELECT id INTO v_host_id FROM users WHERE email = 'host@stayhub.com';

    -- 1. Đặt phòng thành công - Sắp tới (CONFIRMED)
    IF NOT EXISTS (SELECT 1 FROM bookings WHERE guest_id = v_guest_id AND property_id = 101 AND check_in_date = '2026-11-15') THEN
        INSERT INTO bookings (property_id, guest_id, check_in_date, check_out_date, guests, nightly_price, cleaning_fee, service_fee, subtotal_price, discount_amount, total_price, status, created_at, updated_at)
        VALUES (101, v_guest_id, '2026-11-15', '2026-11-18', 4, 2500000, 300000, 0, 7800000, 0, 7800000, 'CONFIRMED', '2026-10-27 10:20:00+07', '2026-10-27 10:30:00+07')
        RETURNING id INTO v_b1;

        INSERT INTO payments (booking_id, payment_method, status, amount, transaction_id, paid_at, created_at, updated_at, provider, currency, provider_txn_ref)
        VALUES (v_b1, 'VNPAY', 'SUCCESS', 7800000, 'VNP14829101', '2026-10-27 10:30:00+07', '2026-10-27 10:20:00+07', '2026-10-27 10:30:00+07', 'VNPAY', 'VND', 'TXN_B1_' || v_b1);
    END IF;

    -- 2. Chuyến đi đang diễn ra (CONFIRMED - Đang lưu trú hôm nay)
    IF NOT EXISTS (SELECT 1 FROM bookings WHERE guest_id = v_guest_id AND property_id = 109 AND check_in_date = '2026-10-27') THEN
        INSERT INTO bookings (property_id, guest_id, check_in_date, check_out_date, guests, nightly_price, cleaning_fee, service_fee, subtotal_price, discount_amount, total_price, status, created_at, updated_at)
        VALUES (109, v_guest_id, '2026-10-27', '2026-10-30', 2, 950000, 100000, 0, 2950000, 0, 2950000, 'CONFIRMED', '2026-10-25 13:50:00+07', '2026-10-25 14:00:00+07')
        RETURNING id INTO v_b2;

        INSERT INTO payments (booking_id, payment_method, status, amount, transaction_id, paid_at, created_at, updated_at, provider, currency, provider_txn_ref)
        VALUES (v_b2, 'VNPAY', 'SUCCESS', 2950000, 'VNP14828109', '2026-10-25 14:00:00+07', '2026-10-25 13:50:00+07', '2026-10-25 14:00:00+07', 'VNPAY', 'VND', 'TXN_B2_' || v_b2);
    END IF;

    -- 3. Đã đặt phòng nhưng cancel (CANCELLED)
    IF NOT EXISTS (SELECT 1 FROM bookings WHERE guest_id = v_guest_id AND property_id = 102 AND check_in_date = '2026-11-05') THEN
        INSERT INTO bookings (property_id, guest_id, check_in_date, check_out_date, guests, nightly_price, cleaning_fee, service_fee, subtotal_price, discount_amount, total_price, status, cancelled_at, created_at, updated_at)
        VALUES (102, v_guest_id, '2026-11-05', '2026-11-07', 2, 700000, 80000, 0, 1480000, 0, 1480000, 'CANCELLED', '2026-10-26 15:45:00+07', '2026-10-24 09:00:00+07', '2026-10-26 15:45:00+07')
        RETURNING id INTO v_b3;

        INSERT INTO payments (booking_id, payment_method, status, amount, transaction_id, created_at, updated_at, provider, currency, provider_txn_ref)
        VALUES (v_b3, 'VNPAY', 'CANCELLED', 1480000, 'VNP_CANCEL_' || v_b3, '2026-10-24 09:00:00+07', '2026-10-26 15:45:00+07', 'VNPAY', 'VND', 'TXN_B3_' || v_b3);
    END IF;

    -- 4. Đã nghỉ ngơi nhưng CHƯA ĐÁNH GIÁ (COMPLETED, no review)
    IF NOT EXISTS (SELECT 1 FROM bookings WHERE guest_id = v_guest_id AND property_id = 11 AND check_in_date = '2026-10-15') THEN
        INSERT INTO bookings (property_id, guest_id, check_in_date, check_out_date, guests, nightly_price, cleaning_fee, service_fee, subtotal_price, discount_amount, total_price, status, completed_at, completed_by, created_at, updated_at)
        VALUES (11, v_guest_id, '2026-10-15', '2026-10-18', 2, 1600000, 200000, 0, 5000000, 0, 5000000, 'COMPLETED', '2026-10-18 12:00:00+07', v_host_id, '2026-10-12 09:00:00+07', '2026-10-18 12:00:00+07')
        RETURNING id INTO v_b4;

        INSERT INTO payments (booking_id, payment_method, status, amount, transaction_id, paid_at, created_at, updated_at, provider, currency, provider_txn_ref)
        VALUES (v_b4, 'VNPAY', 'SUCCESS', 5000000, 'VNP14811011', '2026-10-12 09:15:00+07', '2026-10-12 09:00:00+07', '2026-10-12 09:15:00+07', 'VNPAY', 'VND', 'TXN_B4_' || v_b4);
    END IF;

    -- 5. Đã nghỉ ngơi và ĐÃ ĐÁNH GIÁ (COMPLETED, with review)
    IF NOT EXISTS (SELECT 1 FROM bookings WHERE guest_id = v_guest_id AND property_id = 2 AND check_in_date = '2026-10-01') THEN
        INSERT INTO bookings (property_id, guest_id, check_in_date, check_out_date, guests, nightly_price, cleaning_fee, service_fee, subtotal_price, discount_amount, total_price, status, completed_at, completed_by, created_at, updated_at)
        VALUES (2, v_guest_id, '2026-10-01', '2026-10-04', 2, 1200000, 150000, 0, 3750000, 0, 3750000, 'COMPLETED', '2026-10-04 11:30:00+07', v_host_id, '2026-09-28 16:00:00+07', '2026-10-04 11:30:00+07')
        RETURNING id INTO v_b5;

        INSERT INTO payments (booking_id, payment_method, status, amount, transaction_id, paid_at, created_at, updated_at, provider, currency, provider_txn_ref)
        VALUES (v_b5, 'VNPAY', 'SUCCESS', 3750000, 'VNP14802002', '2026-09-28 16:20:00+07', '2026-09-28 16:00:00+07', '2026-09-28 16:20:00+07', 'VNPAY', 'VND', 'TXN_B5_' || v_b5);

        INSERT INTO reviews (booking_id, property_id, guest_id, rating, comment, created_at, updated_at)
        VALUES (v_b5, 2, v_guest_id, 5, 'Kỳ nghỉ tuyệt vời vượt mong đợi! Khách sạn nằm ngay trung tâm phố cổ Mã Mây, đi bộ vài bước là ra Hồ Hoàn Kiếm và chợ đêm. Phòng ốc cực kỳ sạch sẽ, nội thất gỗ ấm cúng và nhân viên hỗ trợ nhiệt tình 24/7. Nhất định sẽ quay lại!', '2026-10-05 14:30:00+07', '2026-10-05 14:30:00+07');
    END IF;

    -- 6. Đang chờ thanh toán (PENDING_PAYMENT)
    IF NOT EXISTS (SELECT 1 FROM bookings WHERE guest_id = v_guest_id AND property_id = 14 AND check_in_date = '2026-12-20') THEN
        INSERT INTO bookings (property_id, guest_id, check_in_date, check_out_date, guests, nightly_price, cleaning_fee, service_fee, subtotal_price, discount_amount, total_price, status, created_at, updated_at)
        VALUES (14, v_guest_id, '2026-12-20', '2026-12-23', 2, 2800000, 350000, 0, 8750000, 0, 8750000, 'PENDING_PAYMENT', '2026-10-28 18:30:00+07', '2026-10-28 18:30:00+07')
        RETURNING id INTO v_b6;

        INSERT INTO payments (booking_id, payment_method, status, amount, created_at, updated_at, provider, currency, provider_txn_ref)
        VALUES (v_b6, 'VNPAY', 'PENDING', 8750000, '2026-10-28 18:30:00+07', '2026-10-28 18:30:00+07', 'VNPAY', 'VND', 'TXN_B6_' || v_b6);
    END IF;

    -- 7. Bị từ chối (REJECTED)
    IF NOT EXISTS (SELECT 1 FROM bookings WHERE guest_id = v_guest_id AND property_id = 12 AND check_in_date = '2026-11-01') THEN
        INSERT INTO bookings (property_id, guest_id, check_in_date, check_out_date, guests, nightly_price, cleaning_fee, service_fee, subtotal_price, discount_amount, total_price, status, created_at, updated_at)
        VALUES (12, v_guest_id, '2026-11-01', '2026-11-03', 4, 1850000, 250000, 0, 3950000, 0, 3950000, 'REJECTED', '2026-10-20 11:00:00+07', '2026-10-20 14:00:00+07')
        RETURNING id INTO v_b7;

        INSERT INTO payments (booking_id, payment_method, status, amount, created_at, updated_at, provider, currency, provider_txn_ref)
        VALUES (v_b7, 'VNPAY', 'CANCELLED', 3950000, '2026-10-20 11:00:00+07', '2026-10-20 14:00:00+07', 'VNPAY', 'VND', 'TXN_B7_' || v_b7);
    END IF;

    RAISE NOTICE 'Demo bookings verified successfully for guest@stayhub.com';
END $$;
