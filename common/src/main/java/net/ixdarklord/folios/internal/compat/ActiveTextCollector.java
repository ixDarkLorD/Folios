package net.ixdarklord.folios.internal.compat;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

public interface ActiveTextCollector {
    Parameters defaultParameters();

    void accept(TextAlignment alignment, int anchorX, int y, Parameters parameters, FormattedCharSequence text);

    record Parameters(float opacity) {
        public Parameters withOpacity(float opacity) {
            return this.opacity == opacity ? this : new Parameters(opacity);
        }
    }

    static @Nullable Style styleAt(Font font, FormattedCharSequence text, int left, int y, int testX, int testY) {
        if (testY < y || testY >= y + 9 || testX < left) return null;
        return font.getSplitter().componentStyleAtWidth(text, testX - left);
    }

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
