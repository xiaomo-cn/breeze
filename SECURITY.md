# 安全策略 / Security Policy

## 报告安全漏洞

如果您发现安全漏洞，**请不要在公开 Issue 中报告**。请通过以下方式私下报告：

📧 Email: **security@breeze.dev**（替换为你的实际安全邮箱）

我们会在 48 小时内确认收到报告，并在 5 个工作日内提供初步评估。

## 安全最佳实践

### 部署安全

1. **JWT Secret** — 生产环境必须使用强随机密钥（至少 256 位）
   ```bash
   # 生成安全密钥
   openssl rand -base64 32
   ```

2. **API Key** — 所有 AI 服务的 API Key 通过环境变量注入，不写入配置文件
   ```bash
   export DEEPSEEK_API_KEY=sk-your-key
   export EMBEDDING_API_KEY=your-openai-key
   ```

3. **数据库密码** — 修改默认 `breeze123` 密码

4. **HTTPS** — 生产环境通过 Nginx 反向代理启用 HTTPS

5. **防火墙** — 仅暴露 80/443 端口，后端 8080 和数据库 5432/6379 不对外

### 应用安全

| 措施 | 实现 |
|------|------|
| 认证 | Spring Security + JWT（Access Token 1h + Refresh Token 7d） |
| 密码哈希 | BCrypt（Spring Security 默认） |
| API 限流 | Redis INCR + TTL，AI 端点每用户每分钟 20 条 |
| 工具调用确认 | AI 写操作需用户在前端确认后执行 |
| SQL 注入防护 | MyBatis-Plus 参数化查询 + 输入校验 |
| CORS | 白名单配置，生产环境限制具体域名 |

## 支持的版本

| 版本 | 支持状态 |
|------|---------|
| 0.1.x | ⚠️ 开发中，欢迎安全反馈 |

## 依赖扫描

```bash
# 后端依赖检查
cd backend && mvn dependency-check:check

# 前端依赖检查
cd frontend && npm audit
```
