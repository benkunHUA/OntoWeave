# Pipeline Builder 表达式函数

> **分类说明**：Pipeline Builder 表达式语言的函数参考手册：逐个说明每个函数的签名、参数、返回类型与用法示例

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `pb-functions-expression` |
| 文档数量 | 338 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-17 |

---

## 文档列表

### 1. Pipeline Builder Expressions • Absolute value

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/absV1/>
- **文档摘要**：本页介绍 absV1 函数：返回数值的绝对值，支持 Batch、Faster、Streaming 模式。输入为数值型表达式，输出同类型数值；页面含声明参数及基础值、空值、ANSI 模式边界等示例。

### 2. Pipeline Builder Expressions • Add numbers

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/addV2/>
- **文档摘要**：本页介绍 addV2 函数：计算所有输入列之和。接受数值型表达式列表（List<Expression<Numeric>>），输出数值类型，适用于批处理、流式与 Faster 模式。

### 3. Pipeline Builder Expressions • Add or update map

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/addOrUpdateMapEntryV1/>
- **文档摘要**：本页介绍 addOrUpdateMapEntryV1 函数：按指定键更新 map 中的值，或新增键值对。输入为 map 表达式、键与值，适用于 Batch 与 Streaming 管道。

### 4. Pipeline Builder Expressions • Add or update struct field

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/addOrUpdateStructFieldV1/>
- **文档摘要**：本页介绍 addOrUpdateStructFieldV1 函数：更新 struct 的某个字段，或新增字段。输入为 struct 表达式、字段名与值，支持 Batch、Faster 与 Streaming 模式。

### 5. Pipeline Builder Expressions • Add value to date

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/dateAddV2/>
- **文档摘要**：本页介绍 dateAddV2 函数：返回从 start 日期起，经过指定天数/周数/月数/季度/年数后的日期。输入为起始日期、数值与时间单位，支持 Batch、Faster 与 Streaming。

### 6. Pipeline Builder Expressions • All array elements satisfy

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/allArrayElementsSatisfyV1/>
- **文档摘要**：本页介绍 allArrayElementsSatisfyV1 函数：当数组中每个元素都满足给定表达式时返回 true。输入为数组与判定表达式，适用于 Batch 与 Streaming 管道。

### 7. Pipeline Builder Expressions • All of

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/allOfV2/>
- **文档摘要**：本页介绍 allOfV2 函数：计算聚合的布尔“与”（and），对空值采用 SQL ANSI 语义。输入为布尔表达式聚合，适用于 Batch 与 Faster 模式。

### 8. Pipeline Builder Expressions • And

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/andV1/>
- **文档摘要**：本页介绍 andV1 函数：当所有指定条件均为 true 时返回 true；ANSI 模式关闭时将空值视为 false。输入为条件列表，支持 Batch、Faster 与 Streaming。

### 9. Pipeline Builder Expressions • Any array element satisfy

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/anyArrayElementSatisfyV1/>
- **文档摘要**：本页介绍 anyArrayElementSatisfyV1 函数：当数组中任一元素满足给定表达式时返回 true。输入为数组与判定表达式，适用于 Batch 与 Streaming 管道。

### 10. Pipeline Builder Expressions • Any of

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/anyOfV2/>
- **文档摘要**：本页介绍 anyOfV2 函数：计算聚合的布尔“或”（or），对空值采用 SQL ANSI 语义。输入为布尔表达式聚合，适用于 Batch 与 Faster 模式。

### 11. Pipeline Builder Expressions • Approximate median

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/approxMedianV1/>
- **文档摘要**：本页介绍 approxMedianV1 函数：计算列中数值的近似中位数。输入为数值列表达式，仅支持 Batch 模式。

### 12. Pipeline Builder Expressions • Approximate percentile

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/approximatePercentileV1/>
- **文档摘要**：本页介绍 approximatePercentileV1 函数：返回表达式的近似百分位数（有序表达式中最小的对应值）。输入为目标列与百分位参数，仅支持 Batch。

### 13. Pipeline Builder Expressions • Arccos

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/arccosV1/>
- **文档摘要**：本页介绍 arccosV1 函数：反余弦函数。输入为数值表达式与输出角度单位（度或弧度），支持 Batch、Faster 与 Streaming 模式。

### 14. Pipeline Builder Expressions • Arcsin

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/arcsinV1/>
- **文档摘要**：本页介绍 arcsinV1 函数：反正弦函数。输入为数值表达式与输出角度单位（度或弧度），支持 Batch、Faster 与 Streaming 模式。

### 15. Pipeline Builder Expressions • Arctan

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/arctanV1/>
- **文档摘要**：本页介绍 arctanV1 函数：反正切函数。输入为数值表达式与输出角度单位（度或弧度），支持 Batch、Faster 与 Streaming 模式。

### 16. Pipeline Builder Expressions • Arctan2

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/arctan2V1/>
- **文档摘要**：本页介绍 arctan2V1 函数：返回原点到点 (x, y) 的射线与正 x 轴之间的夹角 θ。输入为两个数值表达式与角度单位，支持 Batch、Faster 与 Streaming。

### 17. Pipeline Builder Expressions • Area

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/areaV1/>
- **文档摘要**：本页介绍 areaV1 函数：使用球面近似计算几何图形的面积（平方米）。对面、线串或点几何有效，支持 Batch 与 Streaming 模式。

### 18. Pipeline Builder Expressions • Array add

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/arrayAddV1/>
- **文档摘要**：本页介绍 arrayAddV1 函数：在指定索引位置向数组添加值。输入为数组、索引与待添加值，支持 Batch、Faster 与 Streaming 模式。

### 19. Pipeline Builder Expressions • Array cartesian product

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/arraysCartesianProductV1/>
- **文档摘要**：本页介绍 arraysCartesianProductV1 函数：计算数组的笛卡尔积。输入为待转换的列，适用于 Batch 与 Streaming 管道。

### 20. Pipeline Builder Expressions • Array concat

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/arrayConcatV1/>
- **文档摘要**：本页介绍 arrayConcatV1 函数：将多个数组合并为单一数组（不去重）。输入为数组列表，支持 Batch、Faster 与 Streaming 模式。

### 21. Pipeline Builder Expressions • Array contains

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/arrayContainsV1/>
- **文档摘要**：本页介绍 arrayContainsV1 函数：当数组包含指定值时返回 true。输入为数组与待查找值，输出布尔类型，支持 Batch、Faster 与 Streaming。

### 22. Pipeline Builder Expressions • Array contains null

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/arrayContainsNullV1/>
- **文档摘要**：本页介绍 arrayContainsNullV1 函数：当数组包含空值时返回 true。输入为待检查的数组，输出布尔类型，支持 Batch、Faster 与 Streaming。

### 23. Pipeline Builder Expressions • Array difference

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/arrayDifferenceV1/>
- **文档摘要**：本页介绍 arrayDifferenceV1 函数：返回左数组中不在右数组的全部唯一元素。输入为左右两个数组，支持 Batch、Faster 与 Streaming 模式。

### 24. Pipeline Builder Expressions • Array distinct

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/arrayDistinctV1/>
- **文档摘要**：本页介绍 arrayDistinctV1 函数：移除数组中的重复项并返回去重后的值。输入为数组表达式，支持 Batch、Faster 与 Streaming 模式。

### 25. Pipeline Builder Expressions • Array element

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/arrayElementV1/>
- **文档摘要**：本页介绍 arrayElementV1 函数：返回输入数组在给定位置的元素；超出范围的位置返回空值。输入为数组与位置索引，支持 Batch、Faster 与 Streaming。

### 26. Pipeline Builder Expressions • Array elements are distinct

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/isArrayUniqueV1/>
- **文档摘要**：本页介绍 isArrayUniqueV1 函数：当数组元素均不重复时返回 true，否则返回 false（多个空值视为相等）。输入为数组表达式，支持 Batch、Faster 与 Streaming。

### 27. Pipeline Builder Expressions • Array flatten

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/arrayFlattenV2/>
- **文档摘要**：本页介绍 arrayFlattenV2 函数：将嵌套数组在第一层嵌套上合并为单一数组。输入为嵌套数组，支持 Batch、Faster 与 Streaming 模式。

### 28. Pipeline Builder Expressions • Array intersect

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/arrayIntersectV1/>
- **文档摘要**：本页介绍 arrayIntersectV1 函数：对一组数组去重并求交集。输入为数组列表，支持 Batch、Faster 与 Streaming 模式。

### 29. Pipeline Builder Expressions • Array maximum

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/arrayMaxV1/>
- **文档摘要**：本页介绍 arrayMaxV1 函数：返回数组列中的最大值。输入为数组表达式，支持 Batch、Faster 与 Streaming 模式。

### 30. Pipeline Builder Expressions • Array minimum

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/arrayMinV1/>
- **文档摘要**：本页介绍 arrayMinV1 函数：返回数组列中的最小值。输入为数组表达式，支持 Batch、Faster 与 Streaming 模式。

### 31. Pipeline Builder Expressions • Array position

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/arrayPositionV1/>
- **文档摘要**：本页介绍 arrayPositionV1 函数：返回 value 在给定数组中首次出现的位置/索引；不存在时返回空值。输入为数组与待查找值，支持 Batch、Faster 与 Streaming。

### 32. Pipeline Builder Expressions • Array remove

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/arrayRemoveV1/>
- **文档摘要**：本页介绍 arrayRemoveV1 函数：从给定数组中移除所有指定 value 后返回新数组。输入为数组与待移除值，支持 Batch、Faster 与 Streaming 模式。

### 33. Pipeline Builder Expressions • Array repeat

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/arrayRepeatV1/>
- **文档摘要**：本页介绍 arrayRepeatV1 函数：将数组内容重复拼接 value 次并返回新数组。输入为数组与重复次数，支持 Batch、Faster 与 Streaming 模式。

### 34. Pipeline Builder Expressions • Array reverse

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/arrayReverseV1/>
- **文档摘要**：本页介绍 arrayReverseV1 函数：反转数组中元素的顺序。输入为待反转的数组，支持 Batch、Faster 与 Streaming 模式。

### 35. Pipeline Builder Expressions • Array sort

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/arraySortV1/>
- **文档摘要**：本页介绍 arraySortV1 函数：返回对输入数组排序后的数组；降序排列时空值置于末尾。支持 Batch、Faster 与 Streaming 模式。

### 36. Pipeline Builder Expressions • Array sort by struct key

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/arraySortByKeyV2/>
- **文档摘要**：本页介绍 arraySortByKeyV2 函数：按给定 struct 键的值对 struct 数组排序。输入为数组、排序键与升降序方向，适用于 Batch 与 Streaming 管道。

### 37. Pipeline Builder Expressions • Array union

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/arrayUnionV1/>
- **文档摘要**：本页介绍 arrayUnionV1 函数：对一组数组去重并求并集。输入为数组列表，支持 Batch、Faster 与 Streaming 模式。

### 38. Pipeline Builder Expressions • Arrays have intersection

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/arraysHaveIntersectionV1/>
- **文档摘要**：本页介绍 arraysHaveIntersectionV1 函数：检查给定数组是否至少有一个共享元素。输入为数组列表，输出布尔类型，支持 Batch、Faster 与 Streaming。

### 39. Pipeline Builder Expressions • Arrays zip

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/arraysZipV1/>
- **文档摘要**：本页介绍 arraysZipV1 函数：将一组数组按位置压缩为 struct 数组，第 n 个 struct 含所有数组的第 n 个值。输入为数组列表，支持 Batch、Faster 与 Streaming。

### 40. Pipeline Builder Expressions • Base 64 decode to string

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/base64DecodeToStringV1/>
- **文档摘要**：本页介绍 base64DecodeToStringV1 函数：对给定表达式进行 Base64 解码并转为字符串（二进制使用 UTF-8 编码）。输入为待解码表达式，支持 Batch、Faster 与 Streaming。

### 41. Pipeline Builder Expressions • Base64 decode

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/base64DecodeV1/>
- **文档摘要**：本页介绍 base64DecodeV1 函数：对给定表达式进行 Base64 解码。输入为待解码表达式，输出二进制/字节类型，支持 Batch、Faster 与 Streaming。

### 42. Pipeline Builder Expressions • Base64 encode

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/base64EncodeV1/>
- **文档摘要**：本页介绍 base64EncodeV1 函数：对给定表达式进行 Base64 编码。输入为字符串或二进制表达式，支持 Batch、Faster 与 Streaming 模式。

### 43. Pipeline Builder Expressions • Bit shift left

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/bitShiftLeftV1/>
- **文档摘要**：本页介绍 bitShiftLeftV1 函数：将给定值左移指定位数。输入为待移位的值与位移位数，适用于 Batch 与 Streaming 管道。

### 44. Pipeline Builder Expressions • Bit shift right

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/bitShiftRightV1/>
- **文档摘要**：本页介绍 bitShiftRightV1 函数：将给定值右移指定位数。输入为待移位的值与位移位数，适用于 Batch 与 Streaming 管道。

### 45. Pipeline Builder Expressions • Buffer H3 indices

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/h3BufferV1/>
- **文档摘要**：本页介绍 h3BufferV1 函数：以距离 k 为半径从一组 H3 索引创建缓冲区。输入为 H3 索引数组与距离，支持 Batch、Faster 与 Streaming 模式。

### 46. Pipeline Builder Expressions • Calculate destination point

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/inverseHaversineV1/>
- **文档摘要**：本页介绍 inverseHaversineV1 函数：根据起点、航向与距离计算沿指定路径的目的地点。输入为起始坐标、角度与距离，支持 Batch、Faster 与 Streaming。

