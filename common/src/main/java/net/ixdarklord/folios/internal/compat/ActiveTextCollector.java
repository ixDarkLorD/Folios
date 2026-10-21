package net.ixdarklord.folios.internal.compat;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

// Minecraft 26.1 hands lines of text to a collector: one draws them (with what's under the mouse shown), another finds
// what was clicked in them. On 1.21.1 the drawing one draws each line as it's handed over (GuiGraphicsExtractor has it).
public interface ActiveTextCollector {
    Parameters defaultParameters();

    void accept(TextAlignment alignment, int anchorX, int y, Parameters parameters, FormattedCharSequence text);

    /** How a line is drawn: only its opacity here. */
    record Parameters(float opacity) {
        public Parameters withOpacity(float opacity) {
            return this.opacity == opacity ? this : new Parameters(opacity);
        }
    }

    /** The style under a point of the text: {@code null} when it's on none of the lines. */
    static @Nullable Style styleAt(Font font, FormattedCharSequence text, int left, int y, int testX, int testY) {
        if (testY < y || testY >= y + 9 || testX < left) return null;
        return font.getSplitter().componentStyleAtWidth(text, testX - left);
    }

    /** Finds what a click at a point lands on: a part of the text with a click event, or with an insertion when asked. */
    class ClickableStyleFinder implements ActiveTextCollector {
        private static final Parameters INITIAL = new Parameters(1);
        private final Font font;
        private final int testX;
        private final int testY;
        private boolean includeInsertions;
        private @Nullable Style result;

        public ClickableStyleFinder(Font font, int testX, int testY) {
            this.font = font;
            this.testX = testX;
            this.testY = testY;
        }

        @Override
        public Parameters defaultParameters() {
            return INITIAL;
        }

        @Override
        public void accept(TextAlignment alignment, int anchorX, int y, Parameters parameters, FormattedCharSequence text) {
            Style style = styleAt(this.font, text, alignment.calculateLeft(anchorX, this.font, text), y, this.testX, this.testY);
            if (style != null && (style.getClickEvent() != null || this.includeInsertions && style.getInsertion() != null)) this.result = style;
        }

        public ClickableStyleFinder includeInsertions(boolean flag) {
            this.includeInsertions = flag;
            return this;
        }

        public @Nullable Style result() {
            return this.result;
        }
    }
}
