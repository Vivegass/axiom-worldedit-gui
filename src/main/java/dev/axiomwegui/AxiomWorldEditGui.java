package dev.axiomwegui;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client entrypoint. The UI itself is driven from mixins into Axiom's editor render loop. */
public final class AxiomWorldEditGui implements ClientModInitializer {
    public static final String MOD_ID = "axiom_worldedit_gui";
    public static final Logger LOGGER = LoggerFactory.getLogger("Axiom WorldEdit GUI");

    @Override
    public void onInitializeClient() {
        GuiConfig.load();
        LOGGER.info("Axiom WorldEdit GUI {} loaded", version());
    }

    public static String version() {
        return FabricLoader.getInstance().getModContainer(MOD_ID)
            .map(c -> c.getMetadata().getVersion().getFriendlyString())
            .orElse("?");
    }
}