### 47. Pipeline Builder Expressions • Calculate haversine distance

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/haversineV1/>
- **文档摘要**：本页介绍 haversineV1 函数：计算两组经纬度坐标点之间的球面距离（米）。输入为两个经纬度点对，支持 Batch、Faster 与 Streaming 模式。

### 48. Pipeline Builder Expressions • Case

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/caseV2/>
- **文档摘要**：本页介绍 caseV2 函数：基于条件在多个分支间选择返回值，并含默认分支。输入为条件-取值对与默认值，支持 Batch、Faster 与 Streaming。

### 49. Pipeline Builder Expressions • Cast

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/castV2/>
- **文档摘要**：本页介绍 castV2 函数：将表达式转换为指定类型。输入为待转换表达式与目标类型，支持 Batch、Faster 与 Streaming，页面含大量类型转换示例。

### 50. Pipeline Builder Expressions • Cast media schema

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/castMediaSchemaV1/>
- **文档摘要**：本页介绍 castMediaSchemaV1 函数：将媒体引用转换为特定媒体 schema 与格式（适用于输入媒体为通用/多模态 schema 的场景）。输入为媒体引用与目标 schema 等参数。

### 51. Pipeline Builder Expressions • Ceil

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/ceilV1/>
- **文档摘要**：本页介绍 ceilV1 函数：返回给定小数值的上取整（ceil）。输入为小数值表达式，支持 Batch、Faster 与 Streaming 模式。

### 52. Pipeline Builder Expressions • Change timestamp time zone

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/changeTimestampTimeZoneV1/>
- **文档摘要**：本页介绍 changeTimestampTimeZoneV1 函数：修改时间戳的时区。输入为时间戳表达式与目标时区，支持 Batch 与 Faster 模式。

### 53. Pipeline Builder Expressions • Character-wise translate string

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/translateStringV1/>
- **文档摘要**：本页介绍 translateStringV1 函数：将输入列中出现在匹配集中的单个字符逐字符替换为对应字符。输入为原字符串、匹配集与替换集，支持 Batch、Faster 与 Streaming。

### 54. Pipeline Builder Expressions • Chunk string

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/chunkStringV1/>
- **文档摘要**：本页介绍 chunkStringV1 函数：按指定大小与分隔符将字符串切分为分块。输入为字符串、块大小与分隔符，适用于 Batch 与 Streaming 管道。

### 55. Pipeline Builder Expressions • Cipher decrypt

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/cipherDecryptV1/>
- **文档摘要**：本页介绍 cipherDecryptV1 函数：使用密码（cipher）解密表达式。输入为待解密表达式、密码许可证 RID 等参数，支持 Batch、Faster 与 Streaming。

### 56. Pipeline Builder Expressions • Cipher encrypt

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/cipherEncryptV1/>
- **文档摘要**：本页介绍 cipherEncryptV1 函数：使用密码（cipher）加密表达式。输入为待加密表达式、密码许可证 RID 等参数，支持 Batch、Faster 与 Streaming。

### 57. Pipeline Builder Expressions • Cipher hash

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/cipherHashV1/>
- **文档摘要**：本页介绍 cipherHashV1 函数：使用密码（cipher）对表达式进行哈希。输入为待哈希表达式、密码许可证 RID 等参数，支持 Batch、Faster 与 Streaming。

### 58. Pipeline Builder Expressions • Clean string

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/cleanStringV1/>
- **文档摘要**：本页介绍 Clean string 函数：对表达式应用一组清理动作（Normalize whitespace、Nullify empty、Trim）以清洗字符串。参数包括清理动作集合与待处理字符串表达式，返回 String 类型。

### 59. Pipeline Builder Expressions • Collect array

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/collectArrayV1/>
- **文档摘要**：本页介绍 Collect array 函数：在每个分组内收集值组成数组，自动忽略 null 值。属于聚合（Aggregate）类表达式，适用于将分组内的多行值聚合成单一数组列。

### 60. Pipeline Builder Expressions • Collect distinct array

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/collectDistinctArrayV1/>
- **文档摘要**：本页介绍 Collect distinct array 函数：在每个分组内收集去重后的值组成数组，自动忽略 null 值。属于聚合类表达式，相比 Collect array 还会对值进行去重。

### 61. Pipeline Builder Expressions • Compact a set of H3 indices

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/compactH3SetV1/>
- **文档摘要**：本页介绍 Compact a set of H3 indices 函数：将一组 H3 索引尽可能压缩为混合分辨率的子集（逆操作为 uncompact）。输入与输出均为 Array<H3 Index>；若存在无效索引则返回 null，输出按升序排序。

### 62. Pipeline Builder Expressions • Concatenate strings

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/concatStringsV1/>
- **文档摘要**：本页介绍 Concatenate strings 函数：使用指定的分隔符将字符串列表拼接为单个字符串。适用于将多列或多值字符串按分隔符合并成文本。

### 63. Pipeline Builder Expressions • Construct delegated media Gotham identifier (GID)

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/constructDelegatedMediaGidV1/>
- **文档摘要**：本页介绍 Construct delegated media Gotham identifier (GID) 函数：从给定组件构造有效的委托媒体 Gotham 标识符（GID）。若结果超过 1024 字符，页面说明了相应的处理行为，需参考示例配置组件。

### 64. Pipeline Builder Expressions • Convert DMS to GeoPoint

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/dmsToGeoPointV1/>
- **文档摘要**：本页介绍 Convert DMS to GeoPoint 函数：将度分秒（DMS）格式的地理坐标字符串转换为 GeoPoint。便于在地理空间计算中使用以 DMS 文本表示的坐标。

### 65. Pipeline Builder Expressions • Convert GeoPoint to DMS

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/geoPointToDmsV1/>
- **文档摘要**：本页介绍 Convert GeoPoint to DMS 函数：将 GeoPoint 转换为度分秒（DMS）格式的地理坐标字符串。与 Convert DMS to GeoPoint 互为逆操作。

### 66. Pipeline Builder Expressions • Convert GeoPoint to Geohash

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/geoPointToGeohashV1/>
- **文档摘要**：本页介绍 Convert GeoPoint to Geohash 函数：将 GeoPoint 转换为包含该点的 base32 编码 Geohash，可指定精度。常用于地理编码与空间索引。

### 67. Pipeline Builder Expressions • Convert GeoPoint to MGRS

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/geoPointToMgrsV1/>
- **文档摘要**：本页介绍 Convert GeoPoint to MGRS 函数：将遵循 WGS84（EPSG:4326）坐标系的 GeoPoint 转换为 MGRS 军事网格参考系统坐标字符串。

### 68. Pipeline Builder Expressions • Convert GeoPoint to geometry

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/geoPointToGeometryV1/>
- **文档摘要**：本页介绍 Convert GeoPoint to geometry 函数：将 GeoPoint 转换为 GeoJSON 中 type 为 point 的几何对象。便于在几何运算中使用点。

### 69. Pipeline Builder Expressions • Convert H3 index to GeoPoint

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/h3ToGeoPointV1/>
- **文档摘要**：本页介绍 Convert H3 index to GeoPoint 函数：将 H3 索引转换为代表对应 H3 六边形中心的 GeoPoint。输入为 H3 索引，输出为其几何中心坐标。

### 70. Pipeline Builder Expressions • Convert MGRS to GeoPoint

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/mgrsToGeoPointV1/>
- **文档摘要**：本页介绍 Convert MGRS to GeoPoint 函数：将 MGRS 军事网格参考系统坐标转换为遵循 WGS84 坐标系的 GeoPoint。

### 71. Pipeline Builder Expressions • Convert a string to date

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/stringToDateV3/>
- **文档摘要**：本页介绍 Convert a string to date 函数：依据 Java DateTimeFormatter 将格式化字符串解析为 date。默认格式为 yyyy-MM-dd 与 yyyy-MM-dd'T'HH:mm:ss.SSSXXX，按序匹配第一个；可自定义格式列表，返回 Date 类型。

### 72. Pipeline Builder Expressions • Convert a string to timestamp

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/stringToTimestampV3/>
- **文档摘要**：本页介绍 Convert a string to timestamp 函数：依据 Java DateTimeFormatter 将格式化字符串解析为 timestamp。支持默认格式与自定义格式列表，按序匹配第一个，返回时间戳类型。

### 73. Pipeline Builder Expressions • Convert base

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/convertBaseV1/>
- **文档摘要**：本页介绍 Convert base 函数：将数字（或其字符串表示）从一种进制转换为另一种进制。常用于在不同进制（如二进制、十六进制）间转换数值。

### 74. Pipeline Builder Expressions • Convert between angle units

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/convertAngleV1/>
- **文档摘要**：本页介绍 Convert between angle units 函数：在角度单位之间进行转换（如度与弧度等）。需提供当前单位下的量与目标单位，属于数值/地理空间类表达式。

### 75. Pipeline Builder Expressions • Convert between distance units

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/convertDistanceV1/>
- **文档摘要**：本页介绍 Convert between distance units 函数：在距离单位之间进行转换。需提供当前单位下的距离量与目标单位，返回换算后的距离值。

### 76. Pipeline Builder Expressions • Convert between time units

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/convertTimeV1/>
- **文档摘要**：本页介绍 Convert between time units 函数：在时间单位之间进行转换。需提供当前单位下的时间量与目标单位，属于日期时间类表达式。

### 77. Pipeline Builder Expressions • Convert between weight units

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/convertWeightV1/>
- **文档摘要**：本页介绍 Convert between weight units 函数：在重量单位之间进行转换。需提供当前单位下的重量量与目标单位，返回换算后的重量值。

### 78. Pipeline Builder Expressions • Convert data to JSON

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/jsonStringV2/>
- **文档摘要**：本页介绍 Convert data to JSON 函数：将输入数据转换为 JSON 字符串。可用于将数据序列化为 JSON 文本以便存储或传输，返回 String 类型。

### 79. Pipeline Builder Expressions • Convert from Ontology GeoPoint

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/ontologyGeopointToGeopointV1/>
- **文档摘要**：本页介绍 Convert from Ontology GeoPoint 函数：将 Ontology GeoPoint（特定格式字符串）转换为常规 GeoPoint。用于兼容 Ontology 地理列与普通 GeoPoint 之间的互转。

### 80. Pipeline Builder Expressions • Convert from hexadecimal

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/UnhexV1/>
- **文档摘要**：本页介绍 Convert from hexadecimal 函数：为 hex 的逆操作，将每对字符解释为十六进制数并转换为字节（byte）表示。适用于二进制数据处理。

### 81. Pipeline Builder Expressions • Convert from hexadecimal to string

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/UnhexToStringV1/>
- **文档摘要**：本页介绍 Convert from hexadecimal to string 函数：为 hex 的逆操作，将每对字符解释为十六进制数并转换为 UTF-8 字符串。用于从十六进制还原文本。

### 82. Pipeline Builder Expressions • Convert geocentric coordinates to WGS 84 geodesic coordinates

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/GeocentricToGeodesicV1/>
- **文档摘要**：本页介绍 Convert geocentric coordinates to WGS 84 geodesic coordinates 函数：将地心笛卡尔坐标（ECEF）转换为 WGS84 大地坐标。用于坐标系间的转换。

### 83. Pipeline Builder Expressions • Convert legacy OffsetDateTime

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/convertLegacyOffsetDateTimeV1/>
- **文档摘要**：本页介绍 Convert legacy OffsetDateTime 函数：将旧版 OffsetDateTime 列转换为可在 Foundry 所有管道中使用的 timestamp，并以 UTC 返回。仅支持 Batch 模式。

### 84. Pipeline Builder Expressions • Convert linestring to polygon

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/linestringToPolygonV1/>
- **文档摘要**：本页介绍 Convert linestring to polygon 函数：将线串（linestring）几何转换为多边形（polygon）几何，假定线串已闭合。用于几何类型转换。

### 85. Pipeline Builder Expressions • Convert timestamp from UTC

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/timestampFromUtcV1/>
- **文档摘要**：本页介绍 Convert timestamp from UTC 函数：将 UTC 时间的 timestamp 转换为给定时区的时间。需指定目标时区，属于日期时间类表达式。

### 86. Pipeline Builder Expressions • Convert timestamp to UTC

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/timestampToUtcV1/>
- **文档摘要**：本页介绍 Convert timestamp to UTC 函数：将给定时区的 timestamp 转换为 UTC 时间。需指定源时区，属于日期时间类表达式。

### 87. Pipeline Builder Expressions • Convert to Ontology GeoPoint

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/createOntologyGeopointV1/>
- **文档摘要**：本页介绍 Convert to Ontology GeoPoint 函数：将 GeoPoint 转换为 Ontology 地理索引列（geohash 类型）可接受的字符串。用于写入 Ontology 地理字段。

### 88. Pipeline Builder Expressions • Convert to hexadecimal

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/HexV1/>
- **文档摘要**：本页介绍 Convert to hexadecimal 函数：计算给定表达式的十六进制值。适用于数值或字符串到十六进制表示的转换。

### 89. Pipeline Builder Expressions • Convert to octal

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/octalV1/>
- **文档摘要**：本页介绍 Convert to octal 函数：计算给定表达式的八进制值。适用于数值到八进制表示的转换。

### 90. Pipeline Builder Expressions • Cosine

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/cosineV1/>
- **文档摘要**：本页介绍 Cosine 函数：计算角度的余弦值，可指定角度单位（如度或弧度）。属于数值类表达式，输入为角度表达式。

