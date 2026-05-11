# Nova Pixel Code Wiki

## 1. 项目概览

**Nova Pixel** 是一款全新一代高级像素画编辑器 Android 应用，提供了两个核心创作模式：
- **Canvas 模式**：传统可视化像素画创作
- **Code 模式**：通过 NovaScript 领域特定语言（DSL）来生成像素画和动画

应用采用了 Jetpack Compose 作为 UI 框架，Material 3 设计系统，配合自实现的 NovaScript 语言编译器和解释器。

### 核心特性
- 像素画绘制（笔刷、橡皮擦、填充、取色器）
- NovaScript DSL 编程生成像素画
- 双向同步（代码 → 画布，画布 → 代码）
- 帧动画系统
- 图层管理
- 历史记录（撤销/重做）
- 画布镜像对称模式
- 项目保存与导出

---

## 2. 项目结构

```
/workspace
├── app/
│   ├── src/main/
│   │   ├── kotlin/me/app/pixel/ide/
│   │   │   ├── MainActivity.kt                    # 应用入口
│   │   │   ├── ui/
│   │   │   │   ├── navigation/
│   │   │   │   │   └── AppNavigation.kt           # 应用导航管理
│   │   │   │   ├── home/                          # 首页模块
│   │   │   │   │   ├── HomeScreen.kt
│   │   │   │   │   ├── HomeViewModel.kt
│   │   │   │   │   └── HomeContract.kt
│   │   │   │   ├── newcanvas/                     # 新建画布模块
│   │   │   │   │   ├── NewCanvasScreen.kt
│   │   │   │   │   └── NewCanvasViewModel.kt
│   │   │   │   ├── editor/                        # 编辑器核心模块
│   │   │   │   │   ├── EditorScreen.kt            # 编辑器主界面
│   │   │   │   │   ├── EditorViewModel.kt         # 编辑器视图模型
│   │   │   │   │   ├── EditorContract.kt          # 状态与意图定义
│   │   │   │   │   ├── DrawingEngine.kt           # 绘图引擎
│   │   │   │   │   ├── CodeEngine.kt              # 代码执行引擎
│   │   │   │   │   ├── CodeGenerator.kt           # 画布 → 代码生成器
│   │   │   │   │   ├── HistoryManager.kt          # 历史记录管理
│   │   │   │   │   ├── EditorCanvasArea.kt        # 画布区域
│   │   │   │   │   ├── EditorCodeArea.kt          # 代码编辑区域
│   │   │   │   │   ├── EditorFrameStrip.kt        # 帧条
│   │   │   │   │   ├── EditorBottomDock.kt        # 底部工具栏
│   │   │   │   │   ├── EditorTopIsland.kt         # 顶部栏
│   │   │   │   │   ├── EditorCanvasPanel.kt       # 图层面板
│   │   │   │   │   ├── ColorPaletteDialog.kt      # 调色板对话框
│   │   │   │   │   └── dsl/                       # NovaScript DSL 模块
│   │   │   │   │       ├── NovaAst.kt             # 抽象语法树定义
│   │   │   │   │       ├── NovaCompiler.kt        # 词法分析 + 语法分析
│   │   │   │   │       └── NovaInterpreter.kt     # 解释执行引擎
│   │   │   │   ├── project/                       # 项目管理
│   │   │   │   │   └── ProjectManagementScreen.kt
│   │   │   │   └── theme/                         # 主题配置
│   │   │   │       ├── Color.kt
│   │   │   │       ├── Theme.kt
│   │   │   │       └── Type.kt
│   │   │   └── components/
│   │   │       └── MarkdownRenderer.kt
│   │   ├── assets/
│   │   │   └── NovaScript_Reference.md            # NovaScript 语言文档
│   │   ├── res/                                    # Android 资源文件
│   │   └── AndroidManifest.xml
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── gradle/
│   └── libs.versions.toml                         # 版本目录
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
└── gradlew / gradlew.bat
```

