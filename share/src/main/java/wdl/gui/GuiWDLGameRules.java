/*
 * This file is part of World Downloader: A mod to make backups of your
 * multiplayer worlds.
 * http://www.minecraftforum.net/forums/mapping-and-modding/minecraft-mods/2520465
 *
 * Copyright (c) 2014 nairol, cubic72
 * Copyright (c) 2017 Pokechu22, julialy
 *
 * This project is licensed under the MMPLv2.  The full text of the MMPL can be
 * found in LICENSE.md, or online at https://github.com/iopleke/MMPLv2/blob/master/LICENSE.md
 * For information about this the MMPLv2, see http://stopmodreposts.org/
 *
 * Do not redistribute (in modified or unmodified form) without prior permission.
 */
package wdl.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ImmutableList;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiListExtended;
import net.minecraft.client.gui.GuiListExtended.IGuiListEntry;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.world.GameRules;
import net.minecraft.world.GameRules.ValueType;
import wdl.WDL;
import wdl.config.IConfiguration;

public class GuiWDLGameRules extends GuiScreen {
	private static final Logger LOGGER = LogManager.getLogger();

	/**
	 * Text to draw (set from inner classes)
	 */
	@Nullable
	private String hoveredToolTip;

	private static interface KeyboardEntry extends IGuiListEntry {
		public abstract void keyDown(char typedChar, int keyCode);
		public abstract void onUpdate();
	}

	/**
	 * Colors for the text field on numeric entries when there is/is not a
	 * modified rule.
	 */
	private static final int SET_TEXT_FIELD = 0xE0E0E0, DEFAULT_TEXT_FIELD = 0x808080;

	private class GuiGameRuleList extends GuiListExtended {
		/** The rule that was last clicked.  This should be compared by ref. */
		@Nullable
		private String lastClickedRule = null;

		public GuiGameRuleList() {
			super(GuiWDLGameRules.this.mc, GuiWDLGameRules.this.width,
					GuiWDLGameRules.this.height, 39,
					GuiWDLGameRules.this.height - 32, 24);
			this.entries = new ArrayList<>();
			for (String rule : vanillaGameRules) {
				if (rules.areSameType(rule, ValueType.NUMERICAL_VALUE)) {
					this.entries.add(new IntRuleEntry(rule));
				} else if (rules.areSameType(rule, ValueType.BOOLEAN_VALUE)) {
					this.entries.add(new BooleanRuleEntry(rule));
				} else {
					LOGGER.debug("Couldn't identify type for vanilla game rule " + rule);
				}
			}
		}

		private abstract class RuleEntry extends GuiListEntry {
			@Nonnull
			protected final String ruleName;
			private GuiButton resetButton;

			public RuleEntry(@Nonnull String ruleName) {
				this.ruleName = ruleName;
				this.resetButton = new GuiButton(0, 0, 0, 50, 20, I18n.format("wdl.gui.gamerules.resetRule"));
			}

			@Override
			public final void drawEntry(int slotIndex, int x, int y, int listWidth,
					int slotHeight, int mouseX, int mouseY, boolean isSelected) {
				drawString(fontRenderer, this.ruleName, x, y + 6, 0xFFFFFFFF);
				this.resetButton.x = x + listWidth / 2 + 110;
				this.resetButton.y = y;
				this.resetButton.enabled = isRuleSet(this.ruleName);
				LocalUtils.drawButton(this.resetButton, mc, mouseX, mouseY);
				this.draw(x, y, listWidth, slotHeight, mouseX, mouseY);

				if (this.isMouseOverControl(mouseX, mouseY)) {
					String key = "wdl.gui.gamerules.rules." + ruleName;
					if (I18n.hasKey(key)) { // may return false for mods
						hoveredToolTip = I18n.format(key);
					}
				}
			}
			@Override
			public final boolean mousePressed(int slotIndex, int mouseX, int mouseY,
					int mouseEvent, int relativeX, int relativeY) {
				lastClickedRule = this.ruleName;

				if (resetButton.mousePressed(mc, mouseX, mouseY)) {
					resetButton.playPressSound(mc.getSoundHandler());
					this.performResetAction();
					return true;
				}
				return mouseDown(mouseX, mouseY, mouseEvent);
			}
			@Override
			public final void mouseReleased(int slotIndex, int x, int y,
					int mouseEvent, int relativeX, int relativeY) {
				resetButton.mouseReleased(mouseX, mouseY);
				mouseUp(mouseX, mouseY, mouseEvent);
			}