### 91. Pipeline Builder Expressions • Covariance

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/covarianceV1/>
- **文档摘要**：本页介绍 Covariance 函数：计算两列数值的总体协方差（population covariance）。参数为两个数值列，返回 Double 类型，属于聚合类表达式。

### 92. Pipeline Builder Expressions • Create GeoPoint

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/constructGeoPointV1/>
- **文档摘要**：本页介绍 Create GeoPoint 函数：由纬度列与经度列创建 GeoPoint 列，并校验纬度参数的有效性。常用于将分离的经纬度字段合并为地理点。

### 93. Pipeline Builder Expressions • Create GeoPoint from coordinate system

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/createGeoPointFromCoordinateSystemV1/>
- **文档摘要**：本页介绍 Create GeoPoint from coordinate system 函数：取源坐标系下的一对坐标，并将其转换为 WGS84（EPSG:4326）坐标系的 GeoPoint。

### 94. Pipeline Builder Expressions • Create an empty array

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/createEmptyArrayV1/>
- **文档摘要**：本页介绍 Create an empty array 函数：返回给定元素类型的空数组。参数为数组元素类型，适用于需要初始化空数组的场景。

### 95. Pipeline Builder Expressions • Create array

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/createArrayV1/>
- **文档摘要**：本页介绍 Create array 函数：由提供的多个列创建数组。参数为表达式列表，返回由这些列值组成的数组。

### 96. Pipeline Builder Expressions • Create ellipse geometry

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/createGeoEllipseGeometryV1/>
- **文档摘要**：本页介绍 Create ellipse geometry 函数：以给定地理坐标为中心，将椭圆近似为多边形（polygon）几何，点间距离按设定控制。用于生成椭圆区域几何。

### 97. Pipeline Builder Expressions • Create geodesic line string

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/createGeodesicLineStringV1/>
- **文档摘要**：本页介绍 Create geodesic line string 函数：在两点之间创建测地线（geodesic line）。用于生成沿地球曲面的最短路径线串几何。

### 98. Pipeline Builder Expressions • Create geotemporal series reference

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/constructGeotemporalSeriesReferenceV1/>
- **文档摘要**：本页介绍 Create geotemporal series reference 函数：生成 geotemporal 序列引用对象属性类型所需的值，由地理与时间要素组成。用于构建地理时序引用。

### 99. Pipeline Builder Expressions • Create linestring geometry

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/createGeoLineStringV1/>
- **文档摘要**：本页介绍 Create linestring geometry 函数：由给定点创建 GeoJSON 线串（linestring）几何。参数为点列表，返回线串几何对象。

### 100. Pipeline Builder Expressions • Create map from arrays

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/mapFromArraysV1/>
- **文档摘要**：本页介绍 Create map from arrays 函数：由 zip 后的键值数组对返回 map。键不允许为 null（否则运行时报错），重复键保留最后一对；键多值少或值多键少返回 null。返回 Map<K, V>。

### 101. Pipeline Builder Expressions • Create null value

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/createNullValueV1/>
- **文档摘要**：本页介绍 Create null value 函数：返回给定类型的 null 值。参数为目标类型，用于在表达式中显式生成指定类型的空值。

### 102. Pipeline Builder Expressions • Create range fan geometry

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/createGeoRangeFanGeometryV1/>
- **文档摘要**：本页介绍 Create range fan geometry 函数：将范围扇区（range fan）近似为多边形几何，覆盖到原点 haversine 距离在指定范围内的所有点。用于生成扇形区域。

### 103. Pipeline Builder Expressions • Create simple geometries from ordered rows of GeoPoints

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/createGeometryFromOrderedGeoPointRowsV1/>
- **文档摘要**：本页介绍 Create simple geometries from ordered rows of GeoPoints 函数：给定 GeoPoints 列与排序，按序连接这些点返回多边形或线串几何。用于由有序点生成简单几何。

### 104. Pipeline Builder Expressions • Create struct column

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/createStructV2/>
- **文档摘要**：本页介绍 Create struct column 函数：将多个列合并为单个结构化（struct）列。参数为各结构元素，便于将相关字段封装为嵌套结构。

### 105. Pipeline Builder Expressions • Create time series reference values

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/createQualifiedTimeSeriesIdV1/>
- **文档摘要**：本页介绍 Create time series reference values 函数：创建时间序列引用值，参数为序列标识符等。用于构建指向时间序列数据的引用。

### 106. Pipeline Builder Expressions • Current date

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/currentDateV1/>
- **文档摘要**：本页介绍 Current date 函数：返回计算开始执行时的当前日期，可指定可选的时区。属于日期时间类表达式。

### 107. Pipeline Builder Expressions • Current timestamp

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/currentTimestampV2/>
- **文档摘要**：本页介绍 Current timestamp 函数：返回计算开始执行时的当前时间戳（timestamp）。属于日期时间类表达式，无额外参数。

### 108. Pipeline Builder Expressions • Date sequence

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/dateSequenceV1/>
- **文档摘要**：本页介绍 Date sequence 函数：创建一个从开始日期到结束日期之间按范围排列的日期数组。需指定结束日期，返回日期数组。

### 109. Pipeline Builder Expressions • Decode

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/decodeV1/>
- **文档摘要**：本页介绍 Decode 函数：使用指定的字符集（charset）解码给定表达式。适用于将二进制或编码数据按字符集还原为文本。

### 110. Pipeline Builder Expressions • Decode Geobuf as GeoJSON

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/geobufToGeometryExpressionV1/>
- **文档摘要**：本页介绍 Decode Geobuf as GeoJSON 函数：将 Geobuf 几何解码为 GeoJSON 几何。参数为 Geobuf 几何表达式，便于在 Foundry 中使用 Geobuf 数据。

### 111. Pipeline Builder Expressions • Dense rank

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/denseRankV1/>
- **文档摘要**：本页介绍 Dense rank 函数：在窗口分区内返回行的密集排名（无间隔）；并列的行获得相同排名，后续排名紧接而不跳号。属于窗口函数。

### 112. Pipeline Builder Expressions • Distinct count

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/distinctCountV1/>
- **文档摘要**：本页介绍 Distinct count 函数：计算列中不同值的数量（去重计数）。参数为目标列，属于聚合类表达式。

### 113. Pipeline Builder Expressions • Divide numbers

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/divideV1/>
- **文档摘要**：本页介绍 Divide numbers 函数：用一个数（分子）除以另一个数（分母）。参数为左右两个数值表达式，返回商。

### 114. Pipeline Builder Expressions • Edit distance

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/levenshteinDistanceV2/>
- **文档摘要**：本页介绍 Edit distance 函数：计算两个字符串之间的编辑距离，支持 Levenshtein、indel 与 Damerau-Levenshtein 距离算法。参数为两个字符串与算法选择。

### 115. Pipeline Builder Expressions • Encode GeoJSON as Geobuf

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/geometryToGeobufExpressionV1/>
- **文档摘要**：本页介绍 geometryToGeobufExpressionV1 函数：将 GeoJSON 几何编码为 Geobuf 格式。属于 Geospatial 类别，可指定维度数与精度参数，返回 Geobuf 类型。

### 116. Pipeline Builder Expressions • Ends with

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/endsWithV1/>
- **文档摘要**：本页介绍 endsWithV1 函数：判断字符串是否以指定值结尾，可配置是否忽略大小写，返回布尔值。属于 Boolean、String 类别。

### 117. Pipeline Builder Expressions • Epoch milliseconds to date

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/epochMillisToDateV1/>
- **文档摘要**：本页介绍 epochMillisToDateV1 函数：将 epoch 毫秒值转换为 UTC 日期（Date 类型）。输入为 Double 或 Long 类型的毫秒表达式。

### 118. Pipeline Builder Expressions • Epoch milliseconds to timestamp

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/epochMillisToTimestampV1/>
- **文档摘要**：本页介绍 epochMillisToTimestampV1 函数：将 epoch 毫秒值转换为 UTC 时间戳（Timestamp 类型），输入为 Double 或 Long 表达式。

### 119. Pipeline Builder Expressions • Epoch seconds to date

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/epochSecondsToDateV1/>
- **文档摘要**：本页介绍 epochSecondsToDateV1 函数：将 epoch 秒值转换为 UTC 日期（Date 类型），输入为 Double、Integer 或 Long 表达式。

### 120. Pipeline Builder Expressions • Epoch seconds to timestamp

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/epochSecondsToTimestampV1/>
- **文档摘要**：本页介绍 epochSecondsToTimestampV1 函数：将 epoch 秒值转换为 UTC 时间戳（Timestamp 类型），输入为 Double、Integer、Long 或 String 表达式。

### 121. Pipeline Builder Expressions • Equals

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/equalsV1/>
- **文档摘要**：本页介绍 equalsV1 函数：判断左右两个表达式是否相等，返回布尔值。提供 Null safe 选项以将 null 视为 false。

### 122. Pipeline Builder Expressions • Explode array

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/explodeArrayV1/>
- **文档摘要**：本页介绍 explodeArrayV1 函数：将数组展开为每行一个值。可配置是否保留空数组或 null，属于 Array 类别。

### 123. Pipeline Builder Expressions • Explode array with position

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/explodeArrayWithPositionV2/>
- **文档摘要**：本页介绍 explodeArrayWithPositionV2 函数：将数组展开，每行包含元素在数组中的相对位置及其本身，返回含 position 与 element 的结构体。

### 124. Pipeline Builder Expressions • Explode map

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/explodeMapV1/>
- **文档摘要**：本页介绍 explodeMapV1 函数：将 Map 展开为每行一个键值对，返回含 key 与 value 的结构体，属于 Map 类别。

### 125. Pipeline Builder Expressions • Exponential

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/exponentialV1/>
- **文档摘要**：本页介绍 exponentialV1 函数：计算列的指数 e^x，返回 Double 类型，属于 Numeric 类别。

### 126. Pipeline Builder Expressions • Extract all regex matches

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/regexExtractAllV1/>
- **文档摘要**：本页介绍 regexExtractAllV1 函数：按正则表达式提取所有匹配项为数组。可指定分组编号（0 表示整个模式），属于 Regex、String 类别。

### 127. Pipeline Builder Expressions • Extract audio metadata

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/getAudioMetadataV1/>
- **文档摘要**：本页介绍 getAudioMetadataV1 函数：从音频文件中提取元数据字段（如规格、字节数、格式），返回结构体，属于 Media 类别。

### 128. Pipeline Builder Expressions • Extract content from spreadsheets in JSON

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/spreadsheetJsonExtractionV1/>
- **文档摘要**：本页介绍 spreadsheetJsonExtractionV1 函数：以 JSON 格式提取电子表格所有工作表的内容。可配置错误处理与输出字段，属于 Media 类别。

### 129. Pipeline Builder Expressions • Extract date part

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/datePartV1/>
- **文档摘要**：本页介绍 datePartV1 函数：从日期或时间戳中提取指定部分（如年、月、星期几等），返回整型。

### 130. Pipeline Builder Expressions • Extract document metadata

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/getDocumentMetadataV1/>
- **文档摘要**：本页介绍 getDocumentMetadataV1 函数：从 PDF 文档中提取元数据（如作者、标题、页数），返回结构体，属于 Media 类别。

### 131. Pipeline Builder Expressions • Extract email body

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/extractEmailBodyV1/>
- **文档摘要**：本页介绍 extractEmailBodyV1 函数：从邮件媒体项中提取邮件正文，支持纯文本或 HTML 格式，属于 Media 类别。

### 132. Pipeline Builder Expressions • Extract imagery metadata

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/getImageryMetadataV1/>
- **文档摘要**：本页介绍 getImageryMetadataV1 函数：从图像中提取元数据字段，不适用的字段返回 null，返回结构体，属于 Media 类别。

### 133. Pipeline Builder Expressions • Extract layout-aware content from PDF

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/pdfLayoutAwareContentExtractionV2/>
- **文档摘要**：本页介绍 pdfLayoutAwareContentExtractionV2 函数：从 PDF 页面提取版面感知内容，返回含边界框、文本与块类型的结构化块，支持配置 OCR 模式与输出格式。

### 134. Pipeline Builder Expressions • Extract layout-aware content from image

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/imageLayoutAwareContentExtractionV2/>
- **文档摘要**：本页介绍 imageLayoutAwareContentExtractionV2 函数：从图像提取版面感知内容，返回含边界框、文本与块类型的结构化块数组，支持配置输出格式。

### 135. Pipeline Builder Expressions • Extract map keys

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/extractMapKeysV1/>
- **文档摘要**：本页介绍 extractMapKeysV1 函数：将 Map 的键提取为数组（元素顺序不确定），返回 Array<K>，属于 Map 类别。

### 136. Pipeline Builder Expressions • Extract map values

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/extractMapValuesV1/>
- **文档摘要**：本页介绍 extractMapValuesV1 函数：将 Map 的值提取为数组（元素顺序不确定），返回 Array<V>，属于 Map 类别。

### 137. Pipeline Builder Expressions • Extract offset from legacy OffsetDateTime

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/extractOffsetFromLegacyOffsetDateTimeV1/>
- **文档摘要**：本页介绍 extractOffsetFromLegacyOffsetDateTimeV1 函数：从旧版 OffsetDateTime 列中提取时区偏移（相对于 UTC 的秒数），返回整型。

### 138. Pipeline Builder Expressions • Extract table of contents from PDF

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/pdfTableOfContentsV1/>
- **文档摘要**：本页介绍 pdfTableOfContentsV1 函数：依据文档中的标题生成 PDF 目录，返回含层级、标题与页码的结构体数组，属于 Media 类别。

