
# Fullblock Printer

The Fullblock Printer is designed to reuse all blocks used for the map. It works by utilizing TNT-bomber to reset the Map Area. You can find a Demo video of a full run here:

[![FullBlock Printer](https://img.youtube.com/vi/fY756i6OUpQ/0.jpg)](https://www.youtube.com/watch?v=fY756i6OUpQ)
## Setup
To get the program running we first need to build an area where we will build one 1x1 map at a time. A litematica file with an example Map Area can be found [here](FullblockPrinter.litematic). Note that this is by no means a perfect setup and should be considered an early prototype.

If you change the setup make sure it still fulfills the following points:
- The Setup has to be placed in the Mushroom Fields biome below sea level (y=64) to prevent all monsters from spawning.
- The Restock Station has to be on the north side of the Map Area (could easily be changed with code)
- The Restock Station should have a DumpStation, FinishedMapChest, MapMaterialChest, Reset Trapped Chest, and Cartography Table. I will explain later what the terms mean
- All of the above components should be reachable from each other by walking in a straight line
- Never unload the bomber while they are running. They might break.
- **If the bot can not see the whole map at a corner you need to install a mod that caches chunks to increase your render distance. One mod I know for sure is compatible is [Bobby](https://www.curseforge.com/minecraft/mc-mods/bobby)**.


### Special blocks
Let's go over all the special blocks we need at the Restock Station.

#### DumpStation
The bot will throw all blocks it doesn't need anymore at this position. Ideally, you use a water stream to pipe them back into the sorter.

#### FinishedMapChest
Pretty self-explanatory. The bot will put the maps here.

#### MapMaterialChest
This chest should contain empty maps and glass panes for the bot to lock new maps.

#### Reset Trapped Chests
We use a trapped chest to activate the bomber. Since the bomber currently can't go back and forth in one run, we need a second Reset Chest to trigger the next reset.

### Load NBT files
When the module is started for the first time a "nerv-printer" folder is created in your Minecraft directory. Put in as many 2D 1x1 NBT files as you like. Keep in mind the bot will process them in alphabetical order.
## Workflow
The addon follows these 3 steps:

1. Register important blocks
2. Build Map
3. Create Map Item


### Register important blocks
The module will prompt you to interact with all special blocks. Chests only have to be selected once even though the rendered box might only highlight half of the chest. **Once you are finished interact with the top left (-X, -Z) corner of the map area to start the next step.** It is highlighted with a box. Every inventory slot containing nothing or a material that was registered will be marked as a slot for future material. All other slots will not be used.

### Build Map
The bot will build the map line by line. It calculates the maximum area he can cover with the free slots he has available and restocks accordingly. When one material is empty he dumps the unnecessary items into the DumpStation and the cycle repeats.

### Create Map Item
When the map is finished the bot grabs an empty map and glass pane from the MapMaterialChest and walks a small circle in the center to fill it. Depending on your render distance this step might be unnecessary. After storing the map the bot will trigger the reset and start with the next nbt file.
