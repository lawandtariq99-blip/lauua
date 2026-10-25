package com.lauua.lauuacheats.text.mixin;

import com.lauua.lauuacheats.text.TextPolicy;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Plain-text conversion is shared by sign/anvil and other text inputs; leave rich display alone. */
@Mixin(Component.class)
public interface ComponentMixin {
    @Inject(method="getString", at=@At("HEAD"), cancellable=true)
    private void lauuacheats$rawOwnKeys(CallbackInfoReturnable<String> cir) {
        Component self=(Component)this;
        if (self.getContents() instanceof TranslatableContents translation && self.getSiblings().isEmpty()) {
            String key=TextPolicy.plainKey(translation.getKey());
            if (key!=null) cir.setReturnValue(key);
        }
    }
}
