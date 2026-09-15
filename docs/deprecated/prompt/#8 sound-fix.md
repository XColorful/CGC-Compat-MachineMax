> #8 提示词

# 

```
现在要修复一个问题，当载具很多的时候可能MC的声音会直接静音，但是只要点击声音设置随便改下音量就能恢复
用这个手段，在sound包下Tmp类（改个更合适的名字），监听client tick前后（防止tick时触发静音而漏掉）读取任意音量值并设置
应该了解源码里当客户端打开声音设置时是否触发了一些自动调整/刷新音量的计算，而不只是直接设置音量值

- 注意当前打开的主窗口是CGC，而修改需要写在CGC-Compat
```

# 

I'll start by reading the relevant memory and exploring the compat repo structure.

There's in-progress work already. Let me read it.

Prior session scaffolded `Tmp` with a TODO. Now I need the real Minecraft call chain for sound-settings volume changes. Let me inspect the decompiled sources via IDEA MCP.

`updateCategoryVolume` is the key path. Let me read `ChannelAccess` and `SoundEngine.play` to understand the mute mechanism.

Let me read the compat sound mixins and client setup to understand how MachineMax vehicle sounds are handled here.

MachineMax vehicle sounds go through SparkCore's `SpreadingSoundInstance`, and I saw SparkCore ships its own `SoundEngineMixin`/`SoundManagerMixin`. Let me inspect those.

SparkCore intercepts vehicle sound volume/pitch. Now let me get the full picture of what the sound settings screen triggers — I'll extract the MC sources to a scratch dir to grep the call sites.

Confirmed the slider path: `updateSourceVolume` → `updateCategoryVolume`. Now let me check the OpenAL layer (`Library`/`Channel`/`Listener`) for a global-mute mechanism.

Let me check every place the client re-applies sound volumes, especially outside the options screen.

Let me read SparkCore's `SpreadingSoundInstance` — that's where vehicle sound volume actually originates.

Let me look for existing evidence/notes about this muting in both repos and the MachineMax fork.

Let me verify `SoundEngine.calculateVolume` and check CGC's own `SoundManagerMixin` for any global volume manipulation.

Let me check whether anything in the stack can zero the OpenAL listener gain or channel volumes directly.

I have a solid diagnosis. Let me check the CGC-side event API and the sibling handler's conventions before writing code.

Let me check which `SoundSource` MachineMax registers its vehicle sounds under, so the choice of category is meaningful.

Vehicle machinery sounds use `SoundSource.NEUTRAL`. Let me check the `IEventHandler` contract before writing.

Now let me check the registration mechanics, then implement.

Diagnosis is complete. Now let me implement the fix.

Now update the registration to point at the new class.

Now let me compile to verify.

Let me verify one claim before reporting: whether the option callback only fires on an actual value change.

已实现并编译通过。下面是我从源码查到的结论和改动内容。

## 1. 「拨音量滑块」到底触发了什么（你让我确认的点）

`SoundOptionsScreen#addOptions` 只是把 `Options` 里现成的 `OptionInstance` 塞进列表，屏幕本身不做任何音量相关的计算。全部效果都在滑块的 `OptionInstance` 回调里：

- [Options.java:990](../../CGC-Compat-MachineMax/core/.gradle/repositories/ng_dummy_ng/net/neoforged/neoforge/21.1.219/neoforge-21.1.219-sources.jar!/net/minecraft/client/Options.java) `createSoundSliderOptionInstance` → `SoundManager.updateSourceVolume(source, value)` → `SoundEngine.updateCategoryVolume(category, volume)`：
    - `MASTER`：只做 `listener.setGain(volume)`（OpenAL 的 `AL_GAIN`）。
    - 其它类别：**遍历 `instanceToChannel` 里每一个声道**，用 `calculateVolume(sound)` 重算一次，算出来 `<= 0` 的就 `channel.stop()`，否则 `setVolume(f)`。
- `OptionInstance.set` 里有 `!Objects.equals(this.value, t)` 的判断——**回调只在值真的变了才触发**。所以打开声音设置**没有**任何额外的「自动调整/刷新」逻辑，纯粹就是上面那一次调用。

## 2. 原因推断（未在游戏内复现）

