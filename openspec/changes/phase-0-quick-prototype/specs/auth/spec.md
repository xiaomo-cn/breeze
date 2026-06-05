# Auth

## Register

用户可以通过邮箱和密码注册账号。

### Scenario: 注册成功

- **WHEN** 用户提供 username、email、password
- **THEN** 系统创建用户并返回 JWT Access Token 和 Refresh Token

### Scenario: 用户名已存在

- **WHEN** 用户提供的 username 已被注册
- **THEN** 系统返回 409 错误，提示"用户名已存在"

### Scenario: 邮箱已存在

- **WHEN** 用户提供的 email 已被注册
- **THEN** 系统返回 409 错误，提示"邮箱已注册"

## Login

用户可以通过用户名+密码登录。

### Scenario: 登录成功

- **WHEN** 用户提供正确的 username 和 password
- **THEN** 系统返回 JWT Access Token（1h）和 Refresh Token（7d）

### Scenario: 密码错误

- **WHEN** 用户提供错误的 password
- **THEN** 系统返回 401 错误，提示"用户名或密码错误"

## Token Refresh

Access Token 过期后可用 Refresh Token 获取新的 Access Token。

### Scenario: 刷新成功

- **WHEN** 用户提供有效的 Refresh Token
- **THEN** 系统返回新的 Access Token 和 Refresh Token

### Scenario: Refresh Token 过期

- **WHEN** 用户提供的 Refresh Token 已过期
- **THEN** 系统返回 401 错误，要求重新登录
