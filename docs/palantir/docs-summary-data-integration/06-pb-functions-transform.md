# Pipeline Builder 转换函数

> **分类说明**：Pipeline Builder 转换（Transform）函数参考手册：涵盖聚合、数组、字符串、日期时间、类型转换、条件与窗口等函数

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `pb-functions-transform` |
| 文档数量 | 88 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-17 |

---

## 文档列表

### 1. Pipeline Builder Transforms • Aggregate

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/aggregateV1/>
- **文档摘要**：本页介绍 Aggregate 函数：在输入数据集上按一组列分组并执行指定的聚合操作。支持 Batch 与 Faster 模式，参数包括聚合表达式列表、数据集及可选的按列分组列，适用于求和、计数等分组统计。

### 2. Pipeline Builder Transforms • Aggregate on condition

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/aggregateOnConditionV1/>
- **文档摘要**：本页介绍 Aggregate on condition 函数：基于条件语句对匹配的列应用聚合表达式。支持 Batch 与 Faster 模式，可对所有列或指定类型列（如整数列）批量聚合，并可按列分组，适合条件化批量统计。

### 3. Pipeline Builder Transforms • Aggregate over window

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/aggregateOverWindowV2/>
- **文档摘要**：本页介绍 Aggregate over window 函数：在流式（Streaming）窗口内执行聚合并依触发器输出。可配置窗口、累积模式、按键分区列与触发器，用于流数据的窗口化聚合计算。

### 4. Pipeline Builder Transforms • Anti join

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/complexAntiJoinV1/>
- **文档摘要**：本页介绍 Anti join 函数：对左右数据集做反连接，剔除左表中与右表满足连接条件的所有行。支持 Batch 与 Faster 模式，可指定左表列选择条件与连接条件，用于差集式数据过滤。

### 5. Pipeline Builder Transforms • Apply expression

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/applyExpressionV1/>
- **文档摘要**：本页介绍 Apply expression 函数：通过对输入数据集应用单个表达式进行转换。支持 Batch、Faster 与 Streaming 模式，参数为数据集与表达式，常用于列计算（如距离单位换算）。

### 6. Pipeline Builder Transforms • Apply multiple expressions

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/projectV1/>
- **文档摘要**：本页介绍 Apply multiple expressions（projectV1）函数：通过选择列或对列应用函数来转换数据集。支持多种运行模式，提供列转换列表、数据集及是否保留未投影列的开关，可一次完成多列处理。

### 7. Pipeline Builder Transforms • Apply over window

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/applyOverWindowV1/>
- **文档摘要**：本页介绍 Apply over window 函数：对流式数据按窗口收集成批行并应用指定操作产出输出行。支持 Streaming，可配置窗口、按键分区列与处理操作，用于窗口化的批量行处理。

### 8. Pipeline Builder Transforms • Apply to multiple columns

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/projectOnConditionV1/>
- **文档摘要**：本页介绍 Apply to multiple columns（projectOnConditionV1）函数：对满足条件 schema 的列统一应用表达式。支持 Batch、Faster 与 Streaming，可保留未匹配列与原始列，适用于按类型或正则批量改列。

### 9. Pipeline Builder Transforms • Array elements to columns

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/arrayToColumnsV1/>
- **文档摘要**：本页介绍 Array elements to columns（arrayToColumnsV1）函数：将数组中的元素提取为独立的列。支持 Batch 与 Faster 模式，参数为数组表达式、提取列名列表与数据集，数组元素不足时对应列为 null。

### 10. Pipeline Builder Transforms • Assign timestamps and watermarks

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/assignTimestampsAndWatermarksV1/>
- **文档摘要**：本页介绍 Assign timestamps and watermarks 函数：为流式输入分配时间戳与水印，并过滤掉时间戳为空的记录。支持 Streaming，可配置时间戳表达式、空闲超时与是否每条记录发水印等参数。

### 11. Pipeline Builder Transforms • Coalesce data

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/coalesceV1/>
- **文档摘要**：本页介绍 Coalesce data 函数：减少数据集的分区数量以降低并行度。支持 Batch 与 Faster 模式，仅合并分区而无 shuffle；若目标分区数大于当前数则保持不变，参数为数据集与分区数。

