package com.julflips.map_printer;

import com.julflips.map_printer.modules.CarpetPrinter;
import com.julflips.map_printer.modules.FullBlockPrinter;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class Addon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("MapArt");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Map Printer");

        // Modules
        Modules.get().add(new CarpetPrinter());
        Modules.get().add(new FullBlockPrinter());

    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.julflips.map_printer";
    }
}
