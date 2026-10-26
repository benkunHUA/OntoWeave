-- OntoWeave 零售订单 mock 数据（可重复执行）
--
-- 用法（在仓库根目录，MySQL 容器已启动）：
--   docker compose exec -T mysql mysql -uroot -proot < scripts/mock-retail.sql
--
-- 连接配置：
--   host=mysql  port=3306  database=retail_mock
--   username=ontoweave  password=ontoweave
--
-- 说明：脚本只重建 retail_mock，不影响 compose 初始化时的 retail_sales。
-- 数据为虚构业务样本，但姓名、公司、品牌、商品与订单编号均按真实业务风格生成。

SET NAMES utf8mb4;
SET SESSION cte_max_recursion_depth = 10000;

CREATE DATABASE IF NOT EXISTS retail_mock
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE retail_mock;

DROP TABLE IF EXISTS product_suppliers;
DROP TABLE IF EXISTS order_items;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS suppliers;
DROP TABLE IF EXISTS customers;

CREATE TABLE customers (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    level VARCHAR(20) NOT NULL,
    city VARCHAR(50) NULL,
    signup_date DATE NOT NULL,
    created_at DATETIME NOT NULL,
    KEY ix_customers_level (level),
    KEY ix_customers_city (city)
) ENGINE = InnoDB COMMENT = '客户';

CREATE TABLE suppliers (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    city VARCHAR(50) NOT NULL,
    rating DECIMAL(3, 1) NOT NULL,
    created_at DATETIME NOT NULL,
    KEY ix_suppliers_city (city)
) ENGINE = InnoDB COMMENT = '供应商';

CREATE TABLE products (
    id BIGINT PRIMARY KEY,
    sku VARCHAR(32) NOT NULL,
    name VARCHAR(120) NOT NULL,
    brand VARCHAR(50) NOT NULL,
    category VARCHAR(32) NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    stock INT NOT NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uq_products_sku (sku),
    KEY ix_products_category (category),
    KEY ix_products_brand (brand)
) ENGINE = InnoDB COMMENT = '商品';

CREATE TABLE orders (
    id BIGINT PRIMARY KEY,
    order_no VARCHAR(32) NOT NULL,
    customer_id BIGINT NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uq_orders_order_no (order_no),
    KEY ix_orders_customer (customer_id),
    KEY ix_orders_status (status),
    KEY ix_orders_channel (channel)
) ENGINE = InnoDB COMMENT = '订单';

CREATE TABLE order_items (
    id BIGINT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(10, 2) NOT NULL,
    line_amount DECIMAL(12, 2) NOT NULL,
    KEY ix_order_items_order (order_id),
    KEY ix_order_items_product (product_id)
) ENGINE = InnoDB COMMENT = '订单明细';

CREATE TABLE product_suppliers (
    id BIGINT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    supply_price DECIMAL(10, 2) NOT NULL,
    UNIQUE KEY uq_product_supplier (product_id, supplier_id),
    KEY ix_product_suppliers_supplier (supplier_id)
) ENGINE = InnoDB COMMENT = '商品-供应商连接表';