### 12. Pipeline Builder Transforms • Compute if expression absent

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/computeExpressionIfAbsentV1/>
- **文档摘要**：本页介绍 Compute if expression absent 函数：仅对新行计算表达式，且对同一键的结果只计算一次（跨构建也复用）。支持 Batch 与 Faster，可配置存储 id、是否禁用存储及不存储条件等。

### 13. Pipeline Builder Transforms • Convert media set to table rows

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/loadMediaSetReferencesV2/>
- **文档摘要**：本页介绍 Convert media set to table rows（loadMediaSetReferencesV2）函数：生成包含媒体集内媒体引用与基本元数据的数据集。支持 Batch，可指定媒体集与是否按路径去重（仅快照模式）。

### 14. Pipeline Builder Transforms • Cross join

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/complexCrossJoinV1/>
- **文档摘要**：本页介绍 Cross join 函数：将左右数据集做笛卡尔积交叉连接。支持 Batch 与 Faster 模式，可分别指定左右表列选择条件、右表列前缀，输出为两表所有行的组合。

### 15. Pipeline Builder Transforms • Date distribution

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/dateDistributionV1/>
- **文档摘要**：本页介绍 Date distribution 函数：计算指定列中日期/时间戳的分布。支持 Batch，参数为目标列、数据集、起止时间与时长分桶单位（日/时/分/月/秒/周/年），用于时间维度统计。

### 16. Pipeline Builder Transforms • Decompress gzip files

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/ungzipFilesV1/>
- **文档摘要**：本页介绍 Decompress gzip files 函数：对包含 gzip 文件的数据集中的每个文件进行解压。支持 Batch，参数为待处理文件数据集；预览需具备编辑器权限。

### 17. Pipeline Builder Transforms • Decompress tar files

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/untarFilesV1/>
- **文档摘要**：本页介绍 Decompress tar files 函数：对包含 tar 文件的数据集中的每个文件进行解压。支持 Batch，参数为待处理文件数据集；预览解压后节点需具备编辑器权限。

### 18. Pipeline Builder Transforms • Drop columns

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/dropV1/>
- **文档摘要**：本页介绍 Drop columns 函数：从输入数据集中删除指定列。支持 Batch、Faster 与 Streaming 模式，参数为待删除列集合与数据集，常用于裁剪不需要的字段。

### 19. Pipeline Builder Transforms • Drop duplicates

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/dropDuplicatesV1/>
- **文档摘要**：本页介绍 Drop duplicates 函数：从输入中删除重复行。支持 Batch 与 Faster 模式，可指定列子集判定唯一性，未指定则按整行完全相同去重。

### 20. Pipeline Builder Transforms • Empty file

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/emptyFileV1/>
- **文档摘要**：本页介绍 Empty file 函数：创建一个空文件数据集。支持 Batch，可指定输出数据集的读取模式（增量/快照/流/未知），用于占位或初始化空输出。

### 21. Pipeline Builder Transforms • Empty media set file

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/emptyMediaSetFileV1/>
- **文档摘要**：本页介绍 Empty media set file 函数：创建具有给定 Schema 且为快照读取模式的空媒体集文件。支持 Batch 与 Streaming，参数为输出契约所需的 Schema。

### 22. Pipeline Builder Transforms • Empty table

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/emptyTableV1/>
- **文档摘要**：本页介绍 Empty table 函数：创建具有给定 Schema 与读取模式的空表。支持 Batch、Faster 与 Streaming，参数为 Schema 与读取模式（可选），常用于构建空输出契约。

### 23. Pipeline Builder Transforms • Extract file metadata from dataset as rows

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/extractFileMetadataAsRowsV1/>
- **文档摘要**：本页介绍 Extract file metadata from dataset as rows 函数：将文件数据集的文件元数据读取为行。支持 Batch 与 Faster，可额外包含文件修改时间、字节大小、分支与数据集 rid 等元数据列。

