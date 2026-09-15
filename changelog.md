### 0.0.x

#### 0.0.12.3

Update `./config/cgccmachinemax.json`:
- enableSoundRefresh: Whether to enable sound volume self-healing (automatic cleanup when vehicle sounds exhaust the channel pool and mute everything)
- soundRefreshIntervalTicks: Interval of the sound volume self-healing (in ticks, 1 means every tick)

#### 0.0.12.2
- Fix crash caused by vehicle removal
- Fix vehicle seat occupancy check
- Fix vehicle sounds playing beyond their range

Update `./config/cgccmachinemax.json`:
- Remove `followMobGriefing`, `forceDisableGriefing`
- modifyVehicleSoundDistance: Whether to modify the vehicle sound distance (requires server-side configuration)
- vehicleSoundDistance: Vehicle sound distance (in chunks)

#### 0.0.12.1
Add `./config/cgccmachinemax.json`:
- allowEntityRemoval: Whether vehicles can be removed using /kill and other methods
- discardOnDetach: Whether vehicle parts are immediately discarded when they detach
- modifyRenderDistance: Whether to modify the vehicle render distance (requires server-side configuration)
- renderDistance: Vehicle render distance (in chunks)
- followMobGriefing: Whether vehicle block breaking follows the vanilla mob griefing rule
- forceDisableGriefing: Whether to forcibly prevent vehicles from breaking blocks

#### 0.0.12
- Allows gun projectiles from Custom Gun Continued to deal damage to MachineMax vehicles
- Fixes the conflict between MachineMax camera adjustments and gun recoil