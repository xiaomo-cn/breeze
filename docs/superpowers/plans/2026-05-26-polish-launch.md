# Phase 7: Polish + Launch 实现计划

> **For agentic workers:** Use superpowers:subagent-driven-development to implement.

**Goal:** 生产就绪：健康检查、Docker 部署、前端性能优化、安全加固、移动端适配。

**Architecture:** Spring Boot Actuator 暴露健康指标；Docker 多阶段构建（前端→Nginx，后端→JRE）；React.lazy 路由分包；DOMPurify 清洗 Markdown；Redis 缓存热点数据；CSS 媒体查询响应式。

**Tech Stack:** Spring Boot 3.3, MyBatis-Plus 3.5.9, React 18, Vite 6, Ant Design 5, Docker, Nginx

---

## 分组策略

| 组 | 模块 | 任务数 |
|------|------|--------|
| 7a | 健康检查 + 前端懒加载 | 2 |
| 7b | Docker + Nginx 部署 | 4 |
| 7c | 安全加固 (DOMPurify + SQL + Rate Limit) | 3 |
| 7d | Redis 缓存 | 2 |
| 7e | 移动端响应式 | 3 |

---

### 7a: 健康检查 + 前端懒加载

**健康检查:**

添加 Spring Boot Actuator 依赖，启用 `/actuator/health` 端点。

`backend/pom.xml` 添加:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

`backend/src/main/resources/application.yml` 添加:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: always
```

**前端懒加载:**

修改 `frontend/src/routes/index.tsx`，将静态 import 替换为 `React.lazy()` + `<Suspense fallback={<Spin />}>`。

### 7b: Docker + Nginx 部署

**创建文件:**
- `Dockerfile.backend` — 多阶段构建（Maven 编译 + JRE 运行）
- `Dockerfile.frontend` — 多阶段构建（Node 编译 + Nginx 静态服务）
- `nginx.conf` — 反向代理（/api → backend:8080，/ → frontend），gzip 压缩，缓存头
- `docker-compose.yml` — 重写：PostgreSQL + Redis + MinIO + backend + frontend

### 7c: 安全加固

- **DOMPurify:** `npm install dompurify @types/dompurify`，在所有 Markdown 渲染处清洗 HTML
- **SQL 注入检查:** Grep 所有 `.xml` Mapper 文件确认使用 `#{}` 参数化（检查 `${}` 使用）
- **Rate Limit 增强:** 给认证端点 `/auth/login`、`/auth/register` 添加限流

### 7d: Redis 缓存

- 项目列表缓存（`GET /api/v1/projects`）
- 用户信息缓存（`GET /api/v1/users/{id}`）
- 配置 `RedisCacheManager` + `@Cacheable` 注解

### 7e: 移动端响应式

- 看板页面水平滚动 + 卡片最小宽度
- Sidebar 折叠（Ant Design Layout.Sider 的 `collapsible` + `breakpoint`）
- Dashboard 卡片响应式 grid

---

### 验证

```bash
# 后端
cd backend && mvn test 2>&1 | tail -5

# 前端
cd frontend && npx tsc --noEmit && npm run build

# Docker
docker compose up -d && curl http://localhost/actuator/health
```
