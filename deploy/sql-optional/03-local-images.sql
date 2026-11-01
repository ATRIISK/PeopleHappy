-- =============================================
-- 可选：把商品图从 picsum.photos 外链换成本地图片
--
-- 背景：02-init-data.sql 里 12 个商品的图片是 https://picsum.photos/... 外链。
--      国内浏览器访问 picsum.photos 经常超时，部署后首页会出现一片空白图。
--      仓库自带 200 张演示图（happy_mart/upload/20260815/prod1~200.jpg）。
--
-- 用法（容器起来、数据库初始化完成后执行）：
--   docker exec -i hm-mysql mysql -uroot -p密码 happy_mart < 03-local-images.sql
--
-- 前提：upload 目录已挂到后端容器的 /app/upload，即
--      /www/wwwroot/happy-mart/data/upload/20260815/prod1.jpg 存在
-- =============================================

USE happy_mart;

UPDATE `product` SET `image`='/upload/20260815/prod1.jpg',  `images`='["/upload/20260815/prod1.jpg"]'  WHERE `id`=1;
UPDATE `product` SET `image`='/upload/20260815/prod2.jpg',  `images`='["/upload/20260815/prod2.jpg"]'  WHERE `id`=2;
UPDATE `product` SET `image`='/upload/20260815/prod3.jpg',  `images`='["/upload/20260815/prod3.jpg"]'  WHERE `id`=3;
UPDATE `product` SET `image`='/upload/20260815/prod4.jpg',  `images`='["/upload/20260815/prod4.jpg"]'  WHERE `id`=4;
UPDATE `product` SET `image`='/upload/20260815/prod5.jpg',  `images`='["/upload/20260815/prod5.jpg"]'  WHERE `id`=5;
UPDATE `product` SET `image`='/upload/20260815/prod6.jpg',  `images`='["/upload/20260815/prod6.jpg"]'  WHERE `id`=6;
UPDATE `product` SET `image`='/upload/20260815/prod7.jpg',  `images`='["/upload/20260815/prod7.jpg"]'  WHERE `id`=7;
UPDATE `product` SET `image`='/upload/20260815/prod8.jpg',  `images`='["/upload/20260815/prod8.jpg"]'  WHERE `id`=8;
UPDATE `product` SET `image`='/upload/20260815/prod9.jpg',  `images`='["/upload/20260815/prod9.jpg"]'  WHERE `id`=9;
UPDATE `product` SET `image`='/upload/20260815/prod10.jpg', `images`='["/upload/20260815/prod10.jpg"]' WHERE `id`=10;
UPDATE `product` SET `image`='/upload/20260815/prod11.jpg', `images`='["/upload/20260815/prod11.jpg"]' WHERE `id`=11;
UPDATE `product` SET `image`='/upload/20260815/prod12.jpg', `images`='["/upload/20260815/prod12.jpg"]' WHERE `id`=12;

-- 验证
SELECT `id`, `name`, `image` FROM `product` ORDER BY `id` LIMIT 12;

-- 注意：商品详情有 30 分钟 Redis 缓存，改完要么等缓存过期，要么清一次：
--   docker exec hm-redis redis-cli FLUSHDB
