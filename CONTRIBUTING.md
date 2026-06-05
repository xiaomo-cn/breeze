# 贡献指南 / Contributing Guide

感谢你对 Breeze 的关注！欢迎任何形式的贡献。

## 行为准则

本项目遵循 [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)，参与即表示你同意遵守其条款。

## 如何贡献

### 报告 Bug

1. 在 [Issues](https://github.com/your-username/breeze/issues) 中搜索是否已有相关报告
2. 如果没有，使用 **Bug Report** 模板创建新 Issue
3. 尽量提供：复现步骤、期望行为、实际行为、截图、环境信息

### 建议新功能

1. 在 Issues 中使用 **Feature Request** 模板
2. 描述功能的使用场景和期望效果
3. 我们会在 Issue 中讨论技术方案

### 提交代码

1. **Fork** 本项目
2. 创建特性分支：`git checkout -b feature/your-feature-name`
3. 确保代码符合项目风格
4. 添加必要的测试
5. 提交 Commit：`git commit -m "feat: 添加 xxx 功能"`
6. 推送并创建 Pull Request

## 开发环境

```bash
# 后端
cd backend
mvn spring-boot:run

# 前端
cd frontend
npm install && npm run dev
```

## Commit 规范

使用 [Conventional Commits](https://www.conventionalcommits.org/)：

- `feat:` 新功能
- `fix:` Bug 修复
- `docs:` 文档变更
- `style:` 代码格式（不影响功能）
- `refactor:` 代码重构
- `test:` 测试相关
- `chore:` 构建/工具变更

## 代码风格

- **后端**：遵循 Java 标准编码规范，使用 Lombok 简化 POJO
- **前端**：使用 Prettier + ESLint，TypeScript 严格模式
- **注释**：代码注释使用中文

## PR 审查流程

1. CI 必须通过（编译 + 测试）
2. 至少 1 位维护者 Code Review
3. PR 描述清晰，关联相关 Issue
4. Review 通过后 Squash Merge 到 main 分支

## 项目结构

详见 [ARCHITECTURE.md](docs/ARCHITECTURE.md)