### 24. Pipeline Builder Transforms • Extract many struct fields

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/getManyStructFieldsV1/>
- **文档摘要**：本页介绍 Extract many struct fields 函数：从 struct 中提取多个字段，并删除原 struct 列。支持 Batch 与 Faster，参数为包含 struct 的数据集、字段定位器与输入 struct 列。

### 25. Pipeline Builder Transforms • Extract rows from a CSV file

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/parseCsvV1/>
- **文档摘要**：本页介绍 Extract rows from a CSV file 函数：读取文件数据集并将每个 CSV 文件解析为行。支持 Batch，可配置 Schema、分隔符、引号、编码、是否含表头等参数，灵活适配各类 CSV。

### 26. Pipeline Builder Transforms • Extract rows from a GeoJSON file

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/parseGeoJsonV1/>
- **文档摘要**：本页介绍 Extract rows from a GeoJSON file 函数：将每个 GeoJSON 文件解析为带几何列的行。支持 Batch，支持多行 FeatureCollection 或单行 Feature，可指定提取属性、坐标系等，用于地理空间数据解析。

### 27. Pipeline Builder Transforms • Extract rows from a JSON file

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/parseJsonV1/>
- **文档摘要**：本页介绍 Extract rows from a JSON file 函数：读取文件数据集并将每个 JSON 文件解析为行。支持 Batch，可配置是否允许 JSON 值跨多行及解析所用 Schema。

### 28. Pipeline Builder Transforms • Extract rows from a dataset of email files

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/extractEmailDataAsRowsV1/>
- **文档摘要**：本页介绍 Extract rows from a dataset of email files 函数：读取邮件文件数据集并将每个文件解析为一行。支持 Batch，扩展名含 .eml/.emltpl/.msg，可提取附件、收发件人、正文等元数据。

### 29. Pipeline Builder Transforms • Extract rows from a dataset of text files

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/parseTextFileV1/>
- **文档摘要**：本页介绍 Extract rows from a dataset of text files 函数：读取文本文件数据集并将每个文件解析为一行。支持 Batch，参数为待处理文件数据集，适用于纯文本入库。

### 30. Pipeline Builder Transforms • Extract rows from an Excel file

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/parseExcelV2/>
- **文档摘要**：本页介绍 Extract rows from an Excel file 函数：读取 Excel 文件数据集并将每个文件解析为行。支持 Batch，格式含 .xls/.xlsx 等，可配置跳过行、列名、源工作表正则匹配等，大文件需注意内存设置。

### 31. Pipeline Builder Transforms • Extract rows from an XML file

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/xmlTagExtractV1/>
- **文档摘要**：本页介绍 xmlTagExtractV1 函数：读取文件数据集，将每个 XML 文件解析为行。按指定 XML 标签生成一行，并支持属性前缀、编码类型、值标签等可选参数，常用于从 XML 文件提取结构化数据。

### 32. Pipeline Builder Transforms • Extract rows from shapefile

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/parseShapefileV1/>
- **文档摘要**：本页介绍 parseShapefileV1 函数：读取文件数据集，将每个 shapefile 解析为行（忽略 .shp/.shx/.dbf 之外的文件）。输出含几何列及各属性列，仅支持点、线、面、多点几何类型，属性名与值仅支持 UTF-8 编码。

### 33. Pipeline Builder Transforms • Filter

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/filterV1/>
- **文档摘要**：本页介绍 filterV1 函数：根据指定过滤条件筛选输入数据集，条件为真则保留、为假则删除。支持批处理、Faster 与流式模式，空值按假值处理，是常用的数据准备转换。

### 34. Pipeline Builder Transforms • Filter files

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/filterFilesV1/>
- **文档摘要**：本页介绍 filterFilesV1 函数：对文件数据集应用过滤表达式，仅保留满足条件的文件。支持批处理模式，常用于在后续处理前筛选特定的输入文件。

### 35. Pipeline Builder Transforms • First union by name

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/firstUnionByNameV1/>
- **文档摘要**：本页介绍 firstUnionByNameV1 函数：按第一个数据集的列名对多个数据集求并集，缺失列补空值、非首个数据集新增的列被移除。适用于列结构不完全一致的数据集合并。

