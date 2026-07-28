INSERT INTO users (id, username, email, password, real_name, phone, address, role, created_at, updated_at)
VALUES (1, 'seller', 'seller@race.com', 'pw', '김X진', '010-1234-5678', '서울시 강남구', 'GENERAL', NOW(6), NOW(6));

INSERT INTO vehicle (id, seller_id, manufacturer, model, model_year, mileage,
                     fuel_type, transmission, plate_number, estimated_price, created_at, updated_at)
VALUES (1000, 1, 'HYUNDAI', '그랜저 IG', 2021, 45000,
        'GASOLINE', 'AUTOMATIC', '12가3456', 24800000, NOW(6), NOW(6));

INSERT INTO vehicle_image (id, vehicle_id, image_url, sort_order, created_at, updated_at)
VALUES (1, 1000, 'https://cdn/first.jpg', 1, NOW(6), NOW(6)),
       (2, 1000, 'https://cdn/second.jpg', 2, NOW(6), NOW(6));