-- 1,000 个客户：100 个常见姓氏 × 130 个真实风格名字组合。
-- id=1000 的 city 为空，用于验证可空属性。
INSERT INTO customers (id, name, level, city, signup_date, created_at)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 1000
)
SELECT
    n,
    CONCAT(
        SUBSTRING_INDEX(
            SUBSTRING_INDEX(
                '王|李|张|刘|陈|杨|黄|赵|吴|周|徐|孙|马|朱|胡|郭|何|高|林|罗|郑|梁|谢|宋|唐|许|韩|冯|邓|曹|彭|曾|肖|田|董|袁|潘|于|蒋|蔡|余|杜|叶|程|苏|魏|吕|丁|任|沈|姚|卢|姜|崔|钟|谭|陆|汪|范|金|石|廖|贾|夏|韦|付|方|白|邹|孟|熊|秦|邱|江|尹|薛|闫|段|雷|侯|龙|史|陶|黎|贺|顾|毛|郝|龚|邵|万|钱|严|覃|武|戴|莫|孔|向|汤',
                '|',
                1 + ((n * 37) % 100)
            ),
            '|',
            -1
        ),
        ELT(
            1 + ((n * 97) % 130),
            '晓宁', '嘉禾', '思远', '语桐', '子墨', '雨晴', '浩然', '欣怡', '文博', '诗涵',
            '宇航', '若溪', '嘉诚', '沐阳', '佳琪', '俊杰', '婉清', '承泽', '安然', '可欣',
            '明轩', '雨萱', '一鸣', '静怡', '天佑', '梓涵', '泽宇', '心悦', '皓然', '雅婷',
            '志远', '梦瑶', '子涵', '晨曦', '博文', '佳宁', '逸凡', '思琪', '睿哲', '晓彤',
            '嘉怡', '子昂', '欣妍', '文轩', '若彤', '俊熙', '雨欣', '明哲', '诗琪', '一诺',
            '皓宇', '静雯', '天宇', '紫涵', '泽楷', '佳慧', '子轩', '梦琪', '逸辰', '晓雯',
            '思雨', '嘉明', '欣桐', '文昊', '若曦', '俊豪', '雨涵', '明宇', '诗雅', '可馨',
            '皓轩', '静文', '天琪', '紫萱', '泽宁', '佳颖', '子瑶', '梦洁', '逸飞', '晓峰',
            '思源', '嘉欣', '欣悦', '文静', '若楠', '俊峰', '雨泽', '明辉', '诗蕊', '可儿',
            '皓阳', '静姝', '天朗', '紫晴', '泽民', '佳音', '子悦', '梦露', '逸林', '晓阳',
            '思齐', '嘉懿', '欣荣', '文涛', '若冰', '俊逸', '雨菲', '明达', '诗岚', '可岚',
            '皓辰', '静雅', '天磊', '紫薇', '泽安', '佳彤', '子航', '梦涵', '逸舟', '晓琳',
            '思睿', '嘉铭', '欣雨', '文康', '若岚', '俊驰', '雨桐', '明瑞', '诗敏', '可薇'
        )
    ),
    CASE
        WHEN n % 20 < 3 THEN 'gold'
        WHEN n % 20 < 10 THEN 'silver'
        ELSE 'bronze'
    END,
    CASE
        WHEN n = 1000 THEN NULL
        ELSE ELT(
            1 + (n % 12),
            '上海', '北京', '深圳', '杭州', '成都', '武汉',
            '南京', '广州', '苏州', '西安', '重庆', '长沙'
        )
    END,
    DATE_ADD('2023-01-03', INTERVAL (n * 11) % 1300 DAY),
    TIMESTAMPADD(HOUR, 9 + (n % 10), DATE_ADD('2023-01-03', INTERVAL (n * 11) % 1300 DAY))
FROM seq;