### 139. Pipeline Builder Expressions • Extract text from PDF

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/pdfTextExtractionV2/>
- **文档摘要**：本页介绍 pdfTextExtractionV2 函数：从 PDF 页面提取原始文本，支持配置页码范围、错误处理与输出格式，返回字符串数组，属于 Media 类别。

### 140. Pipeline Builder Expressions • Extract text from PDF (using OCR)

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/pdfOcrV2/>
- **文档摘要**：本页介绍 pdfOcrV2 函数：使用 OCR 从 PDF 页面提取文本，支持配置 OCR 模式、识别语言与输出格式，属于 Media 类别。

### 141. Pipeline Builder Expressions • Extract text from images (using OCR)

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/imageOcrV2/>
- **文档摘要**：本页介绍 imageOcrV2 函数：使用 OCR 从图像提取文本，支持配置识别语言与输出格式，返回字符串，属于 Media 类别。

### 142. Pipeline Builder Expressions • Extract timestamp part

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/timestampPartV1/>
- **文档摘要**：本页介绍 timestampPartV1 函数：从时间戳中提取指定部分（如年、月、小时、毫秒等），返回整型。

### 143. Pipeline Builder Expressions • Filter array elements

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/filterArrayElementV1/>
- **文档摘要**：本页介绍 filterArrayElementV1 函数：依据过滤表达式筛选数组元素（索引从 1 开始），保留表达式为真的元素，返回 Array<T>，属于 Array 类别。

### 144. Pipeline Builder Expressions • Filter by geometry type

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/geometryFilterV1/>
- **文档摘要**：本页介绍 geometryFilterV1 函数：将几何列中不属于指定几何类型的值置为 null，属于 Geospatial 类别。

### 145. Pipeline Builder Expressions • First

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/firstV1/>
- **文档摘要**：本页介绍 firstV1 函数：返回分组中的第一个元素。在聚合或无序窗口中使用时选中行不确定，可配置忽略 null，属于 Aggregate 类别。

### 146. Pipeline Builder Expressions • First non null value (coalesce)

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/firstNonNullV1/>
- **文档摘要**：本页介绍 firstNonNullV1 函数：从多个输入中取第一个非 null 值（即 SQL 的 coalesce），可配置将空字符串视为 null。

### 147. Pipeline Builder Expressions • Floor

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/floorV1/>
- **文档摘要**：本页介绍 floorV1 函数：对给定的小数值向下取整，返回 Decimal 或 Long 类型，属于 Numeric 类别。

### 148. Pipeline Builder Expressions • Format date as string

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/dateToStringV2/>
- **文档摘要**：本页介绍 dateToStringV2 函数：按 Java DateTimeFormatter 将日期格式化为字符串，默认 ISO8601，属于 Cast、String 类别。

### 149. Pipeline Builder Expressions • Format number

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/formatNumberV1/>
- **文档摘要**：本页介绍 formatNumberV1 函数：将数字格式化为指定小数位数，返回字符串，属于 Cast、Numeric、String 类别。

### 150. Pipeline Builder Expressions • Format string

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/formatStringV1/>
- **文档摘要**：本页介绍 formatStringV1 函数：以 printf 风格格式化字符串，可插入多个参数，返回字符串，属于 String 类别。

### 151. Pipeline Builder Expressions • Format timestamp as string

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/timestampToStringV2/>
- **文档摘要**：本页介绍 timestampToStringV2 函数：将时间戳格式化为字符串（默认 ISO8601 含时区偏移），可指定格式与时区，属于 Cast、Datetime、String 类别。

### 152. Pipeline Builder Expressions • Geometries have intersection

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/geometryIntersectsV1/>
- **文档摘要**：本页介绍 geometryIntersectsV1 函数：判断两个几何是否相交，返回布尔值，属于 Geospatial 类别。

### 153. Pipeline Builder Expressions • Geometry 3d affine transformation

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/geometry3dAffineTransformationV1/>
- **文档摘要**：本页介绍 geometry3dAffineTransformationV1 函数：对输入几何施加三维仿射变换，在用户指定的投影坐标系中进行并投影回 WGS84。

### 154. Pipeline Builder Expressions • Geometry array (unary) union

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/geometryArrayUnionV1/>
- **文档摘要**：本页介绍 geometryArrayUnionV1 函数：将几何数组合并为单一几何（无重叠合并），属于 Geospatial 类别。

### 155. Pipeline Builder Expressions • Geometry array line dissolve

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/geometryArrayLineDissolveV1/>
- **文档摘要**：本页介绍 geometryArrayLineDissolveV1 函数：将几何数组合并为线状几何，通过移除多余节点并连接可合并的线串进行溶解，属于 Geospatial 类别。

### 156. Pipeline Builder Expressions • Geometry buffer

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/geometryBufferV1/>
- **文档摘要**：本页介绍 geometryBufferV1 函数：计算几何的缓冲区（支持正负距离），返回距该几何给定距离内（或之外）点的近似表示，属于 Geospatial 类别。

### 157. Pipeline Builder Expressions • Geometry centroid

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/geometryCentroidV1/>
- **文档摘要**：本页介绍 geometryCentroidV1 函数：使用球面近似返回几何的质心（中心），混合维度集合仅取最高维元素参与，返回 GeoPoint，属于 Geospatial 类别。

### 158. Pipeline Builder Expressions • Geometry contains

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/geometryContainsV1/>
- **文档摘要**：本页介绍 geometryContainsV1 函数：判断几何 a 是否包含几何 b（位于多边形边界上的点或线不视为被包含），返回布尔值，属于 Geospatial 类别。

### 159. Pipeline Builder Expressions • Geometry difference

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/geometryDifferenceV1/>
- **文档摘要**：本页介绍 geometryDifferenceV1 函数：计算几何 a 中与几何 b 不相交的部分，返回 Geometry 类型，属于 Geospatial 类别。

### 160. Pipeline Builder Expressions • Geometry explode to array

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/geometryExplodeToArrayV1/>
- **文档摘要**：本页介绍 geometryExplodeToArrayV1 函数：将几何拆分为各组成简单几何的数组，返回 Array<Geometry>，属于 Geospatial 类别。

### 161. Pipeline Builder Expressions • Geometry intersection

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/geometryIntersectionV1/>
- **文档摘要**：本页介绍 geometryIntersectionV1 函数：计算几何 a 与几何 b 相交的部分，返回 Geometry 类型，属于 Geospatial 类别。

### 162. Pipeline Builder Expressions • Geometry length

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/geometryLengthV1/>
- **文档摘要**：本页介绍 geometryLengthV1 函数：以米为单位返回几何中线段与多线段的长度（使用球面近似，非线几何计为 0），返回 Double，属于 Geospatial 类别。

### 163. Pipeline Builder Expressions • Geometry rotate 2d

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/geometryRotate2dV1/>
- **文档摘要**：本页介绍 geometryRotate2dV1 函数：以给定 GeoPoint 为中心对几何施加二维顺时针旋转，在指定参考系中进行后投影回 WGS84，属于 Geospatial 类别。

### 164. Pipeline Builder Expressions • Geometry set z-coordinate

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/geometrySetZCoordinateV1/>
- **文档摘要**：本页介绍 geometrySetZCoordinateV1 函数：设置几何的 z 坐标（已有则覆盖），返回 Geometry 类型，属于 Geospatial 类别。

### 165. Pipeline Builder Expressions • Geometry shortest distance

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/geometryShortestDistanceV1/>
- **文档摘要**：本页介绍 geometryShortestDistanceV1 函数：计算两个几何间最短（大圆）距离（米，使用球面近似，重叠几何距离为 0），返回 Double，属于 Geospatial 类别。

### 166. Pipeline Builder Expressions • Geometry standardize

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/geometryStandardizeV1/>
- **文档摘要**：本页介绍 geometryStandardizeV1 函数：对几何标准化，强制采用 GeoJSON 约定的右手定则，便于等价几何间的相等比较，返回 Geometry，属于 Geospatial 类别。

### 167. Pipeline Builder Expressions • Geometry symmetric difference

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/geometrySymmetricDifferenceV1/>
- **文档摘要**：本页介绍 geometrySymmetricDifferenceV1 函数：计算两个几何的对称差（属于任一几何但不在其交集中的部分），返回 Geometry，属于 Geospatial 类别。

### 168. Pipeline Builder Expressions • Geometry translate expression

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/geometryTranslateV1/>
- **文档摘要**：本页介绍 geometryTranslateV1 函数：对几何施加平移变换（提供 z 偏移时二维几何才转为三维），在指定参考系中操作后投影回 WGS84，属于 Geospatial 类别。

### 169. Pipeline Builder Expressions • Geometry union

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/geometryUnionV1/>
- **文档摘要**：本页介绍 geometryUnionV1 函数：将两个几何合并为单一几何，返回 Geometry 类型，属于 Geospatial 类别。

### 170. Pipeline Builder Expressions • Get H3 index

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/h3IndexExpressionV1/>
- **文档摘要**：本页介绍 h3IndexExpressionV1 函数：将 GeoPoint 转换为指定分辨率的 H3 索引（分辨率须在 0–15，越界返回 null），返回 H3 Index，属于 Geospatial 类别。

### 171. Pipeline Builder Expressions • Get H3 indices covering a geometry

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/polygonToH3V1/>
- **文档摘要**：本页介绍 polygonToH3V1 函数：将几何（多边形/线/点）转换为指定分辨率的 H3 索引数组（分辨率 0–15），支持覆盖、内嵌、外扩等覆盖类型，属于 Geospatial 类别。

### 172. Pipeline Builder Expressions • Get MIME type

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/getMimeTypeV1/>
- **文档摘要**：本页介绍 getMimeTypeV1 函数：返回媒体引用的 IANA MIME 类型。输入为媒体引用表达式（Expression<Media reference>），输出类型为 String，常用于按类型筛选或路由媒体数据。

### 173. Pipeline Builder Expressions • Get PDF page dimensions

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/getPdfPageDimensionsV1/>
- **文档摘要**：本页介绍 getPdfPageDimensionsV1 函数：获取 PDF 每一页的尺寸（以 points 为单位）。输入为媒体引用表达式，输出类型为 Array<Struct<height, width>>，可用于分析文档页面布局。

### 174. Pipeline Builder Expressions • Get XZ curve index of an envelope

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/xzCurveEncodeV1/>
- **文档摘要**：本页介绍 xzCurveEncodeV1 函数：将经纬度包络（envelope）编码进 XZ 曲线以生成空间索引值。参数含曲线预设枚举（LonLat10km/150km/1km）与 LatLonBoundingBox 包络，输出类型为 Long，分辨率越高查询越贵但误报更少。

### 175. Pipeline Builder Expressions • Get bearing from start point to end point

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/getBearingV1/>
- **文档摘要**：本页介绍 getBearingV1 函数：基于球面近似，计算从起点到终点的绝对真方位角（相对正北的顺时针角度，单位为度）。输入为两个 GeoPoint，输出类型为 Double，支持批处理、Faster 与流式模式。

### 176. Pipeline Builder Expressions • Get geometry envelope

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/geometryEnvelopeV1/>
- **文档摘要**：本页介绍 geometryEnvelopeV1 函数：接收单个或一组 GeoJSON 几何，返回能包含其所有最小/最大 x、y 值的最小轴对齐矩形包络。输入为 Geometry 或 Array<Geometry>，输出类型为 Geometry。

### 177. Pipeline Builder Expressions • Get lat/long bounding box struct

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/latLonBoundingBoxV1/>
- **文档摘要**：本页介绍 latLonBoundingBoxV1 函数：对单个或一组 GeoJSON 几何，返回包含其经纬度边界的结构体（含 maxLat/maxLon/minLat/minLon 字段）。输入为 Geometry 或 Array<Geometry>，输出类型为 LatLonBoundingBox。

### 178. Pipeline Builder Expressions • Get neighbors of an H3 index

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/h3NeighborsV1/>
- **文档摘要**：本页介绍 h3NeighborsV1 函数：返回指定 H3 索引的所有相邻单元索引。输入为有效的 H3 Index，输出类型为 Array<H3 Index>，非法索引返回 null。

### 179. Pipeline Builder Expressions • Get struct field

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/getStructFieldV2/>
- **文档摘要**：本页介绍 getStructFieldV2 函数：从结构体中提取指定字段，支持通过形如 ['author','email'] 的多级定位符访问嵌套元素。输入为结构体表达式与 StructLocator 定位符，输出类型为 AnyType。

### 180. Pipeline Builder Expressions • Get the convex hull of a geometry

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/geometryConvexHullV1/>
- **文档摘要**：本页介绍 geometryConvexHullV1 函数：对有效的 GeoJSON 几何，返回包含该几何的最小凸多边形（凸包）。输入为 Geometry 表达式，输出类型同为 Geometry。

### 181. Pipeline Builder Expressions • Get timestamps for scene frames

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/getTimestampsForSceneFramesV1/>
- **文档摘要**：本页介绍 getTimestampsForSceneFramesV1 函数：提取视频中检测到的场景切换时间点及场景评分。参数含媒体引用与场景敏感度枚举（Less/More sensitive、Standard），输出类型为 Array<Struct<timestamp, scene_score>>，仅支持批处理。

### 182. Pipeline Builder Expressions • Greater than

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/greaterThanV2/>
- **文档摘要**：本页介绍 greaterThanV2 函数：当左操作数大于右操作数时返回 true。输入为两个可比较类型（ComparableType）表达式，输出类型为 Boolean，支持数值、字符串等比较。

