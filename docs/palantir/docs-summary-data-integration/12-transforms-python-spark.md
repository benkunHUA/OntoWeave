# Spark 转换（Transforms Python Spark）

> **分类说明**：基于 PySpark 的 Python Spark 转换开发指南：Spark API 用法、性能与调试

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `transforms-python-spark` |
| 文档数量 | 25 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-17 |

---

## 文档列表

### 1. Python (Spark) • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python-spark/overview/>
- **文档摘要**：本页概述基于 PySpark 的 Python transforms 开发指南。页面说明 Python transforms 可配置单节点或多节点计算引擎，并汇总了 Spark 相关的文档入口，包括增量管道、Velox 加速、文件读取、输出列元数据、Foundry connectors、Transforms Python API 参考、PySpark 参考指南与风格指南等。

### 2. Python (Spark) • Incremental transforms • Examples

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python-spark/incremental-examples/>
- **文档摘要**：本页提供增量可计算 transform 的丰富示例。涵盖 Append、合并追加、合并替换、合并替换变体 schema、利用增量 transform 关联大数据集、处理 schema 或逻辑变更、在分支上开发增量代码等场景，并说明了目录事务错误等增量 Python 报错问题。

### 3. Python (Spark) • Incremental transforms • Incremental media sets

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python-spark/incremental-media-sets/>
- **文档摘要**：本页说明如何以增量方式读写 media sets。介绍通过 @incremental(v2_semantics=True) 装饰器使输入与输出 media set 均支持增量，并讲解增量计算的前提条件、读写模式、与分支及事务无关 media set 的配合、中止增量 transform 以及限制输入批量大小等内容。

### 4. Python (Spark) • Incremental transforms • Limit batch size of incremental inputs

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python-spark/incremental-transaction-limits/>
- **文档摘要**：本页说明如何为增量 transform 的输入配置事务数量上限（transaction limit）。通常增量构建会在单个作业中处理所有未处理事务，但输入积累大量事务时可通过限制每个作业读取量来规避风险，并介绍建立构建计划、查看事务范围、理解读取范围及相关限制。

### 5. Python (Spark) • Accelerate Spark with Velox

- **文档类型**：快速入门
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python-spark/velox-overview/>
- **文档摘要**：本页介绍利用 Velox 加速 Spark 作业的方法。Velox 是 Foundry 中用于加速 Spark 的高性能底层数据处理库，可通过升级 Python 仓库、配置堆外内存并启用 VELOX 后端来提升性能，无需改动现有逻辑。页面还给出快速起步与加速内存配置建议。

### 6. Python (Spark) • Read files in a repository

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python-spark/read-files-repository/>
- **文档摘要**：本页说明如何从代码仓库中读取其他文件到 transform 上下文。通过在 setup.py 中用 package_data 打包 yaml、csv 等文件，并使用 pkg_resources.resource_stream 在 transform 中读取配置文件，便于为转换逻辑设置参数。页面给出配置与读取代码示例。

### 7. Python (Spark) • Output column metadata

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python-spark/output-column-metadata/>
- **文档摘要**：本页介绍在 Code Repository Transforms 中读写输出数据集的列描述与列类型（column typeclasses）。说明通过 write_dataframe() 的 column_descriptions 参数写入列描述，以及使用 column_typeclasses 读取和写入类型类，并给出相应代码示例。

### 8. Python (Spark) • Foundry connectors

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python-spark/transforms-python-foundry-connectors/>
- **文档摘要**：本页是 Foundry connectors 的参考手册，介绍如何通过 transforms API 与 Foundry 交互。核心类 FoundryConnector 可用于交互式构建 TransformInput 与 TransformOutput 对象并运行 transform，详细列出其构造参数、input、output、run 方法及属性说明。

### 9. Python (Spark) • AIP • Use Palantir-provided language models within transforms

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python-spark/palantir-provided-models/>
- **文档摘要**：本页说明如何在 Python transforms（含 Spark 与轻量 transform）中使用 Palantir 提供的语言与嵌入模型。通过 palantir_models 库接入，介绍启用 AIP 的前提、仓库与 transform 配置、生成文本补全、生成嵌入向量、使用视觉语言模型抽取 PDF 内容等操作步骤。

### 10. Python (Spark) • PySpark reference • Overview

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python-spark/pyspark-overview/>
- **文档摘要**：本页概述 PySpark transforms 的基础概念。解释 PySpark 作为对接 Apache Spark 后端的封装语言，适合处理分布式大规模数据集，并说明 DataFrame 的不可变、惰性求值与 RDD 等特性，以及起始代码、命名列、链式 transform、窗口函数与在 Foundry 中编写 PySpark 的要点。

### 11. Python (Spark) • PySpark reference • Coming from Python

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python-spark/pyspark-coming-from-python/>
- **文档摘要**：本页面向有 Python 经验的开发者，讲解从过程式思维转向 PySpark 的方式。对比过程式 Python、声明式 SQL 与 PySpark 的差异，说明应以 DataFrame 整体和列为单位处理数据，并给出列表转换、自定义 Python 逻辑等示例，帮助理解 Spark 的惰性求值。

### 12. Python (Spark) • PySpark reference • Syntax cheat sheet

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python-spark/pyspark-syntax-cheat-sheet/>
- **文档摘要**：本页是 PySpark SQL 常用模式与函数的速查表（cheat sheet）。分类汇总常见模式、日志输出、函数与类型导入、过滤、连接、列操作、类型转换、字符串/数值/日期操作、数组与结构体、聚合、窗口及高级操作（如重分区与 UDF）等常用代码片段。

