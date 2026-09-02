# Profile 分区配置长按编辑 — 实施计划(已定稿)

> 状态:**已评审,决策全部闭合,待实施**
> 日期:2026-09-02
> 范围:`ProfileScreen` 中「输入映像 / Input images」分组内的镜像 `PreferenceRow`

## 1. 目标

为 `ProfileScreen` 里管理镜像(分区)信息的每一行 `PreferenceRow` 增加长按回调,
长按后打开对应分区(partition)的配置编辑对话框,编辑该分区在
`profile.json`(v3 `PartitionConfig`)中的签名参数并写回。

## 2. 已拍板的决策

| # | 决策 |
|---|---|
| D-编辑范围 | 分区名(JSON key)只读;`descriptor` 只读;`image` 开放编辑但弹窗警告影响 |
| D-不变量 | Dialog 始终携带 `parseProfile` 产出的**完整 `ProfilePartitionSpec` 拷贝**,只原地修改展示字段,编码器整体写出(防止只提交部分字段抹掉其余 ~30 个字段) |
| D-字段表 | 不复用 `AvbModels.kt` 的 `AvbArg` 表(按命令组织、`--flag` 键、FILE/IMAGE 语义不对应)。新写「spec 字段 × descriptor 适用性 × 控件类型」声明表;`ArgType → 控件` 渲染函数与 `ALGORITHMS`/`HASH_ALGORITHMS`/`FLAGS_OPTIONS` 选项表从 `CommandScreen.kt` 提取共享 |
| D-未设置语义 | 数字字段留空 = 不写对应配置项;带默认值的字段留空写回 = 写默认值(可接受,语义保真而非字节保真);`hash_algorithm` 对 footer 始终写出(`buildAvbArgs` 恒传,防裸 avbtool 对 hashtree 回退 sha1) |
| D-校验 | 清单 V1–V8(硬错误)/ D1–D4(域约定),见 §6;hashtree **豁免** size 互斥(`partition_size=0` = 追加到末尾,合法) |
| D-稀疏编码 | 与解析默认值相等的字段一律省略;例外:`rollback_index` / `flags` / `rollback_index_location` 非 null 即写(含 0,保住 `has()` 语义),`partition_name` / `image` 恒写(generator 对每个分区都输出,round-trip 不能丢)。`hash_algorithm` 仅在 ≠ sha256 时写出——`buildAvbArgs` 本就以 parse 默认值 sha256 兜底,恒写反而会给没有该字段的 profile 凭空加字段(round-trip 测试验证了这一点) |
| D-footgun | `calc_max_image_size` / `print_required_libavb_version` **不**在编辑弹窗暴露(会让签名"打印后退出"无产出);全量拷贝方案下未展示字段天然保真 |
| D-重构 | 允许重构:把分区级 parse + encode + validate 抽到纯 Kotlin `ProfilePartitionCodec.kt`,ViewModel 委托调用(解除 Context 依赖,单测可达) |
| D-fixture | 从根目录 `tb710fu-495-no-ubl.zip` 只提取 `profile.json` 作为 test resource(不提交 keys/*.pem;根目录 zip 随后删除) |
| D-测试比较 | round-trip 断言用结构比较。**注意:unit-test 编译 classpath 上 android.jar(org.json 空壳 stub,无 `similar()`)排在 Maven json 之前**,`JSONObject.similar()` 在测试编译期不可解析,因此用手写 `jsonEquals()` 结构比较(实现见 ProfilePartitionCodecTest) |
| D-传输 | 保存结果用 **UiState 事件**(`savingPartition` busy + `partitionSaveEvent: PartitionSaveEvent?` + consume),与 `AddPartitionEvent` / `KeyImportEvent` 同构;不用回调(旋转屏会吞回调)。失败:Dialog 不关、内联错误;若因旋转弹窗已关,回退 `uiState.message` toast |
| D-次要 | `props` 编码只写规范 `[[k,v],...]`(解析兼容三种历史形态,见 parsePairs);`chain_partitions` 第三段原样透传不拆分重组(`resolveChainEntry` 允许 key 文件名含 `:`);长按无视觉提示;损坏 profile 不在管理范围(交给导入不校验 + 用户文本编辑器) |

## 3. 现状事实(已核对)

- `PreferenceRow` 原生支持 `onLongClick`(`PreferenceComponents.kt:153,219`);
  `KeyRow` 长按先例 `ProfileScreen.kt:386`。
- `ProfilePartitionSpec`(`ProfileViewModel.kt:31-80`)1:1 覆盖 CONFIG_EXPANSION §3.2 全部 v3 字段。
- `parseProfile` 不对称语义(`1574-1622`):`rollback_index`/`flags`/`rollback_index_location`
  用 `obj.has()`(显式 0 ≠ 缺失);`partition_size`/`padding_size` 用 `> 0`;`salt` 空串视为未设置;
  `hash_algorithm` 缺省补 sha256、`block_size` 补 4096、`fec_num_roots` 补 2。
- `image` 双重身份:footer 分区是 scratch 副本文件名(`1240`/`1420`);`resolveImageFile`
  按 `it.image` 文件名字符串匹配(`1517`),改名会静默断开其他分区
  `include_descriptors_from_image` 引用;vbmeta 的 image 只是输出名。
- `key_id` 解析失败(不在 manifest)时 `buildAvbArgs` **静默省略** `--key`(`1476-1479`)。
- `ProfileStore.updateProfileJson` 在 `partitions` 键缺失时 transform 空转仍返回 true。
- 命令行拼装与字段语义的唯一事实源是 `buildAvbArgs`(`1365-1496`),编码/校验必须与其对齐。

## 4. 字段表(按 descriptor 分流)

基础字段(默认展开):

| 字段 | hash | hashtree | vbmeta | 控件 | 备注 |
|---|---|---|---|---|---|
| `image` | ✓ | ✓ | ✓(输出名) | 文本 + 改名警告 | footer: scratch 文件名,改名断开 include 引用 |
| `partition_name` | ✓ | ✓ | 隐藏(无此 flag) | 文本 | 描述符内名字,非 JSON key |
| `algorithm` | ✓ | ✓ | ✓ | 下拉(共享 ALGORITHMS) | NONE ⇒ key 可空 |
| `key_id` | ✓ | ✓ | ✓ | 下拉(`uiState.keys`)+ "(无)" | 见 V5 |
| `partition_size` | ✓ | ✓(留空=追加) | 隐藏 | 数字留空 | hash 见 V1;hashtree >0 见 V2 |
| `dynamic_partition_size` | ✓ | 隐藏 | 隐藏 | Switch | hash 专属 |
| `rollback_index` | ✓ | ✓ | ✓ | 数字留空 | 非 null 即写(含 0) |
| `hash_algorithm` | ✓ | ✓ | 隐藏 | 下拉(共享 HASH_ALGORITHMS) | 恒写 |
| `salt` | ✓ | ✓ | 隐藏 | 文本 | 留空=随机;非空见 V6 |
| `flags` | ✓ | ✓ | ✓ | 下拉(共享 FLAGS_OPTIONS 0–3) | 非 0–3 显示原始值;非 null 即写 |

进阶折叠字段(Advanced):

| 字段 | 适用 descriptor | 控件 |
|---|---|---|
| `rollback_index_location` | 三类 | 数字留空(非 null 即写) |
| `props` / `prop_from_file` | 三类 | 逐行 KEY:VALUE 文本(按第一个冒号拆,V7) |
| `set_hashtree_disabled_flag` / `set_verification_disabled_flag` | 三类 | Switch |
| `block_size` / `do_not_generate_fec` / `fec_num_roots`(1–254,V8) | hashtree | 数字 / Switch |
| `no_hashtree` / `check_at_most_once` / `setup_as_rootfs_from_kernel` | hashtree | Switch |
| `included_partitions`(阶段 4) | vbmeta | 下拉多选(profile 分区名) |
| `chain_partitions` / `chain_partitions_do_not_use_ab` | 三类 | 复用 CommandScreen chain editor 模式 |
| `include_descriptors_from_image` | 三类 | 文本列表 |
| `kernel_cmdlines` | 三类 | 文本列表 |
| `padding_size` | vbmeta | 数字留空 |
| `output_vbmeta_image` / `signing_helper` / `signing_helper_with_files` / `public_key_metadata` / `append_to_release_string` / `setup_rootfs_from_kernel` | 三类 | 文本 |

**不暴露**(D-footgun + 语义不符):`calc_max_image_size`、`print_required_libavb_version`、
`use_persistent_digest`/`do_not_use_ab`(伴随 D3 依赖,阶段 4 再议)、
`dynamic_partition_size` 对非 hash。

## 5. 落地顺序

| 阶段 | 内容 |
|---|---|
| 1 | `ProfilePartitionCodec.kt`(encode + validate 纯 Kotlin,parse 后续迁入)+ `ProfileScreen` 长按 + `PartitionEditDialog`(全字段表 + 进阶折叠)+ ViewModel `updatePartition`(UiState 事件)+ i18n |
| 2 | fixture 提取 + round-trip/校验单测(org.json:json,`similar()` 比较) |
| 3 | 阶段 4 字段(included_partitions 下拉、chain editor、列表字段)|

## 6. 校验清单

硬错误级(avbtool 会抛,保存前拦截):

| # | 规则 | 证据(avbtool.py) |
|---|---|---|
| V1 | hash:`partition_size`/`dynamic_partition_size`/`calc_max_image_size` 至少其一(calc 已不暴露 ⇒ size 或 dynamic);dynamic 与 calc 互斥;calc 需 size ≥ 68KiB | 3373/3376/3396 |
| V2 | `partition_size` 为 4096 倍数(hash:3434;hashtree:3649,size>0 时) | |
| V3 | `chain_partition` 恰 3 段 `PART:SLOT:KEY`,SLOT 整数 ≥ 1 | 2471/3026/3040 |
| V4 | **单分区内** chain ∪ chain_do_not_use_ab 的 SLOT 不重复,且 ≠ 该分区 `rollback_index_location`(`used_locations` 种子) | 3015-3035 |
| V5 | `algorithm ≠ NONE` ⇒ `key_id` 必填(且必须在 keys/manifest.json 中——下拉天然保证;密钥库空 + 算法≠NONE 拒绝保存) | 3132 |
| V6 | `salt` 非空须为偶长合法十六进制 | 3450/3699 |
| V7 | `props`/`prop_from_file` 每项含 `:`(按第一个冒号拆,value 可含冒号) | 3053-3060 |
| V8 | `fec_num_roots` ∈ 1..254(`FEC_RSM=255`,`roots<=0‖roots>=255` 拒绝;CONFIG_EXPANSION §3.5 的 2..255 有误) | avb_fec.cpp:33,153,160 |

域约定级:

| # | 规则 |
|---|---|
| D1 | `rollback_index` ≥ 0 |
| D2 | `block_size` 为 2 的幂且 ≥ 512(avbtool 无硬检查,dm-verity 要求) |
| D3 | `use_persistent_digest` ⇒ `do_not_use_ab`(两开关均不暴露,阶段 4 再议) |
| D4 | `included_partitions` 引用须存在(不存在时 `buildAvbArgs` 静默跳过,加警告) |

## 7. 写回流程

```
Screen: 长按 → editingPartition = spec.partition(完整 spec 存 remember)
Dialog: 全量拷贝编辑;保存 → viewModel.updatePartition(editedSpec)
VM:     viewModelScope.launch {
          savingPartition = true
          event = withContext(IO) {
            store.updateProfileJson(activeId) { obj ->
              val partitions = obj.optJSONObject("partitions")
                ?: throw IllegalStateException(...)   // 判空,防空转 true
              if (!partitions.has(spec.partition)) throw ...   // 防复活
              partitions.put(spec.partition, ProfilePartitionCodec.encode(spec))
            }.let { ... }
          }
          savingPartition = false; partitionSaveEvent = event
        }
Screen: LaunchedEffect(partitionSaveEvent) { Success → consume + 关弹窗;
        Failed → consume + 内联错误(弹窗已关则 toast) }
```

## 8. 测试

- test 依赖 `org.json:json`(testImplementation);fixture:从根目录 zip 提取
  `profile.json` → `app/src/test/resources/`;round-trip 断言用 `JSONObject.similar()`。
- 覆盖:parse→encode→parse 幂等;稀疏写出细则(rollback_index=0 写出、partition_size=0 省略);
  V1–V8 / D1–D2 合法与非法用例;`tb710fu` fixture 导入→编辑→导出→再导入比对。
- 手动验收:编辑后签名跑通(in-place fd 与 scratch 两条路径);动态色开/关两态外观。