-- 30 个供应商，公司名称与所在地保持一致。
INSERT INTO suppliers (id, name, city, rating, created_at)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 30
)
SELECT
    n,
    ELT(
        n,
        '上海恒信供应链有限公司', '苏州澄野食品有限公司', '深圳启辰电子科技有限公司',
        '杭州云栖家居有限公司', '广州青禾服饰有限公司', '宁波海岳商贸有限公司',
        '青岛鲁丰食品有限公司', '东莞锐联电子有限公司', '上海岚川供应链管理有限公司',
        '苏州松间家居用品有限公司', '深圳星野智能科技有限公司', '杭州拾光品牌管理有限公司',
        '广州简序服饰有限公司', '宁波沐白电器有限公司', '青岛海岬食品有限公司',
        '东莞德立精密电子有限公司', '上海禾众商贸有限公司', '苏州吴韵食品有限公司',
        '深圳前海联创科技有限公司', '杭州临安竹里家居有限公司', '广州南沙衣品汇服饰有限公司',
        '宁波甬盛供应链有限公司', '青岛崂山源食品有限公司', '东莞长安芯科电子有限公司',
        '上海云帆数字科技有限公司', '苏州金鸡湖商贸有限公司', '深圳南山声学科技有限公司',
        '杭州西湖生活用品有限公司', '广州番禺织造有限公司', '宁波北仑港供应链有限公司'
    ),
    ELT(
        n,
        '上海', '苏州', '深圳', '杭州', '广州', '宁波', '青岛', '东莞', '上海', '苏州',
        '深圳', '杭州', '广州', '宁波', '青岛', '东莞', '上海', '苏州', '深圳', '杭州',
        '广州', '宁波', '青岛', '东莞', '上海', '苏州', '深圳', '杭州', '广州', '宁波'
    ),
    ROUND(3.8 + ((n * 7) % 12) / 10, 1),
    TIMESTAMPADD(DAY, n * 11, '2023-01-01 09:00:00')
FROM seq;

