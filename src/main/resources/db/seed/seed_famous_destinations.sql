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
    -- Property: Hanoi La Siesta Classic Hotel & Spa
    SELECT id INTO v_prop_id FROM properties WHERE title = 'Hanoi La Siesta Classic Hotel & Spa' LIMIT 1;
    IF v_prop_id IS NULL THEN
        INSERT INTO properties (host_id, title, description, address, city, price_per_night, cleaning_fee, max_guests, bedrooms, beds, bathrooms, property_type, sale_mode, timezone, status, rating_avg)
        VALUES (v_host_id, 'Hanoi La Siesta Classic Hotel & Spa', 'Khách sạn phong cách boutique sang trọng tọa lạc tại trung tâm Phố Cổ Hà Nội, chỉ vài bước chân tới Hồ Hoàn Kiếm và chợ đêm.', '94 Mã Mây, Phường Hàng Buồm, Quận Hoàn Kiếm', 'Hanoi', 1200000, 150000, 6, 3, 3, 2, 'HOTEL_ROOM', 'ROOM_BASED', 'Asia/Ho_Chi_Minh', 'ACTIVE', 4.9)
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

    -- RoomType: Deluxe Double Classic
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Deluxe Double Classic' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Deluxe Double Classic', 8, 2, 3, 'ACTIVE', '{"description": "Deluxe Double Classic"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Deluxe Double Classic
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 1200000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Executive City View Suite
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Executive City View Suite' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Executive City View Suite', 5, 2, 3, 'ACTIVE', '{"description": "Executive City View Suite"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Executive City View Suite
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 1850000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Grand Heritage Family Suite
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Grand Heritage Family Suite' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Grand Heritage Family Suite', 3, 4, 5, 'ACTIVE', '{"description": "Grand Heritage Family Suite"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Grand Heritage Family Suite
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 3200000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- Property: The West Lake Heritage Residence
    SELECT id INTO v_prop_id FROM properties WHERE title = 'The West Lake Heritage Residence' LIMIT 1;
    IF v_prop_id IS NULL THEN
        INSERT INTO properties (host_id, title, description, address, city, price_per_night, cleaning_fee, max_guests, bedrooms, beds, bathrooms, property_type, sale_mode, timezone, status, rating_avg)
        VALUES (v_host_id, 'The West Lake Heritage Residence', 'Khu căn hộ dịch vụ cao cấp hướng trọn tầm nhìn ra Hồ Tây thơ mộng, không gian yên tĩnh, ban công thoáng đãng và tiện nghi chuẩn quốc tế.', '68 Từ Hoa, Phường Quảng An, Quận Tây Hồ', 'Hanoi', 1400000, 200000, 6, 3, 3, 2, 'APARTMENT', 'ROOM_BASED', 'Asia/Ho_Chi_Minh', 'ACTIVE', 4.85)
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

    -- RoomType: Studio Lake View Balcony
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Studio Lake View Balcony' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Studio Lake View Balcony', 6, 2, 2, 'ACTIVE', '{"description": "Studio Lake View Balcony"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Studio Lake View Balcony
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 1400000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: One-Bedroom Executive Apartment
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'One-Bedroom Executive Apartment' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'One-Bedroom Executive Apartment', 5, 2, 3, 'ACTIVE', '{"description": "One-Bedroom Executive Apartment"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for One-Bedroom Executive Apartment
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 2200000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Two-Bedroom Panoramic Penthouse
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Two-Bedroom Panoramic Penthouse' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Two-Bedroom Panoramic Penthouse', 2, 4, 5, 'ACTIVE', '{"description": "Two-Bedroom Panoramic Penthouse"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Two-Bedroom Panoramic Penthouse
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 4200000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- Property: Old Quarter French Colonial Villa
    SELECT id INTO v_prop_id FROM properties WHERE title = 'Old Quarter French Colonial Villa' LIMIT 1;
    IF v_prop_id IS NULL THEN
        INSERT INTO properties (host_id, title, description, address, city, price_per_night, cleaning_fee, max_guests, bedrooms, beds, bathrooms, property_type, sale_mode, timezone, status, rating_avg)
        VALUES (v_host_id, 'Old Quarter French Colonial Villa', 'Biệt thự kiến trúc Pháp cổ kính nguyên bản hòa quyện nội thất gỗ ấm cúng, khu vườn nhiệt đới xanh mát giữa lòng thủ đô.', '42 Hàng Bè, Phường Hàng Bạc, Quận Hoàn Kiếm', 'Hanoi', 1600000, 250000, 6, 3, 3, 2, 'VILLA', 'ROOM_BASED', 'Asia/Ho_Chi_Minh', 'ACTIVE', 4.88)
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

    -- RoomType: Colonial Queen Garden Room
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Colonial Queen Garden Room' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Colonial Queen Garden Room', 4, 2, 2, 'ACTIVE', '{"description": "Colonial Queen Garden Room"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Colonial Queen Garden Room
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 1600000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Courtyard Premium Suite
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Courtyard Premium Suite' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Courtyard Premium Suite', 3, 2, 3, 'ACTIVE', '{"description": "Courtyard Premium Suite"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Courtyard Premium Suite
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 2500000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: French Heritage Master Residence
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'French Heritage Master Residence' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'French Heritage Master Residence', 2, 4, 6, 'ACTIVE', '{"description": "French Heritage Master Residence"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for French Heritage Master Residence
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 4800000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- ----------------------------------------------------------------------
    -- DESTINATION: Ho Chi Minh
    -- ----------------------------------------------------------------------
    -- Property: The Myst Dong Khoi Boutique Hotel
    SELECT id INTO v_prop_id FROM properties WHERE title = 'The Myst Dong Khoi Boutique Hotel' LIMIT 1;
    IF v_prop_id IS NULL THEN
        INSERT INTO properties (host_id, title, description, address, city, price_per_night, cleaning_fee, max_guests, bedrooms, beds, bathrooms, property_type, sale_mode, timezone, status, rating_avg)
        VALUES (v_host_id, 'The Myst Dong Khoi Boutique Hotel', 'Ốc đảo xanh thanh bình giữa trung tâm Quận 1 sầm uất, nổi bật với ban công phủ đầy cây xanh, bồn tắm gỗ teak và nội thất gỗ tái sinh.', '6-8 Hồ Huấn Nghiệp, Phường Bến Nghé, Quận 1', 'Ho Chi Minh', 1800000, 200000, 6, 3, 3, 2, 'HOTEL_ROOM', 'ROOM_BASED', 'Asia/Ho_Chi_Minh', 'ACTIVE', 4.92)
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

    -- RoomType: Dong Khoi City View Room
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Dong Khoi City View Room' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Dong Khoi City View Room', 10, 2, 2, 'ACTIVE', '{"description": "Dong Khoi City View Room"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Dong Khoi City View Room
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 1800000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Saigon River View Suite
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Saigon River View Suite' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Saigon River View Suite', 6, 2, 3, 'ACTIVE', '{"description": "Saigon River View Suite"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Saigon River View Suite
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 2850000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Presidential Garden Terrace Suite
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Presidential Garden Terrace Suite' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Presidential Garden Terrace Suite', 2, 4, 5, 'ACTIVE', '{"description": "Presidential Garden Terrace Suite"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Presidential Garden Terrace Suite
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 5500000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- Property: Vinhomes Central Park Sky Residence
    SELECT id INTO v_prop_id FROM properties WHERE title = 'Vinhomes Central Park Sky Residence' LIMIT 1;
    IF v_prop_id IS NULL THEN
        INSERT INTO properties (host_id, title, description, address, city, price_per_night, cleaning_fee, max_guests, bedrooms, beds, bathrooms, property_type, sale_mode, timezone, status, rating_avg)
        VALUES (v_host_id, 'Vinhomes Central Park Sky Residence', 'Căn hộ chung cư cao cấp liền kề công viên Landmark 81 ven sông Sài Gòn, hồ bơi tràn bờ và đầy đủ tiện nghi sinh hoạt gia đình.', '208 Nguyễn Hữu Cảnh, Phường 22, Quận Bình Thạnh', 'Ho Chi Minh', 1100000, 180000, 6, 3, 3, 2, 'APARTMENT', 'ROOM_BASED', 'Asia/Ho_Chi_Minh', 'ACTIVE', 4.82)
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

    -- RoomType: One-Bedroom Modern City Suite
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'One-Bedroom Modern City Suite' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'One-Bedroom Modern City Suite', 8, 2, 2, 'ACTIVE', '{"description": "One-Bedroom Modern City Suite"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for One-Bedroom Modern City Suite
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 1100000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Two-Bedroom Family Riverview Suite
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Two-Bedroom Family Riverview Suite' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Two-Bedroom Family Riverview Suite', 6, 4, 5, 'ACTIVE', '{"description": "Two-Bedroom Family Riverview Suite"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Two-Bedroom Family Riverview Suite
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 2100000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Three-Bedroom Sky Penthouse
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Three-Bedroom Sky Penthouse' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Three-Bedroom Sky Penthouse', 2, 6, 7, 'ACTIVE', '{"description": "Three-Bedroom Sky Penthouse"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Three-Bedroom Sky Penthouse
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 3900000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- Property: Thao Dien Eco Riverfront Villa
    SELECT id INTO v_prop_id FROM properties WHERE title = 'Thao Dien Eco Riverfront Villa' LIMIT 1;
    IF v_prop_id IS NULL THEN
        INSERT INTO properties (host_id, title, description, address, city, price_per_night, cleaning_fee, max_guests, bedrooms, beds, bathrooms, property_type, sale_mode, timezone, status, rating_avg)
        VALUES (v_host_id, 'Thao Dien Eco Riverfront Villa', 'Biệt thự nghỉ dưỡng phong cách nhiệt đới ven sông Sài Gòn tại Thảo Điền, sân vườn thoáng rộng, hồ bơi riêng và bếp BBQ ngoài trời.', '189 Nguyễn Văn Hưởng, Thảo Điền, TP. Thủ Đức', 'Ho Chi Minh', 1900000, 300000, 6, 3, 3, 2, 'VILLA', 'ROOM_BASED', 'Asia/Ho_Chi_Minh', 'ACTIVE', 4.89)
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

    -- RoomType: Garden View Deluxe Room
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Garden View Deluxe Room' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Garden View Deluxe Room', 5, 2, 2, 'ACTIVE', '{"description": "Garden View Deluxe Room"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Garden View Deluxe Room
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 1900000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Poolside Luxury King Suite
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Poolside Luxury King Suite' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Poolside Luxury King Suite', 4, 2, 3, 'ACTIVE', '{"description": "Poolside Luxury King Suite"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Poolside Luxury King Suite
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 3200000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Riverfront Master Residence Suite
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Riverfront Master Residence Suite' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Riverfront Master Residence Suite', 2, 4, 6, 'ACTIVE', '{"description": "Riverfront Master Residence Suite"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Riverfront Master Residence Suite
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 5800000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- ----------------------------------------------------------------------
    -- DESTINATION: Da Nang
    -- ----------------------------------------------------------------------
    -- Property: My Khe Oceanfront Luxury Resort
    SELECT id INTO v_prop_id FROM properties WHERE title = 'My Khe Oceanfront Luxury Resort' LIMIT 1;
    IF v_prop_id IS NULL THEN
        INSERT INTO properties (host_id, title, description, address, city, price_per_night, cleaning_fee, max_guests, bedrooms, beds, bathrooms, property_type, sale_mode, timezone, status, rating_avg)
        VALUES (v_host_id, 'My Khe Oceanfront Luxury Resort', 'Khu nghỉ dưỡng 5 sao sát biển Mỹ Khê, hồ bơi vô cực ngắm trọn bình minh biển Đông, spa cao cấp và nhà hàng hải sản phong phú.', '256 Võ Nguyên Giáp, Phường Phước Mỹ, Quận Sơn Trà', 'Da Nang', 2200000, 250000, 6, 3, 3, 2, 'RESORT', 'ROOM_BASED', 'Asia/Ho_Chi_Minh', 'ACTIVE', 4.95)
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

    -- RoomType: Ocean Deluxe King Room
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Ocean Deluxe King Room' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Ocean Deluxe King Room', 12, 2, 3, 'ACTIVE', '{"description": "Ocean Deluxe King Room"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Ocean Deluxe King Room
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 2200000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Premier Oceanfront Suite
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Premier Oceanfront Suite' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Premier Oceanfront Suite', 8, 2, 3, 'ACTIVE', '{"description": "Premier Oceanfront Suite"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Premier Oceanfront Suite
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 3500000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Beachfront Family Duplex Suite
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Beachfront Family Duplex Suite' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Beachfront Family Duplex Suite', 4, 4, 6, 'ACTIVE', '{"description": "Beachfront Family Duplex Suite"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Beachfront Family Duplex Suite
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 6200000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- Property: Han River Modern Skyline Suites
    SELECT id INTO v_prop_id FROM properties WHERE title = 'Han River Modern Skyline Suites' LIMIT 1;
    IF v_prop_id IS NULL THEN
        INSERT INTO properties (host_id, title, description, address, city, price_per_night, cleaning_fee, max_guests, bedrooms, beds, bathrooms, property_type, sale_mode, timezone, status, rating_avg)
        VALUES (v_host_id, 'Han River Modern Skyline Suites', 'Căn hộ khách sạn cao cấp bên bờ sông Hàn thơ mộng, view trực diện Cầu Rồng phun lửa, tiện nghi hiện đại và quầy bar tầng thượng.', '120 Bạch Đằng, Phường Hải Châu 1, Quận Hải Châu', 'Da Nang', 1150000, 150000, 6, 3, 3, 2, 'APARTMENT', 'ROOM_BASED', 'Asia/Ho_Chi_Minh', 'ACTIVE', 4.8)
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

    -- RoomType: Studio Dragon Bridge View
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Studio Dragon Bridge View' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Studio Dragon Bridge View', 8, 2, 2, 'ACTIVE', '{"description": "Studio Dragon Bridge View"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Studio Dragon Bridge View
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 1150000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Executive Riverview Suite
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Executive Riverview Suite' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Executive Riverview Suite', 6, 2, 3, 'ACTIVE', '{"description": "Executive Riverview Suite"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Executive Riverview Suite
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 1950000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Grand Han Skyline Apartment
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Grand Han Skyline Apartment' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Grand Han Skyline Apartment', 3, 4, 5, 'ACTIVE', '{"description": "Grand Han Skyline Apartment"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Grand Han Skyline Apartment
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 3400000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- Property: Son Tra Peninsula Retreat Homestay
    SELECT id INTO v_prop_id FROM properties WHERE title = 'Son Tra Peninsula Retreat Homestay' LIMIT 1;
    IF v_prop_id IS NULL THEN
        INSERT INTO properties (host_id, title, description, address, city, price_per_night, cleaning_fee, max_guests, bedrooms, beds, bathrooms, property_type, sale_mode, timezone, status, rating_avg)
        VALUES (v_host_id, 'Son Tra Peninsula Retreat Homestay', 'Khu homestay sinh thái nép mình dưới chân núi Sơn Trà, không khí trong lành thoang thoảng gió biển, view đồi rừng khoáng đạt.', 'Bãi Bụt, Bán đảo Sơn Trà, Quận Sơn Trà', 'Da Nang', 850000, 100000, 6, 3, 3, 2, 'HOMESTAY', 'ROOM_BASED', 'Asia/Ho_Chi_Minh', 'ACTIVE', 4.75)
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
    -- Property: Dalat Pine Forest Heritage Resort
    SELECT id INTO v_prop_id FROM properties WHERE title = 'Dalat Pine Forest Heritage Resort' LIMIT 1;
    IF v_prop_id IS NULL THEN
        INSERT INTO properties (host_id, title, description, address, city, price_per_night, cleaning_fee, max_guests, bedrooms, beds, bathrooms, property_type, sale_mode, timezone, status, rating_avg)
        VALUES (v_host_id, 'Dalat Pine Forest Heritage Resort', 'Resort phong cách biệt thự Pháp cổ ẩn giữa rừng thông bạt ngàn Đà Lạt, lò sưởi ấm cúng, trà chiều sân vườn ngắm sương mù lãng mạn.', '02 Trần Phú, Phường 3, TP. Đà Lạt', 'Da Lat', 1600000, 200000, 6, 3, 3, 2, 'RESORT', 'ROOM_BASED', 'Asia/Ho_Chi_Minh', 'ACTIVE', 4.91)
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

    -- RoomType: Pine Hill Deluxe Room
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Pine Hill Deluxe Room' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Pine Hill Deluxe Room', 8, 2, 3, 'ACTIVE', '{"description": "Pine Hill Deluxe Room"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Pine Hill Deluxe Room
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 1600000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Heritage Valley View Suite
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Heritage Valley View Suite' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Heritage Valley View Suite', 6, 2, 3, 'ACTIVE', '{"description": "Heritage Valley View Suite"}'::jsonb, 0)
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

    -- Property: Tuyen Lam Lake View Villa & Spa
    SELECT id INTO v_prop_id FROM properties WHERE title = 'Tuyen Lam Lake View Villa & Spa' LIMIT 1;
    IF v_prop_id IS NULL THEN
        INSERT INTO properties (host_id, title, description, address, city, price_per_night, cleaning_fee, max_guests, bedrooms, beds, bathrooms, property_type, sale_mode, timezone, status, rating_avg)
        VALUES (v_host_id, 'Tuyen Lam Lake View Villa & Spa', 'Quần thể villa mang hơi thở vùng núi Thụy Sĩ bên bờ hồ Tuyền Lâm phẳng lặng, bến du thuyền riêng và không gian nghỉ dưỡng tuyệt hảo.', 'Khu du lịch Hồ Tuyền Lâm, Phường 4, TP. Đà Lạt', 'Da Lat', 1850000, 250000, 6, 3, 3, 2, 'VILLA', 'ROOM_BASED', 'Asia/Ho_Chi_Minh', 'ACTIVE', 4.93)
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

    -- RoomType: Lakeview Superior Balcony Room
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Lakeview Superior Balcony Room' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Lakeview Superior Balcony Room', 7, 2, 2, 'ACTIVE', '{"description": "Lakeview Superior Balcony Room"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Lakeview Superior Balcony Room
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 1850000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Romantic Lake Mist Suite
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Romantic Lake Mist Suite' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Romantic Lake Mist Suite', 5, 2, 3, 'ACTIVE', '{"description": "Romantic Lake Mist Suite"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Romantic Lake Mist Suite
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 2950000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Lakeside Family Villa Unit
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Lakeside Family Villa Unit' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Lakeside Family Villa Unit', 2, 4, 6, 'ACTIVE', '{"description": "Lakeside Family Villa Unit"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Lakeside Family Villa Unit
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 5200000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- Property: Le Petit Dalat Vintage Homestay
    SELECT id INTO v_prop_id FROM properties WHERE title = 'Le Petit Dalat Vintage Homestay' LIMIT 1;
    IF v_prop_id IS NULL THEN
        INSERT INTO properties (host_id, title, description, address, city, price_per_night, cleaning_fee, max_guests, bedrooms, beds, bathrooms, property_type, sale_mode, timezone, status, rating_avg)
        VALUES (v_host_id, 'Le Petit Dalat Vintage Homestay', 'Ngôi nhà gỗ vintage ngập tràn hoa cẩm tú cầu, hiên nhà thưởng cà phê ngắm đồi dã quỳ và thung lũng đèn đêm huyền ảo.', '88 Mai Anh Đào, Phường 8, TP. Đà Lạt', 'Da Lat', 750000, 80000, 6, 3, 3, 2, 'HOMESTAY', 'ROOM_BASED', 'Asia/Ho_Chi_Minh', 'ACTIVE', 4.84)
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
    -- Property: Phu Quoc Emerald Bay Luxury Resort
    SELECT id INTO v_prop_id FROM properties WHERE title = 'Phu Quoc Emerald Bay Luxury Resort' LIMIT 1;
    IF v_prop_id IS NULL THEN
        INSERT INTO properties (host_id, title, description, address, city, price_per_night, cleaning_fee, max_guests, bedrooms, beds, bathrooms, property_type, sale_mode, timezone, status, rating_avg)
        VALUES (v_host_id, 'Phu Quoc Emerald Bay Luxury Resort', 'Resort biển danh giá bên Bãi Khem cát trắng như kem, kiến trúc đại học Pháp độc bản, hồ bơi vỏ sò biểu tượng và bãi tắm riêng biệt.', 'Bãi Khem, Phường An Thới, TP. Phú Quốc', 'Phu Quoc', 2800000, 350000, 6, 3, 3, 2, 'RESORT', 'ROOM_BASED', 'Asia/Ho_Chi_Minh', 'ACTIVE', 4.97)
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

    -- RoomType: Emerald Ocean Deluxe Room
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Emerald Ocean Deluxe Room' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Emerald Ocean Deluxe Room', 15, 2, 3, 'ACTIVE', '{"description": "Emerald Ocean Deluxe Room"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Emerald Ocean Deluxe Room
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 2800000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Bayview Premier Pavilion Suite
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Bayview Premier Pavilion Suite' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Bayview Premier Pavilion Suite', 8, 2, 3, 'ACTIVE', '{"description": "Bayview Premier Pavilion Suite"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Bayview Premier Pavilion Suite
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 4600000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Beachfront Private Pool Villa Suite
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Beachfront Private Pool Villa Suite' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Beachfront Private Pool Villa Suite', 3, 4, 6, 'ACTIVE', '{"description": "Beachfront Private Pool Villa Suite"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Beachfront Private Pool Villa Suite
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 8900000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- Property: Sunset Sanato Beachside Villas
    SELECT id INTO v_prop_id FROM properties WHERE title = 'Sunset Sanato Beachside Villas' LIMIT 1;
    IF v_prop_id IS NULL THEN
        INSERT INTO properties (host_id, title, description, address, city, price_per_night, cleaning_fee, max_guests, bedrooms, beds, bathrooms, property_type, sale_mode, timezone, status, rating_avg)
        VALUES (v_host_id, 'Sunset Sanato Beachside Villas', 'Quần thể biệt thự nghỉ dưỡng bên bờ biển Bãi Trường nổi tiếng với hoàng hôn rực rỡ, bãi biển dài và không gian tiệc cocktail lãng mạn.', 'Khu Bãi Trường, Xã Dương Tơ, TP. Phú Quốc', 'Phu Quoc', 1750000, 200000, 6, 3, 3, 2, 'VILLA', 'ROOM_BASED', 'Asia/Ho_Chi_Minh', 'ACTIVE', 4.86)
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

    -- RoomType: Sunset Deluxe Queen Room
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Sunset Deluxe Queen Room' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Sunset Deluxe Queen Room', 8, 2, 2, 'ACTIVE', '{"description": "Sunset Deluxe Queen Room"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Sunset Deluxe Queen Room
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 1750000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Beachside Garden Villa Suite
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Beachside Garden Villa Suite' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Beachside Garden Villa Suite', 5, 2, 3, 'ACTIVE', '{"description": "Beachside Garden Villa Suite"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Beachside Garden Villa Suite
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 2950000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Three-Bedroom Ocean Panorama Villa
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Three-Bedroom Ocean Panorama Villa' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Three-Bedroom Ocean Panorama Villa', 2, 6, 8, 'ACTIVE', '{"description": "Three-Bedroom Ocean Panorama Villa"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Three-Bedroom Ocean Panorama Villa
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 6500000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- Property: Duong Dong Central Boutique Hotel
    SELECT id INTO v_prop_id FROM properties WHERE title = 'Duong Dong Central Boutique Hotel' LIMIT 1;
    IF v_prop_id IS NULL THEN
        INSERT INTO properties (host_id, title, description, address, city, price_per_night, cleaning_fee, max_guests, bedrooms, beds, bathrooms, property_type, sale_mode, timezone, status, rating_avg)
        VALUES (v_host_id, 'Duong Dong Central Boutique Hotel', 'Khách sạn phong cách hiện đại ngay trung tâm thị trấn Dương Đông, cách chợ đêm ẩm thực Phú Quốc 3 phút đi bộ, bể bơi tầng thượng.', '145 Trần Hưng Đạo, Phường Dương Đông, TP. Phú Quốc', 'Phu Quoc', 950000, 100000, 6, 3, 3, 2, 'HOTEL_ROOM', 'ROOM_BASED', 'Asia/Ho_Chi_Minh', 'ACTIVE', 4.79)
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

    -- RoomType: Standard Queen Central Room
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Standard Queen Central Room' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Standard Queen Central Room', 10, 2, 2, 'ACTIVE', '{"description": "Standard Queen Central Room"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Standard Queen Central Room
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 950000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Deluxe Balcony Sea Breeze Room
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Deluxe Balcony Sea Breeze Room' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Deluxe Balcony Sea Breeze Room', 8, 2, 3, 'ACTIVE', '{"description": "Deluxe Balcony Sea Breeze Room"}'::jsonb, 0)
        RETURNING id INTO v_rt_id;
    END IF;

    -- RatePlan for Deluxe Balcony Sea Breeze Room
    INSERT INTO rate_plans (property_id, room_type_id, name, currency, policy_version, nightly_price, allowed_payment_modes, pricing_restrictions, status, version)
    VALUES (v_prop_id, v_rt_id, 'Gói Tiêu Chuẩn Linh Hoạt', 'VND', 's0-policy-v1', 1550000, '["FULL_PREPAY","HYBRID_DEPOSIT"]'::jsonb, '{}'::jsonb, 'ACTIVE', 0)
    ON CONFLICT (id, room_type_id, property_id) DO UPDATE SET nightly_price = EXCLUDED.nightly_price;

    -- RoomType: Family Connecting City Suite
    SELECT id INTO v_rt_id FROM room_types WHERE property_id = v_prop_id AND name = 'Family Connecting City Suite' LIMIT 1;
    IF v_rt_id IS NULL THEN
        INSERT INTO room_types (property_id, sale_mode, name, base_quantity, max_adults, max_occupants, status, metadata, version)
        VALUES (v_prop_id, 'ROOM_BASED', 'Family Connecting City Suite', 4, 4, 5, 'ACTIVE', '{"description": "Family Connecting City Suite"}'::jsonb, 0)
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