### 183. Pipeline Builder Expressions • Greater than or equals

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/greaterThanOrEqualsV2/>
- **文档摘要**：本页介绍 greaterThanOrEqualsV2 函数：当左操作数大于或等于右操作数时返回 true。输入为两个可比较类型表达式，输出类型为 Boolean。

### 184. Pipeline Builder Expressions • Greatest

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/greatestV1/>
- **文档摘要**：本页介绍 greatest 函数：在多个输入列中计算最大值，自动跳过 null 值；若全部为 null 则返回 null。输入为可比较类型表达式列表，输出为同类型 T。

### 185. Pipeline Builder Expressions • Grouped geometry envelope

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/groupedGeometryEnvelopeV1/>
- **文档摘要**：本页介绍 groupedGeometryEnvelopeV1 函数：对分组列中所有有效几何计算合并包络，非法几何按 null 忽略。输入为 Geometry 列，输出类型为 Geometry，仅支持批处理与 Faster 模式。

### 186. Pipeline Builder Expressions • Grouped geometry union

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/groupedGeometryUnionV1/>
- **文档摘要**：本页介绍 groupedGeometryUnionV1 函数：将分组列中的多个几何合并为单一几何（几何并集）。输入为 Geometry 列，输出类型为 Geometry，仅支持批处理模式。

### 187. Pipeline Builder Expressions • Grouped latitude/longitude bounding box

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/groupedLatLonBoundingBoxV1/>
- **文档摘要**：本页介绍 groupedLatLonBoundingBoxV1 函数：对分组列中所有有效几何，返回包含其整体范围的经纬度边界结构体，非法几何按 null 忽略。输入为 Geometry 列，输出类型为 LatLonBoundingBox，仅支持批处理。

### 188. Pipeline Builder Expressions • Gzip decompress

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/gzipDecompressV1/>
- **文档摘要**：本页介绍 gzipDecompressV1 函数：将 gzip 压缩的二进制数据解压为字符串。输入为 Binary 表达式，输出类型为 String，支持批处理、Faster 与流式模式。

### 189. Pipeline Builder Expressions • H3 cell to children

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/h3CellToChildrenV1/>
- **文档摘要**：本页介绍 h3CellToChildrenV1 函数：获取指定 H3 索引在给定分辨率（0-15）下的子单元。若分辨率非法或低于索引自身分辨率则返回 null，输出类型为 Array<H3 Index>。

### 190. Pipeline Builder Expressions • H3 cell to parent

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/h3CellToParentV1/>
- **文档摘要**：本页介绍 h3CellToParentV1 函数：获取指定 H3 索引在给定分辨率（0-15）下的父单元。若分辨率非法或高于索引自身则返回 null，输出类型为 H3 Index。

### 191. Pipeline Builder Expressions • H3 to geometry

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/h3ToGeometryV1/>
- **文档摘要**：本页介绍 h3ToGeometryV1 函数：将 H3 索引转换为对应的多边形几何。输入为有效的 H3 Index，输出类型为 Geometry。

### 192. Pipeline Builder Expressions • Has media schema

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/hasMediaSchemaV1/>
- **文档摘要**：本页介绍 hasMediaSchemaV1 函数：检查媒体引用是否具有指定的媒体模式类型与格式，可作为过滤条件按媒体类型筛选媒体集。参数含媒体格式枚举、媒体引用与模式枚举（Audio/Document/Image 等），输出类型为 Boolean，仅支持批处理。

### 193. Pipeline Builder Expressions • Hash sha256

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/sha256V1/>
- **文档摘要**：本页介绍 hashSha256V1 函数：使用 SHA-256 算法对输入进行哈希计算。输入为 Binary 或 String 表达式，输出类型为 String，null 输入返回 null。

### 194. Pipeline Builder Expressions • IPv6 to canonical format

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/ipV6ToCanonicalIpV6V1/>
- **文档摘要**：本页介绍 ipv6ToCanonicalV1 函数：依据 RFC 5952 将 IPv6 地址转换为规范表示形式。输入为 IPv6 表达式，输出类型同为 IPv6，仅支持批处理。

### 195. Pipeline Builder Expressions • Image to embeddings

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/imageToEmbeddingsV1/>
- **文档摘要**：本页介绍 imageToEmbeddingsV1 函数：使用指定的（需具备视觉能力的）嵌入模型将图像转换为向量嵌入。参数含媒体引用、模型与输出模式枚举（Simple/With errors），输出类型为 Embedded vector。

### 196. Pipeline Builder Expressions • Interpolate geo point along linestring

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/interpolateGeoPointAlongLinestringV1/>
- **文档摘要**：本页介绍 interpolateGeoPointAlongLineStringV1 函数：沿线段按给定比例（0-1）插值返回一个点，采用球面最短路径近似。输入为比例值与 LineString 几何，输出类型为 GeoPoint。

### 197. Pipeline Builder Expressions • Is NaN

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/isNaNV1/>
- **文档摘要**：本页介绍 isNaNV1 函数：判断输入是否为 NaN（非数字），是则返回 true。输入为 Double 或 Float 表达式，输出类型为 Boolean，null 输入返回 false。

### 198. Pipeline Builder Expressions • Is empty struct

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/isEmptyStructV1/>
- **文档摘要**：本页介绍 isEmptyStructV1 函数：判断输入是否为空结构体，并递归检查内部数组与嵌套结构体是否均为空。输入为 Struct 表达式，输出类型为 Boolean。

### 199. Pipeline Builder Expressions • Is in

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/isInV1/>
- **文档摘要**：本页介绍 isInV1 函数：判断给定列表是否包含某个值，是则返回 true。列表元素与待查值可为数组、结构体等可比较类型，输出类型为 Boolean。

### 200. Pipeline Builder Expressions • Is not null

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/isNotNullV1/>
- **文档摘要**：本页介绍 isNotNullV1 函数：判断输入是否非空，可选将空字符串也视为 null。输入为任意类型表达式及可选的布尔标志，输出类型为 Boolean。

### 201. Pipeline Builder Expressions • Is null

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/isNullV1/>
- **文档摘要**：本页介绍 isNullV1 函数：判断输入是否为 null，可选将空字符串也视为 null。输入为任意类型表达式及可选的布尔标志，输出类型为 Boolean。

### 202. Pipeline Builder Expressions • Is valid GeoJSON

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/isValidGeoJsonV1/>
- **文档摘要**：本页介绍 isValidGeoJsonV1 函数：判断输入是否为合法的 GeoJSON 字符串。注意并非所有 GeoJSON 都能被 Ontology 索引，需配合 prepare geometry 表达式预处理，输出类型为 Boolean。

### 203. Pipeline Builder Expressions • Is valid Geohash

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/isValidGeohashV1/>
- **文档摘要**：本页介绍 isValidGeohashV1 函数：判断输入是否为合法的 Geohash 字符串。输入为 String 表达式，输出类型为 Boolean。

### 204. Pipeline Builder Expressions • Is valid H3 index

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/isValidH3IndexV1/>
- **文档摘要**：本页介绍 isValidH3IndexV1 函数：判断输入是否为合法的 H3 索引字符串。输入为 String 表达式，输出类型为 Boolean。

### 205. Pipeline Builder Expressions • Is valid IPv4

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/isValidIpV4V1/>
- **文档摘要**：本页介绍 isValidIPv4V1 函数：判断输入是否为合法的 IPv4 地址。输入为 String 表达式，输出类型为 Boolean，仅支持批处理模式。

### 206. Pipeline Builder Expressions • Is valid IPv6

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/isValidIpV6V1/>
- **文档摘要**：本页介绍 isValidIPv6V1 函数：判断输入是否为合法的 IPv6 地址。输入为 String 表达式，输出类型为 Boolean，仅支持批处理模式。

### 207. Pipeline Builder Expressions • Is valid MGRS

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/isValidMgrsV1/>
- **文档摘要**：本页介绍 isValidMgrsV1 函数：判断输入是否为合法的 MGRS（军事网格参考系统）字符串。输入为 String 表达式，输出类型为 Boolean。

### 208. Pipeline Builder Expressions • Is valid MIME type

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/isValidMimeTypeV1/>
- **文档摘要**：本页介绍 isValidMimeTypeV1 函数：判断输入是否为合法的 MIME 类型字符串。输入为 String 表达式，输出类型为 Boolean。

### 209. Pipeline Builder Expressions • Is valid Ontology GeoPoint

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/isValidOntologyGeopointV1/>
- **文档摘要**：本页介绍 isValidOntologyGeoPointV1 函数：判断输入是否为合法的 Ontology GeoPoint 字符串，格式为 '{lat},{lon}'（纬度 -90~90，经度 -180~180）。输入为 String 表达式，输出类型为 Boolean。

### 210. Pipeline Builder Expressions • Is valid delegated media gid

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/isValidDelegatedMediaGidV1/>
- **文档摘要**：本页介绍 isValidDelegatedMediaGidV1 函数：判断输入是否为合法的 Gotham 委托媒体 gid 字符串。输入为 String 表达式，输出类型为 Boolean，非法或 null 时返回 false/null。

### 211. Pipeline Builder Expressions • Is valid media reference

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/isValidMediaReferenceV2/>
- **文档摘要**：本页介绍 isValidMediaReferenceV1 函数：判断输入在结构上是否为 Foundry 媒体引用且 MIME 类型可解析，仅做格式校验（旧格式也返回 true）。输入为 String 表达式，输出类型为 Boolean。

### 212. Pipeline Builder Expressions • Is valid rid

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/isValidRidV1/>
- **文档摘要**：本页介绍 isValidRidV1 函数：判断输入是否为合法的 Foundry 资源标识符（RID）。输入为 String 表达式，输出类型为 Boolean。

### 213. Pipeline Builder Expressions • Is valid uuid

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/isValidUuidV1/>
- **文档摘要**：本页介绍 isValidUuidV1 函数：判断输入是否为合法的 UUID 字符串（不区分大小写）。输入为 String 表达式，输出类型为 Boolean。

### 214. Pipeline Builder Expressions • Join array

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/arrayJoinV1/>
- **文档摘要**：本页介绍 joinArrayV1 函数：使用指定分隔符将字符串数组拼接为单个字符串。输入为 Array<String> 与分隔符表达式，输出类型为 String，空数组返回空字符串。

### 215. Pipeline Builder Expressions • Lag

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/lagV1/>
- **文档摘要**：本页介绍 lagV1 函数：在有序窗口中返回当前行之前第 N 行（lag 指定）的输入表达式值，无需自连接即可与历史行比较。支持指定默认值，输出类型为 T，仅支持批处理与 Faster。

### 216. Pipeline Builder Expressions • Last

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/lastV1/>
- **文档摘要**：本页介绍 lastV1 函数：返回分组中的最后一个元素。在聚合或无序窗口中使用时选中行不确定，可设置忽略 null 值，输出类型为 T。

### 217. Pipeline Builder Expressions • Last day of the week/month/quarter/year

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/lastDayV1/>
- **文档摘要**：本页介绍 lastDayOfV1 函数：返回给定日期所在周/月/季度/年的最后一天。参数为日期表达式与单位枚举（Months/Quarters/Weeks/Years），输出类型为 Date。

### 218. Pipeline Builder Expressions • Lead

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/leadV1/>
- **文档摘要**：本页介绍 leadV1 函数：在有序窗口中返回当前行之后第 N 行（lead 指定，默认 1）的输入表达式值。支持指定默认值，输出类型为 T，仅支持批处理与 Faster。

### 219. Pipeline Builder Expressions • Least

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/leastV1/>
- **文档摘要**：本页介绍 leastV1 函数：在多个输入列中计算最小值，自动跳过 null 值；若全部为 null 则返回 null。输入为可比较类型表达式列表，输出为同类型 T。

### 220. Pipeline Builder Expressions • Left of string

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/leftStringV1/>
- **文档摘要**：本页介绍 leftOfStringV1 函数：按指定长度从字符串左侧截取子串。参数为字符串表达式与截取长度（整数），输出类型为 String，长度超出则返回完整字符串。

### 221. Pipeline Builder Expressions • Left pad string

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/leftPadV1/>
- **文档摘要**：本页介绍 leftPadStringV1 函数：使用指定填充串将字符串左补齐到指定宽度。参数为字符串、目标长度与填充串，输出类型为 String；若长度小于原串则截断。

### 222. Pipeline Builder Expressions • Length

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/lengthV1/>
- **文档摘要**：本页介绍 lengthV1 函数：返回字符串、数组、二进制或映射类型列中每个值的长度。输入为相应类型表达式，输出类型为 Integer。

### 223. Pipeline Builder Expressions • Less than

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/lessThanV2/>
- **文档摘要**：本页介绍 lessThanV2 函数：当左操作数小于右操作数时返回 true。输入为两个可比较类型表达式，输出类型为 Boolean。

### 224. Pipeline Builder Expressions • Less than or equals

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/lessThanOrEqualsV2/>
- **文档摘要**：本页介绍 lessThanOrEqualsV2 函数：当左操作数小于或等于右操作数时返回 true。输入为两个可比较类型表达式，输出类型为 Boolean。

### 225. Pipeline Builder Expressions • Linear regression gradient

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/linearRegressionGradientV2/>
- **文档摘要**：本页介绍 linearRegressionGradientV1 函数：返回分组内非空 (x,y) 数据对的线性回归直线斜率。若有效数据对不足或自变量方差为 0 则返回 null，输入为两个数值列，输出类型为 Double，仅支持批处理。

