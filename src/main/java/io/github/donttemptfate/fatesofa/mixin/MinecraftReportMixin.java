package io.github.donttemptfate.fatesofa.mixin;

import net.minecraft.SystemReport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.resources.language.LanguageManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds a "Fates OFA" line to crash reports and the F3 system report: version, features on, failures, data. */
@Mixin(Minecraft.class)
public abstract class MinecraftReportMixin {
	@Inject(method = "fillSystemReport", at = @At("RETURN"), require = 0)
	private static void fatesofa$report(SystemReport report, Minecraft mc, LanguageManager lang, String version, Options options,
			CallbackInfoReturnable<SystemReport> cir) {
		try {
			cir.getReturnValue().setDetail("Fates OFA", io.github.donttemptfate.fatesofa.CrashInfo.text());
		} catch (Throwable ignored) {
			// a crash report must never fail because of us
		}
	}
}
