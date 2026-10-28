-- ==============================================================================
-- Seed Data: Famous Destinations (15 Properties, 45 Room Types, Rate Plans & 180-day Inventory)
-- ==============================================================================
DO $$
DECLARE
    v_host_id BIGINT;
    v_prop_id BIGINT;
    v_rt_id BIGINT;
BEGIN
    -- 1. Ensure seed users exist
    INSERT INTO users (email, username, password_hash, full_name, phone, role, status, login_provider)
    VALUES
        ('admin@stayhub.com', 'admin', '$2a$10$OZS5U5gSrWm92Do72.DQp.fRmBiToBI6NKoqxpZrDZz7bIYN7mhvu', 'System Administrator', '0901234567', 'ADMIN', 'ACTIVE', 'LOCAL'),
        ('host@stayhub.com', 'stayhub_host', '$2a$10$OZS5U5gSrWm92Do72.DQp.fRmBiToBI6NKoqxpZrDZz7bIYN7mhvu', 'StayHub SuperHost', '0912345678', 'HOST', 'ACTIVE', 'LOCAL'),
        ('guest@stayhub.com', 'stayhub_guest', '$2a$10$OZS5U5gSrWm92Do72.DQp.fRmBiToBI6NKoqxpZrDZz7bIYN7mhvu', 'Nguyen Van Guest', '0987654321', 'GUEST', 'ACTIVE', 'LOCAL')
    ON CONFLICT (email) DO UPDATE SET password_hash = EXCLUDED.password_hash;

    SELECT id INTO v_host_id FROM users WHERE email = 'host@stayhub.com';

    -- 2. Seed active discount codes
    INSERT INTO discount_codes (code, type, value, cap, minimum_amount, starts_at, ends_at, usage_limit, used_count, per_user_limit, active)
    VALUES
        ('STAYHUB2026', 'PERCENT', 10.00, 500000.00, 1000000.00, CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP + INTERVAL '365 days', 1000, 0, NULL, true),
        ('WELCOME', 'FIXED', 100000.00, NULL, 500000.00, CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP + INTERVAL '365 days', 1000, 0, NULL, true)
    ON CONFLICT (code) DO NOTHING;

    -- ----------------------------------------------------------------------
    -- DESTINATION: Hanoi
    -- ----------------------------------------------------------------------
    -- Property: Khách Sạn & Spa Cổ Điển La Siesta Phố Cổ Hà Nội
    SELECT id INTO v_prop_id FROM properties WHERE title = 'Khách Sạn & Spa Cổ Điển La Siesta Phố Cổ Hà Nội' LIMIT 1;
    IF v_prop_id IS NULL THEN
        INSERT INTO properties (host_id, title, description, address, city, price_per_night, cleaning_fee, max_guests, bedrooms, beds, bathrooms, property_type, sale_mode, timezone, status, rating_avg)
        VALUES (v_host_id, 'Khách Sạn & Spa Cổ Điển La Siesta Phố Cổ Hà Nội', 'Khách sạn phong cách boutique sang trọng tọa lạc tại trung tâm Phố Cổ Hà Nội, chỉ vài bước chân tới Hồ Hoàn Kiếm và chợ đêm.', '94 Mã Mây, Phường Hàng Buồm, Quận Hoàn Kiếm', 'Hanoi', 1200000, 150000, 6, 3, 3, 2, 'HOTEL_ROOM', 'ROOM_BASED', 'Asia/Ho_Chi_Minh', 'ACTIVE', 4.9)
        RETURNING id INTO v_prop_id;
    END IF;

    INSERT INTO property_images (property_id, image_url, display_order, is_cover)
    VALUES (v_prop_id, 'https://images.unsplash.com/photo-1566073771259-6a8506099945?auto=format&fit=crop&w=1200&q=80', 0, TRUE)
    ON CONFLICT (property_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url, is_cover = EXCLUDED.is_cover;
    INSERT INTO property_images (property_id, image_url, display_order, is_cover)
    VALUES (v_prop_id, 'https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?auto=format&fit=crop&w=1200&q=80', 1, FALSE)
    ON CONFLICT (property_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url, is_cover = EXCLUDED.is_cover;
    INSERT INTO property_images (property_id, image_url, display_order, is_cover)
    VALUES (v_prop_id, 'https://images.unsplash.com/photo-1590490360182-c33d57733427?auto=format&fit=crop&w=1200&q=80', 2, FALSE)
    ON CONFLICT (property_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url, is_cover = EXCLUDED.is_cover;

    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Wi-Fi'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Air conditioning'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'TV'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Workspace'
    ON CONFLICT DO NOTHING;

    -- RoomType: Phòng Deluxe Giường Đôi Cổ Điển
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Phòng Deluxe Giường Đôi Cổ Điển' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Phòng Deluxe Giường Đôi Cổ Điển', 8, 2, 3, 'ACTIVE', '{"description": "Phòng Deluxe Giường Đôi Cổ Điển"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Deluxe Double Classic
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 1200000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Phòng Suite Executive Tầm Nhìn Thành Phố
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Phòng Suite Executive Tầm Nhìn Thành Phố' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Phòng Suite Executive Tầm Nhìn Thành Phố', 5, 2, 3, 'ACTIVE', '{"description": "Phòng Suite Executive Tầm Nhìn Thành Phố"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Executive City View Suite
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 1850000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Phòng Suite Gia Đình Di Sản Grand
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Phòng Suite Gia Đình Di Sản Grand' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Phòng Suite Gia Đình Di Sản Grand', 3, 4, 5, 'ACTIVE', '{"description": "Phòng Suite Gia Đình Di Sản Grand"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Grand Heritage Family Suite
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 3200000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- Property: Khu Căn Hộ Dịch Vụ Di Sản Hồ Tây
    SELECT id INTO v_prop_id FROM properties WHERE title = 'Khu Căn Hộ Dịch Vụ Di Sản Hồ Tây' LIMIT 1;
    IF v_prop_id IS NULL THEN
        INSERT INTO properties (host_id, title, description, address, city, price_per_night, cleaning_fee, max_guests, bedrooms, beds, bathrooms, property_type, sale_mode, timezone, status, rating_avg)
        VALUES (v_host_id, 'Khu Căn Hộ Dịch Vụ Di Sản Hồ Tây', 'Khu căn hộ dịch vụ cao cấp hướng trọn tầm nhìn ra Hồ Tây thơ mộng, không gian yên tĩnh, ban công thoáng đãng và tiện nghi chuẩn quốc tế.', '68 Từ Hoa, Phường Quảng An, Quận Tây Hồ', 'Hanoi', 1400000, 200000, 6, 3, 3, 2, 'APARTMENT', 'ROOM_BASED', 'Asia/Ho_Chi_Minh', 'ACTIVE', 4.85)
        RETURNING id INTO v_prop_id;
    END IF;

    INSERT INTO property_images (property_id, image_url, display_order, is_cover)
    VALUES (v_prop_id, 'https://images.unsplash.com/photo-1502672260266-1c1ef2d93688?auto=format&fit=crop&w=1200&q=80', 0, TRUE)
    ON CONFLICT (property_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url, is_cover = EXCLUDED.is_cover;
    INSERT INTO property_images (property_id, image_url, display_order, is_cover)
    VALUES (v_prop_id, 'https://images.unsplash.com/photo-1560448204-e02f11c3d0e2?auto=format&fit=crop&w=1200&q=80', 1, FALSE)
    ON CONFLICT (property_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url, is_cover = EXCLUDED.is_cover;
    INSERT INTO property_images (property_id, image_url, display_order, is_cover)
    VALUES (v_prop_id, 'https://images.unsplash.com/photo-1522708323590-d24dbb6b0267?auto=format&fit=crop&w=1200&q=80', 2, FALSE)
    ON CONFLICT (property_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url, is_cover = EXCLUDED.is_cover;

    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Wi-Fi'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Air conditioning'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Kitchen'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Washer'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Parking'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Workspace'
    ON CONFLICT DO NOTHING;

    -- RoomType: Căn Hộ Studio Ban Công Hướng Hồ Tây
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Căn Hộ Studio Ban Công Hướng Hồ Tây' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Căn Hộ Studio Ban Công Hướng Hồ Tây', 6, 2, 2, 'ACTIVE', '{"description": "Căn Hộ Studio Ban Công Hướng Hồ Tây"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Studio Lake View Balcony
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 1400000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Căn Hộ 1 Phòng Ngủ Hạng Executive
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Căn Hộ 1 Phòng Ngủ Hạng Executive' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Căn Hộ 1 Phòng Ngủ Hạng Executive', 5, 2, 3, 'ACTIVE', '{"description": "Căn Hộ 1 Phòng Ngủ Hạng Executive"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for One-Bedroom Executive Apartment
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 2200000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Căn Hộ Penthouse 2 Phòng Ngủ Toàn Cảnh
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Căn Hộ Penthouse 2 Phòng Ngủ Toàn Cảnh' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Căn Hộ Penthouse 2 Phòng Ngủ Toàn Cảnh', 2, 4, 5, 'ACTIVE', '{"description": "Căn Hộ Penthouse 2 Phòng Ngủ Toàn Cảnh"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Two-Bedroom Panoramic Penthouse
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 4200000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- Property: Biệt Thự Kiến Trúc Pháp Cổ Phố Cổ Hà Nội
    SELECT id INTO v_prop_id FROM properties WHERE title = 'Biệt Thự Kiến Trúc Pháp Cổ Phố Cổ Hà Nội' LIMIT 1;
    IF v_prop_id IS NULL THEN
        INSERT INTO properties (host_id, title, description, address, city, price_per_night, cleaning_fee, max_guests, bedrooms, beds, bathrooms, property_type, sale_mode, timezone, status, rating_avg)
        VALUES (v_host_id, 'Biệt Thự Kiến Trúc Pháp Cổ Phố Cổ Hà Nội', 'Biệt thự kiến trúc Pháp cổ kính nguyên bản hòa quyện nội thất gỗ ấm cúng, khu vườn nhiệt đới xanh mát giữa lòng thủ đô.', '42 Hàng Bè, Phường Hàng Bạc, Quận Hoàn Kiếm', 'Hanoi', 1600000, 250000, 6, 3, 3, 2, 'VILLA', 'ROOM_BASED', 'Asia/Ho_Chi_Minh', 'ACTIVE', 4.88)
        RETURNING id INTO v_prop_id;
    END IF;

    INSERT INTO property_images (property_id, image_url, display_order, is_cover)
    VALUES (v_prop_id, 'https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=1200&q=80', 0, TRUE)
    ON CONFLICT (property_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url, is_cover = EXCLUDED.is_cover;
    INSERT INTO property_images (property_id, image_url, display_order, is_cover)
    VALUES (v_prop_id, 'https://images.unsplash.com/photo-1600596542815-ffad4c1539a9?auto=format&fit=crop&w=1200&q=80', 1, FALSE)
    ON CONFLICT (property_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url, is_cover = EXCLUDED.is_cover;
    INSERT INTO property_images (property_id, image_url, display_order, is_cover)
    VALUES (v_prop_id, 'https://images.unsplash.com/photo-1600607687939-ce8a6c25118c?auto=format&fit=crop&w=1200&q=80', 2, FALSE)
    ON CONFLICT (property_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url, is_cover = EXCLUDED.is_cover;

    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Wi-Fi'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Air conditioning'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Kitchen'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'TV'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Workspace'
    ON CONFLICT DO NOTHING;

    -- RoomType: Phòng Colonial Queen Ban Công Sân Vườn
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Phòng Colonial Queen Ban Công Sân Vườn' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Phòng Colonial Queen Ban Công Sân Vườn', 4, 2, 2, 'ACTIVE', '{"description": "Phòng Colonial Queen Ban Công Sân Vườn"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Colonial Queen Garden Room
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 1600000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Phòng Suite Sân Vườn Cao Cấp
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Phòng Suite Sân Vườn Cao Cấp' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Phòng Suite Sân Vườn Cao Cấp', 3, 2, 3, 'ACTIVE', '{"description": "Phòng Suite Sân Vườn Cao Cấp"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Courtyard Premium Suite
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 2500000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Biệt Thự Di Sản Pháp Nguyên Bản
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Biệt Thự Di Sản Pháp Nguyên Bản' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Biệt Thự Di Sản Pháp Nguyên Bản', 2, 4, 6, 'ACTIVE', '{"description": "Biệt Thự Di Sản Pháp Nguyên Bản"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for French Heritage Master Residence
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 4800000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- ----------------------------------------------------------------------
    -- DESTINATION: Ho Chi Minh
    -- ----------------------------------------------------------------------
    -- Property: Khách Sạn Phong Cách Boutique The Myst Đồng Khởi
    SELECT id INTO v_prop_id FROM properties WHERE title = 'Khách Sạn Phong Cách Boutique The Myst Đồng Khởi' LIMIT 1;
    IF v_prop_id IS NULL THEN
        INSERT INTO properties (host_id, title, description, address, city, price_per_night, cleaning_fee, max_guests, bedrooms, beds, bathrooms, property_type, sale_mode, timezone, status, rating_avg)
        VALUES (v_host_id, 'Khách Sạn Phong Cách Boutique The Myst Đồng Khởi', 'Ốc đảo xanh thanh bình giữa trung tâm Quận 1 sầm uất, nổi bật với ban công phủ đầy cây xanh, bồn tắm gỗ teak và nội thất gỗ tái sinh.', '6-8 Hồ Huấn Nghiệp, Phường Bến Nghé, Quận 1', 'Ho Chi Minh', 1800000, 200000, 6, 3, 3, 2, 'HOTEL_ROOM', 'ROOM_BASED', 'Asia/Ho_Chi_Minh', 'ACTIVE', 4.92)
        RETURNING id INTO v_prop_id;
    END IF;

    INSERT INTO property_images (property_id, image_url, display_order, is_cover)
    VALUES (v_prop_id, 'https://images.unsplash.com/photo-1578683010236-d716f9a3f461?auto=format&fit=crop&w=1200&q=80', 0, TRUE)
    ON CONFLICT (property_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url, is_cover = EXCLUDED.is_cover;
    INSERT INTO property_images (property_id, image_url, display_order, is_cover)
    VALUES (v_prop_id, 'https://images.unsplash.com/photo-1591088398332-8a7791972843?auto=format&fit=crop&w=1200&q=80', 1, FALSE)
    ON CONFLICT (property_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url, is_cover = EXCLUDED.is_cover;
    INSERT INTO property_images (property_id, image_url, display_order, is_cover)
    VALUES (v_prop_id, 'https://images.unsplash.com/photo-1618773928121-c32242e63f39?auto=format&fit=crop&w=1200&q=80', 2, FALSE)
    ON CONFLICT (property_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url, is_cover = EXCLUDED.is_cover;

    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Wi-Fi'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Air conditioning'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Pool'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'TV'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Workspace'
    ON CONFLICT DO NOTHING;

    -- RoomType: Phòng Deluxe View Phố Đồng Khởi
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Phòng Deluxe View Phố Đồng Khởi' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Phòng Deluxe View Phố Đồng Khởi', 10, 2, 2, 'ACTIVE', '{"description": "Phòng Deluxe View Phố Đồng Khởi"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Dong Khoi City View Room
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 1800000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Phòng Suite Hướng Sông Sài Gòn
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Phòng Suite Hướng Sông Sài Gòn' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Phòng Suite Hướng Sông Sài Gòn', 6, 2, 3, 'ACTIVE', '{"description": "Phòng Suite Hướng Sông Sài Gòn"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Saigon River View Suite
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 2850000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Phòng Suite Tổng Thống Sân Vườn Trên Cao
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Phòng Suite Tổng Thống Sân Vườn Trên Cao' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Phòng Suite Tổng Thống Sân Vườn Trên Cao', 2, 4, 5, 'ACTIVE', '{"description": "Phòng Suite Tổng Thống Sân Vườn Trên Cao"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Presidential Garden Terrace Suite
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 5500000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- Property: Căn Hộ Cao Cấp Vinhomes Central Park Landmark 81
    SELECT id INTO v_prop_id FROM properties WHERE title = 'Căn Hộ Cao Cấp Vinhomes Central Park Landmark 81' LIMIT 1;
    IF v_prop_id IS NULL THEN
        INSERT INTO properties (host_id, title, description, address, city, price_per_night, cleaning_fee, max_guests, bedrooms, beds, bathrooms, property_type, sale_mode, timezone, status, rating_avg)
        VALUES (v_host_id, 'Căn Hộ Cao Cấp Vinhomes Central Park Landmark 81', 'Căn hộ chung cư cao cấp liền kề công viên Landmark 81 ven sông Sài Gòn, hồ bơi tràn bờ và đầy đủ tiện nghi sinh hoạt gia đình.', '208 Nguyễn Hữu Cảnh, Phường 22, Quận Bình Thạnh', 'Ho Chi Minh', 1100000, 180000, 6, 3, 3, 2, 'APARTMENT', 'ROOM_BASED', 'Asia/Ho_Chi_Minh', 'ACTIVE', 4.82)
        RETURNING id INTO v_prop_id;
    END IF;

    INSERT INTO property_images (property_id, image_url, display_order, is_cover)
    VALUES (v_prop_id, 'https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?auto=format&fit=crop&w=1200&q=80', 0, TRUE)
    ON CONFLICT (property_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url, is_cover = EXCLUDED.is_cover;
    INSERT INTO property_images (property_id, image_url, display_order, is_cover)
    VALUES (v_prop_id, 'https://images.unsplash.com/photo-1512917774080-9991f1c4c750?auto=format&fit=crop&w=1200&q=80', 1, FALSE)
    ON CONFLICT (property_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url, is_cover = EXCLUDED.is_cover;
    INSERT INTO property_images (property_id, image_url, display_order, is_cover)
    VALUES (v_prop_id, 'https://images.unsplash.com/photo-1513694203232-719a280e022f?auto=format&fit=crop&w=1200&q=80', 2, FALSE)
    ON CONFLICT (property_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url, is_cover = EXCLUDED.is_cover;

    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Wi-Fi'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Air conditioning'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Kitchen'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Pool'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Washer'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'TV'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Parking'
    ON CONFLICT DO NOTHING;

    -- RoomType: Căn Hộ 1 Phòng Ngủ Đô Thị Hiện Đại
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Căn Hộ 1 Phòng Ngủ Đô Thị Hiện Đại' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Căn Hộ 1 Phòng Ngủ Đô Thị Hiện Đại', 8, 2, 2, 'ACTIVE', '{"description": "Căn Hộ 1 Phòng Ngủ Đô Thị Hiện Đại"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for One-Bedroom Modern City Suite
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 1100000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Căn Hộ Gia Đình 2 Phòng Ngủ View Sông
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Căn Hộ Gia Đình 2 Phòng Ngủ View Sông' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Căn Hộ Gia Đình 2 Phòng Ngủ View Sông', 6, 4, 5, 'ACTIVE', '{"description": "Căn Hộ Gia Đình 2 Phòng Ngủ View Sông"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Two-Bedroom Family Riverview Suite
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 2100000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Căn Hộ Penthouse 3 Phòng Ngủ Trên Cao
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Căn Hộ Penthouse 3 Phòng Ngủ Trên Cao' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Căn Hộ Penthouse 3 Phòng Ngủ Trên Cao', 2, 6, 7, 'ACTIVE', '{"description": "Căn Hộ Penthouse 3 Phòng Ngủ Trên Cao"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Three-Bedroom Sky Penthouse
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 3900000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- Property: Biệt Thự Sinh Thái Ven Sông Sài Gòn Thảo Điền
    SELECT id INTO v_prop_id FROM properties WHERE title = 'Biệt Thự Sinh Thái Ven Sông Sài Gòn Thảo Điền' LIMIT 1;
    IF v_prop_id IS NULL THEN
        INSERT INTO properties (host_id, title, description, address, city, price_per_night, cleaning_fee, max_guests, bedrooms, beds, bathrooms, property_type, sale_mode, timezone, status, rating_avg)
        VALUES (v_host_id, 'Biệt Thự Sinh Thái Ven Sông Sài Gòn Thảo Điền', 'Biệt thự nghỉ dưỡng phong cách nhiệt đới ven sông Sài Gòn tại Thảo Điền, sân vườn thoáng rộng, hồ bơi riêng và bếp BBQ ngoài trời.', '189 Nguyễn Văn Hưởng, Thảo Điền, TP. Thủ Đức', 'Ho Chi Minh', 1900000, 300000, 6, 3, 3, 2, 'VILLA', 'ROOM_BASED', 'Asia/Ho_Chi_Minh', 'ACTIVE', 4.89)
        RETURNING id INTO v_prop_id;
    END IF;

    INSERT INTO property_images (property_id, image_url, display_order, is_cover)
    VALUES (v_prop_id, 'https://images.unsplash.com/photo-1580587771525-78b9dba3b914?auto=format&fit=crop&w=1200&q=80', 0, TRUE)
    ON CONFLICT (property_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url, is_cover = EXCLUDED.is_cover;
    INSERT INTO property_images (property_id, image_url, display_order, is_cover)
    VALUES (v_prop_id, 'https://images.unsplash.com/photo-1512918728675-ed5a9ecdebfd?auto=format&fit=crop&w=1200&q=80', 1, FALSE)
    ON CONFLICT (property_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url, is_cover = EXCLUDED.is_cover;
    INSERT INTO property_images (property_id, image_url, display_order, is_cover)
    VALUES (v_prop_id, 'https://images.unsplash.com/photo-1613977257363-707ba9348227?auto=format&fit=crop&w=1200&q=80', 2, FALSE)
    ON CONFLICT (property_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url, is_cover = EXCLUDED.is_cover;

    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Wi-Fi'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Air conditioning'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Kitchen'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Pool'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Parking'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Workspace'
    ON CONFLICT DO NOTHING;

    -- RoomType: Phòng Deluxe Ban Công Hướng Vườn
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Phòng Deluxe Ban Công Hướng Vườn' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Phòng Deluxe Ban Công Hướng Vườn', 5, 2, 2, 'ACTIVE', '{"description": "Phòng Deluxe Ban Công Hướng Vườn"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Garden View Deluxe Room
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 1900000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Phòng Suite King Cao Cấp Cạnh Hồ Bơi
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Phòng Suite King Cao Cấp Cạnh Hồ Bơi' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Phòng Suite King Cao Cấp Cạnh Hồ Bơi', 4, 2, 3, 'ACTIVE', '{"description": "Phòng Suite King Cao Cấp Cạnh Hồ Bơi"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Poolside Luxury King Suite
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 3200000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Biệt Thự Master Ven Sông Sài Gòn
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Biệt Thự Master Ven Sông Sài Gòn' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Biệt Thự Master Ven Sông Sài Gòn', 2, 4, 6, 'ACTIVE', '{"description": "Biệt Thự Master Ven Sông Sài Gòn"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Riverfront Master Residence Suite
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 5800000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- ----------------------------------------------------------------------
    -- DESTINATION: Da Nang
    -- ----------------------------------------------------------------------
    -- Property: Khu Nghỉ Dưỡng Cao Cấp Ven Biển Mỹ Khê
    SELECT id INTO v_prop_id FROM properties WHERE title = 'Khu Nghỉ Dưỡng Cao Cấp Ven Biển Mỹ Khê' LIMIT 1;
    IF v_prop_id IS NULL THEN
        INSERT INTO properties (host_id, title, description, address, city, price_per_night, cleaning_fee, max_guests, bedrooms, beds, bathrooms, property_type, sale_mode, timezone, status, rating_avg)
        VALUES (v_host_id, 'Khu Nghỉ Dưỡng Cao Cấp Ven Biển Mỹ Khê', 'Khu nghỉ dưỡng 5 sao sát biển Mỹ Khê, hồ bơi vô cực ngắm trọn bình minh biển Đông, spa cao cấp và nhà hàng hải sản phong phú.', '256 Võ Nguyên Giáp, Phường Phước Mỹ, Quận Sơn Trà', 'Da Nang', 2200000, 250000, 6, 3, 3, 2, 'RESORT', 'ROOM_BASED', 'Asia/Ho_Chi_Minh', 'ACTIVE', 4.95)
        RETURNING id INTO v_prop_id;
    END IF;

    INSERT INTO property_images (property_id, image_url, display_order, is_cover)
    VALUES (v_prop_id, 'https://images.unsplash.com/photo-1540555700478-4be289fbecef?auto=format&fit=crop&w=1200&q=80', 0, TRUE)
    ON CONFLICT (property_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url, is_cover = EXCLUDED.is_cover;
    INSERT INTO property_images (property_id, image_url, display_order, is_cover)
    VALUES (v_prop_id, 'https://images.unsplash.com/photo-1571896349842-33c89424de2d?auto=format&fit=crop&w=1200&q=80', 1, FALSE)
    ON CONFLICT (property_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url, is_cover = EXCLUDED.is_cover;
    INSERT INTO property_images (property_id, image_url, display_order, is_cover)
    VALUES (v_prop_id, 'https://images.unsplash.com/photo-1584132967334-10e028bd69f7?auto=format&fit=crop&w=1200&q=80', 2, FALSE)
    ON CONFLICT (property_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url, is_cover = EXCLUDED.is_cover;

    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Wi-Fi'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Air conditioning'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Pool'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Parking'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'TV'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Workspace'
    ON CONFLICT DO NOTHING;

    -- RoomType: Phòng King Deluxe Hướng Trọn Biển Đông
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Phòng King Deluxe Hướng Trọn Biển Đông' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Phòng King Deluxe Hướng Trọn Biển Đông', 12, 2, 3, 'ACTIVE', '{"description": "Phòng King Deluxe Hướng Trọn Biển Đông"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Ocean Deluxe King Room
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 2200000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Phòng Suite Premier Sát Biển Mỹ Khê
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Phòng Suite Premier Sát Biển Mỹ Khê' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Phòng Suite Premier Sát Biển Mỹ Khê', 8, 2, 3, 'ACTIVE', '{"description": "Phòng Suite Premier Sát Biển Mỹ Khê"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Premier Oceanfront Suite
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 3500000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Căn Hộ Duplex Gia Đình Mặt Biển
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Căn Hộ Duplex Gia Đình Mặt Biển' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Căn Hộ Duplex Gia Đình Mặt Biển', 4, 4, 6, 'ACTIVE', '{"description": "Căn Hộ Duplex Gia Đình Mặt Biển"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Beachfront Family Duplex Suite
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 6200000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- Property: Căn Hộ Khách Sạn Cao Cấp Bờ Sông Hàn
    SELECT id INTO v_prop_id FROM properties WHERE title = 'Căn Hộ Khách Sạn Cao Cấp Bờ Sông Hàn' LIMIT 1;
    IF v_prop_id IS NULL THEN
        INSERT INTO properties (host_id, title, description, address, city, price_per_night, cleaning_fee, max_guests, bedrooms, beds, bathrooms, property_type, sale_mode, timezone, status, rating_avg)
        VALUES (v_host_id, 'Căn Hộ Khách Sạn Cao Cấp Bờ Sông Hàn', 'Căn hộ khách sạn cao cấp bên bờ sông Hàn thơ mộng, view trực diện Cầu Rồng phun lửa, tiện nghi hiện đại và quầy bar tầng thượng.', '120 Bạch Đằng, Phường Hải Châu 1, Quận Hải Châu', 'Da Nang', 1150000, 150000, 6, 3, 3, 2, 'APARTMENT', 'ROOM_BASED', 'Asia/Ho_Chi_Minh', 'ACTIVE', 4.8)
        RETURNING id INTO v_prop_id;
    END IF;

    INSERT INTO property_images (property_id, image_url, display_order, is_cover)
    VALUES (v_prop_id, 'https://images.unsplash.com/photo-1522708323590-d24dbb6b0267?auto=format&fit=crop&w=1200&q=80', 0, TRUE)
    ON CONFLICT (property_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url, is_cover = EXCLUDED.is_cover;
    INSERT INTO property_images (property_id, image_url, display_order, is_cover)
    VALUES (v_prop_id, 'https://images.unsplash.com/photo-1502672260266-1c1ef2d93688?auto=format&fit=crop&w=1200&q=80', 1, FALSE)
    ON CONFLICT (property_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url, is_cover = EXCLUDED.is_cover;
    INSERT INTO property_images (property_id, image_url, display_order, is_cover)
    VALUES (v_prop_id, 'https://images.unsplash.com/photo-1560448204-e02f11c3d0e2?auto=format&fit=crop&w=1200&q=80', 2, FALSE)
    ON CONFLICT (property_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url, is_cover = EXCLUDED.is_cover;

    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Wi-Fi'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Air conditioning'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Kitchen'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'TV'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Washer'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Workspace'
    ON CONFLICT DO NOTHING;

    -- RoomType: Căn Hộ Studio View Trực Diện Cầu Rồng
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Căn Hộ Studio View Trực Diện Cầu Rồng' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Căn Hộ Studio View Trực Diện Cầu Rồng', 8, 2, 2, 'ACTIVE', '{"description": "Căn Hộ Studio View Trực Diện Cầu Rồng"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Studio Dragon Bridge View
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 1150000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Phòng Suite Executive View Sông Hàn
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Phòng Suite Executive View Sông Hàn' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Phòng Suite Executive View Sông Hàn', 6, 2, 3, 'ACTIVE', '{"description": "Phòng Suite Executive View Sông Hàn"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Executive Riverview Suite
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 1950000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Căn Hộ Grand Skyline Bờ Sông Hàn
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Căn Hộ Grand Skyline Bờ Sông Hàn' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Căn Hộ Grand Skyline Bờ Sông Hàn', 3, 4, 5, 'ACTIVE', '{"description": "Căn Hộ Grand Skyline Bờ Sông Hàn"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Grand Han Skyline Apartment
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 3400000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- Property: Homestay Nghỉ Dưỡng Sinh Thái Bán Đảo Sơn Trà
    SELECT id INTO v_prop_id FROM properties WHERE title = 'Homestay Nghỉ Dưỡng Sinh Thái Bán Đảo Sơn Trà' LIMIT 1;
    IF v_prop_id IS NULL THEN
        INSERT INTO properties (host_id, title, description, address, city, price_per_night, cleaning_fee, max_guests, bedrooms, beds, bathrooms, property_type, sale_mode, timezone, status, rating_avg)
        VALUES (v_host_id, 'Homestay Nghỉ Dưỡng Sinh Thái Bán Đảo Sơn Trà', 'Khu homestay sinh thái nép mình dưới chân núi Sơn Trà, không khí trong lành thoang thoảng gió biển, view đồi rừng khoáng đạt.', 'Bãi Bụt, Bán đảo Sơn Trà, Quận Sơn Trà', 'Da Nang', 850000, 100000, 6, 3, 3, 2, 'HOMESTAY', 'ROOM_BASED', 'Asia/Ho_Chi_Minh', 'ACTIVE', 4.75)
        RETURNING id INTO v_prop_id;
    END IF;

    INSERT INTO property_images (property_id, image_url, display_order, is_cover)
    VALUES (v_prop_id, 'https://images.unsplash.com/photo-1587061949409-02df41d5e562?auto=format&fit=crop&w=1200&q=80', 0, TRUE)
    ON CONFLICT (property_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url, is_cover = EXCLUDED.is_cover;
    INSERT INTO property_images (property_id, image_url, display_order, is_cover)
    VALUES (v_prop_id, 'https://images.unsplash.com/photo-1510798831971-661eb04b3739?auto=format&fit=crop&w=1200&q=80', 1, FALSE)
    ON CONFLICT (property_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url, is_cover = EXCLUDED.is_cover;
    INSERT INTO property_images (property_id, image_url, display_order, is_cover)
    VALUES (v_prop_id, 'https://images.unsplash.com/photo-1499793983690-e29da59ef1c2?auto=format&fit=crop&w=1200&q=80', 2, FALSE)
    ON CONFLICT (property_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url, is_cover = EXCLUDED.is_cover;

    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Wi-Fi'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Air conditioning'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Kitchen'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Parking'
    ON CONFLICT DO NOTHING;

    -- RoomType: Bungalow Rừng Xanh Sơn Trà
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Bungalow Rừng Xanh Sơn Trà' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Bungalow Rừng Xanh Sơn Trà', 6, 2, 2, 'ACTIVE', '{"description": "Bungalow Rừng Xanh Sơn Trà"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Bungalow Rừng Xanh Sơn Trà
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 850000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Chalet View Biển Bình Minh
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Chalet View Biển Bình Minh' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Chalet View Biển Bình Minh', 5, 2, 3, 'ACTIVE', '{"description": "Chalet View Biển Bình Minh"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Chalet View Biển Bình Minh
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 1450000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Căn Hộ Gia Đình Nhà Gỗ Mộc
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Căn Hộ Gia Đình Nhà Gỗ Mộc' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Căn Hộ Gia Đình Nhà Gỗ Mộc', 3, 4, 5, 'ACTIVE', '{"description": "Căn Hộ Gia Đình Nhà Gỗ Mộc"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Căn Hộ Gia Đình Nhà Gỗ Mộc
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 2600000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- ----------------------------------------------------------------------
    -- DESTINATION: Da Lat
    -- ----------------------------------------------------------------------
    -- Property: Khu Nghỉ Dưỡng Di Sản Rừng Thông Đà Lạt
    SELECT id INTO v_prop_id FROM properties WHERE title = 'Khu Nghỉ Dưỡng Di Sản Rừng Thông Đà Lạt' LIMIT 1;
    IF v_prop_id IS NULL THEN
        INSERT INTO properties (host_id, title, description, address, city, price_per_night, cleaning_fee, max_guests, bedrooms, beds, bathrooms, property_type, sale_mode, timezone, status, rating_avg)
        VALUES (v_host_id, 'Khu Nghỉ Dưỡng Di Sản Rừng Thông Đà Lạt', 'Resort phong cách biệt thự Pháp cổ ẩn giữa rừng thông bạt ngàn Đà Lạt, lò sưởi ấm cúng, trà chiều sân vườn ngắm sương mù lãng mạn.', '02 Trần Phú, Phường 3, TP. Đà Lạt', 'Da Lat', 1600000, 200000, 6, 3, 3, 2, 'RESORT', 'ROOM_BASED', 'Asia/Ho_Chi_Minh', 'ACTIVE', 4.91)
        RETURNING id INTO v_prop_id;
    END IF;

    INSERT INTO property_images (property_id, image_url, display_order, is_cover)
    VALUES (v_prop_id, 'https://images.unsplash.com/photo-1518780664697-55e3ad937233?auto=format&fit=crop&w=1200&q=80', 0, TRUE)
    ON CONFLICT (property_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url, is_cover = EXCLUDED.is_cover;
    INSERT INTO property_images (property_id, image_url, display_order, is_cover)
    VALUES (v_prop_id, 'https://images.unsplash.com/photo-1542314831-068cd1dbfeeb?auto=format&fit=crop&w=1200&q=80', 1, FALSE)
    ON CONFLICT (property_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url, is_cover = EXCLUDED.is_cover;
    INSERT INTO property_images (property_id, image_url, display_order, is_cover)
    VALUES (v_prop_id, 'https://images.unsplash.com/photo-1571003123894-1f0594d2b5d9?auto=format&fit=crop&w=1200&q=80', 2, FALSE)
    ON CONFLICT (property_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url, is_cover = EXCLUDED.is_cover;

    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Wi-Fi'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Parking'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'TV'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Workspace'
    ON CONFLICT DO NOTHING;

    -- RoomType: Phòng Deluxe Đồi Thông Lãng Mạn
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Phòng Deluxe Đồi Thông Lãng Mạn' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Phòng Deluxe Đồi Thông Lãng Mạn', 8, 2, 3, 'ACTIVE', '{"description": "Phòng Deluxe Đồi Thông Lãng Mạn"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Pine Hill Deluxe Room
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 1600000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Phòng Suite Di Sản View Thung Lũng
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Phòng Suite Di Sản View Thung Lũng' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Phòng Suite Di Sản View Thung Lũng', 6, 2, 3, 'ACTIVE', '{"description": "Phòng Suite Di Sản View Thung Lũng"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Heritage Valley View Suite
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 2600000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Chalet Rừng Thông Gia Đình
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Chalet Rừng Thông Gia Đình' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Chalet Rừng Thông Gia Đình', 3, 4, 5, 'ACTIVE', '{"description": "Chalet Rừng Thông Gia Đình"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Chalet Rừng Thông Gia Đình
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 4500000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- Property: Biệt Thự & Spa Nghỉ Dưỡng Hồ Tuyền Lâm
    SELECT id INTO v_prop_id FROM properties WHERE title = 'Biệt Thự & Spa Nghỉ Dưỡng Hồ Tuyền Lâm' LIMIT 1;
    IF v_prop_id IS NULL THEN
        INSERT INTO properties (host_id, title, description, address, city, price_per_night, cleaning_fee, max_guests, bedrooms, beds, bathrooms, property_type, sale_mode, timezone, status, rating_avg)
        VALUES (v_host_id, 'Biệt Thự & Spa Nghỉ Dưỡng Hồ Tuyền Lâm', 'Quần thể villa mang hơi thở vùng núi Thụy Sĩ bên bờ hồ Tuyền Lâm phẳng lặng, bến du thuyền riêng và không gian nghỉ dưỡng tuyệt hảo.', 'Khu du lịch Hồ Tuyền Lâm, Phường 4, TP. Đà Lạt', 'Da Lat', 1850000, 250000, 6, 3, 3, 2, 'VILLA', 'ROOM_BASED', 'Asia/Ho_Chi_Minh', 'ACTIVE', 4.93)
        RETURNING id INTO v_prop_id;
    END IF;

    INSERT INTO property_images (property_id, image_url, display_order, is_cover)
    VALUES (v_prop_id, 'https://images.unsplash.com/photo-1613977257363-707ba9348227?auto=format&fit=crop&w=1200&q=80', 0, TRUE)
    ON CONFLICT (property_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url, is_cover = EXCLUDED.is_cover;
    INSERT INTO property_images (property_id, image_url, display_order, is_cover)
    VALUES (v_prop_id, 'https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=1200&q=80', 1, FALSE)
    ON CONFLICT (property_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url, is_cover = EXCLUDED.is_cover;
    INSERT INTO property_images (property_id, image_url, display_order, is_cover)
    VALUES (v_prop_id, 'https://images.unsplash.com/photo-1600596542815-ffad4c1539a9?auto=format&fit=crop&w=1200&q=80', 2, FALSE)
    ON CONFLICT (property_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url, is_cover = EXCLUDED.is_cover;

    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Wi-Fi'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Kitchen'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Pool'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Parking'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'TV'
    ON CONFLICT DO NOTHING;

    -- RoomType: Phòng Superior Ban Công View Hồ Tuyền Lâm
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Phòng Superior Ban Công View Hồ Tuyền Lâm' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Phòng Superior Ban Công View Hồ Tuyền Lâm', 7, 2, 2, 'ACTIVE', '{"description": "Phòng Superior Ban Công View Hồ Tuyền Lâm"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Lakeview Superior Balcony Room
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 1850000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Phòng Suite Lãng Mạn Sương Mờ Hồ Tuyền Lâm
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Phòng Suite Lãng Mạn Sương Mờ Hồ Tuyền Lâm' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Phòng Suite Lãng Mạn Sương Mờ Hồ Tuyền Lâm', 5, 2, 3, 'ACTIVE', '{"description": "Phòng Suite Lãng Mạn Sương Mờ Hồ Tuyền Lâm"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Romantic Lake Mist Suite
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 2950000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Biệt Thự Gia Đình Ven Hồ Tuyền Lâm
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Biệt Thự Gia Đình Ven Hồ Tuyền Lâm' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Biệt Thự Gia Đình Ven Hồ Tuyền Lâm', 2, 4, 6, 'ACTIVE', '{"description": "Biệt Thự Gia Đình Ven Hồ Tuyền Lâm"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Lakeside Family Villa Unit
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 5200000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- Property: Homestay Vintage Gỗ Mộc Le Petit Đà Lạt
    SELECT id INTO v_prop_id FROM properties WHERE title = 'Homestay Vintage Gỗ Mộc Le Petit Đà Lạt' LIMIT 1;
    IF v_prop_id IS NULL THEN
        INSERT INTO properties (host_id, title, description, address, city, price_per_night, cleaning_fee, max_guests, bedrooms, beds, bathrooms, property_type, sale_mode, timezone, status, rating_avg)
        VALUES (v_host_id, 'Homestay Vintage Gỗ Mộc Le Petit Đà Lạt', 'Ngôi nhà gỗ vintage ngập tràn hoa cẩm tú cầu, hiên nhà thưởng cà phê ngắm đồi dã quỳ và thung lũng đèn đêm huyền ảo.', '88 Mai Anh Đào, Phường 8, TP. Đà Lạt', 'Da Lat', 750000, 80000, 6, 3, 3, 2, 'HOMESTAY', 'ROOM_BASED', 'Asia/Ho_Chi_Minh', 'ACTIVE', 4.84)
        RETURNING id INTO v_prop_id;
    END IF;

    INSERT INTO property_images (property_id, image_url, display_order, is_cover)
    VALUES (v_prop_id, 'https://images.unsplash.com/photo-1520250497591-112f2f40a3f4?auto=format&fit=crop&w=1200&q=80', 0, TRUE)
    ON CONFLICT (property_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url, is_cover = EXCLUDED.is_cover;
    INSERT INTO property_images (property_id, image_url, display_order, is_cover)
    VALUES (v_prop_id, 'https://images.unsplash.com/photo-1561501900-3701fa6a0864?auto=format&fit=crop&w=1200&q=80', 1, FALSE)
    ON CONFLICT (property_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url, is_cover = EXCLUDED.is_cover;
    INSERT INTO property_images (property_id, image_url, display_order, is_cover)
    VALUES (v_prop_id, 'https://images.unsplash.com/photo-1587061949409-02df41d5e562?auto=format&fit=crop&w=1200&q=80', 2, FALSE)
    ON CONFLICT (property_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url, is_cover = EXCLUDED.is_cover;

    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Wi-Fi'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Kitchen'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Parking'
    ON CONFLICT DO NOTHING;

    -- RoomType: Phòng Áp Mái Vintage Gỗ Thông
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Phòng Áp Mái Vintage Gỗ Thông' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Phòng Áp Mái Vintage Gỗ Thông', 6, 2, 2, 'ACTIVE', '{"description": "Phòng Áp Mái Vintage Gỗ Thông"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Phòng Áp Mái Vintage Gỗ Thông
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 750000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Phòng Ban Công Đồi Hoa Cẩm Tú Cầu
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Phòng Ban Công Đồi Hoa Cẩm Tú Cầu' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Phòng Ban Công Đồi Hoa Cẩm Tú Cầu', 5, 2, 3, 'ACTIVE', '{"description": "Phòng Ban Công Đồi Hoa Cẩm Tú Cầu"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Phòng Ban Công Đồi Hoa Cẩm Tú Cầu
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 1200000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Phòng Gia Đình Ấm Cúng Lò Sưởi
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Phòng Gia Đình Ấm Cúng Lò Sưởi' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Phòng Gia Đình Ấm Cúng Lò Sưởi', 3, 4, 5, 'ACTIVE', '{"description": "Phòng Gia Đình Ấm Cúng Lò Sưởi"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Phòng Gia Đình Ấm Cúng Lò Sưởi
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 2100000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- ----------------------------------------------------------------------
    -- DESTINATION: Phu Quoc
    -- ----------------------------------------------------------------------
    -- Property: Khu Nghỉ Dưỡng 5 Sao Bãi Khem Phú Quốc
    SELECT id INTO v_prop_id FROM properties WHERE title = 'Khu Nghỉ Dưỡng 5 Sao Bãi Khem Phú Quốc' LIMIT 1;
    IF v_prop_id IS NULL THEN
        INSERT INTO properties (host_id, title, description, address, city, price_per_night, cleaning_fee, max_guests, bedrooms, beds, bathrooms, property_type, sale_mode, timezone, status, rating_avg)
        VALUES (v_host_id, 'Khu Nghỉ Dưỡng 5 Sao Bãi Khem Phú Quốc', 'Resort biển danh giá bên Bãi Khem cát trắng như kem, kiến trúc đại học Pháp độc bản, hồ bơi vỏ sò biểu tượng và bãi tắm riêng biệt.', 'Bãi Khem, Phường An Thới, TP. Phú Quốc', 'Phu Quoc', 2800000, 350000, 6, 3, 3, 2, 'RESORT', 'ROOM_BASED', 'Asia/Ho_Chi_Minh', 'ACTIVE', 4.97)
        RETURNING id INTO v_prop_id;
    END IF;

    INSERT INTO property_images (property_id, image_url, display_order, is_cover)
    VALUES (v_prop_id, 'https://images.unsplash.com/photo-1610641818989-c2051b5e2cfd?auto=format&fit=crop&w=1200&q=80', 0, TRUE)
    ON CONFLICT (property_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url, is_cover = EXCLUDED.is_cover;
    INSERT INTO property_images (property_id, image_url, display_order, is_cover)
    VALUES (v_prop_id, 'https://images.unsplash.com/photo-1540555700478-4be289fbecef?auto=format&fit=crop&w=1200&q=80', 1, FALSE)
    ON CONFLICT (property_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url, is_cover = EXCLUDED.is_cover;
    INSERT INTO property_images (property_id, image_url, display_order, is_cover)
    VALUES (v_prop_id, 'https://images.unsplash.com/photo-1571896349842-33c89424de2d?auto=format&fit=crop&w=1200&q=80', 2, FALSE)
    ON CONFLICT (property_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url, is_cover = EXCLUDED.is_cover;

    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Wi-Fi'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Air conditioning'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Pool'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Parking'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'TV'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Workspace'
    ON CONFLICT DO NOTHING;

    -- RoomType: Phòng Deluxe Biển Ngọc Bãi Khem
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Phòng Deluxe Biển Ngọc Bãi Khem' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Phòng Deluxe Biển Ngọc Bãi Khem', 15, 2, 3, 'ACTIVE', '{"description": "Phòng Deluxe Biển Ngọc Bãi Khem"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Emerald Ocean Deluxe Room
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 2800000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Phòng Suite Pavilion Premier View Vịnh Biển
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Phòng Suite Pavilion Premier View Vịnh Biển' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Phòng Suite Pavilion Premier View Vịnh Biển', 8, 2, 3, 'ACTIVE', '{"description": "Phòng Suite Pavilion Premier View Vịnh Biển"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Bayview Premier Pavilion Suite
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 4600000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Biệt Thự Hồ Bơi Riêng Sát Biển Bãi Khem
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Biệt Thự Hồ Bơi Riêng Sát Biển Bãi Khem' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Biệt Thự Hồ Bơi Riêng Sát Biển Bãi Khem', 3, 4, 6, 'ACTIVE', '{"description": "Biệt Thự Hồ Bơi Riêng Sát Biển Bãi Khem"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Beachfront Private Pool Villa Suite
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 8900000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- Property: Biệt Thự Nghỉ Dưỡng Hoàng Hôn Sunset Sanato Phú Quốc
    SELECT id INTO v_prop_id FROM properties WHERE title = 'Biệt Thự Nghỉ Dưỡng Hoàng Hôn Sunset Sanato Phú Quốc' LIMIT 1;
    IF v_prop_id IS NULL THEN
        INSERT INTO properties (host_id, title, description, address, city, price_per_night, cleaning_fee, max_guests, bedrooms, beds, bathrooms, property_type, sale_mode, timezone, status, rating_avg)
        VALUES (v_host_id, 'Biệt Thự Nghỉ Dưỡng Hoàng Hôn Sunset Sanato Phú Quốc', 'Quần thể biệt thự nghỉ dưỡng bên bờ biển Bãi Trường nổi tiếng với hoàng hôn rực rỡ, bãi biển dài và không gian tiệc cocktail lãng mạn.', 'Khu Bãi Trường, Xã Dương Tơ, TP. Phú Quốc', 'Phu Quoc', 1750000, 200000, 6, 3, 3, 2, 'VILLA', 'ROOM_BASED', 'Asia/Ho_Chi_Minh', 'ACTIVE', 4.86)
        RETURNING id INTO v_prop_id;
    END IF;

    INSERT INTO property_images (property_id, image_url, display_order, is_cover)
    VALUES (v_prop_id, 'https://images.unsplash.com/photo-1580587771525-78b9dba3b914?auto=format&fit=crop&w=1200&q=80', 0, TRUE)
    ON CONFLICT (property_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url, is_cover = EXCLUDED.is_cover;
    INSERT INTO property_images (property_id, image_url, display_order, is_cover)
    VALUES (v_prop_id, 'https://images.unsplash.com/photo-1512918728675-ed5a9ecdebfd?auto=format&fit=crop&w=1200&q=80', 1, FALSE)
    ON CONFLICT (property_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url, is_cover = EXCLUDED.is_cover;
    INSERT INTO property_images (property_id, image_url, display_order, is_cover)
    VALUES (v_prop_id, 'https://images.unsplash.com/photo-1613977257363-707ba9348227?auto=format&fit=crop&w=1200&q=80', 2, FALSE)
    ON CONFLICT (property_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url, is_cover = EXCLUDED.is_cover;

    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Wi-Fi'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Air conditioning'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Kitchen'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Pool'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Parking'
    ON CONFLICT DO NOTHING;

    -- RoomType: Phòng Queen Deluxe View Hoàng Hôn Bãi Trường
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Phòng Queen Deluxe View Hoàng Hôn Bãi Trường' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Phòng Queen Deluxe View Hoàng Hôn Bãi Trường', 8, 2, 2, 'ACTIVE', '{"description": "Phòng Queen Deluxe View Hoàng Hôn Bãi Trường"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Sunset Deluxe Queen Room
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 1750000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Biệt Thự Sân Vườn Ven Biển Sunset Sanato
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Biệt Thự Sân Vườn Ven Biển Sunset Sanato' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Biệt Thự Sân Vườn Ven Biển Sunset Sanato', 5, 2, 3, 'ACTIVE', '{"description": "Biệt Thự Sân Vườn Ven Biển Sunset Sanato"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Beachside Garden Villa Suite
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 2950000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Biệt Thự 3 Phòng Ngủ Toàn Cảnh Hoàng Hôn Biển
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Biệt Thự 3 Phòng Ngủ Toàn Cảnh Hoàng Hôn Biển' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Biệt Thự 3 Phòng Ngủ Toàn Cảnh Hoàng Hôn Biển', 2, 6, 8, 'ACTIVE', '{"description": "Biệt Thự 3 Phòng Ngủ Toàn Cảnh Hoàng Hôn Biển"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Three-Bedroom Ocean Panorama Villa
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 6500000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- Property: Khách Sạn Trung Tâm Thị Trấn Dương Đông Phú Quốc
    SELECT id INTO v_prop_id FROM properties WHERE title = 'Khách Sạn Trung Tâm Thị Trấn Dương Đông Phú Quốc' LIMIT 1;
    IF v_prop_id IS NULL THEN
        INSERT INTO properties (host_id, title, description, address, city, price_per_night, cleaning_fee, max_guests, bedrooms, beds, bathrooms, property_type, sale_mode, timezone, status, rating_avg)
        VALUES (v_host_id, 'Khách Sạn Trung Tâm Thị Trấn Dương Đông Phú Quốc', 'Khách sạn phong cách hiện đại ngay trung tâm thị trấn Dương Đông, cách chợ đêm ẩm thực Phú Quốc 3 phút đi bộ, bể bơi tầng thượng.', '145 Trần Hưng Đạo, Phường Dương Đông, TP. Phú Quốc', 'Phu Quoc', 950000, 100000, 6, 3, 3, 2, 'HOTEL_ROOM', 'ROOM_BASED', 'Asia/Ho_Chi_Minh', 'ACTIVE', 4.79)
        RETURNING id INTO v_prop_id;
    END IF;

    INSERT INTO property_images (property_id, image_url, display_order, is_cover)
    VALUES (v_prop_id, 'https://images.unsplash.com/photo-1590490360182-c33d57733427?auto=format&fit=crop&w=1200&q=80', 0, TRUE)
    ON CONFLICT (property_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url, is_cover = EXCLUDED.is_cover;
    INSERT INTO property_images (property_id, image_url, display_order, is_cover)
    VALUES (v_prop_id, 'https://images.unsplash.com/photo-1566073771259-6a8506099945?auto=format&fit=crop&w=1200&q=80', 1, FALSE)
    ON CONFLICT (property_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url, is_cover = EXCLUDED.is_cover;
    INSERT INTO property_images (property_id, image_url, display_order, is_cover)
    VALUES (v_prop_id, 'https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?auto=format&fit=crop&w=1200&q=80', 2, FALSE)
    ON CONFLICT (property_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url, is_cover = EXCLUDED.is_cover;

    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Wi-Fi'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Air conditioning'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Pool'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'TV'
    ON CONFLICT DO NOTHING;
    INSERT INTO property_amenities (property_id, amenity_id)
    SELECT v_prop_id, id FROM amenities WHERE name = 'Workspace'
    ON CONFLICT DO NOTHING;

    -- RoomType: Phòng Queen Tiêu Chuẩn Trung Tâm Dương Đông
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Phòng Queen Tiêu Chuẩn Trung Tâm Dương Đông' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Phòng Queen Tiêu Chuẩn Trung Tâm Dương Đông', 10, 2, 2, 'ACTIVE', '{"description": "Phòng Queen Tiêu Chuẩn Trung Tâm Dương Đông"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Standard Queen Central Room
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 950000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Phòng Deluxe Ban Công Lộng Gió Biển
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Phòng Deluxe Ban Công Lộng Gió Biển' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Phòng Deluxe Ban Công Lộng Gió Biển', 8, 2, 3, 'ACTIVE', '{"description": "Phòng Deluxe Ban Công Lộng Gió Biển"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Deluxe Balcony Sea Breeze Room
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 1550000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Căn Hộ Gia Đình Thông Nhau Trung Tâm Phố Biển
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Căn Hộ Gia Đình Thông Nhau Trung Tâm Phố Biển' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Căn Hộ Gia Đình Thông Nhau Trung Tâm Phố Biển', 4, 4, 5, 'ACTIVE', '{"description": "Căn Hộ Gia Đình Thông Nhau Trung Tâm Phố Biển"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Family Connecting City Suite
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 2700000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- 3. Populate 180-day inventory for all room types
    INSERT INTO type_day_inventory (room_type_id, stay_date, total, out_of_order, stop_sale, version)
    SELECT rt.id, d::date, rt.base_quantity, 0, false, 0
    FROM room_types rt
    CROSS JOIN generate_series(CURRENT_DATE - INTERVAL '15 days', CURRENT_DATE + INTERVAL '180 days', INTERVAL '1 day') AS d
    ON CONFLICT (room_type_id, stay_date) DO UPDATE SET total = EXCLUDED.total;

END $$;