### 36. Pipeline Builder Transforms • Flatten struct

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/flattenStructV2/>
- **文档摘要**：本页介绍 flattenStructV2 函数：将结构体（struct）中的字段展开为输出数据集的列。可指定最大展开深度、列前缀及嵌套字段分隔符，支持批处理、Faster 与流式模式。

### 37. Pipeline Builder Transforms • Frequent pattern growth

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/fpGrowthV1/>
- **文档摘要**：本页介绍 fpGrowthV1 函数（频繁模式增长）：在包含事务与项数组列的数据集中发现频繁模式。通过最小支持度参数控制模式出现频率阈值，输出模式及其出现次数与总数。

### 38. Pipeline Builder Transforms • Geo distance inner join

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/geoDistanceInnerJoinV1/>
- **文档摘要**：本页介绍 geoDistanceInnerJoinV1 函数：基于两几何之间的距离进行内连接，距离在指定范围内则连接左右数据集。内部将几何转换到投影坐标系后再测距并转回 WGS84，支持地理空间与连接类别。

### 39. Pipeline Builder Transforms • Geo distance left join

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/geoDistanceLeftJoinV1/>
- **文档摘要**：本页介绍 geoDistanceLeftJoinV1 函数：当两几何间距离小于等于指定值时进行左连接。内部将几何转换到投影坐标系后测距并转回 WGS84，保留左表全部行，支持地理空间与连接类别。

### 40. Pipeline Builder Transforms • Geo intersection inner join

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/geoIntersectionInnerJoinV1/>
- **文档摘要**：本页介绍 geoIntersectionInnerJoinV1 函数：基于两几何是否重叠（含相切）进行内连接。支持批处理与流式模式，空值或无效几何不参与匹配，常用于地理空间叠加分析。

### 41. Pipeline Builder Transforms • Geo intersection left anti join

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/geoIntersectionAntiJoinV1/>
- **文档摘要**：本页介绍 geoIntersectionAntiJoinV1 函数（地理相交左反连接）：返回左表中几何不与右表任何几何相交的行。空值或无效连接键视为不相交，仅支持流式模式，用于排除重叠区域数据。

### 42. Pipeline Builder Transforms • Geo intersection left join

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/geoIntersectionLeftJoinV1/>
- **文档摘要**：本页介绍 geoIntersectionLeftJoinV1 函数：基于两几何是否重叠（含相切）进行左连接，保留左表全部行。空值或无效几何不返回匹配，支持批处理与流式模式，用于地理空间关联。

### 43. Pipeline Builder Transforms • GeoPoint-to-GeoPoint 3d distance inner join

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/geoPointToPoint3dDistanceInnerJoinV1/>
- **文档摘要**：本页介绍 geoPointToPoint3dDistanceInnerJoinV1 函数：基于点几何间距离（可含 z 坐标）进行内连接。非点几何被忽略，内部转换到投影坐标系测距后转回 WGS84，右表需能装入内存。

### 44. Pipeline Builder Transforms • Geometry intersection join

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/geoIntersectionJoinV1/>
- **文档摘要**：本页介绍 geoIntersectionJoinV1 函数（几何相交连接）：基于几何是否重叠内连接左右数据集，返回两表所有列。暂不支持多连接键，自动过滤空值连接键，左右表列名不可重复。

### 45. Pipeline Builder Transforms • Geometry knn inner join

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/geoKnnInnerJoinV1/>
- **文档摘要**：本页介绍 geoKnnInnerJoinV1 函数：为每个有效输入几何从邻居数据集中选取 k 个最近点。内部转换到投影坐标系测距后转回 WGS84，邻居表需装入内存，属于地理空间与连接类别。

### 46. Pipeline Builder Transforms • Geometry knn left join

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/geoKnnLeftJoinV1/>
- **文档摘要**：本页介绍 geoKnnLeftJoinV1 函数：为每个有效输入几何从邻居数据集选取 k 个最近点进行左连接。内部转换到投影坐标系测距后转回 WGS84，邻居表需装入内存，属于地理空间连接。

