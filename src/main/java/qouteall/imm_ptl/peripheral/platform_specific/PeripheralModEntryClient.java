package qouteall.imm_ptl.peripheral.platform_specific;

import net.fabricmc.api.ClientModInitializer;
import qouteall.imm_ptl.peripheral.PeripheralModMain;

public class PeripheralModEntryClient implements ClientModInitializer {
    public static void registerBlockRenderLayers() {
        // Since 26.1 the chunk section layer (cutout for portalHelperBlock) is derived
        // from the texture transparency of each quad, so no registration is needed.
    }
    
    @Override
    public void onInitializeClient() {
        PeripheralModEntryClient.registerBlockRenderLayers();
        
        PeripheralModMain.initClient();
    }
}
