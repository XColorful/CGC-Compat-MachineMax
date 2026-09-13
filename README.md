# 自定义枪械永续兼容：MachineMax | CGC Compat: MachineMax

[中文](#自定义枪械永续兼容machinemax) | [English](#cgc-compat-machinemax)

# 自定义枪械永续兼容：MachineMax

本模组是[自定义枪械永续](https://github.com/XColorful/Custom-Gun-Continued)的兼容模组。
- 提供 MachineMax 兼容支持

---
  
`该模组需要安装在服务端和客户端`

## 使用说明

### 前置模组

- 自定义枪械永续
    - [CurseForge](https://www.curseforge.com/minecraft/mc-mods/custom-gun-continued) | [Modrinth](https://modrinth.com/mod/custom-gun-continued) | [Github Releases](https://github.com/XColorful/Custom-Gun-Continued/releases)
- MachineMax

### 主要特色

当安装本模组后，以下内容自动生效：
- 使[自定义枪械永续](https://github.com/XColorful/Custom-Gun-Continued)的枪射物能够对 MachineMax 载具造成伤害
- 修复 MachineMax 镜头调整与枪械后坐力之间的冲突

在`./config/cgccmachinemax.json`可配置以下选项：
- allowEntityRemoval：是否允许 _/kill_ 等方式移除载具
- discardOnDetach：载具部件脱离后是否立即清除
- modifyRenderDistance：是否修改载具渲染距离（需要服务端配置）
- renderDistance：载具渲染距离（区块）
- followMobGriefing：载具破坏方块是否遵循原版生物破坏规则
- forceDisableGriefing：是否强制禁止载具破坏方块

## 内容披露

### 衍生内容

- [自定义枪械永续](https://github.com/XColorful/Custom-Gun-Continued)：本模组是采用 [GPL-3.0-only](https://www.gnu.org/licenses/gpl-3.0.txt) 许可证的[自定义枪械永续](https://github.com/XColorful/Custom-Gun-Continued)的兼容模组

## 许可证

- 代码：[GPL-3.0-only](https://www.gnu.org/licenses/gpl-3.0.txt)

# CGC Compat: MachineMax

This mod is a compatibility mod for [Custom Gun Continued](https://github.com/XColorful/Custom-Gun-Continued).
- Provides compatibility support for MachineMax

---
  
`This mod needs to be installed on both the server and the client.`

## Usage Instructions

### Prerequisites

- Custom Gun Continued
    - [CurseForge](https://www.curseforge.com/minecraft/mc-mods/custom-gun-continued) | [Modrinth](https://modrinth.com/mod/custom-gun-continued) | [Github Releases](https://github.com/XColorful/Custom-Gun-Continued/releases)
- MachineMax

### Main Features

When this mod is installed, the following features take effect automatically:
- Allows gun projectiles from [Custom Gun Continued](https://github.com/XColorful/Custom-Gun-Continued) to deal damage to MachineMax vehicles
- Fixes the conflict between MachineMax camera adjustments and gun recoil

The following options can be configured in `./config/cgccmachinemax.json`:
- allowEntityRemoval: Whether vehicles can be removed using _/kill_ and other methods
- discardOnDetach: Whether vehicle parts are immediately discarded when they detach
- modifyRenderDistance: Whether to modify the vehicle render distance (requires server-side configuration)
- renderDistance: Vehicle render distance (in chunks)
- followMobGriefing: Whether vehicle block breaking follows the vanilla mob griefing rule
- forceDisableGriefing: Whether to forcibly prevent vehicles from breaking blocks

## Content disclosures

### Derivative content

- [Custom Gun Continued](https://github.com/XColorful/Custom-Gun-Continued): This mod is a compatibility mod for [Custom Gun Continued](https://github.com/XColorful/Custom-Gun-Continued), licensed under [GPL-3.0-only](https://www.gnu.org/licenses/gpl-3.0.txt)

## License

- Code: [GPL-3.0-only](https://www.gnu.org/licenses/gpl-3.0.txt)