### 47. Pipeline Builder Transforms • Get media references (datasets)

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/loadMediaReferencesV1/>
- **文档摘要**：本页介绍 loadMediaReferencesV1 函数：为数据集中的文件生成含媒体引用及基本元数据的输出数据集。支持批处理，可强制指定 MIME 类型，否则逐文件读取检测类型。

### 48. Pipeline Builder Transforms • Heartbeat detection

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/heartbeatDetectionV2/>
- **文档摘要**：本页介绍 heartbeatDetectionV2 函数：在流式模式下检测一组键在可配置时间内是否未收到记录（心跳）。可指定时间值、单位与分区列，输出存活/超时状态，用于监控数据流。

### 49. Pipeline Builder Transforms • Inner join

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/complexInnerJoinV1/>
- **文档摘要**：本页介绍 complexInnerJoinV1 函数（内连接）：连接两个数据集，仅保留满足连接条件的行。可分别筛选左右输出列、指定连接条件与右表前缀，支持批处理与 Faster 模式。

### 50. Pipeline Builder Transforms • Join

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/joinV2/>
- **文档摘要**：本页介绍 joinV2 函数：连接左、右两个数据集输入。通过连接键列表指定关联列，并选择连接类型（内连接、左连接、右连接、反连接、半连接、交叉连接、全外连接等），支持批处理、Faster 与流式。

### 51. Pipeline Builder Transforms • K-means clustering

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/kmeansV1/>
- **文档摘要**：本页介绍 kmeansV1 函数（K-means 聚类）：一种无监督机器学习算法，将向量数据分组为 k 个簇。通过最小/最大 k 值范围与尝试次数，按轮廓系数自动选取最佳 k，输出簇编号。

### 52. Pipeline Builder Transforms • KNN join

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/complexKnnJoinV1/>
- **文档摘要**：本页介绍 complexKnnJoinV1 函数（KNN 连接）：为左表每行从右表返回基于距离度量的 k 个最近行。支持自定义距离表达式、排名列与右表前缀，常用于近似最近邻关联。

### 53. Pipeline Builder Transforms • Keeps duplicates

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/keepDuplicatesV1/>
- **文档摘要**：本页介绍 keepDuplicatesV1 函数：保留输入中的重复行。可指定列子集用于判定唯一性，未指定则查找完全重复行，支持批处理与 Faster 模式，用于剔除唯一数据仅留重复项。

### 54. Pipeline Builder Transforms • Key by

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/keyByV3/>
- **文档摘要**：本页介绍 keyByV3 函数：在流式模式下按指定列对输入进行分区键控（不重新排序）。可启用 CDC 模式设置主键以处理更新/删除与去重，用于维护按键顺序的流式处理。

### 55. Pipeline Builder Transforms • Left join

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/complexLeftJoinV1/>
- **文档摘要**：本页介绍 complexLeftJoinV1 函数（左连接）：连接两个数据集，保留左表全部行及满足连接条件的右表行。可分别筛选左右输出列、指定连接条件与右表前缀，支持批处理与 Faster。

### 56. Pipeline Builder Transforms • Left lookup join

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/leftLookupJoinV2/>
- **文档摘要**：本页介绍 leftLookupJoinV2 函数（左查找连接）：在流式模式下连接两数据集，保留左表全部行及右表匹配行。通过连接列列表关联，并限制单行最多连接数，用于实时查找补全。

### 57. Pipeline Builder Transforms • Manually entered table

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/manuallyEnteredTableV1/>
- **文档摘要**：本页介绍 manuallyEnteredTableV1 函数：使用手动输入的表数据生成输出数据集。以结构体列表表示行，可指定 schema 定义列名与类型，否则从非空行推断，支持批处理、Faster 与流式。

### 58. Pipeline Builder Transforms • Mapping join

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/mappingJoinV1/>
- **文档摘要**：本页介绍 mappingJoinV1 函数（映射连接）：用映射数据集的值替换源数据集目标列中的值。可指定映射键列、目标列、映射值列及默认值，支持唯一映射假设与批处理、Faster 模式。