---

## 3. 核心模块详解

### 3.1 应用入口与导航

#### MainActivity.kt
- **职责**：应用的主入口点
- **关键功能**：
  - 启用 Edge-to-Edge 沉浸式体验
  - 配置 Compose 主题
  - 初始化 `AppNavigation` 导航

#### AppNavigation.kt
- **职责**：管理应用内所有页面导航
- **路由**：
  - `home` → 首页
  - `new_canvas` → 新建画布设置页
  - `editor/{name}/{width}/{height}/{bg}` → 编辑器页
  - `project_management` → 项目管理页
  - `editor_open/{filePath}` → 打开已存项目的编辑器页

---

### 3.2 编辑器核心模块

#### EditorViewModel.kt
- **职责**：编辑器的核心状态管理和业务逻辑
- **架构模式**：MVI（Model-View-Intent）
- **主要状态**（EditorState）：
  - `canvases`: 所有独立画布列表
  - `activeCanvasIndex`: 当前激活画布索引
  - `currentMode`: 当前编辑模式（CANVAS 或 CODE）
  - `selectedTool`: 当前选择工具
  - `currentColor`: 当前颜色
  - `codeContent`: NovaScript 代码内容
  - `isPlaying`: 动画播放状态
  - `playbackFps`: 帧率设置

- **主要意图**（EditorIntent）：
  - `InitProject` / `InitProjectFromFile`: 初始化项目
  - `DrawAction`: 画布绘制操作
  - `SelectionAction`: 选区操作
  - `UpdateCode`: 更新代码内容
  - `SyncCodeToCanvas`: 代码同步到画布
  - `Undo` / `Redo`: 撤销/重做
  - `TogglePlay`: 播放/暂停动画

#### EditorScreen.kt
- **职责**：编辑器 UI 主容器
- **主要组件**：
  - `EditorTopIsland`: 顶部导航栏
  - `EditorCanvasArea` / `EditorCodeArea`: 主编辑区域（根据模式切换）
  - `EditorBottomDock`: 底部工具栏
  - `EditorFrameStrip`: 帧条（动画帧列表）
  - `EditorCanvasPanel`: 图层面板
  - 各种对话框：调色板、帧设置等

---

### 3.3 绘图引擎

#### DrawingEngine.kt
- **职责**：底层像素级绘制操作
- **主要功能**：
  - `drawStroke()`: 绘制笔触（支持 Bresenham 画线、圆形/方形笔刷、对称模式）
  - `performFloodFill()`: 洪水填充算法
  - `extractSelection()`: 提取选区为浮动图层
  - `mergeSelection()`: 合并选区回画布
  - `createClearBitmap()`: 创建空白画布
  - `createExportBitmap()`: 创建导出用位图

---

### 3.4 NovaScript DSL 系统

#### 整体架构
NovaScript 的执行流程：
```
源代码 → NovaLexer → Token 列表 → NovaParser → AST → NovaInterpreter → 像素画布/动画
```

#### NovaAst.kt
- **职责**：定义抽象语法树（AST）节点
- **核心节点**：
  - `Expr`（表达式）：`NumLiteral`, `ColorLiteral`, `StrLiteral`, `Variable`, `Binary`, `Unary`, `FuncCall`, `ListLiteral`, `IndexAccess`
  - `Stmt`（语句）：`Assign`, `FuncCall`, `Block`, `Repeat`, `If`, `MacroDef`, `Mirror`, `Offset`

#### NovaCompiler.kt
- **职责**：词法分析（Lexing）和语法分析（Parsing）
- **NovaLexer**：
  - 将源代码拆分为 Token 序列
  - 处理缩进（Indentation-based 语法）
  - 支持十六进制颜色（`#RRGGBB` / `#AARRGGBB`）作为原生 Token
  - Token 类型：数字、颜色、字符串、标识符、关键字、运算符等

