package qouteall.imm_ptl.core.ducks;

public interface IERenderSection {
    void portal_fullyReset();
    
    long portal_getMark();
    
    void portal_setMark(long arg);
    
    void portal_setIndex(int arg);
    
    //? if >=26.2 {
    /**
     * The dirty state of the section in an ImmPtlViewArea (26.2+ tracks it in the camera-centered
     * SectionUpdateTracker, which does not cover the sections around other portal views).
     */
    net.minecraft.client.SectionUpdateTracker.SectionDirtyState ip_getDirtyState();
    //?}
}
