package com.lauua.lauuacheats.text.mixin;

import com.lauua.lauuacheats.text.TextPolicy;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Rewrite the outgoing brand at payload construction rather than sending a second brand packet. */
@Mixin(BrandPayload.class)
public class BrandPayloadMixin {
    @ModifyVariable(method="<init>(Ljava/lang/String;)V", at=@At("HEAD"), argsOnly=true, index=1)
    private static String lauuacheats$brand(String original) {
        return TextPolicy.vanillaBrand && (original.equals("fabric") || original.endsWith(",fabric")) ? "vanilla" : original;
    }
}
