# LearnSpark

跨平台（Windows / Linux / macOS / Android）学习助手。围绕**任务拆解、资料归集、定时提醒、AI 标注**四个场景，把"想到要做的事"变成"实际做完的事"。自带 Spring Boot 后端 + MySQL。

---

## 你手里已经有了什么

仓库根目录的 `releases/` 目录是空的——你手里是 CI 构建好的产物或本地打包好的：

| 产物 | 在哪台机器跑 |
|------|-------------|
| `LearnSpark-1.0.0.exe` | Windows 桌面（双击即用） |
| `LearnSpark-1.0.0.deb` | Linux 桌面（`sudo dpkg -i ...`） |
| `LearnSpark-1.0.0-linux.tar.gz` | Linux 桌面（解压即用） |
| `LearnSpark-1.0.0.apk` | Android 手机/模拟器 |
| `server-1.0.0.jar` | 后端（Win / Linux 任选一台跑） |

> 后端 JAR 在仓库本地路径：`learnspark-kmp/server/build/libs/server-1.0.0.jar`

---

## 怎么跑起来（无 Docker，Win/Linux 桌面同机场景）

### 1. 准备 MySQL

后端要连 MySQL 8（必须 `utf8mb4`）。

- **Windows**：装 [MySQL 8 Installer](https://dev.mysql.com/downloads/installer/)，记住 root 密码，建库 `learnspark`
- **Linux**：`sudo apt install mysql-server` 或 `sudo dnf install mysql-server`

```sql
CREATE DATABASE learnspark CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'learnspark'@'localhost' IDENTIFIED BY '你的密码';
GRANT ALL ON learnspark.* TO 'learnspark'@'localhost';
FLUSH PRIVILEGES;
```

### 2. 启动后端

把 `server-1.0.0.jar` 放到任意目录，比如 `C:\learnspark\`（Windows）或 `~/learnspark/`（Linux），在同目录建一个 `application.properties`：

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/learnspark?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
spring.datasource.username=learnspark
spring.datasource.password=你的密码

learnspark.auth.jwt-secret=随便一个48字节以上的随机字符串
learnspark.storage.upload-dir=./uploads
```

**Windows**（PowerShell）：
```powershell
java -Dfile.encoding=UTF-8 -jar server-1.0.0.jar
```

**Linux**：
```bash
java -Dfile.encoding=UTF-8 -jar server-1.0.0.jar
```

启动成功后访问 `http://localhost:8080/actuator/health`，应返回 `{"status":"UP"}`。Flyway 会在首次启动时自动跑 9 个迁移建表。

> ⚠️ 如果后端既跑 API 又跑文件解析（单机用），直接用这个命令即可（profile 默认 `api`）。如果想拆 API 和 worker 到两台机器，加 `--spring.profiles.active=worker` 跑第二份。

### 3. 启动桌面客户端

| 平台 | 操作 |
|------|------|
| Windows | 双击 `LearnSpark-1.0.0.exe` |
| Linux (deb) | `sudo dpkg -i LearnSpark-1.0.0.deb`，应用菜单找 LearnSpark |
| Linux (tar.gz) | `tar -xzf LearnSpark-1.0.0-linux.tar.gz`，进目录运行 `./LearnSpark-1.0.0/bin/LearnSpark` |

应用打开后默认连 `http://localhost:8080`。登录页注册一个账号即可使用。

### 4. Android 客户端

把 `LearnSpark-1.0.0.apk` 传到手机，点开安装（可能要在系统设置里允许"安装未知来源"）。

如果手机和电脑不在同一台机器，桌面客户端也要改地址——见下面的"改后端地址"。

---

## 改后端地址

应用启动后进 **设置 → 服务器地址** 改成实际地址即可（这个配置存在本地，重启后保留）。

如果要改源码默认地址：
- 桌面端默认：`composeApp/src/desktopMain/kotlin/com/learnspark/core/network/ApiBaseUrl.kt`
- Android 端默认：`composeApp/src/androidMain/kotlin/com/learnspark/core/network/ApiBaseUrl.kt`
- Android 模拟器访问宿主机用 `10.0.2.2:8080`，真机和电脑同 WiFi 用电脑的局域网 IP

---

## 配置 AI

进 **设置 → AI 配置**，填对应 provider 的 API Key：

| Provider | 默认 baseUrl |
|----------|--------------|
| DeepSeek | `https://api.deepseek.com/v1` |
| OpenAI | `https://api.openai.com/v1` |
| 智谱 GLM | `https://open.bigmodel.cn/api/paas/v4` |
| 通义千问 DashScope | `https://dashscope.aliyuncs.com/compatible-mode/v1` |
| Moonshot | `https://api.moonshot.cn/v1` |

Key 用 AES-256 在服务端加密落库。每个用户每分钟最多 5 次 AI 调用。

---

## 怎么重新打客户端

```bash
cd learnspark-kmp
./gradlew :composeApp:packageExe    # Windows
./gradlew :composeApp:packageDeb    # Linux DEB
./gradlew :composeApp:assembleDebug  # Android APK
```

或者在 Windows 上跑 `scripts\build-release.ps1`，会自动收集产物到 `releases/`。

---

## 常见问题

**Q: 启动后端报 `UnsupportedEncodingException: utf8mb4`？**
JDBC URL 里的 `characterEncoding` 必须写 `UTF-8`，不能写 `utf8mb4`（这是 Java 字符串，不是 MySQL 字符集名）。

**Q: 启动后端报 `FlywayException: no schema history table`？**
加上 `spring.flyway.baseline-on-migrate=true`（默认配置已带，但如果你用外部 `application.properties` 覆盖了就要确认这条还在）。

**Q: 桌面端双击 EXE 闪退？**
用命令行跑 `LearnSpark-1.0.0.exe`，看控制台报错。99% 是缺 Visual C++ Redistributable，装一下 [VC_redist.x64.exe](https://aka.ms/vs/17/release/vc_redist.x64.exe)。

**Q: 桌面端连不上后端？**
- 同机：默认 `localhost:8080`，应能直连
- 不同机：进设置改地址；Windows 防火墙要放行 8080
- Android 模拟器：用 `10.0.2.2:8080`
- Android 真机：电脑和手机同 WiFi，用电脑的局域网 IP

**Q: 备份数据？**
```bash
mysqldump -u learnspark -p learnspark > backup_$(date +%F).sql
```
