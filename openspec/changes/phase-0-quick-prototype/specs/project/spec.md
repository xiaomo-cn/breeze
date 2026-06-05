# Project

## Create Project

登录用户可以创建项目。

### Scenario: 创建成功

- **WHEN** 用户提供 name 和 key（唯一标识）
- **THEN** 系统创建项目，创建者自动成为 admin 角色成员

### Scenario: key 已存在

- **WHEN** 用户提供的 project key 已被占用
- **THEN** 系统返回 409 错误

## List Projects

用户可以查看自己参与的所有项目。

### Scenario: 查看项目列表

- **WHEN** 用户请求项目列表
- **THEN** 系统返回该用户作为成员的所有项目，按创建时间倒序

## Get Project

用户可以查看项目详情。

### Scenario: 查看项目详情

- **WHEN** 用户请求某个项目的详情
- **THEN** 系统返回项目信息（name, key, description, status, owner, 成员数）
