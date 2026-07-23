-- =============================================
-- 众乐电子商城 - 初始化商品数据
-- 执行前确保 product 和 category 表已存在且为空
-- 使用方法：在 MySQL 中 source 此文件，或粘贴执行
-- =============================================

-- =============================================
-- 分类数据（8个一级分类 + 4个子分类）
-- =============================================
INSERT INTO `category` (`id`, `name`, `parent_id`, `level`, `sort`) VALUES
(1,  '手机数码', 0, 1, 1),
(2,  '电脑办公', 0, 1, 2),
(3,  '家用电器', 0, 1, 3),
(4,  '服装鞋帽', 0, 1, 4),
(5,  '食品生鲜', 0, 1, 5),
(6,  '图书教育', 0, 1, 6),
(7,  '母婴玩具', 0, 1, 7),
(8,  '运动户外', 0, 1, 8);

-- 子分类（对应前端 Mock 中的 children）
INSERT INTO `category` (`id`, `name`, `parent_id`, `level`, `sort`) VALUES
(9,  '手机',   1, 2, 1),
(10, '平板',   1, 2, 2),
(11, '笔记本', 2, 2, 1),
(12, '台式机', 2, 2, 2);

-- =============================================
-- 商品数据（12个商品，对应前端 Mock）
-- =============================================
INSERT INTO `product` (`id`, `name`, `description`, `price`, `original_price`, `image`, `images`, `category_id`, `category_name`, `sales`, `stock`, `rating`, `status`) VALUES
(1,  '华为 Mate 70 Pro',       '麒麟9100芯片，鸿蒙OS 5.0，超聚光影像系统',                       6999.00, 7999.00, 'https://picsum.photos/seed/product1/400',   '[\"https://picsum.photos/seed/product1a/400\",\"https://picsum.photos/seed/product1b/400\"]', 9,  '手机',       2589, 500,  4.9, 0),
(2,  'Apple Watch Ultra 2',    '钛金属表壳，49mm，全天候视网膜屏',                                 5999.00, 6499.00, 'https://picsum.photos/seed/product2/400',   '[\"https://picsum.photos/seed/product2a/400\",\"https://picsum.photos/seed/product2b/400\"]', 9,  '手机',       1856, 300,  4.8, 0),
(3,  'MacBook Pro 16寸',       'M3 Max芯片，36GB统一内存，512GB SSD',                             24999.00,27999.00,'https://picsum.photos/seed/product3/400',   '[\"https://picsum.photos/seed/product3a/400\",\"https://picsum.photos/seed/product3b/400\"]', 11, '笔记本',     3267, 200,  4.9, 0),
(4,  '戴尔 U2723QX 显示器',    '27英寸 4K IPS Black，Type-C 90W反向充电',                         4299.00, 4999.00, 'https://picsum.photos/seed/product4/400',   '[\"https://picsum.photos/seed/product4a/400\",\"https://picsum.photos/seed/product4b/400\"]', 11, '笔记本',     1589, 400,  4.7, 0),
(5,  '海尔 1.5匹 空调',        '新一级能效，变频冷暖，智能自清洁',                                 3299.00, 3999.00, 'https://picsum.photos/seed/product5/400',   '[\"https://picsum.photos/seed/product5a/400\",\"https://picsum.photos/seed/product5b/400\"]', 3,  '家用电器',   4523, 800,  4.6, 0),
(6,  '美的 双开门冰箱',        '508升双开门，风冷无霜，变频节能',                                  3899.00, 4599.00, 'https://picsum.photos/seed/product6/400',   '[\"https://picsum.photos/seed/product6a/400\",\"https://picsum.photos/seed/product6b/400\"]', 3,  '家用电器',   3210, 350,  4.7, 0),
(7,  'Nike Air Force 1',       '经典复古，纯白百搭运动鞋',                                        799.00,  999.00, 'https://picsum.photos/seed/product7/400',   '[\"https://picsum.photos/seed/product7a/400\",\"https://picsum.photos/seed/product7b/400\"]', 4,  '服装鞋帽',   6789, 1200, 4.5, 0),
(8,  '海澜之家 轻薄羽绒服',    '90%鸭绒，立领保暖短款外套',                                       599.00,  899.00, 'https://picsum.photos/seed/product8/400',   '[\"https://picsum.photos/seed/product8a/400\",\"https://picsum.photos/seed/product8b/400\"]', 4,  '服装鞋帽',   4567, 900,  4.4, 0),
(9,  '三只松鼠 坚果礼盒',      '每日坚果750g，混合果仁零食大礼包',                                129.00,  169.00, 'https://picsum.photos/seed/product9/400',   '[\"https://picsum.photos/seed/product9a/400\",\"https://picsum.photos/seed/product9b/400\"]', 5,  '食品生鲜',   8921, 2000, 4.8, 0),
(10, '深入理解计算机系统',     'CSAPP 第三版，计算机科学经典教材',                                 139.00,  179.00, 'https://picsum.photos/seed/product10/400',  '[\"https://picsum.photos/seed/product10a/400\",\"https://picsum.photos/seed/product10b/400\"]', 6, '图书教育',   5678, 1500, 4.9, 0),
(11, '乐高 消防局套装',        '60321 消防局主题，845粒颗粒，适合6岁+',                           699.00,  899.00, 'https://picsum.photos/seed/product11/400',  '[\"https://picsum.photos/seed/product11a/400\",\"https://picsum.photos/seed/product11b/400\"]', 7, '母婴玩具',   2345, 600,  4.6, 0),
(12, '李宁 羽毛球拍',          '风刃900C，全碳素专业进攻型',                                     1299.00, 1599.00,'https://picsum.photos/seed/product12/400',  '[\"https://picsum.photos/seed/product12a/400\",\"https://picsum.photos/seed/product12b/400\"]', 8, '运动户外',   3456, 450,  4.7, 0);