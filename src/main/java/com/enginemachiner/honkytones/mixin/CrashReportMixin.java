package com.enginemachiner.honkytones.mixin;

import net.minecraft.util.crash.CrashReport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;

import static com.enginemachiner.honkytones.ConfigKt.*;
import static com.enginemachiner.honkytones.UtilityKt.isClient;

@Mixin( CrashReport.class )
public class CrashReportMixin {

    /** Write HonkyTones config in case of crash. */
    @Inject( at = @At("HEAD"), method = "writeToFile" )
    private void honkyTonesWriteConfigOnCrash( File file, CallbackInfoReturnable<Boolean> callback ) {

        if ( isClient() ) clientConfigFile.updateProperties(clientConfig);
        else serverConfigFile.updateProperties(serverConfig);

    }

}