### 226. Pipeline Builder Expressions • Logarithm

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/logV1/>
- **文档摘要**：本页介绍 logarithmV1 函数：计算列中数值的自然对数 ln(x)。输入为数值型表达式，输出类型为 Double，null 输入返回 null。

### 227. Pipeline Builder Expressions • Logarithm with base

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/logNV1/>
- **文档摘要**：本页介绍 logarithmWithBaseV1 函数：计算以指定底数为基的对数。参数含底数字面量（Double）与数值型表达式，输出类型为 Double，null 输入返回 null。

### 228. Pipeline Builder Expressions • Logical type cast

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/logicalTypeCastV1/>
- **文档摘要**：本页介绍 logicalTypeCastV1 函数：将表达式转换为指定逻辑类型，与常规 cast 不同，它不改变底层基础数据表示，仅强制该逻辑类型的约束，使输出可作为要求该类型的下游表达式输入。支持指定默认值，输出类型为 C。

### 229. Pipeline Builder Expressions • Lowercase

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/lowercaseV1/>
- **文档摘要**：本页介绍 lowercaseV1 函数：将字符串中所有字符转换为小写。支持 Batch、Faster、Streaming，属 String 类表达式。参数 Expression 为待转换字符串（Expression<String>），输出类型 String；页面给出基本用例与 null 输入示例。

### 230. Pipeline Builder Expressions • Map values

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/mapValuesV2/>
- **文档摘要**：本页介绍 mapValuesV2 函数：基于键值对映射表将输入列的值替换为新值，未在映射表中找到时使用默认值。支持 Batch/Faster/Streaming，属 Data preparation 类。参数含待替换列、默认值与映射表（Expression<Map<T1,T2>>），输出类型 T2。

### 231. Pipeline Builder Expressions • Max

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/maxV1/>
- **文档摘要**：本页介绍 maxV1 函数：计算列中的最大值。支持 Batch/Faster/Streaming，属 Numeric 类。参数为待计算列（Expression<ComparableType>），输出类型 ComparableType；null 值会被忽略不计入。

### 232. Pipeline Builder Expressions • Max by

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/maxByV1/>
- **文档摘要**：本页介绍 maxByV1 函数：在流式（Streaming）窗口中按最大值列表达式计算最大行，可经过滤条件筛选并返回投影表达式结果。属 Aggregate 类。参数为最大列表达式、可选投影表达式与可选过滤条件，输出类型 AnyType；无匹配行时返回 null。

### 233. Pipeline Builder Expressions • Mean

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/meanV1/>
- **文档摘要**：本页介绍 meanV1 函数：计算列中数值的均值。支持 Batch/Faster/Streaming，属 Numeric 类。参数为数值列（Expression<Numeric>），输出 Decimal 或 Double；null 值不参与计算。

### 234. Pipeline Builder Expressions • Median

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/medianV1/>
- **文档摘要**：本页介绍 medianV1 函数：计算列中数值的中位数。支持 Batch、Faster，属 Numeric 类。参数为数值列（Expression<Numeric>），输出 Decimal 或 Double；页面提供多种含 null 与边界情形示例。

### 235. Pipeline Builder Expressions • Min

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/minV1/>
- **文档摘要**：本页介绍 minV1 函数：计算列中的最小值。支持 Batch/Faster/Streaming，属 Numeric 类。参数为待计算列（Expression<ComparableType>），输出类型 ComparableType；null 值会被忽略。

### 236. Pipeline Builder Expressions • Min by

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/minByV1/>
- **文档摘要**：本页介绍 minByV1 函数：在流式（Streaming）窗口中按最小值列表达式计算最小行，可经过滤条件筛选并返回投影表达式结果。属 Aggregate 类。参数为最小列表达式、可选投影表达式与可选过滤条件，输出 AnyType；无匹配行返回 null。

### 237. Pipeline Builder Expressions • Mode

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/modeV1/>
- **文档摘要**：本页介绍 modeV1 函数：计算列中出现频率最高的众数。支持 Batch、Faster，属 Aggregate 类。参数可为 Binary/String/Date/数值等多种类型，输出同类型；列全空时返回 null。

### 238. Pipeline Builder Expressions • Modulo

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/moduloV1/>
- **文档摘要**：本页介绍 moduloV1 函数：返回表达式的模（取余）运算结果。支持 Batch/Faster/Streaming，属 Numeric 类。参数 Denominator 与 Numerator 均为 DefiniteNumeric，输出 DefiniteNumeric；除数为 0 时返回 null。

### 239. Pipeline Builder Expressions • Multiply numbers

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/multiplyV2/>
- **文档摘要**：本页介绍 multiplyV2 函数：计算所有输入列的乘积。支持 Batch/Faster/Streaming，属 Numeric 类。参数为列表达式列表（List<Expression<Numeric>>），输出 Numeric；任一输入为 null 时结果为 null。

### 240. Pipeline Builder Expressions • Natural random number

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/naturalRandomV1/>
- **文档摘要**：本页介绍 naturalRandomV1 函数：返回一个随机自然数，非确定性。支持 Batch/Faster/Streaming，属 Numeric 类。参数为上界（不含，Literal<Long>）、下界（含，可选）与可选种子（Literal<Long>），输出 Long；即使使用种子也不完全确定。

### 241. Pipeline Builder Expressions • Negate

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/negateV1/>
- **文档摘要**：本页介绍 negateV1 函数：对数值表达式取负。支持 Batch/Faster/Streaming，属 Numeric 类。参数为待取负的数值表达式（Expression<Numeric>），输出 Numeric；页面给出 decimal/double/int/long/short 等示例。

### 242. Pipeline Builder Expressions • Next day

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/nextDayV1/>
- **文档摘要**：本页介绍 nextDayV1 函数：返回晚于给定日期的第一个指定星期几的日期。仅支持 Batch，属 Datetime 类。参数为日期（Expression<Date>）与星期枚举，输出 Date；含闰年等边界示例。

### 243. Pipeline Builder Expressions • Normal random number

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/normalRandomV1/>
- **文档摘要**：本页介绍 normalRandomV1 函数：返回服从正态分布（零均值、单位方差）的随机数，非确定性。支持 Batch/Faster/Streaming，属 Numeric 类。可选种子（Literal<Long>），输出 Double；即使使用种子也不完全确定。

### 244. Pipeline Builder Expressions • Not

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/notV1/>
- **文档摘要**：本页介绍 notV1 函数：对布尔表达式取反。支持 Batch/Faster/Streaming，属 Boolean 类。参数为布尔表达式（Expression<Boolean>），输出 Boolean；输入为 null 时返回 null。

### 245. Pipeline Builder Expressions • Not any

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/notAnyV1/>
- **文档摘要**：本页介绍 notAnyV1 函数：仅当所有条件都不成立时返回 true。支持 Batch/Streaming，属 Boolean 类。参数为布尔条件列表（List<Expression<Boolean>>），输出 Boolean；ANSI 模式关闭时 null 视为 false，否则传播 null。

### 246. Pipeline Builder Expressions • Nth chain in polygon

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/getNthChainFromPolygonV1/>
- **文档摘要**：本页介绍 getNthChainFromPolygonV1 函数：返回多边形几何中第 n 个环（1 基索引，1 为外环）。支持 Batch/Faster/Streaming，属 Geospatial 类。参数为索引 N 与多边形，输出 Geometry；非单多边形或索引越界等返回 null。

### 247. Pipeline Builder Expressions • Nth point in linestring

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/linestringPointNV1/>
- **文档摘要**：本页介绍 linestringPointNV1 函数：返回线串几何中第 n 个点（1 基索引，负索引从末尾倒数）。支持 Batch/Faster/Streaming，属 Geospatial 类。参数为线串与索引 N，输出 GeoPoint；几何非法或越界返回 null。

### 248. Pipeline Builder Expressions • Nullify empty string

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/nullifyEmptyStringV1/>
- **文档摘要**：本页介绍 nullifyEmptyStringV1 函数：将空字符串转换为 null。支持 Batch/Faster/Streaming，属 String 类。参数为字符串表达式（Expression<String>），输出 String；非空字符串原样返回，null 仍为 null。

### 249. Pipeline Builder Expressions • Or

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/orV1/>
- **文档摘要**：本页介绍 orV1 函数：当任一指定条件为真时返回 true。支持 Batch/Faster/Streaming，属 Boolean 类。参数为布尔条件列表（List<Expression<Boolean>>），输出 Boolean；ANSI 模式关闭时 null 视为 false，否则传播 null。

### 250. Pipeline Builder Expressions • Parse GeoJSON from a non-WGS 84 coordinate system

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/parseGeoJsonAsGeometryV1/>
- **文档摘要**：本页介绍 parseGeoJsonAsGeometryV1 函数：将非 WGS 84 的 GeoJSON 字符串转为 WGS 84 几何。支持 Batch/Faster/Streaming。参数为 GeoJSON 字符串与源坐标系标识（如 EPSG:32618），输出 Geometry，解析或转换失败返回 null。

### 251. Pipeline Builder Expressions • Parse JSON string

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/parseJsonAsSchemaV3/>
- **文档摘要**：本页介绍 parseJsonAsSchemaV3 函数：按给定 schema 解析 JSON 字符串，忽略 schema 外字段。支持 Batch/Faster/Streaming，输出 Array/Map/Struct。参数为 JSON 表达式、可选 schema 与可选输出模式。

### 252. Pipeline Builder Expressions • Parse KML string as geometry

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/parseKmlAsGeometryV1/>
- **文档摘要**：本页介绍 parseKmlAsGeometryV1 函数：将 KML 几何定义解析为 GeoJSON（忽略属性），需先提取为文本。支持 Batch/Streaming。参数含 KML 字符串、可选输出模式与解析后是否准备几何，输出 String 或含 ok/error 字段的 Struct。

### 253. Pipeline Builder Expressions • Parse KML string as geometry list

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/parseKmlAsGeometryListV1/>
- **文档摘要**：本页介绍 parseKmlAsGeometryListV1 函数：将 KML 字符串解析为 GeoJSON 几何列表（忽略属性）。支持 Batch/Streaming。参数含 KML 字符串、可选输出模式与是否准备几何，输出 Array<Geometry> 或含 ok/error 字段的 Struct。

### 254. Pipeline Builder Expressions • Parse XML as schema

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/parseXmlAsSchemaV2/>
- **文档摘要**：本页介绍 parseXmlAsSchemaV2 函数：按给定 schema 解析 XML 字符串，忽略 schema 外字段。支持 Batch/Streaming，属 File/Struct 类。参数含输入 schema、XML 字符串及可选属性前缀、忽略命名空间、输出模式、值标签等，输出 Struct。

### 255. Pipeline Builder Expressions • Parse classification string

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/cbacStringToGroupNamesV3/>
- **文档摘要**：本页介绍 cbacStringToGroupNamesV3 函数：从给定分类字符串解析出标记（markings）。支持 Batch/Streaming。参数为分类字符串（Expression<String>），输出 Struct<markingIds, errors>；字符串无效时列表为 null，异步调用以提升性能。

### 256. Pipeline Builder Expressions • Parse duration

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/parseDurationV1/>
- **文档摘要**：本页介绍 parseDurationV1 函数：解析 ISO8601 时长字符串并结合起始时间，计算其在指定时间单位下的长度。仅支持 Batch，属 Datetime/String 类。参数含时长字符串、起始时间戳与单位枚举（天/时/分/月等），输出 Long；无效时长返回 null。

### 257. Pipeline Builder Expressions • Parse phone number

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/parsePhoneNumberV2/>
- **文档摘要**：本页介绍 parsePhoneNumberV2 函数：将不同地区和格式的电话号码规范化为统一格式。支持 Batch/Faster/Streaming，属 String 类。参数为电话号码表达式、可选格式枚举与可选地区，无法解析时返回 null。

### 258. Pipeline Builder Expressions • Parse semantic version

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/parseSemanticVersionV1/>
- **文档摘要**：本页介绍 parseSemanticVersionV1 函数：将语义化版本字符串解析为逻辑类型（含主/次/修订及预发布）。支持 Batch/Streaming，属 String 类。参数为版本字符串（Expression<String>），输出 Semantic Version；格式不符返回 null。

### 259. Pipeline Builder Expressions • Parse well known binary as geometry

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/parseWellKnownBinaryAsGeometryV1/>
- **文档摘要**：本页介绍 parseWellKnownBinaryAsGeometryV1 函数：将 WKB（已知二进制）转换为几何逻辑类型。支持 Batch/Faster/Streaming，属 Geospatial 类。参数为二进制表达式与可选源坐标系，输出 Geometry；无效输入返回 null。

### 260. Pipeline Builder Expressions • Parse well known text as geometry

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/parseWellKnownTextAsGeometryV1/>
- **文档摘要**：本页介绍 parseWellKnownTextAsGeometryV1 函数：将 WKT（已知文本）字符串转换为几何逻辑类型。支持 Batch/Faster/Streaming，属 Geospatial 类。参数为文本表达式与可选源坐标系，输出 Geometry；无效输入返回 null。

### 261. Pipeline Builder Expressions • Partition array

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/arrayPartitionV1/>
- **文档摘要**：本页介绍 arrayPartitionV1 函数：将数组按给定大小拆分为连续子数组（保留顺序），大小须为正整数。支持 Batch/Streaming，属 Array 类。参数为数组与大小（Expression<Integer>），输出 Array<Array<T>>；数组或大小无效返回 null。

### 262. Pipeline Builder Expressions • Percent rank

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/percentRankV1/>
- **文档摘要**：本页介绍 percentRankV1 函数：返回窗口分区内各行的百分位排名，相同值（平局）获得相同百分位。支持 Batch/Faster，属 Aggregate 类。该函数无参数，输出 Double。