- 载具音效走 SparkCore 的 `SpreadingSoundInstance`，一个实例占一个 OpenAL 声道，而 `ClientSpreadingSoundPlayer` 不做并发限制；`Library` 按设备报的源数分配池，设备只报 30 时静态池只有 `clamp(30-5,8,255)=25` 个。
- `SoundEngine.tickNonPaused` 只在**类别**音量为 0 时才停声道，不会清理「距离远到听不见」的载具音效 → 声道被一直占住 → 新音效在 `SoundEngine.play` 里拿不到声道就被静默丢弃（池满时那句日志还只在 IDE 里打）。
- 所以拨任意**非 MASTER** 滑块能恢复：那次遍历把距离远、算出来是 0 的载具声道 `stop()` 掉，下一帧 `ChannelAccess.scheduleTick` 释放它们，池子重新有空位。
- 我扫了 MachineMax / SparkCore / CGC 三个 jar 的常量池，**没有任何地方**调用 `updateSourceVolume` / `updateCategoryVolume` / `setGain`，所以不是这几个模组显式写了音量。

## 3. 改动

- 新增 [SoundVolumeRefresher.java](core/src/main/java/dev/xcolorful/cgccompat/machinemax/client/sound/SoundVolumeRefresher.java)（原 `Tmp` 已 `git rm`）。每 tick 读一次音量并重新下发：`MASTER` 走监听器增益（音量为 0 时跳过，避免每 tick 触发 `updateSourceVolume` 里「停掉全部音效」的分支），再加一次非 MASTER 类别（取载具子系统用的 `NEUTRAL`）触发整池声道的重算与静音声道回收。
- [ClientEventHandlers.java:16](core/src/main/java/dev/xcolorful/cgccompat/machinemax/client/event/custom/ClientEventHandlers.java:16) 沿用之前的注册：`PREPARE_CLIENT_TICK_EVENT`(LOWEST，tick 体之前最后一刻) 与 `CLIENT_TICK_EVENT`(HIGHEST，tick 体之后第一刻)。已确认这两个就是 NeoForge 的 `ClientTickEvent.Pre/Post`，且 `Minecraft.tick()` 里的 `soundManager.tick()` 在 Post 之前，所以 tick 中途造成的静音能被 Post 那一次补上。

`:core:compileJava :neoforge-compat:compileJava` 通过（BUILD SUCCESSFUL）。

## 4. 需要你确认的

- **没有在游戏里验证过**，也没法在这里复现「载具很多」。第 2 节是推断，不是实测结论。如果实测发现只有 MASTER 滑块才管用，那说明机制在监听器增益上，`SoundVolumeRefresher` 里那一段已经包含；反之如果非 MASTER 才管用，就是整池重算那段。
- 每 tick 两次全池重算 = `2 × 声道数` 个任务丢给声音线程。声道数上百时这个常态开销如果你觉得在意，可以改成只在池接近满时刷（要访问 `SoundEngine.library`，得加个 accessor mixin）。
- 兼容性报备：新引入的原版 API 是 `Minecraft#options`、`Options#getSoundSourceVolume`、`SoundManager#updateSourceVolume`、`SoundSource`，都在原版客户端内、无项目包装器可用。

# 

```
看起来是修复了，至少目前没碰到静音

你看这个修复是否可以改成原本config里注释掉的sound并发限制，或者如果拨滑块只是清理的作用的话，改成每20tick拨一次应该也能起到清理的作用？
```

# 

Let me look at the commented-out config you're referring to.

看了下，那两组配置项确实还在（`concurrentSoundLimitEnabled` / `maxConcurrentSounds` 默认 8，tag 也在，只有 read/write 两行被注释，文档写着"当前未实现"）。两个问题分开答。

## 每 20 tick 拨一次：机制上成立

清理这个动作是**排空式**的，不是持续维持：被 sweep 停掉的实例在 SparkCore 那边 `isPlaying` 已经是 `true` 且没有置回 `false` 的路径（我核过 `SpreadingSoundInstance` 反编译和 MachineMax 源码，两边都不写这个字段），所以它不会重播、不会再来占声道。既然是一次性排空，就不需要每 tick 都做。

但代价有两块，得权衡：

- **静音窗口从"几乎为 0"变成"最多 1 秒"**：池子被占满后，下一次边界之前新音效一律拿不到声道。而且这期间被丢掉的音效不会补播（`createHandle` 返回 null 就是丢了）。
- **性能上其实省不了什么**：一次 sweep 就是遍历活动声道、每个往声音线程投一个空任务，几百个声道也就几百微秒量级（我的估算，没实测；一个 tick 有 50ms）。所以我认为 20 tick 节流**不是**为了性能。

