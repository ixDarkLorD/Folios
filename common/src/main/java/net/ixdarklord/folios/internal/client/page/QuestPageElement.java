package net.ixdarklord.folios.internal.client.page;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ixdarklord.folios.api.client.FoliosBook;
import net.ixdarklord.folios.api.client.FoliosObjective;
import net.ixdarklord.folios.api.client.page.FoliosLayout;
import net.ixdarklord.folios.api.client.page.FoliosPageElement;
import net.ixdarklord.folios.api.client.page.FoliosPainter;
import net.ixdarklord.folios.api.client.page.FoliosPalette;
import net.ixdarklord.folios.api.client.page.FoliosText;
import net.ixdarklord.folios.internal.client.ClientQuests;
import net.ixdarklord.folios.internal.quest.PlayerQuests;
import net.ixdarklord.folios.internal.quest.QuestRewards;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record QuestPageElement(List<FoliosObjective> objectives, Optional<String> title, String text, Optional<String> reward, boolean inOrder,
                               Optional<ResourceLocation> quest, Optional<String> locked) implements FoliosPageElement {
    public static final MapCodec<QuestPageElement> CODEC = RecordCodecBuilder.<QuestPageElement>mapCodec(instance -> instance.group(
            FoliosObjective.CODEC.listOf(0, 32).optionalFieldOf("objectives", List.of()).forGetter(QuestPageElement::objectives),
            FoliosText.CODEC.optionalFieldOf("title").forGetter(QuestPageElement::title),
            FoliosText.CODEC.optionalFieldOf("text", "").forGetter(QuestPageElement::text),
            FoliosText.CODEC.optionalFieldOf("reward").forGetter(QuestPageElement::reward),
            Codec.BOOL.optionalFieldOf("in_order", false).forGetter(QuestPageElement::inOrder),
            ResourceLocation.CODEC.optionalFieldOf("quest").forGetter(QuestPageElement::quest),
            FoliosText.CODEC.optionalFieldOf("locked").forGetter(QuestPageElement::locked)
    ).apply(instance, (objectives, title, text, reward, inOrder, quest, locked) ->
            new QuestPageElement(inOrder ? FoliosObjective.inOrder(objectives) : objectives, title, text, reward, inOrder, quest, locked))
    ).validate(element -> element.objectives.isEmpty() && element.quest.isEmpty()
            ? DataResult.error(() -> "A quest has \"objectives\", or names a server \"quest\"") : DataResult.success(element));

    private static final int HEAD = 12;
    private static final int BAR_ROOM = 5;
    private static final int GAP = 3;
    private static final float SMALL = 0.75F;
    private static final float SMALLER = 0.65F;
    private static final int MOST_LINES = 3;
    private static final int TOOLTIP_WIDTH = 170;
    private static final int SLOT = 16;
    private static final int SLOT_GAP = 2;
    private static final int MOST_ROWS = 3;

    @Override
    public List<FoliosObjective> objectives() {
        return this.shown();
    }

    private List<FoliosObjective> shown() {
        if (quest.isEmpty()) return objectives;
        ClientQuests.Quest sent = ClientQuests.get(quest.get());
        return sent == null ? List.of() : sent.inOrder() ? FoliosObjective.inOrder(sent.objectives()) : sent.objectives();
    }

    private @Nullable Component shut() {
        if (quest.isEmpty()) return null;
        if (ClientQuests.get(quest.get()) == null) return Component.translatableWithFallback("folios.quest.server", "This quest is kept by the server");
        if (!ClientQuests.progress(quest.get()).isOpen()) {
            return locked.map(FoliosText::inline).orElseGet(() -> Component.translatableWithFallback("folios.quest.locked", "Not yet open"));
        }
        return null;
    }

    private boolean givesItems() {
        ClientQuests.Quest sent = quest.map(ClientQuests::get).orElse(null);
        return sent != null && sent.rewards() != null;
    }

    private record Track(float[] progress, boolean[] waiting, boolean complete, boolean claimed) {
        boolean done(int index) {
            return progress[index] >= 1;
        }

        int doneCount() {
            int done = 0;
            for (float value : progress) if (value >= 1) done++;
            return done;
        }
    }

    private Track track(FoliosBook book, List<FoliosObjective> shown) {
        float[] progress = new float[shown.size()];
        boolean[] waiting = new boolean[shown.size()];
        if (quest.isPresent()) {
            PlayerQuests.Progress found = ClientQuests.progress(quest.get());
            ClientQuests.Quest sent = ClientQuests.get(quest.get());
            boolean blocked = false;
            for (int index = 0; index < progress.length; index++) {
                progress[index] = found.done() ? 1 : index < found.objectives().size() ? found.objectives().get(index) : 0;
                waiting[index] = blocked && progress[index] < 1;
                if (sent != null && sent.inOrder() && progress[index] < 1) blocked = true;
            }
            return new Track(progress, waiting, found.done() && progress.length > 0, found.claimed());
        }
        boolean all = true;
        for (int index = 0; index < progress.length; index++) {
            FoliosObjective objective = shown.get(index);
            progress[index] = objective.progress(book);
            waiting[index] = progress[index] < 1 && objective.isWaiting(book);
            all &= progress[index] >= 1;
        }
        return new Track(progress, waiting, all && progress.length > 0, false);
    }

    @Override
    public List<Laid> lay(FoliosLayout layout) {
        FoliosBook book = layout.book();
        List<FoliosObjective> shown = this.shown();
        int rewardHeight = this.rewardHeight(layout.font(), layout.contentWidth());
        int across = layout.contentWidth() - FRAME_LEFT - FRAME_PAD * 2;
        int[] laid = new int[shown.size()];
        float[] small = new float[shown.size()];
        boolean[] barred = new boolean[shown.size()];
        for (int index = 0; index < laid.length; index++) {
            small[index] = SMALL;
            laid[index] = label(layout.font(), shown.get(index).label(book), across, MOST_LINES, SMALL).lines.size();
            barred[index] = !shown.get(index).isAllOrNothing();
        }
        int fit = layout.contentBottom() - FoliosLayout.CONTENT_TOP - FoliosLayout.TITLE_HEIGHT;
        int room = room(laid, small, barred, rewardHeight);
        for (int index = laid.length - 1; index >= 0 && room > fit; index--) {
            if (laid[index] <= 1) continue;
            small[index] = SMALLER;
            laid[index] = label(layout.font(), shown.get(index).label(book), across, MOST_LINES, SMALLER).lines.size();
            room = room(laid, small, barred, rewardHeight);
        }
        boolean scrolls = room > fit;
        if (scrolls) {
            across -= SCROLLBAR;
            for (int index = 0; index < laid.length; index++) {
                small[index] = SMALL;
                laid[index] = label(layout.font(), shown.get(index).label(book), across, MOST_LINES, SMALL).lines.size();
            }
            room = fit;
        }
        int labelWidth = across;
        int[] rows = new int[laid.length];
        int total = 0;
        for (int index = 0; index < rows.length; index++) {
            rows[index] = Label.height(laid[index], small[index]) + (barred[index] ? BAR_ROOM : 0) + GAP;
            total += rows[index];
        }
        int window = scrolls ? fit - HEAD + GAP - (rewardHeight > 0 ? REWARD_GAP + rewardPanel(rewardHeight) : 0) : total;
        int lastFirst = rows.length;
        for (int below = 0; lastFirst > 0 && below + rows[lastFirst - 1] <= window; ) below += rows[--lastFirst];
        int scrollTo = Math.max(0, Math.min(lastFirst, rows.length - 1));
        int allRows = Math.max(1, total);
        int[] first = {0};
        float[] dragged = {0};
        Component shut = this.shut();
        return layout.flow(title.orElse("folios.quest"), shut != null ? HEAD : room, text, (painter, y) -> {
            FoliosPalette palette = painter.palette();
            int left = FoliosLayout.CONTENT_LEFT;
            int right = painter.contentRight();
            if (shut != null) {
                painter.text(shut, left, y + 1, palette.muted());
                return;
            }
            Track track = this.track(book, shown);
            Component count = track.complete() ? Component.translatableWithFallback("folios.quest.complete", "Complete").withStyle(ChatFormatting.BOLD)
                    : Component.translatableWithFallback("folios.quest.progress", "%s of %s done", track.doneCount(), shown.size());
            painter.text(count, left, y + 1, track.complete() ? 0xFF3E8E41 : palette.muted());
            Stop[] stops = new Stop[shown.size()];
            boolean[] walked = new boolean[shown.size()];
            boolean reached = true;
            boolean next = true;
            for (int index = 0; index < stops.length; index++) {
                boolean ticked = track.done(index);
                stops[index] = ticked ? Stop.DONE : next && !track.waiting()[index] ? Stop.NEXT : Stop.AHEAD;
                if (!ticked && !track.waiting()[index]) next = false;
                reached = reached && ticked;
                walked[index] = reached;
            }
            int top = y + HEAD;
            first[0] = Math.max(0, Math.min(first[0], scrollTo));
            int from = scrolls ? first[0] : 0;
            if (scrolls) {
                painter.scroll(left, top, right - left, window, notches -> first[0] = Math.max(0, Math.min(scrollTo, first[0] - (int) Math.signum(notches))));
                int trackHeight = window - GAP;
                int[] starts = new int[rows.length];
                for (int index = 1; index < rows.length; index++) starts[index] = starts[index - 1] + rows[index - 1];
                int barX = right - Shapes.SCROLLBAR;
                int thumb = Math.max(8, trackHeight * window / allRows);
                int travel = Math.max(1, trackHeight - thumb);
                boolean held = painter.drag(barX - 2, top, Shapes.SCROLLBAR + 4, trackHeight, (dx, dy) -> {
                    dragged[0] += dy * starts[scrollTo] / (float) travel;
                    int target = starts[first[0]] + Math.round(dragged[0]);
                    int nearest = first[0];
                    for (int index = 0; index <= scrollTo; index++) {
                        if (Math.abs(starts[index] - target) < Math.abs(starts[nearest] - target)) nearest = index;
                    }
                    if (nearest != first[0]) {
                        dragged[0] -= starts[nearest] - starts[first[0]];
                        first[0] = nearest;
                    }
                });
                if (!held) dragged[0] = 0;
                int thumbTop = top + (scrollTo == 0 ? 0 : travel * starts[from] / starts[scrollTo]);
                Shapes.scrollbar(painter, barX, top, trackHeight, thumbTop, thumb, held);
            }
            int pathX = left + 3;
            int rowY = top;
            int previous = from > 0 ? top - 2 : -1;
            for (int index = from; index < shown.size(); index++) {
                if (scrolls && rowY - top + rows[index] > window) break;
                FoliosObjective objective = shown.get(index);
                boolean ticked = track.done(index);
                boolean waiting = track.waiting()[index];
                int stopY = rowY + 5;
                if (previous >= 0) path(painter, pathX, previous + (previous == top - 2 ? 0 : 4), stopY - 4, walked[index], palette);
                stop(painter, pathX, stopY, stops[index], track.complete(), palette);
                previous = stopY;
                Component label = objective.label(book);
                if (ticked) label = label.copy().withStyle(ChatFormatting.STRIKETHROUGH);
                int frameRight = left + TEXT_LEFT + labelWidth + FRAME_PAD;
                Shapes.panel(painter, left + FRAME_LEFT, rowY - 1, frameRight, rowY + rows[index] - GAP);
                if (stops[index] == Stop.NEXT) outline(painter, left + FRAME_LEFT, rowY - 1, frameRight, rowY + rows[index] - GAP, FoliosPalette.alpha(palette.accent(), 0x99));
                Label drawn = label(painter.font(), label, labelWidth, laid[index], small[index]);
                for (int line = 0; line < drawn.lines.size(); line++) {
                    painter.push();
                    painter.translate(left + TEXT_LEFT, rowY + 1 + (drawn.lines.size() == 1 ? (1 - drawn.scale) * 4 : line * Label.step(drawn.scale)));
                    painter.scale(drawn.scale);
                    painter.text(drawn.lines.get(line), 0, 0, ticked || waiting ? palette.muted() : palette.text());
                    painter.pop();
                }
                int height = Label.height(laid[index], small[index]);
                if (drawn.cut && painter.hovered(left + TEXT_LEFT, rowY, labelWidth, height)) painter.tooltip(wrapped(painter.font(), objective.label(book)));
                if (barred[index] && !ticked && !waiting) bar(painter, left + TEXT_LEFT, rowY + height, labelWidth, 2, track.progress()[index], palette.accent());
                rowY += rows[index];
            }
            if (scrolls) rowY = top + window;
            if (rewardHeight > 0) this.drawReward(painter, rowY - GAP + REWARD_GAP, pathX, previous, track);
        });
    }

    private int rewardHeight(Font font, int contentWidth) {
        Component heading = this.rewardHeading();
        Label headed = heading != null ? label(font, heading, contentWidth - REWARD_INSET, MOST_LINES, SMALL) : null;
        int height = headed != null ? Label.height(headed.lines.size(), headed.scale) : 0;
        if (this.givesItems()) height += (height > 0 ? SLOT_GAP : 0) + Grid.of(this.rewardSlots(), contentWidth - REWARD_INSET).height();
        return height;
    }

    private @Nullable Component rewardHeading() {
        if (this.givesItems()) return Component.translatableWithFallback("folios.quest.rewards", "Rewards:");
        return reward.map(QuestPageElement::rewardText).orElse(null);
    }

    private int rewardSlots() {
        ClientQuests.Quest sent = quest.map(ClientQuests::get).orElse(null);
        if (sent == null || sent.rewards() == null) return 0;
        int items = sent.rewards().has("items") ? sent.rewards().getAsJsonArray("items").size() : 0;
        boolean random = sent.rewards().has("random") && !sent.rewards().getAsJsonArray("random").isEmpty();
        return items + (random ? 1 : 0);
    }

    private record Grid(int columns, int rows, float pitch, int shown, int all) {
        static Grid of(int slots, int width) {
            int columns = Math.max(1, (width + SLOT_GAP) / (SLOT + SLOT_GAP));
            int rows = Math.max(1, Math.min(MOST_ROWS, (slots + columns - 1) / columns));
            int room = columns * rows;
            int shown = slots <= room ? slots : room - 1;
            float pitch = columns > 1 ? (width - SLOT) / (float) (columns - 1) : SLOT + SLOT_GAP;
            return new Grid(columns, rows, Math.min(pitch, SLOT + SLOT_GAP * 3), shown, slots);
        }

        int height() {
            return rows * SLOT + (rows - 1) * SLOT_GAP;
        }

        int x(int left, int index) {
            return left + Math.round((index % columns) * pitch);
        }

        int y(int top, int index) {
            return top + (index / columns) * (SLOT + SLOT_GAP);
        }
    }

    private void drawReward(FoliosPainter painter, int panelTop, int pathX, int previous, Track track) {
        FoliosPalette palette = painter.palette();
        int left = FoliosLayout.CONTENT_LEFT;
        int right = painter.contentRight();
        int height = this.rewardHeight(painter.font(), right - left);
        int panel = rewardPanel(height);
        int middle = panelTop + panel / 2;
        if (previous >= 0) path(painter, pathX, previous + 4, middle - REWARD_STOP / 2, track.complete(), palette);
        reward(painter, pathX, middle, track.complete(), track.claimed());
        boolean claimable = quest.isPresent() && this.givesItems() && track.complete() && !track.claimed();
        Shapes.panel(painter, left + FRAME_LEFT, panelTop, right, panelTop + panel);
        if (claimable) outline(painter, left + FRAME_LEFT, panelTop, right, panelTop + panel, palette.accent());
        int contentTop = panelTop + (panel - height) / 2 + 1;
        Component gift = this.rewardHeading();
        if (gift != null) {
            Label shown = label(painter.font(), gift, right - left - REWARD_INSET, MOST_LINES, SMALL);
            for (int line = 0; line < shown.lines.size(); line++) {
                painter.push();
                painter.translate(left + TEXT_LEFT, contentTop + (shown.lines.size() == 1 ? (1 - shown.scale) * 4 : line * Label.step(shown.scale)));
                painter.scale(shown.scale);
                painter.text(shown.lines.get(line), 0, 0, track.complete() ? palette.heading() : palette.muted());
                painter.pop();
            }
            if (shown.cut && painter.hovered(left + FRAME_LEFT, panelTop, right - left - FRAME_LEFT, panel)) painter.tooltip(wrapped(painter.font(), gift));
            contentTop += Label.height(shown.lines.size(), shown.scale) + SLOT_GAP;
        }
        if (!this.givesItems()) return;
        Component state = track.claimed() ? Component.translatableWithFallback("folios.quest.claimed", "Claimed")
                : track.complete() ? Component.translatableWithFallback("folios.quest.claim", "Press to claim")
                : Component.translatableWithFallback("folios.quest.claim_later", "Finish the quest to claim it");
        if (claimable) painter.action(left + FRAME_LEFT, panelTop, right - left - FRAME_LEFT, panel, state, () -> ClientQuests.claim(quest.get()));
        else if (!track.claimed() && painter.hovered(left + FRAME_LEFT, panelTop, right - left - FRAME_LEFT, panel)) painter.tooltip(state);
        this.slots(painter, left + TEXT_LEFT, contentTop - 1, right - FRAME_PAD, track.claimed());
    }

    private void slots(FoliosPainter painter, int x, int y, int end, boolean claimed) {
        QuestRewards rewards = quest.map(ClientQuests::rewards).orElse(null);
        if (rewards == null) return;
        List<ItemStack> items = rewards.items();
        int gifts = rewards.random().isEmpty() ? 0 : 1;
        Grid grid = Grid.of(items.size() + gifts, end - x);
        for (int index = 0; index < grid.shown(); index++) {
            int slotX = grid.x(x, index);
            int slotY = grid.y(y, index);
            if (index >= items.size()) {
                gift(painter, slotX, slotY, rewards);
                continue;
            }
            Shapes.slot(painter, slotX, slotY, SLOT);
            painter.push();
            painter.translate(slotX + 1, slotY + 1);
            painter.scale((SLOT - 2) / 16F);
            painter.item(items.get(index).copyWithCount(1), 0, 0, claimed ? 0x99FFFFFF : -1);
            painter.pop();
            if (items.get(index).getCount() > 1) count(painter, slotX, slotY, items.get(index).getCount());
        }
        if (grid.shown() == grid.all()) return;
        int moreX = grid.x(x, grid.shown());
        int moreY = grid.y(y, grid.shown());
        Shapes.slot(painter, moreX, moreY, SLOT);
        Component more = Component.literal("+" + (grid.all() - grid.shown()));
        painter.text(more, moreX + (SLOT - painter.font().width(more)) / 2 + 1, moreY + 4, painter.palette().muted());
        if (!painter.hovered(moreX, moreY, SLOT, SLOT)) return;
        List<Component> rest = new ArrayList<>();
        for (int index = grid.shown(); index < items.size(); index++) rest.add(count(items.get(index)));
        if (gifts > 0) rest.add(Component.translatableWithFallback("folios.quest.gift", "A random gift"));
        painter.tooltip(rest);
    }

    private static void gift(FoliosPainter painter, int x, int y, QuestRewards rewards) {
        Shapes.slot(painter, x, y, SLOT);
        int left = x + 2;
        int lid = y + 4;
        int body = lid + 3;
        int bottom = y + SLOT - 2;
        int red = GIFT_RED;
        int redDark = GIFT_RED_DARK;
        int redLight = GIFT_RED_LIGHT;
        int goldLight = 0xFFF4D27A;
        painter.fill(left + 1, bottom, left + 12, bottom + 1, 0x38000000);
        painter.fill(left + 1, body, left + 11, bottom, red);
        painter.fill(left + 9, body, left + 11, bottom, redDark);
        painter.fill(left + 1, bottom - 1, left + 11, bottom, redDark);
        painter.fill(left, lid, left + 12, body, red);
        painter.fill(left, lid, left + 12, lid + 1, redLight);
        painter.fill(left, body - 1, left + 12, body, redDark);
        painter.fill(left + 5, lid, left + 7, body, GOLD);
        painter.fill(left + 6, lid, left + 7, body, GOLD_RIM);
        painter.fill(left + 2, lid - 3, left + 5, lid, GOLD);
        painter.fill(left + 7, lid - 3, left + 10, lid, GOLD);
        painter.fill(left + 2, lid - 3, left + 4, lid - 2, goldLight);
        painter.fill(left + 7, lid - 3, left + 9, lid - 2, goldLight);
        painter.fill(left + 3, lid - 2, left + 4, lid - 1, GOLD_RIM);
        painter.fill(left + 8, lid - 2, left + 9, lid - 1, GOLD_RIM);
        painter.fill(left + 5, lid - 2, left + 7, lid, GOLD_RIM);
        question(painter, left + 4, body, redDark);
        if (!painter.hovered(x, y, SLOT, SLOT)) return;
        List<Component> lines = new ArrayList<>();
        lines.add(rewards.rolls() > 1
                ? Component.translatableWithFallback("folios.quest.gifts", "%s random gifts, each one of:", rewards.rolls())
                : Component.translatableWithFallback("folios.quest.gift_of", "A random gift, one of:"));
        for (QuestRewards.Choice choice : rewards.random()) lines.add(count(choice.stack()).copy().withStyle(ChatFormatting.GRAY));
        painter.tooltip(lines);
    }

    private static void question(FoliosPainter painter, int x, int y, int outline) {
        int[][] marks = {{1, 0}, {2, 0}, {0, 1}, {3, 1}, {3, 2}, {2, 3}, {1, 3}, {1, 5}};
        for (int[] mark : marks) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx != 0 || dy != 0) painter.fill(x + mark[0] + dx, y + mark[1] + dy, x + mark[0] + dx + 1, y + mark[1] + dy + 1, outline);
                }
            }
        }
        for (int[] mark : marks) painter.fill(x + mark[0], y + mark[1], x + mark[0] + 1, y + mark[1] + 1, 0xFFFFFFFF);
    }

    private static void count(FoliosPainter painter, int x, int y, int count) {
        Component text = Component.literal(String.valueOf(count));
        int textX = x + SLOT - painter.font().width(text);
        int textY = y + SLOT - 7;
        painter.text(text, textX + 1, textY + 1, 0xFF3F3F3F);
        painter.text(text, textX, textY, 0xFFFFFFFF);
    }

    private static Component count(ItemStack stack) {
        return stack.getCount() > 1 ? Component.literal(stack.getCount() + " × ").append(stack.getHoverName()) : stack.getHoverName();
    }

    private static void outline(FoliosPainter painter, int x0, int y0, int x1, int y1, int color) {
        painter.fill(x0 + 1, y0, x1 - 1, y0 + 1, color);
        painter.fill(x0 + 1, y1 - 1, x1 - 1, y1, color);
        painter.fill(x0, y0 + 1, x0 + 1, y1 - 1, color);
        painter.fill(x1 - 1, y0 + 1, x1, y1 - 1, color);
    }

    private static final int FRAME_LEFT = 9;
    private static final int FRAME_PAD = 3;
    private static final int TEXT_LEFT = FRAME_LEFT + FRAME_PAD;
    private static final int REWARD_INSET = TEXT_LEFT + FRAME_PAD;
    private static final int SCROLLBAR = 6;
    private static final int REWARD_STOP = 11;
    private static final int REWARD_GAP = 4;
    private static final int GOLD = 0xFFD9A63A;
    private static final int GOLD_RIM = 0xFF9C6F1C;
    private static final int GIFT_RED = 0xFFC0443A;
    private static final int GIFT_RED_DARK = 0xFF86281F;
    private static final int GIFT_RED_LIGHT = 0xFFDD6A5E;

    private enum Stop { DONE, NEXT, AHEAD }

    private static int rewardPanel(int textHeight) {
        return Math.max(REWARD_STOP + 2, textHeight + 3);
    }

    private static void path(FoliosPainter painter, int x, int from, int to, boolean walked, FoliosPalette palette) {
        if (to <= from) return;
        if (walked) {
            painter.fill(x, from, x + 1, to, palette.accent());
            return;
        }
        for (int y = from; y < to; y += 3) painter.fill(x, y, x + 1, Math.min(to, y + 2), palette.line());
    }

    private static void stop(FoliosPainter painter, int x, int y, Stop stop, boolean complete, FoliosPalette palette) {
        int done = complete ? 0xFF4CAF50 : palette.accent();
        switch (stop) {
            case DONE -> {
                orb(painter, x, y, 3, done);
                checkMark(painter, x, y, 0xFFFFFFFF, FoliosPalette.mix(done, 0xFF000000, 0.45F));
            }
            case NEXT -> {
                orb(painter, x, y, 3, palette.accent());
                round(painter, x, y, 2, palette.panel());
                painter.fill(x - 1, y - 1, x + 2, y + 2, palette.accent());
                painter.fill(x - 1, y - 1, x, y, FoliosPalette.mix(palette.accent(), 0xFFFFFFFF, 0.45F));
                painter.fill(x + 1, y + 1, x + 2, y + 2, FoliosPalette.mix(palette.accent(), 0xFF000000, 0.25F));
            }
            case AHEAD -> {
                orb(painter, x, y, 3, palette.line());
                round(painter, x, y, 2, palette.panel());
                painter.fill(x - 1, y - 2, x + 2, y - 1, FoliosPalette.mix(palette.panel(), palette.line(), 0.4F));
            }
        }
    }

    private static void orb(FoliosPainter painter, int x, int y, int radius, int color) {
        round(painter, x, y + 1, radius, 0x30000000);
        round(painter, x, y, radius, FoliosPalette.mix(color, 0xFF000000, 0.3F));
        round(painter, x, y, radius - 1, color);
        int light = FoliosPalette.mix(color, 0xFFFFFFFF, 0.45F);
        int shade = FoliosPalette.mix(color, 0xFF000000, 0.15F);
        int reach = radius - 2;
        painter.fill(x - reach, y - reach - 1, x, y - reach, light);
        painter.fill(x - reach - 1, y - reach, x - reach, y, light);
        painter.fill(x - reach, y - reach, x - reach + 1, y - reach + 1, FoliosPalette.mix(color, 0xFFFFFFFF, 0.25F));
        painter.fill(x + 1, y + reach + 1, x + reach + 1, y + reach + 2, shade);
        painter.fill(x + reach + 1, y + 1, x + reach + 2, y + reach + 1, shade);
    }

    private static void checkMark(FoliosPainter painter, int x, int y, int color, int shadow) {
        int[][] marks = {{-2, 0}, {-1, 1}, {0, 0}, {1, -1}, {2, -2}};
        for (int[] mark : marks) painter.fill(x + mark[0], y + mark[1] + 1, x + mark[0] + 1, y + mark[1] + 2, shadow);
        for (int[] mark : marks) painter.fill(x + mark[0], y + mark[1], x + mark[0] + 1, y + mark[1] + 1, color);
    }

    private static void reward(FoliosPainter painter, int x, int y, boolean complete, boolean claimed) {
        orb(painter, x, y, 5, complete ? FoliosPalette.mix(GOLD, 0xFFFFFFFF, 0.15F) : GOLD);
        if (claimed) {
            checkMark(painter, x, y, 0xFFFFF8E6, GOLD_RIM);
            return;
        }
        int ribbon = 0xFFFFF4D6;
        painter.fill(x - 2, y, x + 3, y + 3, GIFT_RED);
        painter.fill(x + 2, y, x + 3, y + 3, GIFT_RED_DARK);
        painter.fill(x - 3, y - 2, x + 4, y, GIFT_RED);
        painter.fill(x - 3, y - 2, x + 4, y - 1, GIFT_RED_LIGHT);
        painter.fill(x, y - 2, x + 1, y + 3, ribbon);
        painter.fill(x - 2, y - 4, x, y - 2, ribbon);
        painter.fill(x + 1, y - 4, x + 3, y - 2, ribbon);
        painter.fill(x - 1, y - 3, x, y - 2, GIFT_RED_DARK);
        painter.fill(x + 1, y - 3, x + 2, y - 2, GIFT_RED_DARK);
    }

    private static void round(FoliosPainter painter, int x, int y, int radius, int color) {
        int cut = radius >= 4 ? 2 : 1;
        for (int row = -radius; row <= radius; row++) {
            int edge = radius - Math.abs(row);
            int inset = Math.max(0, cut - edge);
            painter.fill(x - radius + inset, y + row, x + radius + 1 - inset, y + row + 1, color);
        }
    }

    private static int room(int[] laid, float[] small, boolean[] barred, int rewardHeight) {
        int room = HEAD - (laid.length == 0 ? 0 : GAP) + (rewardHeight > 0 ? REWARD_GAP + rewardPanel(rewardHeight) : 0);
        for (int index = 0; index < laid.length; index++) room += Label.height(laid[index], small[index]) + (barred[index] ? BAR_ROOM : 0) + GAP;
        return room;
    }

    private record Label(List<FormattedCharSequence> lines, float scale, boolean cut) {
        static int height(int lines, float small) {
            return lines <= 1 ? 10 : lines * step(small) + 1;
        }

        static int step(float scale) {
            return Math.round(10 * scale);
        }
    }

    private static Label label(Font font, Component label, int across, int most, float small) {
        int width = font.width(label);
        if (width <= across) return new Label(List.of(label.getVisualOrderText()), 1, false);
        if (width * small <= across || most <= 1) {
            float scale = Math.max(small, across / (float) width);
            if (width * scale <= across) return new Label(List.of(label.getVisualOrderText()), scale, false);
            return new Label(List.of(ellipsis(font, label, (int) (across / small))), small, true);
        }
        int room = (int) (across / small);
        List<FormattedText> parts = font.getSplitter().splitLines(label, room, Style.EMPTY);
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (int line = 0; line < Math.min(most, parts.size()); line++) {
            boolean cut = line == most - 1 && parts.size() > most;
            lines.add(cut ? ellipsis(font, FormattedText.composite(parts.get(line), FormattedText.of(" …")), room) : Language.getInstance().getVisualOrder(parts.get(line)));
        }
        return new Label(lines, small, parts.size() > most);
    }

    private static List<Component> wrapped(Font font, Component label) {
        List<Component> lines = new ArrayList<>();
        for (FormattedText part : font.getSplitter().splitLines(label, TOOLTIP_WIDTH, Style.EMPTY)) {
            MutableComponent line = Component.empty();
            part.visit((style, string) -> {
                line.append(Component.literal(string).withStyle(style.getColor() == null ? style.withColor(ChatFormatting.WHITE) : style));
                return Optional.empty();
            }, Style.EMPTY);
            lines.add(line);
        }
        return lines;
    }

    private static FormattedCharSequence ellipsis(Font font, FormattedText text, int across) {
        if (font.width(text) <= across) return Language.getInstance().getVisualOrder(text);
        return Language.getInstance().getVisualOrder(FormattedText.composite(font.substrByWidth(text, Math.max(0, across - font.width("…"))), FormattedText.of("…")));
    }

    public static void gilded(FoliosPainter painter, int x, int y, int width, float fill) {
        int accent = painter.palette().accent() | 0xFF000000;
        int rim = FoliosPalette.mix(0xFFE8C66A, accent, 0.35F);
        int y1 = y + 7;
        Sprites.draw(painter, Sprites.BAR_GILDED_BORDER, x, y, width + 1, 8, FoliosPalette.mix(rim, 0xFFFFFFFF, 0.25F));
        Sprites.draw(painter, Sprites.BAR_GILDED_GROOVE, x, y, width + 1, 8, Sprites.AS_IS);
        int inside = width - 2;
        int filled = Math.round(inside * Math.max(0, Math.min(1, fill)));
        int left = x + 1;
        int right = left + filled;
        boolean full = fill >= 1;
        for (int quarter = 1; quarter < 4; quarter++) tickLine(painter, left + inside * quarter / 4, y, y1 - 1, 0xFF161112);
        if (filled > 0) {
            int color = full ? 0xFF4CAF50 : FoliosPalette.mix(accent, 0xFFFFFFFF, 0.15F);
            fillStriped(painter, left, y + 1, filled, 5, color);
            for (int quarter = 1; quarter < 4; quarter++) notch(painter, left + inside * quarter / 4, y + 1, 5, left, right, color);
            if (!full && filled > 2) tip(painter, right - 1, y + 1, 5, color);
        }
        for (int quarter = 1; quarter < 4; quarter++) {
            int at = left + inside * quarter / 4;
            tick(painter, at, y, y1 - 1, FoliosPalette.mix(rim, 0xFFFFFFFF, at < right ? 0.45F : 0.38F));
        }
    }

    public static void stepped(FoliosPainter painter, int x, int y, int width, float fill, int steps, int color) {
        FoliosPalette palette = painter.palette();
        int filled = Math.round((width - 2) * Math.max(0, Math.min(1, fill)));
        int rim = FoliosPalette.mix(0xFFE8C66A, palette.accent() | 0xFF000000, 0.35F);
        int groove = 0xFF1E1719;
        frame(painter, x, y, width, 5, rim, groove);
        int inside = x + 1;
        int parts = Math.max(1, steps);
        boolean ticked = parts > 1 && (width - 2) / parts >= 4;
        if (ticked) {
            for (int part = 1; part < parts; part++) tickLine(painter, inside + Math.round((width - 2) * part / (float) parts), y, y + 4, groove);
        }
        if (filled > 0) {
            fillClean(painter, inside, y + 1, filled, color);
            if (ticked) {
                for (int part = 1; part < parts; part++) {
                    notch(painter, inside + Math.round((width - 2) * part / (float) parts), y + 1, 3, inside, inside + filled, color);
                }
            }
        }
        if (ticked) {
            for (int part = 1; part < parts; part++) {
                int at = inside + Math.round((width - 2) * part / (float) parts);
                boolean lit = at < inside + filled;
                tick(painter, at, y, y + 4, lit ? FoliosPalette.mix(FoliosPalette.mix(rim, color, 0.3F), 0xFFFFFFFF, 0.3F) : FoliosPalette.mix(rim, 0xFFFFFFFF, 0.3F));
            }
        }
        if (filled > 0 && filled < width - 2) tip(painter, inside + filled - 1, y + 1, 3, color);
        if (filled < width - 2) return;
        float sweep = (painter.time() % 3.5F) / 1.2F;
        if (sweep >= 1) return;
        int band = inside + Math.round((width + 4) * sweep) - 4;
        int from = Math.max(inside, band);
        int to = Math.min(inside + filled, band + 3);
        if (to > from) glint(painter, from, y + 1, to - from, 3);
    }

    private static void frame(FoliosPainter painter, int x, int y, int width, int height, int rim, int groove) {
        Sprites.draw(painter, Sprites.BAR_BORDER, x, y, width + 1, height + 1, FoliosPalette.mix(rim, 0xFFFFFFFF, 0.2F));
        Sprites.draw(painter, Sprites.BAR_GROOVE, x, y, width + 1, height + 1, FoliosPalette.mix(groove, 0xFFFFFFFF, 0.12F));
    }

    private static void tick(FoliosPainter painter, int x, int top, int foot, int color) {
        Sprites.draw(painter, Sprites.BAR_TICK, x, top, 1, foot - top + 1, color);
    }

    private static void tickLine(FoliosPainter painter, int x, int top, int foot, int groove) {
        int r = groove >> 16 & 0xFF, g = groove >> 8 & 0xFF, b = groove & 0xFF;
        boolean dark = (r * 299 + g * 587 + b * 114) / 1000 < 64;
        int line = dark ? FoliosPalette.mix(groove, 0xFFFFFFFF, 0.06F) : FoliosPalette.mix(groove, 0xFF000000, 0.07F);
        Sprites.draw(painter, Sprites.BAR_TICK_LINE, x, top + 1, 1, foot - top - 1, line);
    }

    private static void fillClean(FoliosPainter painter, int x, int y, int width, int color) {
        int body = FoliosPalette.mix(color, 0xFFFFFFFF, 0.15F);
        painter.fill(x, y, x + width, y + 1, FoliosPalette.mix(body, 0xFFFFFFFF, 0.35F));
        painter.fill(x, y + 1, x + width, y + 2, body);
        painter.fill(x, y + 2, x + width, y + 3, FoliosPalette.mix(body, 0xFF000000, 0.22F));
        for (int start = -2; start < width; start += 4) {
            for (int row = 0; row < 3; row++) {
                int column = start + (2 - row);
                if (column < 0 || column >= width) continue;
                painter.fill(x + column, y + row, x + column + 1, y + row + 1, FoliosPalette.alpha(0xFFFFFFFF, row == 0 ? 0x1C : 0x34));
            }
        }
    }

    private static void tip(FoliosPainter painter, int x, int y, int height, int color) {
        int lit = FoliosPalette.mix(color, 0xFFFFFFFF, 0.62F);
        painter.fill(x, y, x + 1, y + height, lit);
        painter.fill(x, y, x + 1, y + 1, FoliosPalette.mix(color, 0xFFFFFFFF, 0.82F));
    }

    private static void notch(FoliosPainter painter, int x, int y, int height, int from, int to, int color) {
        if (x <= from + 1 || x >= to - 2) return;
        for (int row = 0; row < height; row++) {
            float strength = height > 2 && (row == 0 || row == height - 1) ? 0.5F : 1;
            painter.fill(x, y + row, x + 1, y + row + 1, FoliosPalette.alpha(0xFF000000, Math.round(0x78 * strength)));
            painter.fill(x - 1, y + row, x, y + row + 1, FoliosPalette.alpha(0xFF000000, Math.round(0x26 * strength)));
            painter.fill(x + 1, y + row, x + 2, y + row + 1, FoliosPalette.alpha(0xFFFFFFFF, Math.round(0x50 * strength)));
        }
    }

    private static void glint(FoliosPainter painter, int x, int y, int width, int height) {
        Sprites.draw(painter, Sprites.BAR_GLINT, x, y, width, height, Sprites.AS_IS);
    }

    private static void fillStriped(FoliosPainter painter, int x, int y, int width, int height, int color) {
        Sprites.draw(painter, Sprites.BAR_FILL, x, y, width, height, FoliosPalette.mix(color, 0xFFFFFFFF, 0.42F));
    }

    public static void bar(FoliosPainter painter, int x, int y, int width, int height, float fill, int color) {
        FoliosPalette palette = painter.palette();
        int filled = Math.round(width * Math.max(0, Math.min(1, fill)));
        int track = FoliosPalette.mix(palette.panel(), palette.line(), 0.6F);
        if (height < 4) {
            slim(painter, x, y, width, fill, color);
            return;
        }
        int rim = FoliosPalette.mix(palette.line(), palette.text(), 0.45F);
        frame(painter, x - 1, y - 1, width + 2, height + 2, rim, FoliosPalette.mix(track, palette.text(), 0.08F));
        if (filled <= 0) return;
        fillStriped(painter, x, y, filled, height, color);
        if (filled < width) tip(painter, x + filled - 1, y, height, color);
    }

    private static void slim(FoliosPainter painter, int x, int y, int width, float fill, int color) {
        FoliosPalette palette = painter.palette();
        int groove = FoliosPalette.mix(palette.panel(), palette.line(), 0.55F);
        int rim = FoliosPalette.mix(palette.line(), palette.text(), 0.3F);
        Sprites.draw(painter, Sprites.BAR_SLIM_BORDER, x, y, width, 5, rim);
        Sprites.draw(painter, Sprites.BAR_SLIM_GROOVE, x, y, width, 5, groove);
        int filled = Math.round((width - 2) * Math.max(0, Math.min(1, fill)));
        if (filled <= 0) return;
        int left = x + 1;
        Sprites.draw(painter, Sprites.BAR_SLIM_FILL, left, y + 1, filled, 2, FoliosPalette.mix(color, 0xFFFFFFFF, 0.38F));
        if (filled < width - 2) tip(painter, left + filled - 1, y + 1, 2, color);
    }

    private static Component rewardText(String reward) {
        return Component.translatableWithFallback("folios.quest.reward", "Reward: %s", FoliosText.inline(reward));
    }

    @Override
    public String searchText() {
        return FoliosText.resolve(title.orElse("")) + " " + FoliosText.resolve(text);
    }
}