### 263. Pipeline Builder Expressions • Perimeter

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/perimeterV1/>
- **文档摘要**：本页介绍 perimeterV1 函数：使用球面近似计算几何的周长（单位：米）；线串或点周长为 0。支持 Batch/Streaming，属 Geospatial 类。参数为几何表达式（Expression<Geometry>），输出 Double；几何为 null 时返回 null。

### 264. Pipeline Builder Expressions • Pivot

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/pivotExpressionV1/>
- **文档摘要**：本页介绍 pivotExpressionV1 函数：在流式（Streaming）透视上下文中应用聚合表达式，聚合按透视表达式各不同值分别作用，输出为透视值到聚合结果的映射。属 Aggregate 类。参数为聚合表达式与透视表达式，输出 Map<K,V>。

### 265. Pipeline Builder Expressions • Positive modulo

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/positiveModuloV1/>
- **文档摘要**：本页介绍 positiveModuloV1 函数：返回表达式的正模运算结果（整数类型）。支持 Batch/Faster，属 Numeric 类。参数 Denominator 与 Numerator 为整数类型，输出分子类型 T1；除数为 0 或任一为 null 返回 null。

### 266. Pipeline Builder Expressions • Power of

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/powerOfV1/>
- **文档摘要**：本页介绍 powerOfV1 函数：计算表达式的指定次幂。支持 Batch/Faster/Streaming，属 Numeric 类。参数为底数 Expression 与指数 Exponent（均为 Numeric），输出 Double；任一为 null 返回 null。

### 267. Pipeline Builder Expressions • Prepare geometry

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/normalizeGeometryV4/>
- **文档摘要**：本页介绍 normalizeGeometryV4 函数：将几何字符串转为合法 GeoJSON，为下游使用（如 Ontology 索引）做准备——闭合去重多边形、分裂跨反子午线几何。支持 Batch/Streaming，参数为几何字符串与可选输出模式，输出 Geometry 或含 ok/error 字段的 Struct。

### 268. Pipeline Builder Expressions • Product

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/productV1/>
- **文档摘要**：本页介绍 productV1 函数：计算所有输入列的乘积。仅支持 Batch，属 Numeric 类。参数为数值表达式（Expression<Numeric>），输出 Double；null 值跳过参与计算。

### 269. Pipeline Builder Expressions • ROUGE score

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/rougeScoreV1/>
- **文档摘要**：本页介绍 rougeScoreV1 函数：计算预测与目标文本的 ROUGE 精确率、召回率与 F 值。仅支持 Batch。参数含预测文本、ROUGE 变体枚举、目标文本与可选词干提取，输出含 precision/recall/fmeasure 的 Struct；输入 null 或超界返回 null。

### 270. Pipeline Builder Expressions • Rank

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/rankV1/>
- **文档摘要**：本页介绍 rankV1 函数：返回窗口分区内各行的排名，平局取相同名次，并在名次间留空（与 dense_rank 不同）。支持 Batch/Faster，属 Aggregate 类。该函数无参数，输出 Integer。

### 271. Pipeline Builder Expressions • Reduce array elements

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/reduceArrayElementsV1/>
- **文档摘要**：本页介绍 reduceArrayElementsV1 函数：使用表达式对数组元素进行归约（累积计算）。支持 Batch/Streaming，属 Array 类。参数为数组、每元素应用的归约表达式与初始累加值，输出类型 T；数组长度 0 时返回初始值。

### 272. Pipeline Builder Expressions • Regex extract

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/regexExtractV1/>
- **文档摘要**：本页介绍 regexExtractV1 函数：从正则匹配中提取指定分组，未找到匹配时返回空字符串。支持 Batch/Faster/Streaming，属 Regex/String 类。参数为表达式、分组序号（Literal<Integer>）与正则模式，输出 String；null 输入返回 null。

### 273. Pipeline Builder Expressions • Regex find

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/regexFindV1/>
- **文档摘要**：本页介绍 regexFindV1 函数：判断表达式是否匹配正则表达式（可匹配字符串任意部分）。支持 Batch/Faster/Streaming，属 Regex/String 类。参数为表达式与正则，输出 Boolean；null 模式或列在 ANSI 模式下传播为 null。

### 274. Pipeline Builder Expressions • Regex index

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/regexIndexV2/>
- **文档摘要**：本页介绍 regexIndexV2 函数：返回正则模式在表达式中匹配的索引数组（按 Unicode 码点计数）。支持 Batch/Faster/Streaming，属 Regex/String 类。参数为表达式与正则，输出 Array<Integer>；无匹配返回空数组，null 返回 null。

### 275. Pipeline Builder Expressions • Regex match

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/regexMatchV2/>
- **文档摘要**：本页介绍 regexMatchV2 函数：判断表达式是否整体匹配正则表达式（须匹配整个字符串）。支持 Batch/Faster/Streaming，属 Regex/String 类。参数为表达式与正则，输出 Boolean；null 处理依 ANSI 模式而定。

### 276. Pipeline Builder Expressions • Regex replace

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/regexReplaceV1/>
- **文档摘要**：本页介绍 regexReplaceV1 函数：使用正则模式替换字符串。支持 Batch/Faster/Streaming，属 Regex/String 类。参数为输入字符串、正则模式与替换字符串，输出 String；含转义与捕获组等示例。

### 277. Pipeline Builder Expressions • Remove map entry by key

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/removeMapEntryByKeyV1/>
- **文档摘要**：本页介绍 removeMapEntryByKeyV1 函数：按给定键移除映射中的条目。支持 Batch/Streaming，属 Map 类。参数为键 Key 与映射 Map（Expression<Map<K,V>>），输出 Map<K,V>；键不存在时原样返回，null 处理依 ANSI 模式。

### 278. Pipeline Builder Expressions • Rename struct field

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/renameStructFieldV1/>
- **文档摘要**：本页介绍 renameStructFieldV1 函数：重命名结构体（struct）内的字段。支持 Batch/Faster/Streaming，属 Data preparation/Struct 类。参数为结构体表达式与重命名列表（字段定位符到新名），输出 Struct；输入为 null 返回 null。

### 279. Pipeline Builder Expressions • Right of string

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/rightStringV1/>
- **文档摘要**：本页介绍 rightStringV1 函数：按指定长度从字符串右侧截取子串。支持 Batch/Faster/Streaming，属 String 类。参数为字符串表达式与长度（Expression<Integer>），输出 String；长度超过串长返回完整字符串，null 返回 null。

### 280. Pipeline Builder Expressions • Right pad string

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/rightPadV1/>
- **文档摘要**：本页介绍 rightPadV1 函数：用填充串将字符串右补齐到指定宽度，字符串过长则截断。支持 Batch/Faster/Streaming，属 String 类。参数为字符串、长度与填充串，输出 String；null 处理依 ANSI 模式。

### 281. Pipeline Builder Expressions • Round number

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/roundV1/>
- **文档摘要**：本页介绍 roundV1 函数：将数值四舍五入到指定小数位数（默认 0 位）。支持 Batch/Faster/Streaming，属 Numeric 类。参数为列（Decimal/Double/Float，可选）与小数位 Scale（Literal<Integer>），输出同列类型。

### 282. Pipeline Builder Expressions • Row count

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/rowCountV1/>
- **文档摘要**：本页介绍 rowCountV1 函数：统计分组内非 null 行的数量。支持 Batch/Faster/Streaming，属 Aggregate 类。可选参数为任意类型表达式（Expression<AnyType>），输出 Long；null 行不计数。

### 283. Pipeline Builder Expressions • Row number

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/rowNumberV1/>
- **文档摘要**：本页介绍 rowNumberV1 函数：在每个分区内返回从 1 开始的顺序行号。支持 Batch/Faster/Streaming，属 Aggregate 类。该函数无参数，输出 Integer。

### 284. Pipeline Builder Expressions • Sample covariance

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/sampleCovarianceV1/>
- **文档摘要**：本页介绍 sampleCovarianceV1 函数：计算两列数值的样本协方差。支持 Batch/Streaming，属 Aggregate 类。参数为两数值列 Left 与 Right，输出 Double；含 null 行仍参与配对计算示例。

### 285. Pipeline Builder Expressions • Sample variance

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/sampleVarianceV1/>
- **文档摘要**：本页介绍 sampleVarianceV1 函数：计算列中数值的样本方差。支持 Batch/Faster/Streaming，属 Aggregate 类。参数为数值表达式（Expression<Numeric>），输出 Double；含 null 行示例。

### 286. Pipeline Builder Expressions • Secant

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/secantV1/>
- **文档摘要**：本页介绍 secantV1 函数：计算角度的正割值（secant）。需指定角度单位（度或弧度枚举）与角度值（DefiniteNumeric 类型），返回 Double 类型；输入为 null 时返回 null，支持 Batch、Faster、Streaming。

### 287. Pipeline Builder Expressions • Sentence case

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/sentenceCaseV1/>
- **文档摘要**：本页介绍 sentenceCaseV1 函数：将文本中每个句子的首字母转为大写，其余保持不变。接收一个字符串表达式，返回 String 类型；空串返回空，null 输入返回 null，支持 Batch、Faster、Streaming。

### 288. Pipeline Builder Expressions • Sequence

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/sequenceV2/>
- **文档摘要**：本页介绍 sequenceV2 函数：生成从起始值到结束值（均含）的数字数组，可选步长。泛型 T 支持 Byte、Integer、Long、Short，返回 Array<T> 类型，支持 Batch、Faster、Streaming。

### 289. Pipeline Builder Expressions • Similarity score

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/similarityScoreV1/>
- **文档摘要**：本页介绍 similarityScoreV1 函数：计算两个 embedding 向量的相似度分数。接收左右两个 Array<Float> 向量与相似度度量（余弦距离/余弦相似度/点积/欧氏距离枚举），返回 Double 类型；仅支持 Batch。

### 290. Pipeline Builder Expressions • Simplify geometry

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/simplifyGeometryV1/>
- **文档摘要**：本页介绍 simplifyGeometryV1 函数：基于球面模型简化 GeoJSON 几何，移除容差距离内的点，过小环路可能被整体删除。参数含几何对象、容差（米）与可选坐标精度，返回 Geometry 类型，支持 Batch、Faster、Streaming。

### 291. Pipeline Builder Expressions • Sine

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/sineV1/>
- **文档摘要**：本页介绍 sineV1 函数：计算角度的正弦值（sine）。需指定角度单位（度或弧度枚举）与角度值（DefiniteNumeric），返回 Double 类型；输入为 null 时返回 null，支持 Batch、Faster、Streaming。

### 292. Pipeline Builder Expressions • Skip bytes

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/skipBytesV1/>
- **文档摘要**：本页介绍 skipBytesV1 函数：在二进制列中跳过指定数量的字节。接收一个二进制表达式和要跳过的字节数（整数），返回 Binary 类型；输入为 null 或跳过数超限时返回 null，支持 Batch、Faster、Streaming。

### 293. Pipeline Builder Expressions • Slice array

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/arraySliceV1/>
- **文档摘要**：本页介绍 arraySliceV1 函数：对数组进行切片，返回从起始位置起指定长度的元素。起始位置为 1 索引（负数为从末尾），长度超出则返回剩余；起始为 0 会报错，返回 Array<T>（T 为任意类型），支持 Batch、Faster、Streaming。

### 294. Pipeline Builder Expressions • Soundex

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/soundexV1/>
- **文档摘要**：本页介绍 soundexV1 函数：计算单词的 Soundex 语音编码（phonetic representation）。接收一个字符串表达式（建议为单词），返回 String 类型；null 与空串分别返回 null 与空串，仅支持 Batch、Faster。

### 295. Pipeline Builder Expressions • Split string

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/splitStringV2/>
- **文档摘要**：本页介绍 splitStringV2 函数：按指定的正则表达式模式拆分字符串。接收字符串表达式、正则模式与可选拆分元素上限（须大于 0），返回 Array<String> 类型，支持 Batch、Faster、Streaming。

### 296. Pipeline Builder Expressions • Square root

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/sqrtV1/>
- **文档摘要**：本页介绍 squareRootV1 函数：计算列的平方根。接收一个数值型表达式，返回 Double 类型；输入为 null 时返回 null，支持 Batch、Faster、Streaming。

### 297. Pipeline Builder Expressions • Standard deviation

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/standardDeviationV1/>
- **文档摘要**：本页介绍 standardDeviationV1 函数：计算列中数值的标准差（standard deviation）。接收一个数值型表达式，返回 Double 类型；null 值参与计算处理，支持 Batch、Faster。

### 298. Pipeline Builder Expressions • Starts with

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/startsWithV1/>
- **文档摘要**：本页介绍 startsWithV1 函数：判断字符串是否以指定值开头。接收字符串表达式、是否忽略大小写及比较值，返回 Boolean 类型；支持 ANSI 模式下的 null 处理，支持 Batch、Faster、Streaming。

### 299. Pipeline Builder Expressions • String after delimiter

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/stringAfterDelimiterV1/>
- **文档摘要**：本页介绍 stringAfterDelimiterV1 函数：提取第一个分隔符之后的字符串。接收正则分隔符、输入字符串与是否忽略大小写，未匹配则返回原串，返回 String 类型，支持 Batch、Faster、Streaming。

