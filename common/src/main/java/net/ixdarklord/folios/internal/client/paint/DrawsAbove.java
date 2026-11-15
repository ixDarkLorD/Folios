package net.ixdarklord.folios.internal.client.paint;

import net.ixdarklord.folios.api.client.page.FoliosPainter;

public interface DrawsAbove {
    void above(Runnable draw);

    static void above(FoliosPainter painter, Runnable draw) {
        if (painter instanceof DrawsAbove layered) layered.above(draw);
        else draw.run();
    }
}
