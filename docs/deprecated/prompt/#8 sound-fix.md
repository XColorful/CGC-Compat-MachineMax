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
