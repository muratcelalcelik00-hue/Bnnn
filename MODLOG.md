# MODLOG — Polyfield → Minecraft Fabric 1.20.1

## Input
- `com.MA.Polyfield_0.7.5.7z.001..008` → `com.MA.Polyfield_0.7.5.apks` (base.apk + split_config.arm64_v8a.apk).
- Unity 2022.3.62f3, IL2CPP (metadata v31), Android. Data: `assets/bin/Data/data.unity3d` (bundle with
  globalgamemanagers, sharedassets0-3, level0-3, resources.assets) + external `*.resource` (audio).
- Online-capable game: only its files are read, offline. Nothing is launched, patched or hooked.

## Route
Mashup pattern 1 (port the content) with a converter run on the user's own copy. Host = Fabric 1.20.1
mod; guest assets → private resource dir packed into the user's personal jar, never committed.

## What the game data holds
- Weapons: `<Name>_Prop` (world/third-person model, flat material colours, no textures) and
  `<Name>_Prefab` (first person + `Weapon` MonoBehaviour). Props are ~1.65x real size → scale 0.6.
- `Weapon` fields (metadata order): weaponClass, allowedClass(string), model, fireMode, fireRate(rounds/s),
  bulletSpeed, range, damage, magnification, aimFOV, aimZoffset, boltAction, [needBoltAction not serialized],
  bulletShellDelay, ..., forces(Vector3 recoil), isReloading, isBoltAction, fireCoolDown, MAG_SIZE, CLIP_SIZE,
  ammo(magazine), mag(reserve), hasSoundLoop, pitchOffset, reloadSound, weaponSoundSub, Close, Tail, Distant,
  boltActionSound. Tail decoded by anchoring on the run of AudioClip PPtrs.
- Launchers use class `Gadget` (fireRate, bulletSpeed, range, damage, …, ammo 1, mag 3/6).
- Soldiers: `PolySoldier_GER`, `PolySoldier_USA` — Mixamo rig (22 bones + Armature), 3 SkinnedMeshRenderers
  (body, helmet, backpack) with **different bind poses per renderer** (bindpose includes renderer transform).
  Helmet uses a 1-component bone-index channel and no weight channel → weight 1.
- Animation clips are humanoid muscle clips (binding typeID 95) → not evaluated; procedural pose + IK instead.
- Tanks: `Tank_ShermanFirefly`, `Tank_Jadgpanther`: `_Turret` (yaw), `_Cannon` (pitch), `_Cannon_Explosion`
  (muzzle), `_Seats_*`; tracks are skinned meshes, baked in prefab pose.
- Higgins boat in resources.assets (ramp at -X; rotated to +Z, scaled 0.75).
- Audio: all FSB5 Vorbis (203 stereo, 9 mono) → fsb5.rebuild_sample → ffmpeg mono OGG.

## Gotchas
1. `fsb5` 1.0 sdist fails to build on modern setuptools (`install_layout`); copy the pure-Python package.
2. UnityPy `TypeTreeGeneratorAPI` failed on this libil2cpp ("Sequence contains no matching element");
   metadata parsed by hand instead (typedef size 88 bytes for v31).
3. Skinned parts must use their own renderer's bind poses, or helmet/backpack land 1.2 m off.
4. Network policy of the build container blocked maven.fabricmc.net and Mojang hosts (403).

## Verification so far
- Converter: 26 models, 78 sound events; preview sheet rendered and inspected; real-world sizes checked
  (Kar98k 1.11 m, MP40 0.86 m, P38 0.20 m, MG42 1.13 m).
- Soldier IK pose prototyped in Python (hold, aim up/down, walk) and inspected.
- Java code checked against Yarn 1.20.1 mappings (class names, member names, descriptors).
