-- Migration: Update all property titles, addresses, descriptions, and room type names to pure Vietnamese

-- 1. Update Properties in Da Nang
UPDATE properties SET 
    title = 'Biệt Thự Biển Nghỉ Dưỡng Kèm Hồ Bơi Vô Cực Riêng',
    address = 'Đường Trường Sa, Phường Hòa Hải, Quận Ngũ Hành Sơn',
    description = 'Thức giấc cùng tiếng sóng vỗ tại biệt thự sát biển tuyệt đẹp. Sở hữu lối đi thẳng ra bãi biển, hồ bơi vô cực riêng, gian bếp hiện đại trang bị đầy đủ, Wi-Fi tốc độ cao và sân hiên tắm nắng thoáng đãng. Điểm đến lý tưởng cho gia đình hoặc nhóm bạn tận hưởng kỳ nghỉ dưỡng nhiệt đới tại Đà Nẵng.'
WHERE id = 101;

UPDATE properties SET 
    title = 'Căn Hộ Studio View Sông Hàn Gần Cầu Rồng',
    address = 'Đường Trần Hưng Đạo, Phường An Hải Tây, Quận Sơn Trà',
    description = 'Căn hộ studio phong cách tối giản ngập tràn ánh sáng tự nhiên, chỉ cách Cầu Rồng vài bước chân. Thích hợp cho chuyến công tác hoặc kỳ nghỉ đôi, không gian đầy đủ tiện nghi với Wi-Fi siêu tốc, điều hòa, bàn làm việc riêng và sân thượng ngắm toàn cảnh sông Hàn.'
WHERE id = 102;

UPDATE properties SET 
    title = 'Phòng Khách Sạn Boutique Gần Biển Mỹ Khê',
    address = 'Đường Võ Nguyên Giáp, Phường Mỹ An, Quận Ngũ Hành Sơn',
    description = 'Phòng khách sạn phong cách boutique sang trọng mang đến sự thoải mái chuẩn 5 sao với mức giá hợp lý. Bao gồm nệm giường cao cấp, dịch vụ dọn phòng hàng ngày, Smart TV Netflix, quầy minibar và lễ tân hỗ trợ 24/7.'
WHERE id = 109;

UPDATE properties SET 
    title = 'Khu Nghỉ Dưỡng Cao Cấp Ven Biển Mỹ Khê'
WHERE id = 8;

UPDATE properties SET 
    title = 'Căn Hộ Khách Sạn Cao Cấp Bờ Sông Hàn'
WHERE id = 9;

UPDATE properties SET 
    title = 'Homestay Nghỉ Dưỡng Sinh Thái Bán Đảo Sơn Trà'
WHERE id = 10;

-- 2. Update Properties in Hanoi
UPDATE properties SET 
    title = 'Khách Sạn & Spa Cổ Điển La Siesta Phố Cổ Hà Nội'
WHERE id = 2;

UPDATE properties SET 
    title = 'Khu Căn Hộ Dịch Vụ Di Sản Hồ Tây'
WHERE id = 3;

UPDATE properties SET 
    title = 'Biệt Thự Kiến Trúc Pháp Cổ Phố Cổ Hà Nội'
WHERE id = 4;

UPDATE properties SET 
    title = 'Không Gian Nghỉ Dưỡng Đông Dương Phố Cổ Hà Nội',
    address = 'Phố Hàng Bạc, Phường Hàng Bạc, Quận Hoàn Kiếm',
    description = 'Đắm chìm trong vẻ đẹp nghìn năm văn hiến của thủ đô Hà Nội. Căn nhà phố kết hợp hài hòa kiến trúc Đông Dương hoài niệm và tiện nghi hiện đại. Chỉ 5 phút tản bộ tới Hồ Hoàn Kiếm, phố ẩm thực và những góc cà phê phố cổ trứ danh.'
WHERE id = 103;

UPDATE properties SET 
    title = 'Penthouse Hồ Tây Tầm Nhìn Toàn Cảnh Thủ Đô',
    address = 'Đường Tô Ngọc Vân, Phường Quảng An, Quận Tây Hồ',
    description = 'Trải nghiệm không gian sống thượng lưu phía trên mặt nước Hồ Tây thơ mộng. Căn penthouse rộng lớn với kính tràn viền từ sàn đến trần, hai ban công lộng gió, dịch vụ đầu bếp riêng, TV giải trí cao cấp và trang thiết bị gia dụng hiện đại.'
WHERE id = 104;

UPDATE properties SET 
    title = 'Biệt Thự Pháp Cổ Thập Niên 1930 Kèm Sân Vườn',
    address = 'Đường Phan Đình Phùng, Phường Quán Thánh, Quận Ba Đình',
    description = 'Ngược dòng thời gian trong căn biệt thự di sản Pháp từ năm 1930 nguyên bản. Trần nhà cao thoáng đãng, sàn gạch bông cổ kính, lò sưởi ấm cúng, khoảng sân vườn lát đá trang nhã cùng nội thất gỗ tinh tế tại khu vực ngoại giao đoàn an ninh.'
