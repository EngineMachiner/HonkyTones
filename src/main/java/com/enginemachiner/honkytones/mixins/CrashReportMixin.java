package com.enginemachiner.honkytones.mixins;

import com.enginemachiner.honkytones.Base;
import com.enginemachiner.honkytones.BaseKt;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.minecraft.util.crash.CrashReport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;

import static com.enginemachiner.honkytones.BaseKt.getServerConfig;

@Mixin( CrashReport.class )
public class CrashReportMixin {

    /** Write HonkyTones config in case of crash (not sure if it will always work) */
    @Inject(at = @At("HEAD"), method = "writeToFile")
    private void honkyTonesWriteConfigOnCrash( File file,
                                               CallbackInfoReturnable<Boolean> callback ) {

        if ( FabricLoaderImpl.INSTANCE.getEnvironmentType() == EnvType.CLIENT ) {
            Base.clientConfigFile.updateProperties( BaseKt.clientConfig );
        } else Base.serverConfigFile.updateProperties( getServerConfig() );

    }

}
