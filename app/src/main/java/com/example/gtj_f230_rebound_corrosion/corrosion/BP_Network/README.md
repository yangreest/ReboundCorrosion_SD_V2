# 钢筋锈蚀度 BP 集成预测 —— Java 移植版

纯 Java 实现的 BP 集成锈蚀度预测工程，**不依赖任何第三方库**（无需 Maven / jar），
JDK 11+ 即可编译运行。类名、方法名、字段名、变量名沿用原 MATLAB 脚本命名。

## 一、目录结构

```
java/
├─ src/rustnet/
│  ├─ BpModel.java        单个 BP 网络(模型字段 W/b/Xm/Xs/ym/ys/vaMSE)
│  ├─ EnsembleModel.java  集成模型: bestModels / kTop / ACC_THRESH / featureNames, 加载文本模型
│  ├─ BpTrainer.java      bp_train: Adam + Mini-batch + L2 + 验证集早停
│  ├─ fenxi.java          训练入口: 10 重启取 Top-5 集成, 保存文本模型
│  └─ verify_model.java   验证入口: 读 CSV → 清洗 → 预测 → R²/RMSE/准确率 → 导出
├─ model/
│  └─ bp_model.txt        文本权重模型（Java 直接加载）
├─ classes/               已编译的 .class（可直接运行；删除后可用下面命令重新编译）
└─ README.md
```

## 二、编译（在 `java/` 目录下执行）

```bash
javac -encoding UTF-8 -d classes src\rustnet\*.java
```

> 本机若 `javac` 不在 PATH，可使用 JDK/JBR 全路径，例如：
> `"D:\pycharm\PyCharm Community Edition 2024.2.1\jbr\bin\javac.exe" -encoding UTF-8 -d classes src\rustnet\*.java`

## 三、运行

### 1. 外部数据验证 / 预测（对应 verify_model.m）

```bash
java -cp classes rustnet.verify_model <数据.csv> [模型 bp_model.txt]
```

- 不传参数时默认读取当前目录 `data.csv` 与 `model/bp_model.txt`。
- 含真实标签列时输出 R²、RMSE、准确率；无标签列时仅预测并导出结果。
- 结果导出为 `预测结果_<数据文件名>.csv`（V1~V7、真实锈蚀度、预测锈蚀度、绝对误差）。

### 2. 重新训练（对应 fenxi.m）

```bash
java -cp classes rustnet.fenxi <训练数据.csv> [输出模型.txt]
```

- 10 次重启，按验证集 MSE 取 Top-5 集成（`bestModels`），保存为与 `bp_model.txt` 同格式的文本模型。
- 默认输出 `model/bp_model.txt`。

## 四、数据准备（CSV）

纯 Java 不直接解析 `.xlsx`，请先用 Excel 打开数据文件 → **另存为 CSV（UTF-8）**，再传给上面的程序。

**必需列**：`V1 V2 V3 V4 V5 V6 V7`。

**可选列（按列名自动识别）**：

| MATLAB 变量 | 用途 | 匹配关键词 |
|---|---|---|
| `y_true` | 真实标签 | 含「锈蚀度 / 腐蚀度」，排除含「测试 / 软件」的对照列 |
| `x_ratio` | 商值 | 含「商值」 |
| `rust_amt` | 锈蚀量/深度 | 含「锈蚀量 / 腐蚀量 / 深度」 |
| `cov_thk` | 保护层厚度 | 精确匹配「保护层厚度」 |

**清洗规则（与 MATLAB/Python 版一致）**：按 V1~V7（+商值）去重 → 去缺失 → 商值 0.5~1.1、标签 >0、深度 ≥0 过滤。
标签量纲自动判断：中位数 <2 视为比例，自动 ×100 换算到训练量纲。

## 五、评价口径

- 准确率判定：**绝对误差 |预测−真实| ≤ 5 算成功**（不是相对误差百分比）。
- 指标：`R2n`、`RMSEN`、`accn`（成功数 / 有效样本数）。

## 六、模型文本格式（bp_model.txt）

```
kTop 5
accThresh 5.0
nModels 5
feature V1 V2 V3 V4 V5 V6 V7
=== model 0 ===
Xm <7 个输入均值>
Xs <7 个输入标准差>
ym <标签均值>
ys <标签标准差>
nLayers 4
W 0 128 7          # 第 0 层权重: out×in，下接 128 行、每行 7 个数
...
b 0 128            # 第 0 层偏置: 一行 128 个数
...
```

网络结构：`7 → 128 → 64 → 32 → 1`，隐藏层 ReLU，输出层线性。

## 七、与 MATLAB 的命名对应关系

| MATLAB | Java |
|---|---|
| `bp_predict(model, X)` | `BpModel.bp_predict(...)` / `EnsembleModel.bp_predict(...)` |
| `bp_train(Xtr, ytr)` | `BpTrainer.bp_train(Xtr, ytr, seed)` |
| 模型 struct 字段 `W / b / Xm / Xs / ym / ys / vaMSE` | `BpModel` 同名字段 |
| `bestModels` | `EnsembleModel.bestModels` |
| `kTop` | `EnsembleModel.kTop` |
| `ACC_THRESH` | `EnsembleModel.ACC_THRESH` |
| `featureNames` | `EnsembleModel.featureNames` |
| `fenxi.m` 主脚本 | `rustnet.fenxi` |
| `verify_model.m` 主脚本 | `rustnet.verify_model` |
| `gX / gy / Ng / ypn / R2n / RMSEN / ibn / accn` | 同名局部变量 |

## 八、已验证情况与限制

- **前向预测已核对**：用两批外部数据跑 Java 版，R²、RMSE 与 MATLAB/Python 结果逐位一致
  （1028 复测 R²=0.5548 / RMSE=11.674；1015 测试 R²=−1.4611 / RMSE=13.187）。
- **重训差异**：Java 随机数生成器与 MATLAB `randn` 不同，重新训练的权重不会与 MATLAB 逐位一致，
  但网络结构、超参、清洗规则、指标口径完全对应。
- 超参（复刻自 `fenxi.m`）：Adam β1=0.9、β2=0.999、eps=1e-8；初始学习率 0.003，按 0.995^epoch 衰减；
  L2 权重衰减 1e-4；batch=64；15% 验证集；早停容忍 200 轮；最多 3000 轮。
- 控制台中文若显示乱码，执行 `chcp 65001` 后再运行，或在启动参数加 `-Dfile.encoding=UTF-8`。
