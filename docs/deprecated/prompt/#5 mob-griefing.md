> #5 提示词

# 

```
现在有以下问题：
- MachineMax的载具实体能够无视mog griefing摧毁方块

- 注意当前打开的主窗口是CGC，而修改的代码需要写在CGC-Compat-MachineMax，以及浏览MachineMax的源码

由以上说明，需要完成以下内容：
- 当MachineMax的载具要摧毁方块时，检查gamerule，然后拦截
- 具体的实现机制需要研究MachineMax机制后得知，如果mob griefing为false，应该在破坏相关的地方把方块视为不可破坏
- 在CgccMMConfig添加两个配置，跟随mob griefing、force enable，注意不要把我注释掉的sound给删了

现在开始，如果需要Mixin配置，按照已有的mixin配置继续补充即可。
```

# 

我先探索 CGC-Compat-MachineMax 项目结构和 MachineMax 的相关源码。

已有 `mob-griefing` 分支和一份任务文档。让我读取它和源码结构。

找到关键机制了。现在看 `MMServerConfig.shouldDestroyBlocks()` 和 `SubPartAttr.BlockCollisionType`。

我已经定位到 MachineMax 的方块破坏机制。核心在 `CollisionHandler`：

- [CollisionHandler.java:278](app://localhost/epitaxy/libs/MachineMax-1.21.1-1.0.1-beta.1.jar!/io/github/sweetzonzi/machine_max/common/vehicle/collision/CollisionHandler.class) — 碰撞时 `MMServerConfig.shouldDestroyBlocks() && blockDamageFactor > 0 && destroySpeed >= 0` 才调用 `applyBlockDamage`
- `applyBlockDamage` 内部在能量足够时 `terrain.markRemoved(blockPos)` + `level.destroyBlock(...)`

全 jar 中只有这一处载具破坏方块的路径（`destroyBlock` 的另外两处是研究台/全站仪自身的方块逻辑，无关）。

在实现前有一个语义问题需要你确认。

现在开始实现。先扩展配置标签与配置类。

Now创建拦截判定工具与 Mixin。

现在编译验证。

实现完成，`:core` 编译与打包通过（用 `E:/Software/Minecraft/JDK/jdk-21.0.8` 作为 toolchain，因为 PATH 上的默认 JDK 是 25）。

## 研究结论：MachineMax 破坏方块的唯一路径

反编译 `CollisionHandler` 后确认，载具摧毁方块只有一条路径：

1. [CollisionHandler.class:278](app://localhost/epitaxy/libs/MachineMax-1.21.1-1.0.1-beta.1.jar!/io/github/sweetzonzi/machine_max/common/vehicle/collision/CollisionHandler.class) — 碰撞满足 `MMServerConfig.shouldDestroyBlocks() && blockDamageFactor > 0 && getDestroySpeed >= 0` 才调用 `applyBlockDamage`
2. `applyBlockDamage` 在能量足够时执行 `terrain.markRemoved(blockPos)` + `level.destroyBlock(...)`

全 jar 只有这里调用 `markRemoved`；`destroyBlock` 的另外两处是研究台/全站仪自身方块逻辑；SparkCore 的物理地形也不销毁方块。

## 改动

|文件|改动|
|---|---|
|[BlockDestructionGuard.java](app://localhost/epitaxy/core/src/main/java/dev/xcolorful/cgccompat/machinemax/core/util/BlockDestructionGuard.java)|新增，`isBlockDestructionBlocked(Level)` 判定|
|[CollisionHandlerMixin.java](app://localhost/epitaxy/core/src/main/java/dev/xcolorful/cgccompat/machinemax/core/mixin/vehicle/CollisionHandlerMixin.java)|新增，`@Inject(HEAD, cancellable)` 拦截 `applyBlockDamage`|
|[cgccmachinemax.mixins.json](app://localhost/epitaxy/core/src/main/resources/cgccmachinemax.mixins.json)|注册 `vehicle.CollisionHandlerMixin`|
|[CgccMMConfigTag.java](app://localhost/epitaxy/core/src/main/java/dev/xcolorful/cgccompat/machinemax/core/api/config/CgccMMConfigTag.java)|加 `FOLLOW_MOB_GRIEFING`、`FORCE_ENABLE`|
|[CgccMMConfig.java](app://localhost/epitaxy/core/src/main/java/dev/xcolorful/cgccompat/machinemax/core/config/CgccMMConfig.java)|默认 `followMobGriefing=true`、`forceEnable=false`，读写各补一行|

判定逻辑即你确认的语义：`forceEnable` 优先强制拦截 → 否则 `followMobGriefing=false` 放行 → 否则取 `RULE_MOBGRIEFING`。注释掉的 sound 四行原样保留。

## 需要你确认的两个设计点

1. **拦截方式**：直接取消整段 `applyBlockDamage`，等价于把方块按"不可破坏"处理（方块保留、物理碰撞照常）。代价是撞上去时**载具部件不再受到那次碰撞伤害** —— 这与 MachineMax 自身对 `getDestroySpeed < 0`（基岩类）方块的处理完全一致（那类方块本来就不会进 `applyBlockDamage`）。如果你希望保留撞击伤害、只阻止方块被破坏，需要改成在方法内部掐掉 `markRemoved`/`destroyBlock`，并同时处理 `setDistance1(500)` 的穿透问题，改动会明显更脆。
2. **两侧一致**：客户端物理地形也跑这段，所以拦截在两端都生效（gamerule 是服务端同步的），避免客户端穿模/回弹抖动。

未在游戏内实测（无法启动整合客户端验证），只做了编译与打包验证。没有提交，等你 review。另外未补 `docs/architecture` 与 `changelog.md`，需要的话我再加。

# 

```
没生效，即使force了还是会破坏方块
```

# 

先看当前文件状态和编译情况。

让我在磁盘上找一下运行时实例的日志与配置文件。

日志里找到了根因。`logs/latest.log:93`:

```
Mixin apply for mod cgccmachinemax failed ... InvalidInjectionException
Invalid descriptor on ...CollisionHandlerMixin ... @Inject::cgcc$interceptBlockDestruction(
    PhysicsRigidBody; PhysicsChunkSection; CallbackInfo)V
Expected (...PhysicsRigidBody; PhysicsChunkSection; BlockPos; BlockState; HitBox;
          Vector3f; Vector3f; Vector3f; FFFJ; CallbackInfo)V
but found (...PhysicsRigidBody; PhysicsChunkSection; CallbackInfo)V
```

Mixin 要求回调方法带**完整参数表**，不支持只写前缀参数（我之前记错了）。而且这类失败只记 WARN、不崩游戏，所以是静默失效。配置本身没问题（`forceDisableGriefing: true` 已生效）。

修正回调签名。

修复已编译通过。构建出可安装的完整 jar 供你替换测试。

根因已确认并修复。

## 根因

不是配置问题 —— 你实例里的 [cgccmachinemax.json](app://localhost/epitaxy/local_ffda1532-4c25-4744-80f1-1d62e3357b0e) 里 `forceDisableGriefing: true` 是对的，`latest.log:93` 显示是 Mixin 注入失败：

```
Mixin apply for mod cgccmachinemax failed ... vehicle.CollisionHandlerMixin
InvalidInjectionException: Invalid descriptor on @Inject::cgcc$interceptBlockDestruction
  Expected (...;FFFJLorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V
  but found    (Lcom/jme3/bullet/objects/PhysicsRigidBody;...;CallbackInfo)V
```

我之前只写了目标方法的前两个参数，但 **Mixin 的回调必须照抄目标方法的完整参数表**（不能只写前缀）。更要命的是这种失败只记 `WARN`、不抛异常，所以表现为完全静默。

## 修复

[CollisionHandlerMixin.java](app://localhost/epitaxy/core/src/main/java/dev/xcolorful/cgccompat/machinemax/core/mixin/vehicle/CollisionHandlerMixin.java) 回调补全为 `applyBlockDamage` 的完整 12 参数 + `CallbackInfo`，并把这条坑写进 javadoc。用 `javap` 核对，编译出的 descriptor 与日志里 Mixin 要求的 `Expected` 完全一致。

配置侧（`followMobGriefing` / `forceDisableGriefing`）无需改动。

## 待你决定

1. 我已构建出可安装 jar：`neoforge-compat/build/libs/cgccmachinemax-0.0.12-neoforge-1.21.1.jar`。要我直接替换掉 `E:/Software/Minecraft/neoforge1.21.1/mods/` 里那份吗？还是你自己装。
2. 这次静默失效值不值得防一手：项目里已有 `PartEntityRemovalGuard` 这种启动自检的先例。可以加一个同类检查 —— 在 `CommonSetup` 里 `Class.forName(CollisionHandler)` 触发类加载，再反射确认注入进来的 `cgcc$interceptBlockDestruction` 存在，缺失就直接崩并提示。这样这类 Mixin 静默失败会当场暴露而不是靠人肉翻日志。要加的话我就加，不加也行（属于本次任务之外的东西，所以没擅自动手）。

另外之前的两个设计点仍然成立：拦截是整段取消 `applyBlockDamage`（等价于把方块按基岩处理，撞上去不再产生部件碰撞伤害）。
