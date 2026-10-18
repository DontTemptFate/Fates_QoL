package io.github.donttemptfate.fatesofa.game;

import io.github.donttemptfate.fatesofa.core.Highlight;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** World drawing in a Highlight bundle's style (outline / filled / both, width, through walls, labels). Call inside a gizmo frame. */
public final class Draw {
	private Draw() {}

	public static void box(Highlight h, AABB box, int argb, float extraWidth) {
		float w = h.width() + extraWidth;
		GizmoStyle st = h.outline() && h.filled() ? GizmoStyle.strokeAndFill(argb, w, h.fill(argb))
				: h.outline() ? GizmoStyle.stroke(argb, w) : GizmoStyle.fill(h.fill(argb));
		var p = Gizmos.cuboid(box, st);
		if (h.throughWalls()) p.setAlwaysOnTop();
	}

	public static void label(Highlight h, String text, Vec3 at, int argb) {
		if (!h.label()) return;
		var p = Gizmos.billboardText(text, at, TextGizmo.Style.forColorAndCentered(argb).withScale(TextGizmo.Style.DEFAULT_SCALE * h.labelScale()));
		if (h.throughWalls()) p.setAlwaysOnTop();
	}

	public static void line(Highlight h, Vec3 a, Vec3 b, int argb) {
		var p = Gizmos.line(a, b, argb, h.width());
		if (h.throughWalls()) p.setAlwaysOnTop();
	}
}
