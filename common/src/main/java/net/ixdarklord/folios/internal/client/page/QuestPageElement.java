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
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// A quest: how far along it is, then a path of its objectives leading down to what it gives once done. Drawn live: it
// moves as the player plays, the book open or not. Its objectives are the book's own, followed by the client; or, for
// one naming a server quest ("quest"), the server's, followed and rewarded by the server.
public record QuestPageElement(List<FoliosObjective> objectives, Optional<String> title, String text, Optional<String> reward, boolean inOrder,
                               Optional<Identifier> quest, Optional<String> locked) implements FoliosPageElement {
    public static final MapCodec<QuestPageElement> CODEC = RecordCodecBuilder.<QuestPageElement>mapCodec(instance -> instance.group(
            FoliosObjective.CODEC.listOf(0, 32).optionalFieldOf("objectives", List.of()).forGetter(QuestPageElement::objectives),
            FoliosText.CODEC.optionalFieldOf("title").forGetter(QuestPageElement::title),
            FoliosText.CODEC.optionalFieldOf("text", "").forGetter(QuestPageElement::text),
            FoliosText.CODEC.optionalFieldOf("reward").forGetter(QuestPageElement::reward),
            // Each objective counts only once those before it are done.
            Codec.BOOL.optionalFieldOf("in_order", false).forGetter(QuestPageElement::inOrder),
            // A quest of the server's (data/<namespace>/folios/quests): its objectives and rewards are the server's.
            Identifier.CODEC.optionalFieldOf("quest").forGetter(QuestPageElement::quest),
            // What a server quest says while it is locked, in place of "Not yet open".
            FoliosText.CODEC.optionalFieldOf("locked").forGetter(QuestPageElement::locked)
    ).apply(instance, (objectives, title, text, reward, inOrder, quest, locked) ->
            new QuestPageElement(inOrder ? FoliosObjective.inOrder(objectives) : objectives, title, text, reward, inOrder, quest, locked))
    ).validate(element -> element.objectives.isEmpty() && element.quest.isEmpty()
            ? DataResult.error(() -> "A quest has \"objectives\", or names a server \"quest\"") : DataResult.success(element));

    /** The line saying how far along it is, above the path. */
    private static final int HEAD = 12;
    /** Under an objective's label, the room for its bar (one with a count or an amount), and before the next. */
    private static final int BAR_ROOM = 5;
    private static final int GAP = 3;
    /**
     * The smallest a label is drawn, before it is split over lines, and the most lines it takes; and smaller yet, on
     * closer lines, in a quest that wouldn't otherwise fit on a page.
     */
    private static final float SMALL = 0.75F;
    private static final float SMALLER = 0.65F;
    private static final int MOST_LINES = 3;
    /** How wide a cut-short label's tooltip is at most. */
    private static final int TOOLTIP_WIDTH = 170;
    /** A reward's item slots, and the room between them. */
    private static final int SLOT = 16;
    private static final int SLOT_GAP = 2;
    /** The most rows of slots a reward shows. */
    private static final int MOST_ROWS = 3;

    @Override
    public List<FoliosObjective> objectives() {
        return this.shown();
    }

    // The objectives shown: the book's own, or the server quest's as the server sent it (none before it has).
    private List<FoliosObjective> shown() {
        if (quest.isEmpty()) return objectives;
        ClientQuests.Quest sent = ClientQuests.get(quest.get());
        return sent == null ? List.of() : sent.inOrder() ? FoliosObjective.inOrder(sent.objectives()) : sent.objectives();
    }

    // What a server quest says in its place while it can't be shown: not sent (a server without it, or without Folios),
    // or locked (what opens it not yet met). Null to show it.
    private @Nullable Component shut() {
        if (quest.isEmpty()) return null;
        if (ClientQuests.get(quest.get()) == null) return Component.translatableWithFallback("folios.quest.server", "This quest is kept by the server");
        if (!ClientQuests.progress(quest.get()).isOpen()) {
            return locked.map(FoliosText::inline).orElseGet(() -> Component.translatableWithFallback("folios.quest.locked", "Not yet open"));
        }
        return null;
    }

    // Whether the server quest gives items, so the reward keeps a row for its slots.
    private boolean givesItems() {
        ClientQuests.Quest sent = quest.map(ClientQuests::get).orElse(null);
        return sent != null && sent.rewards() != null;
    }

    /**
     * How far along each objective is, and the quest as a whole: worked out by the client for a book's own quest, or as
     * the server found it for a server quest.
     */
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
        // The reward set out as a label is: smaller, then over more lines, if it doesn't fit beside the path; and a
        // row of slots under it for the items a server quest gives.
        int rewardHeight = this.rewardHeight(layout.font(), layout.contentWidth());
        // A label too wide for its line is drawn smaller, then over more lines, its row taller for them.
        int across = layout.contentWidth() - FRAME_LEFT - FRAME_PAD * 2;
        int[] laid = new int[shown.size()];
        float[] small = new float[shown.size()];
        boolean[] barred = new boolean[shown.size()];
        for (int index = 0; index < laid.length; index++) {
            small[index] = SMALL;
            laid[index] = label(layout.font(), shown.get(index).label(book), across, MOST_LINES, SMALL).lines.size();
            // Only one counted or measured has a bar; one done or not done keeps no room for it.
            barred[index] = !shown.get(index).isAllOrNothing();
        }
        // A quest is never split over pages. One too tall for a page draws its labels smaller and closer, the last
        // first; if it still doesn't fit, its objectives scroll, as many shown at once as the page holds, under the
        // line saying how far along it is and over the reward, which stay where they are.
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
            // Back to the larger labels, beside a scrollbar.
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
        // The room the objectives have when they scroll, and the first that can be at the top with the last in view.
        int window = scrolls ? fit - HEAD + GAP - (rewardHeight > 0 ? REWARD_GAP + rewardPanel(rewardHeight) : 0) : total;
        int lastFirst = rows.length;
        for (int below = 0; lastFirst > 0 && below + rows[lastFirst - 1] <= window; ) below += rows[--lastFirst];
        int scrollTo = Math.max(0, Math.min(lastFirst, rows.length - 1));
        int allRows = Math.max(1, total);
        int[] first = {0};
        // How far the scrollbar has been dragged since the objectives last moved, in pixels of their rows.
        float[] dragged = {0};
        // A server quest shows only a line until the server has sent it, and while it is locked.
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
            // Each objective's stop, and whether the path up to it has been walked: every objective to it is done.
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
                // The scrollbar (the book's scrollbar sprites): its thumb as long as the share of the objectives in
                // view, and dragged to scroll them, the objective nearest where it is dragged to coming to the top.
                int trackHeight = window - GAP;
                int[] starts = new int[rows.length];
                for (int index = 1; index < rows.length; index++) starts[index] = starts[index - 1] + rows[index - 1];
                int barX = right - Shapes.SCROLLBAR;
                boolean held = painter.drag(barX - 2, top, Shapes.SCROLLBAR + 4, trackHeight, (dx, dy) -> {
                    dragged[0] += dy * allRows / (float) trackHeight;
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
                int thumb = Math.max(8, trackHeight * window / allRows);
                int thumbTop = top + Math.min(trackHeight - thumb, trackHeight * starts[from] / allRows);
                Shapes.scrollbar(painter, barX, top, trackHeight, thumbTop, thumb, held || painter.hovered(barX - 2, top, Shapes.SCROLLBAR + 4, trackHeight));
            }
            // The path: a stop for each objective, joined by a line that is drawn in as far as the player has come,
            // and dotted beyond; it leads down to the reward, the last and largest stop. Scrolled, it runs on in from
            // the top and out at the bottom.
            int pathX = left + 3;
            int rowY = top;
            int previous = from > 0 ? top - 2 : -1;
            for (int index = from; index < shown.size(); index++) {
                if (scrolls && rowY - top + rows[index] > window) break;
                FoliosObjective objective = shown.get(index);
                boolean ticked = track.done(index);
                // One waiting on those before it (an "in_order" quest) is shown faded, not yet to be worked on.
                boolean waiting = track.waiting()[index];
                int stopY = rowY + 5;
                if (previous >= 0) path(painter, pathX, previous + (previous == top - 2 ? 0 : 4), stopY - 4, walked[index], palette);
                stop(painter, pathX, stopY, stops[index], track.complete(), palette);
                previous = stopY;
                Component label = objective.label(book);
                if (ticked) label = label.copy().withStyle(ChatFormatting.STRIKETHROUGH);
                // As many lines as were laid out for it at most; one line drawn smaller sits in the middle of its line.
                // Its frame, from just above its label to the room under it; the one to work on next ringed.
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
                // Cut short, it is shown whole when hovered.
                if (drawn.cut && painter.hovered(left + TEXT_LEFT, rowY, labelWidth, height)) painter.tooltip(wrapped(painter.font(), objective.label(book)));
                if (barred[index] && !ticked && !waiting) bar(painter, left + TEXT_LEFT, rowY + height, labelWidth, 2, track.progress()[index], palette.accent());
                rowY += rows[index];
            }
            // Scrolled, the reward stays at the foot of the objectives' room.
            if (scrolls) rowY = top + window;
            if (rewardHeight > 0) this.drawReward(painter, rowY - GAP + REWARD_GAP, pathX, previous, track);
        });
    }

    // ---- The reward ----

    // How tall the reward's text and slots are; 0 for a quest that gives nothing.
    private int rewardHeight(Font font, int contentWidth) {
        Component heading = this.rewardHeading();
        Label headed = heading != null ? label(font, heading, contentWidth - REWARD_INSET, MOST_LINES, SMALL) : null;
        int height = headed != null ? Label.height(headed.lines.size(), headed.scale) : 0;
        if (this.givesItems()) height += (height > 0 ? SLOT_GAP : 0) + Grid.of(this.rewardSlots(), contentWidth - REWARD_INSET).height();
        return height;
    }

    // What the reward's panel says at its top: "Rewards:" over the items a server quest gives, else the reward as written.
    private @Nullable Component rewardHeading() {
        if (this.givesItems()) return Component.translatableWithFallback("folios.quest.rewards", "Rewards:");
        return reward.map(QuestPageElement::rewardText).orElse(null);
    }

    // How many slots the server quest's reward shows: one for each item, and one for its random gifts.
    private int rewardSlots() {
        ClientQuests.Quest sent = quest.map(ClientQuests::get).orElse(null);
        if (sent == null || sent.rewards() == null) return 0;
        int items = sent.rewards().has("items") ? sent.rewards().getAsJsonArray("items").size() : 0;
        boolean random = sent.rewards().has("random") && !sent.rewards().getAsJsonArray("random").isEmpty();
        return items + (random ? 1 : 0);
    }

    /**
     * The reward's slots set out in a grid as wide as the panel: as many columns as fit, spread evenly across it, and
     * as many rows as they take, up to {@link #MOST_ROWS}; past that the last slot says how many more there are.
     */
    private record Grid(int columns, int rows, float pitch, int shown, int all) {
        static Grid of(int slots, int width) {
            int columns = Math.max(1, (width + SLOT_GAP) / (SLOT + SLOT_GAP));
            int rows = Math.max(1, Math.min(MOST_ROWS, (slots + columns - 1) / columns));
            int room = columns * rows;
            int shown = slots <= room ? slots : room - 1;
            // A row of one is left where it starts; more are spread to the panel's width.
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

    // The reward: its stop at the end of the path, and its panel beside it with its text and the items it gives.
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
        // Pressed once done, the server gives it; before, it says what it waits for.
        Component state = track.claimed() ? Component.translatableWithFallback("folios.quest.claimed", "Claimed")
                : track.complete() ? Component.translatableWithFallback("folios.quest.claim", "Press to claim")
                : Component.translatableWithFallback("folios.quest.claim_later", "Finish the quest to claim it");
        if (claimable) painter.action(left + FRAME_LEFT, panelTop, right - left - FRAME_LEFT, panel, state, () -> ClientQuests.claim(quest.get()));
        else if (!track.claimed() && painter.hovered(left + FRAME_LEFT, panelTop, right - left - FRAME_LEFT, panel)) painter.tooltip(state);
        this.slots(painter, left + TEXT_LEFT, contentTop - 1, right - FRAME_PAD, track.claimed());
    }

    // The reward's items in a grid of slots, each with its count, then a gift with a question mark for its random draws;
    // past the grid's room the last slot says how many more there are, with a tooltip naming them.
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

    // A random gift's slot: a red box tied with a gold ribbon and a bow, shaded and lit, a white question mark outlined
    // on its front; what it could be when hovered.
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
        // Its shadow on the slot, then the body, darker down its right side and along its foot.
        painter.fill(left + 1, bottom, left + 12, bottom + 1, 0x38000000);
        painter.fill(left + 1, body, left + 11, bottom, red);
        painter.fill(left + 9, body, left + 11, bottom, redDark);
        painter.fill(left + 1, bottom - 1, left + 11, bottom, redDark);
        // The lid, wider than the box and lit along its top.
        painter.fill(left, lid, left + 12, body, red);
        painter.fill(left, lid, left + 12, lid + 1, redLight);
        painter.fill(left, body - 1, left + 12, body, redDark);
        // The ribbon over the lid, and the bow: two loops lit at their tops, a knot between them.
        painter.fill(left + 5, lid, left + 7, body, GOLD);
        painter.fill(left + 6, lid, left + 7, body, GOLD_RIM);
        painter.fill(left + 2, lid - 3, left + 5, lid, GOLD);
        painter.fill(left + 7, lid - 3, left + 10, lid, GOLD);
        painter.fill(left + 2, lid - 3, left + 4, lid - 2, goldLight);
        painter.fill(left + 7, lid - 3, left + 9, lid - 2, goldLight);
        painter.fill(left + 3, lid - 2, left + 4, lid - 1, GOLD_RIM);
        painter.fill(left + 8, lid - 2, left + 9, lid - 1, GOLD_RIM);
        painter.fill(left + 5, lid - 2, left + 7, lid, GOLD_RIM);
        // The question mark on its front, outlined in the box's dark red so it reads on it.
        question(painter, left + 4, body, redDark);
        if (!painter.hovered(x, y, SLOT, SLOT)) return;
        List<Component> lines = new ArrayList<>();
        lines.add(rewards.rolls() > 1
                ? Component.translatableWithFallback("folios.quest.gifts", "%s random gifts, each one of:", rewards.rolls())
                : Component.translatableWithFallback("folios.quest.gift_of", "A random gift, one of:"));
        for (QuestRewards.Choice choice : rewards.random()) lines.add(count(choice.stack()).copy().withStyle(ChatFormatting.GRAY));
        painter.tooltip(lines);
    }

    // A question mark 4 pixels wide and 6 tall from (x, y), white, outlined all round in a colour.
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

    // A stack's count at the lower right of its slot, white with a shadow as the game's are.
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

    // A one-pixel frame round a box, in a colour: a reward ready to be claimed.
    private static void outline(FoliosPainter painter, int x0, int y0, int x1, int y1, int color) {
        painter.fill(x0 + 1, y0, x1 - 1, y0 + 1, color);
        painter.fill(x0 + 1, y1 - 1, x1 - 1, y1, color);
        painter.fill(x0, y0 + 1, x0 + 1, y1 - 1, color);
        painter.fill(x1 - 1, y0 + 1, x1, y1 - 1, color);
    }

    /** How far in the reward's text is from the content's left: past the path, and the panel's edge. */
    /** Where an objective's frame starts, past the path, how far in its text is from the frame's edges, and so its text. */
    private static final int FRAME_LEFT = 9;
    private static final int FRAME_PAD = 3;
    private static final int TEXT_LEFT = FRAME_LEFT + FRAME_PAD;
    private static final int REWARD_INSET = TEXT_LEFT + FRAME_PAD;
    /** The room a scrollbar takes at the right of objectives that scroll. */
    private static final int SCROLLBAR = 6;
    /** The reward's stop, and the room above its panel. */
    private static final int REWARD_STOP = 11;
    private static final int REWARD_GAP = 4;
    private static final int GOLD = 0xFFD9A63A;
    private static final int GOLD_RIM = 0xFF9C6F1C;
    private static final int GIFT_RED = 0xFFC0443A;
    private static final int GIFT_RED_DARK = 0xFF86281F;
    private static final int GIFT_RED_LIGHT = 0xFFDD6A5E;

    private enum Stop { DONE, NEXT, AHEAD }

    // The reward's panel round text so tall: a little room above and below it, and never shorter than its stop.
    private static int rewardPanel(int textHeight) {
        return Math.max(REWARD_STOP + 2, textHeight + 3);
    }

    // The line between two stops, a pixel wide down x: in the book's colour as far as the player has come, else dotted.
    private static void path(FoliosPainter painter, int x, int from, int to, boolean walked, FoliosPalette palette) {
        if (to <= from) return;
        if (walked) {
            painter.fill(x, from, x + 1, to, palette.accent());
            return;
        }
        for (int y = from; y < to; y += 3) painter.fill(x, y, x + 1, Math.min(to, y + 2), palette.line());
    }

    // An objective's stop, an orb 7 pixels across centred on (x, y): filled and ticked once done, ringed in the book's
    // colour with a dot for the one to work on next, and a quiet ring for those ahead.
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
                // The dot, lit from above like the orb round it.
                painter.fill(x - 1, y - 1, x + 2, y + 2, palette.accent());
                painter.fill(x - 1, y - 1, x, y, FoliosPalette.mix(palette.accent(), 0xFFFFFFFF, 0.45F));
                painter.fill(x + 1, y + 1, x + 2, y + 2, FoliosPalette.mix(palette.accent(), 0xFF000000, 0.25F));
            }
            case AHEAD -> {
                orb(painter, x, y, 3, palette.line());
                round(painter, x, y, 2, palette.panel());
                // Sunk into the page: a shade along its inner top.
                painter.fill(x - 1, y - 2, x + 2, y - 1, FoliosPalette.mix(palette.panel(), palette.line(), 0.4F));
            }
        }
    }

    // A small orb: a soft shadow under it, a darker rim, its body, lit at its upper left and shaded at its lower right.
    private static void orb(FoliosPainter painter, int x, int y, int radius, int color) {
        round(painter, x, y + 1, radius, 0x30000000);
        round(painter, x, y, radius, FoliosPalette.mix(color, 0xFF000000, 0.3F));
        round(painter, x, y, radius - 1, color);
        int light = FoliosPalette.mix(color, 0xFFFFFFFF, 0.45F);
        int shade = FoliosPalette.mix(color, 0xFF000000, 0.15F);
        int reach = radius - 2;
        // The light: a short arc at the upper left, brightest at its middle.
        painter.fill(x - reach, y - reach - 1, x, y - reach, light);
        painter.fill(x - reach - 1, y - reach, x - reach, y, light);
        painter.fill(x - reach, y - reach, x - reach + 1, y - reach + 1, FoliosPalette.mix(color, 0xFFFFFFFF, 0.25F));
        // The shade: the same at the lower right.
        painter.fill(x + 1, y + reach + 1, x + reach + 1, y + reach + 2, shade);
        painter.fill(x + reach + 1, y + 1, x + reach + 2, y + reach + 1, shade);
    }

    // A tick centred on (x, y), with a shadow a pixel under it so it stands off what it is drawn on.
    private static void checkMark(FoliosPainter painter, int x, int y, int color, int shadow) {
        int[][] marks = {{-2, 0}, {-1, 1}, {0, 0}, {1, -1}, {2, -2}};
        for (int[] mark : marks) painter.fill(x + mark[0], y + mark[1] + 1, x + mark[0] + 1, y + mark[1] + 2, shadow);
        for (int[] mark : marks) painter.fill(x + mark[0], y + mark[1], x + mark[0] + 1, y + mark[1] + 1, color);
    }

    // The reward's stop: a gold orb 11 pixels across with a gift in it, or a tick once the reward is given.
    private static void reward(FoliosPainter painter, int x, int y, boolean complete, boolean claimed) {
        orb(painter, x, y, 5, complete ? FoliosPalette.mix(GOLD, 0xFFFFFFFF, 0.15F) : GOLD);
        if (claimed) {
            checkMark(painter, x, y, 0xFFFFF8E6, GOLD_RIM);
            return;
        }
        // A small red gift, as the random gift's slot shows one: its box, its wider lid, a cream ribbon down the
        // middle and a bow on top, so it stands out on the gold.
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

    // A round spot `radius` pixels out from (x, y) every way, its corners cut so it reads round at so few pixels.
    private static void round(FoliosPainter painter, int x, int y, int radius, int color) {
        int cut = radius >= 4 ? 2 : 1;
        for (int row = -radius; row <= radius; row++) {
            int edge = radius - Math.abs(row);
            int inset = Math.max(0, cut - edge);
            painter.fill(x - radius + inset, y + row, x + radius + 1 - inset, y + row + 1, color);
        }
    }

    // How tall a quest is: its bar, each objective's row (taller for a label over more lines), and the reward's lines.
    private static int room(int[] laid, float[] small, boolean[] barred, int rewardHeight) {
        int room = HEAD - (laid.length == 0 ? 0 : GAP) + (rewardHeight > 0 ? REWARD_GAP + rewardPanel(rewardHeight) : 0);
        for (int index = 0; index < laid.length; index++) room += Label.height(laid[index], small[index]) + (barred[index] ? BAR_ROOM : 0) + GAP;
        return room;
    }

    /** An objective's label as drawn: its lines, the scale they are drawn at, and whether it was cut short. */
    private record Label(List<FormattedCharSequence> lines, float scale, boolean cut) {
        /** From the top of a label of so many lines at so small to its bar: 10 for one, at any scale, else a line's step each. */
        static int height(int lines, float small) {
            return lines <= 1 ? 10 : lines * step(small) + 1;
        }

        /** From one line of a label to the next, drawn at a scale: 8 at three quarters, 7 at the smallest. */
        static int step(float scale) {
            return Math.round(10 * scale);
        }
    }

    // A label in `across`: as it is if it fits; else smaller, down to SMALL; else at SMALL over up to `most` lines, the
    // last cut short with an ellipsis if even they aren't enough.
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

    // A label set out in lines for a tooltip.
    private static List<Component> wrapped(Font font, Component label) {
        List<Component> lines = new ArrayList<>();
        for (FormattedText part : font.getSplitter().splitLines(label, TOOLTIP_WIDTH, Style.EMPTY)) {
            MutableComponent line = Component.empty();
            part.visit((style, string) -> {
                // White unless coloured: a tooltip greys its later lines, which here go on the same text.
                line.append(Component.literal(string).withStyle(style.getColor() == null ? style.withColor(ChatFormatting.WHITE) : style));
                return Optional.empty();
            }, Style.EMPTY);
            lines.add(line);
        }
        return lines;
    }

    // Text cut short with an ellipsis to fit in `across`.
    private static FormattedCharSequence ellipsis(Font font, FormattedText text, int across) {
        if (font.width(text) <= across) return Language.getInstance().getVisualOrder(text);
        return Language.getInstance().getVisualOrder(FormattedText.composite(font.substrByWidth(text, Math.max(0, across - font.width("…"))), FormattedText.of("…")));
    }

    /**
     * The mastery bar, 7 pixels tall from (x, y), kin to the build guide's but its own: a soft shadow under it and to its
     * right; a gilt rim, lighter along its top and darker along its foot, its corners rounded off and nothing standing
     * past it; a dark groove with a faint light line along its floor; the filled length in the book's colour (green
     * once full), lit along its top, shaded along its foot and banded with fine slanting stripes, a glint at its end;
     * and quarters marked by gilt dividers inside the groove.
     */
    public static void gilded(FoliosPainter painter, int x, int y, int width, float fill) {
        int accent = painter.palette().accent() | 0xFF000000;
        int rim = FoliosPalette.mix(0xFFE8C66A, accent, 0.35F);
        int y1 = y + 7;
        // The gilded frame sprite: its shadow, the rim lit and shaded, the groove dark under it.
        Sprites.draw(painter, Sprites.BAR_GILDED_BORDER, x, y, width + 1, 8, FoliosPalette.mix(rim, 0xFFFFFFFF, 0.25F));
        Sprites.draw(painter, Sprites.BAR_GILDED_GROOVE, x, y, width + 1, 8, Sprites.AS_IS);
        int inside = width - 2;
        int filled = Math.round(inside * Math.max(0, Math.min(1, fill)));
        int left = x + 1;
        int right = left + filled;
        boolean full = fill >= 1;
        // The quarters' lines first, in the groove: the fill covers them where it reaches.
        for (int quarter = 1; quarter < 4; quarter++) tickLine(painter, left + inside * quarter / 4, y, y1 - 1, 0xFF161112);
        if (filled > 0) {
            int color = full ? 0xFF4CAF50 : FoliosPalette.mix(accent, 0xFFFFFFFF, 0.15F);
            fillStriped(painter, left, y + 1, filled, 5, color);
            if (!full && filled > 2) glint(painter, right - 1, y + 1, 1, 5);
        }
        // The quarters: small gilt ticks in the rim above and below, brighter over what is filled; the groove stays
        // one smooth run.
        for (int quarter = 1; quarter < 4; quarter++) {
            int at = left + inside * quarter / 4;
            // A shade past the rim's lit top (the gilt a quarter lighter), so the marks read without standing out.
            tick(painter, at, y, y1 - 1, FoliosPalette.mix(rim, 0xFFFFFFFF, at < right ? 0.45F : 0.38F));
        }
    }

    /**
     * A bar of {@code steps} parts, as a category's entries or a quest's objectives, 5 pixels tall from (x, y) and a
     * pixel of shadow under it, drawn as the mastery bar is: a rim with its corners left out, lit along its top and left
     * and shaded along its foot and right; a groove sunk under it, a faint light along its floor; the fill lit along
     * its top, shaded along its foot and crossed by fine slanting stripes, with a glint at its end. Where parts meet,
     * small ticks in the rim. Once full it shows {@code color} with a light sweeping across it now and then.
     */
    public static void stepped(FoliosPainter painter, int x, int y, int width, float fill, int steps, int color) {
        FoliosPalette palette = painter.palette();
        int filled = Math.round((width - 2) * Math.max(0, Math.min(1, fill)));
        int rim = FoliosPalette.mix(palette.line(), palette.text(), 0.35F);
        int groove = FoliosPalette.mix(palette.panel(), palette.line(), 0.55F);
        frame(painter, x, y, width, 5, rim, groove);
        int inside = x + 1;
        // Where one part ends and the next begins: a small tick in the rim above and below, lit over what is
        // filled, a line between them in the groove under the fill. Left out when the parts are too close.
        int parts = Math.max(1, steps);
        boolean ticked = parts > 1 && (width - 2) / parts >= 4;
        if (ticked) {
            for (int part = 1; part < parts; part++) tickLine(painter, inside + Math.round((width - 2) * part / (float) parts), y, y + 4, groove);
        }
        if (filled > 0) fillStriped(painter, inside, y + 1, filled, 3, color);
        if (ticked) {
            for (int part = 1; part < parts; part++) {
                int at = inside + Math.round((width - 2) * part / (float) parts);
                boolean lit = at < inside + filled;
                // The rim's own tone a shade lighter, warmed towards the fill's colour over what is filled.
                tick(painter, at, y, y + 4, lit ? FoliosPalette.mix(FoliosPalette.mix(rim, color, 0.3F), 0xFFFFFFFF, 0.3F) : FoliosPalette.mix(rim, 0xFFFFFFFF, 0.3F));
            }
        }
        if (filled > 0 && filled < width - 2) glint(painter, inside + filled - 1, y + 1, 1, 3);
        if (filled < width - 2) return;
        // Full: a band of light crossing it every few seconds.
        float sweep = (painter.time() % 3.5F) / 1.2F;
        if (sweep >= 1) return;
        int band = inside + Math.round((width + 4) * sweep) - 4;
        int from = Math.max(inside, band);
        int to = Math.min(inside + filled, band + 3);
        if (to > from) glint(painter, from, y + 1, to - from, 3);
    }

    // A bar's frame, (x, y) to (x + width, y + height): a pixel of shadow under and to the right of it (its outer
    // corner left out, so it rounds as the rim does); a rim with its
    // corners left out, lit along its top and left and shaded along its foot and right; and the groove inside, sunk
    // under the rim's top and lit faintly along its floor.
    // A bar's frame and the pixel of shadow beyond it: the border sprite (its rim lit and shaded, the shadow painted
    // in) in the rim's colour and the groove sprite in the groove's, each tinted a shade lighter as their shading is
    // painted at or under the tint.
    private static void frame(FoliosPainter painter, int x, int y, int width, int height, int rim, int groove) {
        Sprites.draw(painter, Sprites.BAR_BORDER, x, y, width + 1, height + 1, FoliosPalette.mix(rim, 0xFFFFFFFF, 0.2F));
        Sprites.draw(painter, Sprites.BAR_GROOVE, x, y, width + 1, height + 1, FoliosPalette.mix(groove, 0xFFFFFFFF, 0.12F));
    }

    // A tick in a bar's rim where its parts meet: at the rim's top and its foot, the foot a shade darker.
    private static void tick(FoliosPainter painter, int x, int top, int foot, int color) {
        Sprites.draw(painter, Sprites.BAR_TICK, x, top, 1, foot - top + 1, color);
    }

    // The line in the groove under a tick, joining its foot mark to its top mark; drawn before the
    // fill, which covers it. The groove's own tone, only just dimmed (on a groove too dark to dim, just lit), so it
    // shows faintly without blending into it.
    private static void tickLine(FoliosPainter painter, int x, int top, int foot, int groove) {
        int r = groove >> 16 & 0xFF, g = groove >> 8 & 0xFF, b = groove & 0xFF;
        boolean dark = (r * 299 + g * 587 + b * 114) / 1000 < 64;
        int line = dark ? FoliosPalette.mix(groove, 0xFFFFFFFF, 0.06F) : FoliosPalette.mix(groove, 0xFF000000, 0.07F);
        Sprites.draw(painter, Sprites.BAR_TICK_LINE, x, top + 1, 1, foot - top - 1, line);
    }

    // A glint of light over a fill: its leading edge, or the band sweeping across a full bar.
    private static void glint(FoliosPainter painter, int x, int y, int width, int height) {
        Sprites.draw(painter, Sprites.BAR_GLINT, x, y, width, height, Sprites.AS_IS);
    }

    // A bar's fill, (x, y) {@code width} by {@code height}: lit along its top, shaded along its foot, fine slanting
    // stripes a shade lighter across what lies between.
    // The fill sprite (its light top, shaded foot and stripes painted in, the stripes repeated), tinted a little
    // lighter than the colour, as its body is painted under the tint.
    private static void fillStriped(FoliosPainter painter, int x, int y, int width, int height, int color) {
        Sprites.draw(painter, Sprites.BAR_FILL, x, y, width, height, FoliosPalette.mix(color, 0xFFFFFFFF, 0.42F));
    }

    /**
     * A bar of a given fill (0 to 1) in a colour, drawn as pixel art. Four or more pixels tall: framed as the mastery
     * bar is (a shadow, a rim lit above and shaded below, a sunk track), the fill striped, lit along its top and shaded
     * along its foot, with a bright cap at its leading edge. Thinner: a slim rounded groove with its fill in it (slim()).
     */
    public static void bar(FoliosPainter painter, int x, int y, int width, int height, float fill, int color) {
        FoliosPalette palette = painter.palette();
        int filled = Math.round(width * Math.max(0, Math.min(1, fill)));
        int track = FoliosPalette.mix(palette.panel(), palette.line(), 0.6F);
        if (height < 4) {
            slim(painter, x, y, width, fill, color);
            return;
        }
        // In the mastery bar's manner: its frame a pixel round it, the fill striped and notched like a gauge.
        int rim = FoliosPalette.mix(palette.line(), palette.text(), 0.45F);
        frame(painter, x - 1, y - 1, width + 2, height + 2, rim, FoliosPalette.mix(track, palette.text(), 0.08F));
        if (filled <= 0) return;
        fillStriped(painter, x, y, filled, height, color);
        if (filled < width) glint(painter, x + filled - 1, y, 1, height);
    }


    // A slim bar, 4 pixels tall from (x, y) and a pixel of shadow under it, as an objective's: a groove with its ends
    // rounded off, sunk along its top; the fill in it, lit along its top and shaded along its foot, a bright cap at its
    // leading edge.
    private static void slim(FoliosPainter painter, int x, int y, int width, float fill, int color) {
        FoliosPalette palette = painter.palette();
        int groove = FoliosPalette.mix(palette.panel(), palette.line(), 0.55F);
        int rim = FoliosPalette.mix(palette.line(), palette.text(), 0.3F);
        // The slim bar's sprites: its border (the shadow under it painted in) and its groove (sunk along its top).
        Sprites.draw(painter, Sprites.BAR_SLIM_BORDER, x, y, width, 5, rim);
        Sprites.draw(painter, Sprites.BAR_SLIM_GROOVE, x, y, width, 5, groove);
        int filled = Math.round((width - 2) * Math.max(0, Math.min(1, fill)));
        if (filled <= 0) return;
        int left = x + 1;
        Sprites.draw(painter, Sprites.BAR_SLIM_FILL, left, y + 1, filled, 2, FoliosPalette.mix(color, 0xFFFFFFFF, 0.38F));
        if (filled < width - 2) glint(painter, left + filled - 1, y + 1, 1, 2);
    }

    private static Component rewardText(String reward) {
        return Component.translatableWithFallback("folios.quest.reward", "Reward: %s", FoliosText.inline(reward));
    }

    @Override
    public String searchText() {
        return FoliosText.resolve(title.orElse("")) + " " + FoliosText.resolve(text);
    }
}