- **NovaParser**：
  - 将 Token 序列解析为 AST
  - 支持 Python 风格的缩进块
  - 支持宏定义（动作宏、计算宏）
  - 抛出 `NovaCompileException` 语法错误

#### NovaInterpreter.kt
- **职责**：执行 AST，生成像素画和动画
- **核心功能**：
  - 变量环境管理
  - 宏定义与调用
  - 空间变换（offset, mirror）
  - 绘图指令执行
  - 帧动画管理
  - 数学表达式求值（支持：`+ - * / %`，比较，len/abs/min/max/clamp 等）

- **内置绘图函数**：
  - `dot(x, y, color)`: 绘制像素点
  - `line(x1, y1, x2, y2, color)`: 画线
  - `rect(x, y, w, h, color)` / `fillRegion(...)`: 填充矩形
  - `circle(x, y, r, color)` / `drawCircle(...)`: 画圆
  - `erase(x, y, w, h)`: 擦除区域
  - `clear()`: 清空画布

- **动画与内存**：
  - `save("name")`: 保存内存快照
  - `load("name")`: 加载快照
  - `stamp("name", x, y)`: 盖印快照
  - `copyFrame()`: 复制当前帧进入下一帧
  - `newFrame()`: 新建空白帧
  - `setFps(fps)`, `setHolds(holds)`: 动画设置

#### CodeEngine.kt
- **职责**：封装 NovaScript 的完整执行流程
- **输入**：源代码字符串、画布尺寸
- **输出**：`RenderResult`（帧列表、fps、holds）

#### CodeGenerator.kt
- **职责**：将画布操作逆向生成 NovaScript 代码（画布 → 代码）
- **主要函数**：`generateDiffToDsl(oldCode, historyAction)`

---

### 3.5 历史记录系统

#### HistoryManager.kt
- **职责**：管理绘制操作的撤销与重做
- **存储方式**：分块存储（只保存变化的矩形区域）
- **核心数据结构**：
  - `HistoryAction`: 单次绘制操作记录（画布索引、帧索引、变化区域、新旧像素块）
- **限制**：默认保留最近 30 步历史

---

## 4. 关键类与函数

### 4.1 数据类与枚举

#### EditorContract.kt
- `EditorMode`: `CANVAS`, `CODE`
- `Tool`: `BRUSH`, `ERASER`, `FILL`, `PICKER`, `SELECT`, `MOVE`
- `BrushShape`: `SQUARE`, `CIRCLE`
- `SymmetryMode`: `OFF`, `HORIZONTAL`, `VERTICAL`, `QUAD`
- `DrawEvent`: `START`, `MOVE`, `END`
- `PixelFrame`: 单帧数据（Bitmap）
- `PixelCanvas`: 独立画布（多帧动画、名称、当前帧索引）

### 4.2 核心函数速查

| 类/文件 | 关键函数 | 功能 |
|--------|---------|------|
| `DrawingEngine` | `drawStroke()` | 绘制笔触（包含 Bresenham 算法） |
| `DrawingEngine` | `performFloodFill()` | 洪水填充 |
| `NovaInterpreter` | `execute()` | 执行 NovaScript 代码 |
| `CodeEngine` | `executeToFrames()` | 执行代码并返回所有帧 |
| `HistoryManager` | `undo()`, `redo()` | 撤销/重做操作 |
| `EditorViewModel` | `processIntent()` | 处理用户意图 |

---

## 5. 依赖关系

### 5.1 外部库

项目使用 Gradle Version Catalog (`libs.versions.toml`) 管理依赖：

