package qouteall.imm_ptl.core.platform_specific;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.MixinEnvironment;
import qouteall.imm_ptl.core.IPModMain;
import qouteall.q_misc_util.Helper;

public class IPModEntry implements ModInitializer {
    
    @Override
    public void onInitialize() {
        IPModMain.init();
        RequiemCompat.init();
        
        IPModMain.registerEntityTypes(
            (id, entityType) -> Registry.register(BuiltInRegistries.ENTITY_TYPE, id, entityType)
        );
        
        IPModMain.registerBlocks((id, obj) -> Registry.register(BuiltInRegistries.BLOCK, id, obj));
        
        if (FabricLoader.getInstance().isModLoaded("dimthread")) {
            O_O.isDimensionalThreadingPresent = true;
            Helper.log("Dimensional Threading is present");
        }
        else {
            Helper.log("Dimensional Threading is not present");
        }
        
        // dev check (-Dimm_ptl.mixin_audit=true, gradle -PmixinAudit): force-load every mixin target
        if (Boolean.getBoolean("imm_ptl.mixin_audit")) {
            ServerLifecycleEvents.SERVER_STARTED.register(s -> MixinEnvironment.getCurrentEnvironment().audit());
        }
    }
    
}
