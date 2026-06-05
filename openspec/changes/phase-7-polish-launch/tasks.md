# Phase 7: Tasks

## Performance

- [ ] 排查 N+1 查询（MyBatis-Plus 懒加载 → 批量预加载/join）
- [ ] Redis 缓存策略：项目列表、用户信息、看板配置
- [ ] 数据库慢查询优化（EXPLAIN ANALYZE + 索引优化）
- [ ] 前端：代码分割（React.lazy + Suspense）按路由分包
- [ ] 前端：Ant Design 按需加载（tree shaking）
- [ ] 前端：图片懒加载 + 附件缩略图优化
- [ ] Nginx：gzip + brotli 压缩 + 静态资源缓存头

## Mobile Responsive

- [ ] 看板页面：水平滚动 + 列宽自适应
- [ ] 任务详情：全屏 Modal → BottomSheet（移动端）
- [ ] Sidebar：可折叠/汉堡菜单
- [ ] 登录/注册页响应式
- [ ] Dashboard 卡片布局响应式

## E2E Testing (Playwright)

- [ ] 注册 → 登录 → 创建项目 → 创建任务 → 拖拽 → AI 对话
- [ ] Sprint 创建 → 添加任务 → 启动 → 关闭
- [ ] 多标签页协作（A 拖拽 → B 看到更新）
- [ ] 文件上传流程
- [ ] AI 工具确认流程

## Security

- [ ] OWASP Top 10 扫描（ZAP / Snyk）
- [ ] JWT 安全：HTTPS only、short TTL、签名验证
- [ ] 文件上传安全：类型白名单、大小限制、病毒扫描（ClamAV 可选）
- [ ] SQL 注入：验证所有 MyBatis XML 使用参数化查询（#{} 而非 ${}）
- [ ] XSS：Markdown 渲染 sanitize（DOMPurify）
- [ ] CSRF：SameSite Cookie + CSRF Token
- [ ] Rate limiting：所有认证端点 + AI 端点
- [ ] 依赖漏洞扫描：`mvn dependency-check` + `npm audit`

## Deployment

- [ ] 生产级 docker-compose.yml（PostgreSQL + Redis + MinIO + Spring Boot + Nginx）
- [ ] Nginx 配置：SSL 终止、反向代理、静态资源、gzip
- [ ] 环境变量管理（.env 文件，敏感信息不进 Git）
- [ ] Spring Boot 生产 profile（连接池调优、GC 配置）
- [ ] 数据库备份策略（pg_dump cron + 阿里云 OSS 异地备份）
- [ ] 健康检查端点：/actuator/health

## Monitoring

- [ ] Spring Boot Actuator + Micrometer → Prometheus
- [ ] Grafana Dashboard（QPS、延迟分位数、错误率、JVM 内存、数据库连接池）
- [ ] 日志：JSON 格式 → Filebeat → Elasticsearch / Loki
- [ ] 告警：错误率 > 5%、P95 延迟 > 2s、磁盘 > 80%

## Verification

- [ ] Playwright E2E 全流程通过
- [ ] Lighthouse 性能评分 > 80
- [ ] OWASP ZAP 无高危漏洞
- [ ] Docker Compose 一键启动 → 所有功能正常
- [ ] 压力测试：wrk 并发 100 → P95 < 500ms
