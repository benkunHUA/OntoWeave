-- 只在数据卷为空时由官方镜像的入口脚本执行一次。
-- 已存在的卷需要手工 createdb，或 docker compose down -v 后重建。
CREATE DATABASE ontoweave_test OWNER ontoweave;