-- 200 个商品：家电、数码、食品、服饰、家居各 40 个。
INSERT INTO products (id, sku, name, brand, category, price, stock, created_at)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 200
)
SELECT
    n,
    CONCAT(
        CASE
            WHEN n <= 40 THEN 'HA'
            WHEN n <= 80 THEN 'DX'
            WHEN n <= 120 THEN 'SP'
            WHEN n <= 160 THEN 'FS'
            ELSE 'JJ'
        END,
        '-2026-', LPAD(n, 4, '0')
    ),
    CONCAT(
        ELT(1 + (((n - 1) % 40) * 7 + ((n - 1) DIV 40) * 3) % 10,
            '云杉', '澄野', '青禾', '岚川', '栖木', '星野', '拾光', '简序', '沐白', '松间'),
        ' ',
        CASE
            WHEN n <= 40 THEN CONCAT('HA-', LPAD(1 + ((n - 1) % 40), 3, '0'), ' ')
            WHEN n <= 80 THEN CONCAT('DX-', LPAD(1 + ((n - 1) % 40), 3, '0'), ' ')
            WHEN n <= 120 THEN ''
            WHEN n <= 160 THEN ''
            ELSE CONCAT('JJ-', LPAD(1 + ((n - 1) % 40), 3, '0'), ' ')
        END,
        CASE
            WHEN n <= 40 THEN ELT(1 + ((n - 1) % 40),
                '变频空气炸锅', '破壁料理机', '扫地机器人', '无线吸尘器', '即热饮水机',
                '智能电饭煲', '多功能料理锅', '智能电烤箱', '蒸汽挂烫机', '家用除湿机',
                '空气净化器', '静音加湿器', '声波电动牙刷', '便携冲牙器', '高速吹风机',
                '电动剃须刀', '全自动咖啡机', '恒温电热水壶', '养生壶', '台式洗碗机',
                '台式净饮机', '热泵干衣机', '石墨烯取暖器', '直流变频电风扇', '智能晾衣架',
                '电压力锅', '破壁豆浆机', '多功能绞肉机', '原汁榨汁机', '家用面包机',
                '三明治早餐机', '超薄电磁炉', '电陶炉', '餐具消毒柜', '变频微波炉',
                '衣物护理机', '手持除螨仪', '智能浴霸', '冷风扇', '家用制冰机')
            WHEN n <= 80 THEN ELT(1 + ((n - 1) % 40),
                '无线降噪耳机', '开放式蓝牙耳机', '智能运动手环', '智能手表', '便携蓝牙音箱',
                '桌面无线充电站', '65W 氮化镓充电器', '热插拔机械键盘', '人体工学鼠标', '27 英寸 4K 显示器',
                '移动固态硬盘', '智能家居摄像头', '双口车载充电器', '磁吸充电宝', 'USB-C 扩展坞',
                '电竞头戴耳机', '降噪会议耳机', '便携显示器', '智能门锁', '蓝牙自拍杆',
                '无线领夹麦克风', '桌面补光灯', '智能插座', 'Wi-Fi 6 路由器', '4K 电视盒子',
                '电子阅读器', '手写数位板', '无线游戏手柄', '铝合金电脑支架', '静音散热风扇',
                '高清网络摄像头', '蓝牙音频接收器', '多功能读卡器', '防水运动耳机', '头戴式监听耳机',
                '无线演示器', '智能温湿度计', '桌面立体声音箱', '便携投影仪', '快充数据线套装')
            WHEN n <= 120 THEN ELT(1 + ((n - 1) % 40),
                '东北长粒香大米 5kg', '五常稻花香大米 5kg', '云南小粒咖啡豆 500g', '阿拉比卡挂耳咖啡 10包',
                '特级初榨橄榄油 750ml', '有机山茶籽油 1L', '有机纯牛奶 250ml×12', '低脂高钙奶 250ml×12',
                '阳光玫瑰葡萄 2kg', '赣南脐橙 5kg', '新疆灰枣 500g', '宁夏枸杞 250g',
                '云南普洱茶饼 357g', '明前龙井茶 100g', '茉莉花茶 150g', '黑芝麻核桃粉 600g',
                '即食燕麦片 1kg', '全麦吐司面包 400g', '手工挂面 1kg', '东北黑木耳 200g',
                '武夷山岩茶 125g', '贵州红酸汤 500g', '四川郫县豆瓣酱 500g', '山东花生油 5L',
                '海南椰子水 330ml×12', '内蒙古风干牛肉 200g', '舟山带鱼段 500g', '湛江金鲳鱼 500g',
                '有机土鸡蛋 30枚', '黑猪五花肉 500g', '原切牛排 200g×5', '法式可颂 6只',
                '手工水饺 1kg', '鲜肉小笼包 500g', '低糖酸奶 200g×8', '每日坚果礼盒 1.2kg',
                '蜂蜜柚子茶 500g', '红枣桂圆茶 300g', '海盐苏打饼干 400g', '高蛋白能量棒 12支')
            WHEN n <= 160 THEN ELT(1 + ((n - 1) % 40),
                '男士轻量羽绒服', '女士双面羊毛大衣', '纯棉圆领 T 恤', '亚麻宽松衬衫', '直筒水洗牛仔裤',
                '高腰阔腿裤', '女士针织开衫', '男士商务西服', '真丝印花衬衫', '运动速干 T 恤',
                '抓绒三合一冲锋衣', '轻便防晒衣', '女士百褶半身裙', '男士休闲夹克', '儿童纯棉卫衣',
                '女士吊带连衣裙', '男士美利奴羊毛衫', '女士羊绒围巾', '透气缓震跑步鞋', '轻量徒步鞋',
                '真皮通勤皮鞋', '经典帆布休闲鞋', '女士通勤托特包', '男士双肩背包', '纯棉棒球帽',
                '纯棉中筒袜 5双', '羊毛触屏手套', '头层牛皮腰带', '女士家居服套装', '男士纯棉睡衣',
                '儿童防风外套', '女士牛仔短外套', '男士多袋工装裤', '女士针织长裙', '高弹瑜伽裤',
                '速干连体泳衣', '羊毛贝雷帽', '真丝睡眠眼罩', '商务真丝领带', '旅行衣物收纳袋')
            ELSE ELT(1 + ((n - 1) % 40),
                '天然乳胶枕', '记忆棉护脊床垫', '新疆长绒棉四件套', '水洗棉被套', '澳洲羊毛毯',
                '亚麻遮光窗帘', '棉麻沙发套', '白蜡木边几', '北欧落地灯', '大豆蜡香薰蜡烛',
                '无火藤条香薰', '陶瓷餐具 16件套', '耐热玻璃杯 6只', '不锈钢保温杯', '竹纤维毛巾 3条',
                '硅藻泥吸水地垫', '可折叠收纳箱', '实木衣架 10只', '厨房多层置物架', '桌面分格收纳盒',
                '真皮办公坐垫', '藤编脏衣篮', '加厚纯棉浴巾', '防水防油桌布', '手工陶瓷花瓶',
                '绿植陶土花盆', '智能感应垃圾桶', '床底防尘收纳袋', '遮光睡眠眼罩', '记忆棉办公坐垫',
                '折叠晾衣架', '防滑无痕衣架 20只', '机械厨房计时器', '加厚隔热手套', '食品级硅胶保鲜盖',
                '玻璃食品密封罐 3只', '旅行分装瓶套装', '简约静音挂钟', '陶瓷香薰灯', '天然椰棕坐垫')
        END
    ),
    ELT(1 + (((n - 1) % 40) * 7 + ((n - 1) DIV 40) * 3) % 10,
        '云杉', '澄野', '青禾', '岚川', '栖木', '星野', '拾光', '简序', '沐白', '松间'),
    CASE
        WHEN n <= 40 THEN '家电'
        WHEN n <= 80 THEN '数码'
        WHEN n <= 120 THEN '食品'
        WHEN n <= 160 THEN '服饰'
        ELSE '家居'
    END,
    CASE
        WHEN n <= 40 THEN ROUND(159 + ((n * 137) % 2800) + (n % 100) / 100, 2)
        WHEN n <= 80 THEN ROUND(49 + ((n * 89) % 3800) + (n % 100) / 100, 2)
        WHEN n <= 120 THEN ROUND(9.9 + ((n * 17) % 280) + (n % 100) / 100, 2)
        WHEN n <= 160 THEN ROUND(59 + ((n * 43) % 850) + (n % 100) / 100, 2)
        ELSE ROUND(29 + ((n * 31) % 1200) + (n % 100) / 100, 2)
    END,
    (n * 37 + 11) % 760,
    TIMESTAMPADD(DAY, (n * 13) % 500, '2025-05-01 10:00:00')
