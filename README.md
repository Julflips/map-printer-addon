
# Nerv Printer Addon

Nerv Printer is an addon for the meteor client allowing you to build flat mapart from a batch of NBT files. It works 100% autonomously and supports both carpet and fullblock maps. Its main focus is reliability and compatibility with strict anti-cheat servers. Besides Meteor no other mod is needed to run this addon. However, you might need a mod that caches chunks (such as [Bobby](https://www.curseforge.com/minecraft/mc-mods/bobby)) if your render distance is too small. The required distance is described in the module-specific documentation.

## Carpet Printer
The Carpet Printer prints the map line-by-line and does not reuse carpet items, making it only suited for servers where carpet duping is enabled. You can find the full documentation [here](Documentation/CarpetGuide.md).

## Fullblock Printer
The Fullblock Printer utilizes a TNT-bomber and a large item sorter to reuse most materials used to build the map. However, it is only compatible with servers where TNT duplication is enabled. You can find the full documentation [here](Documentation/FullblockGuide.md).

## Map Namer
Semi-automatically names unnamed map items in inventory. Pauses on anvil break and insufficient xp and can be resumed.

[![Map Namer](https://img.youtube.com/vi/3karXgUGU8U/0.jpg)]([https://www.youtube.com/watch?v=fY756i6OUpQ](https://www.youtube.com/watch?v=3karXgUGU8U))