### 59. Pipeline Builder Transforms • Narrow union by name

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/narrowUnionByNameV1/>
- **文档摘要**：本页介绍 narrowUnionByNameV1 函数：按列名交集对多个数据集求并集，仅保留所有输入数据集共有的列。适用于合并列结构部分重叠的数据集，支持批处理与 Faster 模式。

### 60. Pipeline Builder Transforms • New operator chain

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/newOperatorChainV1/>
- **文档摘要**：本页介绍 newOperatorChainV1 函数：流式模式下的高级 Flink 特性，从指定数据集处开启新的算子链（operator chain）。可指定可选的槽共享组以控制调度，用于性能调优。

### 61. Pipeline Builder Transforms • Normalize column names

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/normalizeColumnNamesV1/>
- **文档摘要**：本页介绍 Normalize column names 函数：将列名规范化为 lower_snake_case，在驼峰边界插入下划线、字母转小写、连字符与空白转为下划线、去除句点，并折叠连续下划线、去除首尾下划线。支持 Dataset 与可选 Remove special characters 参数，Foundry 不允许的字符始终替换为下划线。支持 Batch、Faster、Streaming。

### 62. Pipeline Builder Transforms • Numeric distribution

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/numericDistributionV2/>
- **文档摘要**：本页介绍 Numeric distribution 函数：计算指定数值列中数值的分布，按桶（Bucket count）统计落入各区间的数量。可配置 Column、Minimum value、Maximum value 与桶数量，输出含 bucket、min_value、max_value、count、bucket_start、bucket_end 的结果。属于 Numeric 类转换，支持 Batch、Faster。

### 63. Pipeline Builder Transforms • Outer caching join

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/frontendOuterCachingJoinV3/>
- **文档摘要**：本页介绍 Outer caching join 函数（frontend 版本）：在 Streaming 中将左右输入中满足匹配条件且位于缓存窗口内的行，以及两侧所有未匹配行进行外连接。支持配置默认缓存时长单位/数值、连接键、左右保留列、右表缓存覆盖等参数。属于 Join 类转换。

### 64. Pipeline Builder Transforms • Outer caching join

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/outerCachingJoinV3/>
- **文档摘要**：本页介绍 Outer caching join 函数：在 Streaming 中将左右数据集合并，缓存每侧事件时间最新的记录供后续连接使用，处理时间作为并列时的决胜依据。支持默认缓存时长、连接键、左右数据集、右表缓存覆盖等参数；缓存时间设为 0 则不缓存。属于 Join 类转换。

### 65. Pipeline Builder Transforms • Outer join

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/complexOuterJoinV1/>
- **文档摘要**：本页介绍 Outer join 函数：在 Batch、Faster 中对多个数据集做外连接，保留两侧所有行，无匹配时对应列为 null。可配置左侧/右侧列选择条件、连接条件（Join condition）、左右数据集及右侧列前缀。页面给出多个示例说明基本用法。属于 Join 类转换。

### 66. Pipeline Builder Transforms • Parse KML files into geometry lists

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/parseKmlFilesV1/>
- **文档摘要**：本页介绍 Parse KML files into geometry lists 函数：在 Batch 中解析每个原始 KML 文件为一组带类型的几何体列表。参数包括待处理文件数据集、是否包含原始 KML（Include raw KML，大文件建议设 false 以免内存溢出）以及解析后是否准备几何体（Should prepare）供 Ontology 摄取。属于 File 类转换。

### 67. Pipeline Builder Transforms • Pivot

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/pivotV1/>
- **文档摘要**：本页介绍 Pivot 函数：对输入数据集按一组列分组并执行指定聚合，将透视列的唯一值转为多列输出（宽表）。需预先提供透视值以保证输出 schema 在运行前已知，提升稳定性。参数含 Aggregations、Group by columns、Pivot by column/values、前缀或后缀别名。支持 Batch、Faster。

