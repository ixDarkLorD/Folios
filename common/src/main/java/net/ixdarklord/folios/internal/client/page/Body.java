package net.ixdarklord.folios.internal.client.page;

import net.ixdarklord.folios.api.client.page.GuidePage;

/** What a page shows between its title and its text, {@code height} tall: a recipe, a build's window. */
record Body(int height, GuidePage.Part part) {}