WHERE id = 110;

-- 3. Update Properties in Ho Chi Minh City
UPDATE properties SET 
    title = 'Khách Sạn Phong Cách Boutique The Myst Đồng Khởi'
WHERE id = 5;

UPDATE properties SET 
    title = 'Căn Hộ Cao Cấp Vinhomes Central Park Landmark 81'
WHERE id = 6;

UPDATE properties SET 
    title = 'Biệt Thự Sinh Thái Ven Sông Sài Gòn Thảo Điền'
WHERE id = 7;

-- 4. Update Properties in Da Lat
UPDATE properties SET 
    title = 'Khu Nghỉ Dưỡng Di Sản Rừng Thông Đà Lạt'
WHERE id = 11;

UPDATE properties SET 
    title = 'Biệt Thự & Spa Nghỉ Dưỡng Hồ Tuyền Lâm'
WHERE id = 12;

UPDATE properties SET 
    title = 'Homestay Vintage Gỗ Mộc Le Petit Đà Lạt'
WHERE id = 13;

UPDATE properties SET 
    title = 'Nhà Gỗ Mộc Giữa Rừng Thông Đà Lạt',
    address = 'Đường Khe Sanh, Phường 10, TP. Đà Lạt',
    description = 'Tạm gác lại âu lo trong căn nhà gỗ mộc mạc nép mình bên sườn đồi thông. Thưởng thức ly cà phê ấm nóng trên hiên ngắm sương mù sớm mai và vườn hoa rực rỡ, kèm lò sưởi nướng BBQ ngoài trời, hệ thống sưởi sàn và chỗ đỗ xe riêng biệt.'
WHERE id = 105;

UPDATE properties SET 
    title = 'Căn Hộ Áp Mái Bohemian Vườn Hoa Bí Mật',
    address = 'Đường Lê Hồng Phong, Phường 4, TP. Đà Lạt',
    description = 'Không gian gác mái vintage đầy chất thơ ngập tràn ánh sáng tự nhiên và cây xanh. Trang bị máy phát đĩa than cổ, ban công gỗ ngắm hoa cẩm tú cầu, tọa lạc trong con hẻm yên tĩnh chỉ cách trung tâm thành phố vài phút di chuyển.'
WHERE id = 106;

UPDATE properties SET 
    title = 'Nhà Kính Hiện Đại Thung Lũng Hoa Cẩm Tú Cầu',
    address = 'Khu Trại Mát, Phường 11, TP. Đà Lạt',
    description = 'Căn nhà kính 4 mặt thoáng sở hữu góc nhìn 360 độ ôm trọn thung lũng đèn đêm và những cánh đồng hoa Đà Lạt bạt ngàn. Trải nghiệm đêm se lạnh ấm cúng bên dàn âm thanh chất lượng cao, sưởi sàn và khoảng sân đốt lửa trại thoáng đãng.'
WHERE id = 111;

-- 5. Update Properties in Phu Quoc
UPDATE properties SET 
    title = 'Khu Nghỉ Dưỡng 5 Sao Bãi Khem Phú Quốc'
WHERE id = 14;

UPDATE properties SET 
    title = 'Biệt Thự Nghỉ Dưỡng Hoàng Hôn Sunset Sanato Phú Quốc'
WHERE id = 15;

UPDATE properties SET 
    title = 'Khách Sạn Trung Tâm Thị Trấn Dương Đông Phú Quốc'
WHERE id = 16;

UPDATE properties SET 
    title = 'Bungalow Ven Biển Kèm Võng Nằm Thư Giãn',
    address = 'Bãi biển Ông Lang, Xã Cửa Dương, TP. Phú Quốc',
    description = 'Tọa lạc bên trong khu nghỉ dưỡng sinh thái yên bình, chỉ 10 bước chân tới bờ cát trắng mịn và làn nước biển ngọc bích của Bãi Ông Lang. Sử dụng tiện ích hồ bơi resort, quầy bar ven biển, thuyền chèo SUP và bữa sáng buffet miễn phí.'
WHERE id = 107;

UPDATE properties SET 
    title = 'Biệt Thự Đồi Hồ Bơi Riêng Ngắm Toàn Cảnh Đảo Ngọc',
    address = 'Đường Trần Hưng Đạo, Phường Dương Đông, TP. Phú Quốc',
    description = 'Tọa lạc trên ngọn đồi thoai thoải ngắm trọn khoảnh khắc hoàng hôn rực rỡ của đảo Phú Quốc. Căn biệt thự sở hữu hồ bơi vô cực riêng, quầy bar ngoài trời hiện đại, không gian mở và hệ thống công nghệ nhà thông minh.'
WHERE id = 108;