### 68. Pipeline Builder Transforms • Project over window

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/windowedProjectV1/>
- **文档摘要**：本页介绍 Project over window 函数：在窗口内的数据上执行指定聚合，每收到一行即输出一行结果。参数含待聚合的数据集、窗口内待求值的表达式列表（Expressions）以及窗口定义（Window）。属于 Aggregate 类转换，支持 Batch、Faster、Streaming。

### 69. Pipeline Builder Transforms • Rename columns

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/renameColumnsV1/>
- **文档摘要**：本页介绍 Rename columns 函数：对数据集中的一组列进行重命名。参数含输入数据集（Input dataset）与重命名映射（Renames，从原列名到新名）。属于 Data preparation 常用转换，支持 Batch、Faster、Streaming，页面给出一个基础示例。

### 70. Pipeline Builder Transforms • Repartition data

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/repartitionV1/>
- **文档摘要**：本页介绍 Repartition data 函数：基于可选的分区列与指定分区数对数据进行强制重洗（shuffle），未提供时由系统自动决定。参数含数据集、分区列（Partitioning columns）、分区数（Number of partitions）及增量构建时的分区数。属于 Other 类转换，支持 Batch、Faster。

### 71. Pipeline Builder Transforms • Rollup

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/rollUpV1/>
- **文档摘要**：本页介绍 Rollup 函数：对输入数据集以不同粒度执行指定聚合，同时提供中间与超级聚合结果。参数含聚合表达式列表（Aggregations）、数据集与滚动列（Rollup columns，为空则不做聚合）。属于 Aggregate 类转换，支持 Batch、Faster，页面含多个基础与边界示例。

### 72. Pipeline Builder Transforms • Row size

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/rowSizeV1/>
- **文档摘要**：本页介绍 Row size 函数：在 Batch 中估算单行在 JVM 中的大小（字节）。参数含待计算的数据集与可选的行大小列别名（默认 'size'）。输出为原数据加一列字节估算值。属于 Other 类转换，页面给出一个基础示例。

### 73. Pipeline Builder Transforms • Select columns

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/selectV1/>
- **文档摘要**：本页介绍 Select columns 函数：从输入数据集中选取一组列。参数含要选择的列列表（Columns to select）与源数据集（Input dataset）。属于 Popular 常用转换，支持 Batch、Faster、Streaming。

### 74. Pipeline Builder Transforms • Semi join

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/complexSemiJoinV1/>
- **文档摘要**：本页介绍 Semi join 函数：在 Batch、Faster 中将左右数据集做半连接，仅保留左表中满足连接条件的行、去除不匹配行。参数含左侧列选择条件、连接条件（Join condition）、左右数据集。属于 Join 类转换，页面给出多个基础与复杂条件示例。

### 75. Pipeline Builder Transforms • Sort

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/sortV2/>
- **文档摘要**：本页介绍 Sort 函数：对输入数据集按所选列或列上的函数进行排序。参数含待排序数据集（Dataset）与排序规范（Sort specification，列与升序/降序）。属于 Other 类转换，支持 Batch、Faster，页面给出基础示例。

### 76. Pipeline Builder Transforms • Split on condition

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/splitOnConditionV1/>
- **文档摘要**：本页介绍 Split on condition 函数：在 Batch、Faster 中依据给定条件将输入拆分为两个输出——条件为真的行进入 true 输出，为假进入 false 输出。参数含布尔条件（Condition）与输入数据集（Input）。属于 Other 类转换。

### 77. Pipeline Builder Transforms • Text block

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/textBlockV1/>
- **文档摘要**：本页介绍 Text block 函数：在各转换之间插入一段文本说明，不以任何方式转换输入数据。参数含输入数据集（Input）与可选的文字描述（Text）。属于 Other 类转换，支持 Batch、Faster、Streaming。

### 78. Pipeline Builder Transforms • Time bounded drop duplicates

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/timeBoundedDropDuplicatesV3/>
- **文档摘要**：本页介绍 Time bounded drop duplicates 函数：在 Streaming 中按给定列子集去除重复行，已见行在配置的事件时间后过期；迟到超过配置时长的行始终被丢弃。可按键列分区分别计算。参数含键过期时长、列子集、驱逐窗口滑动、按键列等。属于 Other 类转换。