			protected abstract void draw(int x, int y, int listWidth,
					int slotHeight, int mouseX, int mouseY);
			protected abstract boolean mouseDown(int x, int y, int button);
			protected abstract void mouseUp(int x, int y, int button);
			protected abstract boolean isMouseOverControl(int mouseX, int mouseY);

			protected boolean isFocused() {
				return lastClickedRule == this.ruleName;  // Ref equals
			}

			/** Called when the reset button is clicked. */
			protected void performResetAction() {
				clearRule(this.ruleName);
			}
		}

		private class IntRuleEntry extends RuleEntry implements KeyboardEntry {
			private GuiNumericTextField field;

			public IntRuleEntry(String ruleName) {
				super(ruleName);
				field = new GuiNumericTextField(0, fontRenderer, 0, 0, 100, 20);
				field.setText(getRule(ruleName));
			}

			@Override
			public void draw(int x, int y, int listWidth, int slotHeight,
					int mouseX, int mouseY) {
				if (!this.isFocused()) {
					field.setFocused(false);
				}
				if (isRuleSet(this.ruleName)) {
					field.setTextColor(SET_TEXT_FIELD);
				} else {
					field.setTextColor(DEFAULT_TEXT_FIELD);
				}
				field.x = x + listWidth / 2;
				field.y = y;
				field.drawTextBox();
			}

			@Override
			protected boolean mouseDown(int x, int y, int button) {
				field.mouseClicked(x, y, button);
				return false;
			}

			@Override
			protected void mouseUp(int x, int y, int button) { }

			@Override
			public void onUpdate() {
				this.field.updateCursorCounter();
			}

			@Override
			public void keyDown(char typedChar, int keyCode) {
				if (this.field.textboxKeyTyped(typedChar, keyCode)) {
					setRule(ruleName, Integer.toString(this.field.getValue()));
				}
			}

			@Override
			protected boolean isMouseOverControl(int mouseX, int mouseY) {
				return Utils.isMouseOverTextBox(mouseX, mouseY, field);
			}

			@Override
			protected void performResetAction() {
				super.performResetAction();
				this.field.setText(getRule(this.ruleName)); // Reset field text to default
			}
		}

		private class BooleanRuleEntry extends RuleEntry {
			private GuiButton button;

			public BooleanRuleEntry(String ruleName) {
				super(ruleName);
				button = new GuiButton(0, 0, 0, 100, 20, "");
			}

			@Override
			protected void draw(int x, int y, int listWidth, int slotHeight,
					int mouseX, int mouseY) {
				this.button.x = x + listWidth / 2;
				this.button.y = y;
				this.button.displayString = getRule(ruleName);
				LocalUtils.drawButton(this.button, mc, mouseX, mouseY);
			}

			@Override
			protected boolean mouseDown(int x, int y, int button) {
				if (this.button.mousePressed(mc, x, y)) {
					this.button.playPressSound(mc.getSoundHandler());
					boolean oldValue = getRule(ruleName).equals("true");
					setRule(ruleName, oldValue ? "false" : "true");
					return true;
				} else {
					return false;
				}
			}

			@Override
			protected void mouseUp(int x, int y, int button) {
				this.button.mouseReleased(x, y);
			}

			@Override
			protected boolean isMouseOverControl(int mouseX, int mouseY) {
				return button.isMouseOver();
			}
		}

		private final List<GuiListEntry> entries;

		@Override
		public IGuiListEntry getListEntry(int index) {
			return entries.get(index);
		}

		@Override
		protected int getSize() {
			return entries.size();
		}

		public void update() {
			// Use a manual for loop to avoid concurrent modification exceptions
			for (int i = 0; i < getSize(); i++) {
				IGuiListEntry entry = getListEntry(i);
				if (entry instanceof KeyboardEntry) {
					((KeyboardEntry) entry).onUpdate();
				}
			}
		}