-- 6. Update Room Types to Pure Vietnamese
UPDATE room_types SET name = 'Phòng Deluxe Giường Đôi Cổ Điển' WHERE id = 1;
UPDATE room_types SET name = 'Phòng Suite Executive Tầm Nhìn Thành Phố' WHERE id = 2;
UPDATE room_types SET name = 'Phòng Suite Gia Đình Di Sản Grand' WHERE id = 3;
UPDATE room_types SET name = 'Căn Hộ Studio Ban Công Hướng Hồ Tây' WHERE id = 4;
UPDATE room_types SET name = 'Căn Hộ 1 Phòng Ngủ Hạng Executive' WHERE id = 5;
UPDATE room_types SET name = 'Căn Hộ Penthouse 2 Phòng Ngủ Toàn Cảnh' WHERE id = 6;
UPDATE room_types SET name = 'Phòng Colonial Queen Ban Công Sân Vườn' WHERE id = 7;
UPDATE room_types SET name = 'Phòng Suite Sân Vườn Cao Cấp' WHERE id = 8;
UPDATE room_types SET name = 'Biệt Thự Di Sản Pháp Nguyên Bản' WHERE id = 9;
UPDATE room_types SET name = 'Phòng Deluxe View Phố Đồng Khởi' WHERE id = 10;
UPDATE room_types SET name = 'Phòng Suite Hướng Sông Sài Gòn' WHERE id = 11;
UPDATE room_types SET name = 'Phòng Suite Tổng Thống Sân Vườn Trên Cao' WHERE id = 12;
UPDATE room_types SET name = 'Căn Hộ 1 Phòng Ngủ Đô Thị Hiện Đại' WHERE id = 13;
UPDATE room_types SET name = 'Căn Hộ Gia Đình 2 Phòng Ngủ View Sông' WHERE id = 14;
UPDATE room_types SET name = 'Căn Hộ Penthouse 3 Phòng Ngủ Trên Cao' WHERE id = 15;
UPDATE room_types SET name = 'Phòng Deluxe Ban Công Hướng Vườn' WHERE id = 16;
UPDATE room_types SET name = 'Phòng Suite King Cao Cấp Cạnh Hồ Bơi' WHERE id = 17;
UPDATE room_types SET name = 'Biệt Thự Master Ven Sông Sài Gòn' WHERE id = 18;
UPDATE room_types SET name = 'Phòng King Deluxe Hướng Trọn Biển Đông' WHERE id = 19;
UPDATE room_types SET name = 'Phòng Suite Premier Sát Biển Mỹ Khê' WHERE id = 20;
UPDATE room_types SET name = 'Căn Hộ Duplex Gia Đình Mặt Biển' WHERE id = 21;
UPDATE room_types SET name = 'Căn Hộ Studio View Trực Diện Cầu Rồng' WHERE id = 22;
UPDATE room_types SET name = 'Phòng Suite Executive View Sông Hàn' WHERE id = 23;
UPDATE room_types SET name = 'Căn Hộ Grand Skyline Bờ Sông Hàn' WHERE id = 24;
UPDATE room_types SET name = 'Phòng Deluxe Đồi Thông Lãng Mạn' WHERE id = 28;
UPDATE room_types SET name = 'Phòng Suite Di Sản View Thung Lũng' WHERE id = 29;
UPDATE room_types SET name = 'Phòng Superior Ban Công View Hồ Tuyền Lâm' WHERE id = 31;
UPDATE room_types SET name = 'Phòng Suite Lãng Mạn Sương Mờ Hồ Tuyền Lâm' WHERE id = 32;
UPDATE room_types SET name = 'Biệt Thự Gia Đình Ven Hồ Tuyền Lâm' WHERE id = 33;
UPDATE room_types SET name = 'Phòng Deluxe Biển Ngọc Bãi Khem' WHERE id = 37;
UPDATE room_types SET name = 'Phòng Suite Pavilion Premier View Vịnh Biển' WHERE id = 38;
UPDATE room_types SET name = 'Biệt Thự Hồ Bơi Riêng Sát Biển Bãi Khem' WHERE id = 39;
UPDATE room_types SET name = 'Phòng Queen Deluxe View Hoàng Hôn Bãi Trường' WHERE id = 40;
UPDATE room_types SET name = 'Biệt Thự Sân Vườn Ven Biển Sunset Sanato' WHERE id = 41;
UPDATE room_types SET name = 'Biệt Thự 3 Phòng Ngủ Toàn Cảnh Hoàng Hôn Biển' WHERE id = 42;
UPDATE room_types SET name = 'Phòng Queen Tiêu Chuẩn Trung Tâm Dương Đông' WHERE id = 43;
UPDATE room_types SET name = 'Phòng Deluxe Ban Công Lộng Gió Biển' WHERE id = 44;
UPDATE room_types SET name = 'Căn Hộ Gia Đình Thông Nhau Trung Tâm Phố Biển' WHERE id = 45;