### 300. Pipeline Builder Expressions • String before delimiter

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/stringBeforeDelimiterV1/>
- **文档摘要**：本页介绍 stringBeforeDelimiterV1 函数：提取第一个分隔符之前的字符串。接收正则分隔符、输入字符串与是否忽略大小写，未匹配则返回原串，返回 String 类型，支持 Batch、Faster、Streaming。

### 301. Pipeline Builder Expressions • String contains

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/stringContainsV1/>
- **文档摘要**：本页介绍 stringContainsV1 函数：判断字符串是否包含指定值。接收字符串表达式、是否忽略大小写及比较值，返回 Boolean 类型；支持 ANSI 模式下的 null 处理，支持 Batch、Faster、Streaming。

### 302. Pipeline Builder Expressions • Substring

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/substringV1/>
- **文档摘要**：本页介绍 substringV1 函数：提取子字符串。接收字符串表达式、起始索引（1 索引，负数为从末尾）与可选长度，返回 String 类型；输入为 null 时返回 null，支持 Batch、Faster、Streaming。

### 303. Pipeline Builder Expressions • Subtract multiple expressions

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/subtractManyV1/>
- **文档摘要**：本页介绍 subtractMultipleExpressionsV1 函数：用一个数减去所有输入的列。接收表达式列表（被减数列）与被减数，返回 Numeric 类型，支持 Batch、Faster、Streaming。

### 304. Pipeline Builder Expressions • Subtract numbers

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/subtractV1/>
- **文档摘要**：本页介绍 subtractNumbersV1 函数：计算两数之差。接收左操作数与右操作数（均为数值型表达式），返回 Numeric 类型，支持 Batch、Faster、Streaming。

### 305. Pipeline Builder Expressions • Subtract value from date

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/dateSubV2/>
- **文档摘要**：本页介绍 subtractValueFromDateV1 函数：返回某个日期之前若干天/周/月/季度/年的日期。接收日期、单位枚举（天/月/季度/周/年）与数值，返回 Date 类型，支持 Batch、Faster、Streaming。

### 306. Pipeline Builder Expressions • Sum

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/sumV1/>
- **文档摘要**：本页介绍 sumV1 函数：对指定表达式求和。接收一个数值型表达式，返回 Decimal、Double 或 Long 类型；null 值被忽略，支持 Batch、Faster、Streaming。

### 307. Pipeline Builder Expressions • Sum of array elements

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/arraySumV1/>
- **文档摘要**：本页介绍 sumOfArrayElementsV1 函数：对数组内元素求和。接收数值数组与可选「null 视为零」开关，返回 T 类型（T 为 DefiniteNumeric）；含 null 且未开启开关时返回 null，支持 Batch、Faster、Streaming。

### 308. Pipeline Builder Expressions • Tangent

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/tangentV1/>
- **文档摘要**：本页介绍 tangentV1 函数：计算角度的正切值（tangent）。需指定角度单位（度或弧度枚举）与角度值（DefiniteNumeric），返回 Double 类型；输入为 null 时返回 null，支持 Batch、Faster、Streaming。

### 309. Pipeline Builder Expressions • Text segmentation

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/textSegmentationV1/>
- **文档摘要**：本页介绍 textSegmentationV1 函数：使用滑动窗口对文本进行分段。接收文本、分段词长与可选重叠词数，返回 Array<String> 类型；输入为 null 时返回 null，支持 Batch、Faster、Streaming。

### 310. Pipeline Builder Expressions • Text to embeddings

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/textToEmbeddingsV2/>
- **文档摘要**：本页介绍 textToEmbeddingsV1 函数：将文本转换为 embedding 向量。接收嵌入模型、文本列与可选输出模式（简单/带错误），返回 Embedded vector 类型，仅支持 Batch。

### 311. Pipeline Builder Expressions • Timestamp add

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/timestampAddV1/>
- **文档摘要**：本页介绍 timestampAddV1 函数：为时间戳按指定单位增加数值。接收时间戳、时间单位枚举（天/时/毫秒/分/月/季度/秒/周/年）与增量，返回 Timestamp 类型，支持 Batch、Faster、Streaming。

### 312. Pipeline Builder Expressions • Timestamp difference

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/timestampDiffV1/>
- **文档摘要**：本页介绍 timestampDifferenceV1 函数：返回两个时间戳在给定单位下的差值。接收结束与开始时间（Date 或 Timestamp）及时间单位枚举，返回 Long 类型；任一输入为 null 时返回 null，支持 Batch、Faster、Streaming。

### 313. Pipeline Builder Expressions • Timestamp sequence

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/timestampSequenceV1/>
- **文档摘要**：本页介绍 timestampSequenceV1 函数：生成从开始到结束（均含）的时间戳数组。接收起止时间、步长单位枚举与可选步长，返回 Array<Timestamp> 类型；输入为 null 时返回 null，仅支持 Batch、Faster。

### 314. Pipeline Builder Expressions • Timestamp subtract

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/timestampSubtractV1/>
- **文档摘要**：本页介绍 timestampSubtractV1 函数：为时间戳按指定单位减去数值。接收时间戳、时间单位枚举与减量，返回 Timestamp 类型，支持 Batch、Faster、Streaming。

### 315. Pipeline Builder Expressions • Timestamp to epoch millis

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/timestampToEpochMillisV1/>
- **文档摘要**：本页介绍 timestampToEpochMillisV1 函数：将 UTC 时间戳转换为纪元毫秒数。接收一个 Timestamp 表达式，返回 Long 类型；输入为 null 时返回 null，支持 Batch、Faster、Streaming。

### 316. Pipeline Builder Expressions • Timestamp to epoch seconds

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/timestampToEpochSecondsV1/>
- **文档摘要**：本页介绍 timestampToEpochSecondsV1 函数：将 UTC 时间戳转换为纪元秒数。接收一个 Timestamp 表达式，返回 Long 类型；输入为 null 时返回 null，支持 Batch、Faster、Streaming。

### 317. Pipeline Builder Expressions • Title case

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/titleCaseV1/>
- **文档摘要**：本页介绍 titleCaseV1 函数：将每个单词的首字母大写、其余小写。接收一个字符串表达式，返回 String 类型；输入为 null 时返回 null，支持 Batch、Faster、Streaming。

### 318. Pipeline Builder Expressions • Token set ratio

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/tokenSetRatioV1/>
- **文档摘要**：本页介绍 tokenSetRatioV1 函数：计算两个字符串的 token set ratio 相似度。接收是否忽略大小写及左右字符串，返回 0 到 1 之间数值（1 表示完全相同或包含）；输入为 null 时返回 null，仅支持 Batch、Streaming。

### 319. Pipeline Builder Expressions • Transcribe audio into JSON using CPU

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/cpuJsonAudioTranscriptionV1/>
- **文档摘要**：本页介绍 transcribeAudioIntoJsonUsingCpuV1 函数：使用 CPU 将音频文件转录为 JSON。接收媒体引用列与可选语言检测（未提供则由前 30 秒推断），返回 String 类型；输入为 null 时返回 null，仅支持 Batch。

### 320. Pipeline Builder Expressions • Transcribe audio into JSON using GPU

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/gpuJsonAudioTranscriptionV1/>
- **文档摘要**：本页介绍 transcribeAudioIntoJsonUsingGpuV1 函数：使用 GPU 将音频文件转录为 JSON。接收媒体引用列与可选语言检测（未提供则由前 30 秒推断），返回 String 类型；输入为 null 时返回 null，仅支持 Batch。

### 321. Pipeline Builder Expressions • Transcribe audio into text

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/audioTranscriptionV1/>
- **文档摘要**：本页介绍 transcribeAudioIntoTextV1 函数：将音频文件转录为文本。接收媒体引用列、可选语言检测与输出模式（简单/带错误信息），返回文本结果；输入为 null 时返回 null，支持 Batch、Faster。

### 322. Pipeline Builder Expressions • Transform array element

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/transformArrayElementV1/>
- **文档摘要**：本页介绍 transformArrayElementV1 函数：使用表达式对数组每个元素进行映射转换（索引从 1 开始）。接收输入数组与待应用表达式，返回 Array<T>（T 为任意类型），支持 Batch、Streaming。

### 323. Pipeline Builder Expressions • Transform map keys

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/transformMapKeysV1/>
- **文档摘要**：本页介绍 transformMapKeysV1 函数：对 map 的每个键值对应用表达式以转换键。接收待应用表达式与 Map 表达式，返回 Map<K, V> 类型，支持 Batch、Streaming。

### 324. Pipeline Builder Expressions • Transform map values

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/transformMapValuesV1/>
- **文档摘要**：本页介绍 transformMapValuesV1 函数：对 map 的每个键值对应用表达式以转换值。接收待应用表达式与 Map 表达式，返回 Map<K, V> 类型，仅支持 Batch。

### 325. Pipeline Builder Expressions • Trim whitespace

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/trimV1/>
- **文档摘要**：本页介绍 trimWhitespaceV1 函数：去除字符串首尾的空白字符（依 Unicode/Java 定义）。接收一个字符串表达式，返回 String 类型；输入为 null 时返回 null，支持 Batch、Faster、Streaming。

### 326. Pipeline Builder Expressions • Truncate date

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/dateTruncateV1/>
- **文档摘要**：本页介绍 truncateDateV1 函数：将日期向下取整到最近的天/周/月/季度/年。接收日期（Date 或 Timestamp）与单位枚举，返回 Date 类型；输入为 null 时返回 null，支持 Batch、Faster。

### 327. Pipeline Builder Expressions • Truncate timestamp

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/timestampTruncateV1/>
- **文档摘要**：本页介绍 truncateTimestampV1 函数：将时间戳截断到指定单位。接收时间戳与时间单位枚举（天/时/毫秒/分/月/季度/秒/周/年），返回 Timestamp 类型；输入为 null 时返回 null，支持 Batch、Faster。

### 328. Pipeline Builder Expressions • UUID V5

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/uuidV5V1/>
- **文档摘要**：本页介绍 uuidV5V1 函数：基于命名空间 UUID 与名称字符串，使用 SHA-1（RFC 4122）生成确定性 UUID v5。相同输入恒得相同 UUID；命名空间非法时返回 null，返回 String 类型，支持 Batch、Faster、Streaming。

### 329. Pipeline Builder Expressions • Uncompact a set of H3 indices

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/uncompactH3SetV1/>
- **文档摘要**：本页介绍 uncompactASetOfH3IndicesV1 函数：将压缩的 H3 索引集合展开到指定分辨率。接收 H3 索引数组与 0–15 的分辨率，返回升序排序的 Array<H3 Index>；索引非法或分辨率过低时返回 null，支持 Batch、Faster、Streaming。

### 330. Pipeline Builder Expressions • Unicode normalize

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/unicodeNormalizeV1/>
- **文档摘要**：本页介绍 unicodeNormalizeV1 函数：按 Unicode 标准附件 #15 执行 Unicode 规范化。接收一个字符串表达式与规范化形式枚举（NFC/NFD/NFKC/NFKD），返回 String 类型，支持 Batch、Faster、Streaming。

### 331. Pipeline Builder Expressions • Uniform random number

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/uniformRandomV1/>
- **文档摘要**：本页介绍 uniformRandomNumberV1 函数：返回 0 到 1 之间的均匀分布随机数列。可选种子不影响完全确定性（因分布式计算），返回 Numeric 类型；结果非确定性，支持 Batch、Faster、Streaming。

### 332. Pipeline Builder Expressions • Universally unique identifier (uuid) (unstable)

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/uuidV1/>
- **文档摘要**：本页介绍 UUID（unstable）函数：返回 UUID 列（V4）。该函数非确定性，不推荐用于构建 id 列，建议使用 SHA-256 或确定性的 UUID v5；无参数，返回 String 类型，支持 Batch、Faster、Streaming。

### 333. Pipeline Builder Expressions • Uppercase

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/uppercaseV1/>
- **文档摘要**：本页介绍 uppercaseV1 函数：将字符串中所有字符转为大写。接收一个字符串表达式，返回 String 类型；输入为 null 时返回 null，支持 Batch、Faster、Streaming。

### 334. Pipeline Builder Expressions • Url decode

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/urlDecodeV1/>
- **文档摘要**：本页介绍 urlDecodeV1 函数：将百分号编码的字符串解码为明文。接收一个字符串表达式，返回 String 类型；输入为 null 时返回 null，支持 Batch、Faster、Streaming。

### 335. Pipeline Builder Expressions • Url encode

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/urlEncodeV1/>
- **文档摘要**：本页介绍 urlEncodeV1 函数：对字符串进行百分号编码以便放入 URL。接收一个字符串表达式，返回 String 类型；输入为 null 时返回 null，支持 Batch、Faster、Streaming。

### 336. Pipeline Builder Expressions • Use LLM

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/useLlmV3/>
- **文档摘要**：本页介绍 useLlmV1 函数：以可配置提示调用大语言模型（LLM）。接收模型、用户提示（支持图片/PDF 等多媒体）与系统提示，返回 String 类型；整条提示为空或 null 时返回 null，仅支持 Batch。

### 337. Pipeline Builder Expressions • Value from map

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/valueFromMapV1/>
- **文档摘要**：本页介绍 valueFromMapV1 函数：使用键从 map 中获取值。接收键表达式与 Map 表达式，返回 V 类型（V 为任意类型）；键不存在时返回 null，支持 Batch、Faster、Streaming。

### 338. Pipeline Builder Expressions • Variance

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pb-functions-expression/varianceV1/>
- **文档摘要**：本页介绍 varianceV1 函数：计算列中数值的总体方差（population variance）。接收一个数值型表达式，返回 Double 类型；null 值参与计算处理，支持 Batch、Faster、Streaming。