FROM seq;

-- 2,000 条正常订单 + 2 条坏外键订单（customer_id 不在客户表）。
INSERT INTO orders (id, order_no, customer_id, amount, status, channel, created_at)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 2000
)
SELECT
    n,
    CONCAT(
        'SO',
        DATE_FORMAT(TIMESTAMPADD(HOUR, n * 3, '2026-01-02 09:00:00'), '%Y%m%d'),
        LPAD(n, 4, '0')
    ),
    1 + ((n * 37) % 1000),
    0,
    CASE
        WHEN n % 100 < 55 THEN 'completed'
        WHEN n % 100 < 70 THEN 'shipped'
        WHEN n % 100 < 85 THEN 'paid'
        WHEN n % 100 < 95 THEN 'pending'
        ELSE 'cancelled'
    END,
    ELT(1 + (n % 5), 'tmall', 'jd', 'douyin', 'wechat', 'offline'),
    TIMESTAMPADD(HOUR, n * 3, '2026-01-02 09:00:00')
FROM seq;

INSERT INTO orders (id, order_no, customer_id, amount, status, channel, created_at) VALUES
    (2001, 'SO202609010901', 9999, 88.00, 'paid', 'tmall', '2026-09-01 10:00:00'),
    (2002, 'SO202609020902', 8888, 66.00, 'pending', 'jd', '2026-09-02 11:00:00');

-- 5,000 条正常明细：前 1,000 单每单 3 件，后 1,000 单每单 2 件。
INSERT INTO order_items (id, order_id, product_id, quantity, unit_price, line_amount)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 3000
)
SELECT
    n,
    1 + ((n - 1) % 1000) AS order_id,
    1 + (((1 + ((n - 1) % 1000)) * 7 + ((n - 1) DIV 1000) * 67) % 200) AS product_id,
    1 + (((1 + ((n - 1) % 1000)) + ((n - 1) DIV 1000) * 2) % 3) AS quantity,
    p.price,
    (1 + (((1 + ((n - 1) % 1000)) + ((n - 1) DIV 1000) * 2) % 3)) * p.price