如果还是要改，最小改法是把两次注册并成一次（节流之后"tick 前后各一次"就没意义了），加个 tick 计数器。我倾向保持现状。

## 照原样实现那个并发上限：不建议

- 它是**硬上限**（默认 8），会直接掐掉听得见的近处载具音效。现在的 refresh 是「只停算出来是 0 的」，不损失可听内容——上限反而更影响体验。
- **没有可靠的计数来源**：`ClientSpreadingSoundPlayer.fadeSound(uuid)` 会立刻把实例从 `activeInstances` 里移除，但它还要淡出 `fadeOutTicks` 才真正停，按那个 map 计数会少算，很容易卡成「以为没满其实已满」。
- 得 mixin 进 SparkCore 内部，而 `libs/` 那份 1.0.1023 只有 class（且按之前的记录比 MachineMax fork 还旧），维护成本明显高于现在这个 tick 钩子。

顺带一个根因方向：真正的声道压力来自我们自己那个 `vehicleSoundDistance`——它把载具音效的 `maxRange` 拉到 20 区块（320 格），音源点活得久、实例就活得久、占的声道也久。refresh 的价值正好是「不缩短可听距离」的前提下解决占满，所以两者不冲突。

## 需要你定

那两组配置项现在是死配置。要处理的话有两条路，都算改配置意图，我不擅自动：

1. 直接删掉（连 tag 一起）。
2. 改造成 refresh 自己的开关/间隔（比如 `soundRefreshIntervalTicks`），让「要不要节流、节流到多少」由配置决定——这样你两种选择都保留。

你要哪个？或者维持每 tick、配置项先不动也行。

# 

```
那把这两个配置项改成一个bool和int，一个做开关，一个用来表示清理频率，并且去掉一个client tick监听，把原来注释的tag给换掉，并且README.md里也要补充这两个配置
默认启用，默认频率为1（每tick执行）
```

# 

Implementing: config rename to switch + interval, single tick listener, README entries.

Now the config class:

Now the read/write lines that were commented out:

Now update the handler to a single boundary with the configurable interval.

Now the registration and README:

改完了，编译通过（BUILD SUCCESSFUL）。注意你已经把上一版提交成了 `7bc2323`，所以现在这些是它之上的改动。

## 改动

- [CgccMMConfigTag.java:7](core/src/main/java/dev/xcolorful/cgccompat/machinemax/core/api/config/CgccMMConfigTag.java:7)：`concurrentSoundLimitEnabled`/`maxConcurrentSounds` → `soundRefreshEnabled`/`soundRefreshIntervalTicks`（Java 常量名一起换了）。
- [CgccMMConfig.java](core/src/main/java/dev/xcolorful/cgccompat/machinemax/core/config/CgccMMConfig.java)：默认值 `true` / `1`，新增 `MIN_SOUND_REFRESH_INTERVAL_TICKS = 1`，两个字段的文档重写（不再是"当前未实现"），`_read`/`_write` 里被注释的两行恢复并改名。
- [SoundVolumeRefresher.java](core/src/main/java/dev/xcolorful/cgccompat/machinemax/client/sound/SoundVolumeRefresher.java)：加 `tickCounter`，`soundRefreshEnabled` 关掉就直接返回，`++tickCounter < soundRefreshIntervalTicks` 才执行（所以 1 = 每 tick，和之前行为一致）。
- [ClientEventHandlers.java:17](core/src/main/java/dev/xcolorful/cgccompat/machinemax/client/event/custom/ClientEventHandlers.java:17)：只留 `PREPARE_CLIENT_TICK_EVENT`，去掉 `CLIENT_TICK_EVENT`。
- [README.md](README.md)：中英两节都补了两条。

**保留的是 Pre 而不是 Post**：Pre 时上一 tick 的声音引擎已经跑完，而本 tick 的 `SoundEngine#tick`（里面才做 `ChannelAccess#scheduleTick` 的声道释放）还没跑，所以被停掉的声道在同一 tick 内就能收回；Post 则要等下一 tick。另外节流之后"前后各一次"本来也没意义了。

顺带确认了一个迁移点：`_read` 里未知键走 `skipValue`、`_write` 是按已知键整份重写，所以老配置文件（哪怕是之前手写进去的旧 key）都只会被忽略并清掉，不需要额外处理。

没在游戏里实测——逻辑和你已验证的那版只差「去掉一次冗余调用 + 加了默认等于 1 的间隔门」。