### 79. Pipeline Builder Transforms • Time bounded drop out of order

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/timeBoundedDropOutOfOrderTransformV1/>
- **文档摘要**：本页介绍 Time bounded drop out of order 函数：在 Streaming 中丢弃按所有键列相同但顺序错乱的行——即相对已接收同键行在排序列上本应提前到达的行。为每个键缓存当前最大值直至过期。参数含键过期时长单位/数值、数据集与排序规范等。属于 Other 类转换。

### 80. Pipeline Builder Transforms • Time bounded event time sort

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/timeBoundedEventTimeSortV1/>
- **文档摘要**：本页介绍 Time bounded event time sort 函数：在 Streaming 中按键以升序事件时间输出行，允许迟到的记录直到允许的迟到时长；超过该时长加少量缓冲的记录将被丢弃。参数含允许迟到时长、数据集与可选的按键列（Key by columns）。属于 Other 类转换。

### 81. Pipeline Builder Transforms • Top rows

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/topRowV2/>
- **文档摘要**：本页介绍 Top rows 函数：在每个排序分区中选取前若干行。参数含输入数据集、分区列（Partition by columns）、排序规范（Sort specification，至少一项）、以及可选的行数（默认 1）。属于 Aggregate 类转换，支持 Batch、Faster，页面给出多个示例。

### 82. Pipeline Builder Transforms • Union by name

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/unionByNameV1/>
- **文档摘要**：本页介绍 Union by name 函数：在 Batch、Faster、Streaming 中按匹配列名将多个数据集纵向合并。参数含待合并的数据集列表（Datasets to union）。属于 Join 类转换，页面给出一个基础示例说明按列名对齐合并。

### 83. Pipeline Builder Transforms • Union files

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/unionFilesV1/>
- **文档摘要**：本页介绍 Union files 函数：在 Batch 中将多个文件数据集纵向合并。参数含待合并的文件数据集（Dataset，类型为文件列表）。属于 File 类转换。

### 84. Pipeline Builder Transforms • Unpivot

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/unpivotV1/>
- **文档摘要**：本页介绍 Unpivot 函数：为 Pivot 的逆操作，将多列转换为行（宽表转长表）。它新建两列——一列存放原列名、一列存放对应数据值，其余未透视列保持不变。参数含待逆透视列、数据集、名称列与值列。属于 Aggregate 常用转换，支持 Batch、Faster、Streaming。

### 85. Pipeline Builder Transforms • Unzip files

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/unzipFilesV1/>
- **文档摘要**：本页介绍 Unzip files 函数：在 Batch 中解压文件数据集内的每个 zip 文件，非 zip 文件会被忽略；预览此转换及下游节点需具备编辑权限。参数含待处理的文件数据集（Dataset）。属于 File 类转换。

### 86. Pipeline Builder Transforms • Uppercase column names

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/uppercaseColumnNamesV1/>
- **文档摘要**：本页介绍 Uppercase column names 函数：将数据集所有列名转为大写。参数仅含待处理的数据集（Dataset）。属于 Data preparation 类转换，支持 Batch、Faster、Streaming，页面给出两个基础示例。

### 87. Pipeline Builder Transforms • Wide union by name

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/wideUnionByNameV1/>
- **文档摘要**：本页介绍 Wide union by name 函数：在 Batch、Faster、Streaming 中按列名超集合并多个数据集，缺失列以 null 填充。参数含待合并的数据集列表（Datasets to union）。属于 Join 类转换，页面给出一个含缺失列的示例。

### 88. Pipeline Builder Transforms • Window

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-transform/windowV1/>
- **文档摘要**：本页介绍 Window 函数：对输入数据集按一组列分组并在窗口上执行指定聚合。参数含数据集（Dataset）、窗口内待求值的表达式列表（Expressions）与窗口定义（Window）。属于 Aggregate 常用转换，支持 Batch、Faster。
