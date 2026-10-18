package io.github.donttemptfate.fatesofa.hud;

import io.github.donttemptfate.fatescommon.ui.Card;
import io.github.donttemptfate.fatesofa.core.Feature;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * One HUD element: a card built fresh each frame (or null when there is nothing to show), owned by a
 * feature. The compositor decides where it goes; the element never picks absolute pixels itself.
 */
public final class HudElement {
	public final String id;
	public final String name;
	public final Feature owner;
	public final HudPos defaultPos;
	public final int priority;
	final Supplier<Card> content;
	final Supplier<Card> sample;
	final BooleanSupplier visible;
	/** The owner's Display bundle (islands, hide in menus), or null. */
	public io.github.donttemptfate.fatesofa.core.Display display;

	/**
	 * @param content what to show now (null = nothing)
	 * @param sample  example content for the HUD editor
	 * @param visible extra condition (island, activity); the owner being active is always checked
	 */
	public HudElement(String id, String name, Feature owner, HudPos defaultPos, int priority, Supplier<Card> content, Supplier<Card> sample,
			BooleanSupplier visible) {
		this.id = id;
		this.name = name;
		this.owner = owner;
		this.defaultPos = defaultPos;
		this.priority = priority;
		this.content = content;
		this.sample = sample;
		this.visible = visible == null ? () -> true : visible;
	}

	/** Lets the Display bundle decide where and when this card shows. */
	public HudElement display(io.github.donttemptfate.fatesofa.core.Display d) {
		this.display = d;
		return this;
	}
}
