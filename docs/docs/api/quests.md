# Server quests

[Server quests](../quests.md#server-quests) are data packs' (`data/<namespace>/folios/quests/<name>.json`): the server
follows them for each player and gives their rewards. `FoliosQuests` is how a mod works with them, on the server.

```java
import net.ixdarklord.folios.api.FoliosQuests;

// Told whenever a player finishes one.
FoliosQuests.onCompleted((player, quest) -> {
    if (quest.equals(MyMod.id("first_supplies"))) player.sendSystemMessage(Component.literal("Well done!"));
});

// What "source" objectives follow: {"source": "mymod:mana", "goal": 100}.
FoliosQuests.registerValue(MyMod.id("mana"), player -> Mana.of(player).current());

// Flags for "flag" objectives: one a mod works out, and one of a player's own.
FoliosQuests.registerFlag("mymod:hard_mode", player -> MyConfig.HARD_MODE.get());
FoliosQuests.setFlag(player, "mymod:met_the_wizard", true);

// What a player has done.
boolean done = FoliosQuests.isDone(player, MyMod.id("first_supplies"));
boolean claimed = FoliosQuests.isClaimed(player, MyMod.id("first_supplies"));
float along = FoliosQuests.progress(player, MyMod.id("first_supplies"));
```

| Method | What it does |
| --- | --- |
| `onCompleted(listener)` | Told when a player finishes a server quest. |
| `registerValue(id, value)` | A value for `source` objectives, read for a player as their quests are checked. |
| `registerFlag(flag, test)` | A flag a mod works out for each player, for `flag` objectives. |
| `setFlag(player, flag, on)` | Sets or clears one of a player's own flags; their quests are checked straight away. |
| `isDone(player, quest)` | Whether they have finished it. |
| `isClaimed(player, quest)` | Whether they have claimed its reward. |
| `progress(player, quest)` | How far along they are, 0 to 1. |
| `complete(player, quest)` | Finishes it for them, as though they had done all of it. |
| `reset(player, quest)` | Takes it back, and its claim. |
| `quests()` | The server quests there are. |

Call these on the server thread. A player's quests are checked about once a second, so keep values and flags cheap:
read them from what the server already has.

!!! note "Client-side values and flags"
    Values and flags a mod gives the client (`FoliosObjectives.register`, `FoliosGuides.setFlag`) are for quests
    written in books. Server quests follow only those given to the server here.