| 库 | 版本 | 用途 |
|---|-----|------|
| `androidx.core:core-ktx` | 1.17.0 | Android KTX 扩展 |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.9.2 | 生命周期管理 |
| `androidx.activity:activity-compose` | 1.11.0 | Compose Activity 集成 |
| `androidx.compose:compose-bom` | 2025.10.01 | Compose BOM |
| `androidx.navigation:navigation-compose` | 2.8.8 | Compose 导航 |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.8.7 | ViewModel + Compose |
| Material3 | - | UI 组件库 |
| Material Icons Extended | - | 扩展图标库 |

### 5.2 模块间依赖关系

```
MainActivity
    ↓
AppNavigation
    ↓
    ├─→ HomeScreen
    │      ↓
    │   HomeViewModel
    │
    ├─→ NewCanvasScreen
    │
    └─→ EditorScreen
           ↓
    EditorViewModel
           ↓
    ┌─────────────────┬─────────────────┬─────────────────┐
    ↓                 ↓                 ↓                 ↓
DrawingEngine    CodeEngine     HistoryManager   CodeGenerator
                      ↓
              ┌───────────────┐
              ↓               ↓
         NovaCompiler   NovaInterpreter
              ↓
          NovaLexer → NovaParser → NovaAst
```

---

## 6. 构建与运行

### 6.1 环境要求
- **Android Gradle Plugin**: 8.13.0
- **Kotlin**: 2.1.0
- **minSdk**: 29 (Android 10)
- **targetSdk**: 34
- **compileSdk**: 36
- **JDK**: Java 17

### 6.2 构建命令

```bash
# 构建 Debug 版本
./gradlew assembleDebug

# 构建 Release 版本
./gradlew assembleRelease

# 安装到连接设备
./gradlew installDebug

# 运行测试
./gradlew test
```

### 6.3 项目配置

- **命名空间**：`me.app.pixel.ide`
- **应用 ID**：`me.app.pixel.ide`
- **版本**：1.1.0 (versionCode: 2)

---

## 7. 架构设计模式

### 7.1 MVI 架构
项目核心采用 **MVI (Model-View-Intent)** 架构：
- **State**：单向数据流，单一可信源
- **Intent**：用户操作意图
- **Effect**：一次性副作用（导航、Toast、显示菜单等）

### 7.2 状态管理
使用 `MutableStateFlow` + `collectAsState()` 进行响应式状态管理。

### 7.3 协程使用
- `viewModelScope`：ViewModel 内异步任务
- `Dispatchers.Default`：后台计算（代码生成、编译）
- `delay()`：动画播放调度

---

## 8. 关键算法

### 8.1 Bresenham 直线算法
- **位置**：`DrawingEngine.drawStroke()`、`NovaInterpreter` 内置 `line()` 函数
- **用途**：绘制任意方向的无锯齿像素直线

### 8.2 洪水填充算法
- **位置**：`DrawingEngine.performFloodFill()`
- **实现**：基于栈的扫描线填充算法

---

## 9. 扩展与开发

### 9.1 添加新的绘图工具
1. 在 `EditorContract.kt` 的 `Tool` 枚举中添加新工具
2. 在 `EditorBottomDock.kt` 中添加 UI 按钮
3. 在 `EditorViewModel.processIntent(DrawAction)` 中处理绘制逻辑
4. 可选：在 `DrawingEngine` 中添加底层绘制函数

### 9.2 扩展 NovaScript 语言
1. **添加新内置函数**：在 `NovaInterpreter.executeFunction()` 中添加 case 分支
2. **添加新语法**：
   - 在 `NovaAst.kt` 中添加新的 AST 节点
   - 在 `NovaLexer` 中添加 Token 识别
   - 在 `NovaParser` 中添加语法解析
   - 在 `NovaInterpreter` 中添加执行逻辑

### 9.3 自定义主题
- 修改 `ui/theme/Color.kt` 颜色配置
- 修改 `ui/theme/Theme.kt` 主题组合函数

---

## 10. 参考文档

- 完整的 NovaScript 语言文档：[NovaScript_Reference.md](file:///workspace/app/src/main/assets/NovaScript_Reference.md)