### 13. Python (Spark) • PySpark reference • Style guide

- **文档类型**：最佳实践
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python-spark/pyspark-style-guide/>
- **文档摘要**：本页给出 PySpark 代码风格指南与最佳实践。针对 PySpark 特有的代码模式提出规范，如优先隐式列选择、操作 Column 而非 DataFrame、避免覆盖列、避免循环列、使用 select 约定 schema、UDF 与 collect 注意事项等，并建议启用 Pylint 保持一致风格。

### 14. Python (Spark) • PySpark reference • Concept: Columns

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python-spark/pyspark-columns/>
- **文档摘要**：本页是 PySpark 列（Column）概念参考，讲解列的获取与操作。涵盖获取 schema（columns、dtypes）、select 选择列、withColumn 创建或更新列、重命名、drop 删除、cast 类型转换以及 when/otherwise 条件表达式等方法，并给出代码示例。

### 15. Python (Spark) • PySpark reference • Concept: Queries

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python-spark/pyspark-queries/>
- **文档摘要**：本页是 PySpark 查询概念参考，聚焦去重、空值处理、行数限制与排序等操作。介绍 distinct、drop_duplicates、dropna 过滤空值、limit 行数限制以及 sort/orderBy 与 asc/desc 排序等 DataFrame 方法，并说明相关参数用法。

### 16. Python (Spark) • PySpark reference • Concept: User-defined functions

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python-spark/pyspark-udfs/>
- **文档摘要**：本页讲解 PySpark 中的用户自定义函数（UDF）。说明 UDF 允许在 PySpark 中嵌入任意 Python 逻辑，介绍其动机、示例、读取多列以及调试方法，并指出应尽量优先使用内置函数，仅在必要时使用 UDF 以保证性能。

### 17. Python (Spark) • PySpark reference • Filtering

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python-spark/pyspark-filtering/>
- **文档摘要**：本页是 PySpark 过滤（Filtering）操作参考。讲解 filter/where 按布尔表达式筛选行、常量（literal）比较、逻辑运算、like/rlike 正则匹配、startswith/endswith/contains、substr 子串、isin 与 between 区间等常用列方法，并给出代码示例。

### 18. Python (Spark) • PySpark reference • Dates and timestamps

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python-spark/pyspark-dates/>
- **文档摘要**：本页汇总 PySpark 处理日期与时间戳的函数。分为日期加减（date_add、add_months 等）、日期取值（year、quarter 等）、格式化（to_date、to_timestamp、date_format）以及由 long 转换为 timestamp、截断等内容，并附日期格式语法参考。

### 19. Python (Spark) • PySpark reference • Strings

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python-spark/pyspark-strings/>
- **文档摘要**：本页汇总 PySpark 处理字符串（Strings）的常用函数。分类列出大小写转换（initcap、lower、upper）、拼接与拆分（concat、split）、子串（substring、instr）、修剪与填充（trim、lpad）、正则（regexp_extract、regexp_replace）及其他杂项字符串函数。

### 20. Python (Spark) • PySpark reference • Math

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python-spark/pyspark-math/>
- **文档摘要**：本页汇总 PySpark 的数学（Math）函数。分类列出四舍五入与取整（round、ceil、floor、bround）、对数（log、log10）、随机（rand、randn）、三角函数（cos、sin、tan 等）、角度转换（degrees、radians）及其他杂项函数（abs、pow、sqrt 等）。

### 21. Python (Spark) • PySpark reference • Joins

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python-spark/pyspark-joins/>
- **文档摘要**：本页讲解 PySpark 中的数据连接（Joins）。介绍使用 .join() 方法完成简单左连接、按多字段连接、基于任意表达式的高级连接约束以及交叉连接（Cartesian product），并提示处理列名冲突、重命名前缀等注意事项。

### 22. Python (Spark) • PySpark reference • Aggregation and pivot tables

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python-spark/pyspark-aggregation/>
- **文档摘要**：本页介绍 PySpark 的聚合与透视表（pivot）操作。讲解推荐的 groupBy 与 agg 聚合语法、使用窗口函数保留原行列追加聚合值、groupBy 后 pivot 生成透视表，并列出常用聚合函数（avg、collect_list、max 等）说明。

### 23. Python (Spark) • PySpark reference • Window

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python-spark/pyspark-window/>
- **文档摘要**：本页讲解 PySpark 窗口函数（Window）。说明窗口函数可在不丢失非聚合列的前提下为行追加聚合或其他值，介绍定义窗口 partitionBy/orderBy 并在其上进行聚合的写法，以及 dense_rank、lag、lead、row_number 等专用窗口函数。

### 24. Python (Spark) • PySpark reference • Logging

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python-spark/pyspark-logging/>
- **文档摘要**：本页介绍在 Foundry 中输出 PySpark 调试信息的日志（Logging）方法。说明在 Code Workbook 中用 print、在 Code Repositories 中用 logging 库输出及查看位置，并讲解在 UDF 内部记录日志的替代方案，以及列检查、验证连接与查看查询计划等示例。

### 25. Python (Spark) • PySpark reference • Other

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python-spark/pyspark-other/>
- **文档摘要**：本页汇总 PySpark 其他常用函数。分类列出集合（array、size、struct 等）、排序（asc、desc）、二进制位运算、空值处理（coalesce、isnull 等）、列操作（explode、expr 等）、JSON（from_json、to_json 等）以及 checkpoint 与 localCheckpoint 使用说明。