		public void keyDown(char typedChar, int keyCode) {
			// Use a manual for loop to avoid concurrent modification exceptions
			for (int i = 0; i < getSize(); i++) {
				IGuiListEntry entry = getListEntry(i);
				if (entry instanceof KeyboardEntry) {
					((KeyboardEntry) entry).keyDown(typedChar, keyCode);
				}
			}
		}

		@Override
		public int getListWidth() {
			return 210 * 2;
		}

		@Override
		protected int getScrollBarX() {
			return this.width / 2 + getListWidth() / 2 + 4;
		}
	}

	private String title;
	private GuiGameRuleList list;
	@Nullable
	private final GuiScreen parent;
	private final IConfiguration config;

	private GameRules rules;
	/** All vanilla game rules; this list is immutable. */
	private final List<String> vanillaGameRules;

	/**
	 * Gets the value of the given rule, using WDL's overriden value if set,
	 * otherwise the default.
	 *
	 * @param ruleName
	 *            The name of the rule.
	 * @return The rule's string value. Will be null if no such rule exists.
	 */
	@Nullable
	private String getRule(@Nonnull String ruleName) {
		if (isRuleSet(ruleName)) {
			return config.getGameRule("GameRule." + ruleName);
		} else {
			return rules.getString(ruleName);
		}
	}

	/**
	 * Overrides the given rule in WDL's settings.
	 *
	 * @param ruleName
	 *            The name of the rule.
	 * @param value
	 *            The new value. Must not be null; to clear a rule, use
	 *            {@link #clearRule(String)}.
	 */
	private void setRule(@Nonnull String ruleName, @Nonnull String value) {
		config.setGameRule(ruleName, value);
	}

	/**
	 * Un-overrides the given rule. If the rule is not currently overridden, or
	 * does not exist at all, nothing happens.
	 *
	 * @param ruleName
	 *            The name of the rule.
	 */
	private void clearRule(@Nonnull String ruleName) {
		config.clearGameRule(ruleName);
	}

	/**
	 * Checks if the given rule is overridden.
	 *
	 * @param ruleName
	 *            The name of the rule.
	 * @return True if the rule is overriden; false otherwise (not overriden or
	 *         no such rule exists).
	 */
	private boolean isRuleSet(@Nonnull String ruleName) {
		return config.hasGameRule(ruleName);
	}

	public GuiWDLGameRules(@Nullable GuiScreen parent) {
		this.parent = parent;
		this.config = WDL.worldProps;
		this.rules = WDL.worldClient.getGameRules();
		this.vanillaGameRules = ImmutableList.copyOf(rules.getRules());
	}

	@Override
	public void initGui() {
		this.title = I18n.format("wdl.gui.gamerules.title");
		this.list = new GuiGameRuleList();

		this.buttonList.add(new GuiButton(100, this.width / 2 - 100,
				this.height - 29, I18n.format("gui.done")));
	}

	@Override
	public void updateScreen() {
		this.list.update();
		super.updateScreen();
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		hoveredToolTip = null;
		this.list.drawScreen(mouseX, mouseY, partialTicks);

		super.drawScreen(mouseX, mouseY, partialTicks);

		this.drawCenteredString(fontRenderer, title, width / 2, 4, 0xFFFFFF);

		if (hoveredToolTip != null) {
			Utils.drawGuiInfoBox(hoveredToolTip, width, height, 48);
		}
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int mouseButton)
			throws IOException {
		this.list.mouseClicked(mouseX, mouseY, mouseButton);
		super.mouseClicked(mouseX, mouseY, mouseButton);
	}

	@Override
	protected void mouseReleased(int mouseX, int mouseY, int state) {
		this.list.mouseReleased(mouseX, mouseY, state);
		super.mouseReleased(mouseX, mouseY, state);
	}

	@Override
	protected void keyTyped(char typedChar, int keyCode) throws IOException {
		this.list.keyDown(typedChar, keyCode);
		super.keyTyped(typedChar, keyCode);
	}

	@Override
	public void handleMouseInput() throws IOException {
		super.handleMouseInput();
		this.list.handleMouseInput();
	}

	@Override
	protected void actionPerformed(GuiButton button) throws IOException {
		if (button.id == 100) {
			this.mc.displayGuiScreen(this.parent);
		}
	}

	@Override
	public void onGuiClosed() {
		WDL.saveProps();
	}
}