FROM seq
JOIN products p
  ON p.id = 1 + (((1 + ((n - 1) % 1000)) * 7 + ((n - 1) DIV 1000) * 67) % 200);

INSERT INTO order_items (id, order_id, product_id, quantity, unit_price, line_amount)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 2000
)
SELECT
    3000 + n,
    1001 + ((n - 1) % 1000) AS order_id,
    1 + (((1001 + ((n - 1) % 1000)) * 7 + ((n - 1) DIV 1000) * 67) % 200) AS product_id,
    1 + (((1001 + ((n - 1) % 1000)) + ((n - 1) DIV 1000) * 2) % 3) AS quantity,
    p.price,
    (1 + (((1001 + ((n - 1) % 1000)) + ((n - 1) DIV 1000) * 2) % 3)) * p.price
FROM seq
JOIN products p
  ON p.id = 1 + (((1001 + ((n - 1) % 1000)) * 7 + ((n - 1) DIV 1000) * 67) % 200);

-- 2 条坏外键明细：分别缺订单和商品。
INSERT INTO order_items (id, order_id, product_id, quantity, unit_price, line_amount) VALUES
    (5001, 9999, 1, 1, 10.00, 10.00),
    (5002, 1, 9999, 1, 10.00, 10.00);

-- 订单金额由明细汇总，保证业务口径一致。
UPDATE orders o
JOIN (
    SELECT order_id, SUM(line_amount) AS total_amount
    FROM order_items
    GROUP BY order_id
) t ON t.order_id = o.id
SET o.amount = t.total_amount
WHERE o.id <= 2000;

-- 400 条商品-供应商连接（每个商品 2 个供应商）+ 1 条坏外键。
INSERT INTO product_suppliers (id, product_id, supplier_id, supply_price)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 400
)
SELECT
    n,
    1 + ((n - 1) % 200),
    1 + ((n * 7 + ((n - 1) DIV 200) * 11) % 30),
    ROUND(p.price * (0.45 + ((n * 13) % 30) / 100), 2)
FROM seq
JOIN products p ON p.id = 1 + ((n - 1) % 200);

INSERT INTO product_suppliers (id, product_id, supplier_id, supply_price) VALUES
    (401, 1, 999, 8.88);

ANALYZE TABLE customers, suppliers, products, orders, order_items, product_suppliers;

SELECT 'customers' AS table_name, COUNT(*) AS row_count FROM customers
UNION ALL SELECT 'suppliers', COUNT(*) FROM suppliers
UNION ALL SELECT 'products', COUNT(*) FROM products
UNION ALL SELECT 'orders', COUNT(*) FROM orders
UNION ALL SELECT 'order_items', COUNT(*) FROM order_items
UNION ALL SELECT 'product_suppliers', COUNT(*) FROM product_suppliers;

SELECT
    (SELECT COUNT(*) FROM orders o LEFT JOIN customers c ON c.id = o.customer_id WHERE c.id IS NULL) AS orphan_orders,
    (SELECT COUNT(*) FROM order_items i LEFT JOIN orders o ON o.id = i.order_id WHERE o.id IS NULL) AS orphan_items_by_order,
    (SELECT COUNT(*) FROM order_items i LEFT JOIN products p ON p.id = i.product_id WHERE p.id IS NULL) AS orphan_items_by_product,
    (SELECT COUNT(*) FROM product_suppliers ps LEFT JOIN suppliers s ON s.id = ps.supplier_id WHERE s.id IS NULL) AS orphan_links,
    (SELECT COUNT(*) FROM customers WHERE city IS NULL) AS customers_without_city;
