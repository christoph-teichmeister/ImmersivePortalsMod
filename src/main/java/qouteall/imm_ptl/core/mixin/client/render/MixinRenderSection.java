package qouteall.imm_ptl.core.mixin.client.render;

import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import qouteall.imm_ptl.core.ducks.IERenderSection;
//? if >=26.2 {
import net.minecraft.client.SectionUpdateTracker;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?}

@Mixin(SectionRenderDispatcher.RenderSection.class)
public abstract class MixinRenderSection implements IERenderSection {
    
    private long portal_mark;
    
    //? if >=26.2 {
    // see ImmPtlViewArea.getDirtyState, a new section has to be compiled
    @Unique
    private SectionUpdateTracker.SectionDirtyState ip_dirtyState;
    //?}
    
    @Shadow
    protected abstract void reset();
    
    @Shadow
    @Final
    @Mutable
    public int index;
    
    @Override
    public void portal_fullyReset() {
        reset();
    }
    
    @Override
    public long portal_getMark() {
        return portal_mark;
    }
    
    @Override
    public void portal_setMark(long arg) {
        portal_mark = arg;
    }
    
    @Override
    public void portal_setIndex(int arg) {
        index = arg;
    }
    
    //? if >=26.2 {
    @Override
    public SectionUpdateTracker.SectionDirtyState ip_getDirtyState() {
        if (ip_dirtyState == null) {
            ip_dirtyState = IESectionDirtyState_Invoker.ip_create(true, false, 0);
        }
        return ip_dirtyState;
    }
    
    // the mesh is gone, compile it again when the section is visible
    @Inject(method = "reset", at = @At("TAIL"))
    private void onReset(CallbackInfo ci) {
        if (ip_dirtyState != null) {
            ip_dirtyState.setDirty(false);
        }
    }
    //?}
}
