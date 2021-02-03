
package jp.minecraftuser.ecodragon.listener;

import java.util.HashMap;
import jp.minecraftuser.ecoframework.ListenerFrame;
import jp.minecraftuser.ecoframework.PluginFrame;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;

/**
 * プレイヤーイベント処理リスナークラス
 * @author ecolight
 */
public class DragonEggListener extends ListenerFrame {
    private static HashMap<Item, String> dropTable = new HashMap<>();
    private static Player piston = null;
    private static Player torch = null;

    /**
     * コンストラクタ
     * @param plg_ プラグインインスタンス
     * @param name_　名前
     */
    public DragonEggListener(PluginFrame plg_, String name_) {
        super(plg_, name_);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void BlockPlace(BlockPlaceEvent event) {

        // エンドラ戦対象ワールド以外では何もしない
        Player pl = event.getPlayer();
        String prefix = plg.getDefaultConfig().getString("worldprefix");
        if (!pl.getWorld().getName().toLowerCase().startsWith(prefix.toLowerCase())) return;

        // 設置者を記録
        Block b = event.getBlock();
        if ((b.getType() == Material.PISTON) || b.getType() == Material.STICKY_PISTON) piston = pl;
        if (b.getType() == Material.REDSTONE_TORCH || b.getType() == Material.REDSTONE_WALL_TORCH) torch = pl;
    }

    /**
     * プレイヤーログインイベント処理
     * @param event イベント情報
     */
    @EventHandler
    public void PlayerPickupItem(PlayerPickupItemEvent event)
    {
        // エンドラ卵以外は何もしない
        Item pickup = event.getItem();
        if (pickup.getItemStack().getType() != Material.DRAGON_EGG) return;

        // エンドラ戦対象ワールド以外では何もしない
        Player pl = event.getPlayer();
        String prefix = plg.getDefaultConfig().getString("worldprefix");
        if (!pl.getWorld().getName().toLowerCase().startsWith(prefix.toLowerCase())) return;

        // 誰かが落とした卵だったら何もしない
        if (dropTable.containsKey(pickup)) return;

        for (Entity ent: pl.getNearbyEntities(4.0D, 3.0D, 4.0D)) {
            if (ent.getType() == EntityType.PLAYER) {
                Player getter = (Player)ent;
                if ((getter.equals(piston)) && (getter.equals(torch))) {
                    pl = getter;
                    log.info("エンダードラゴンエッグ貢献：PISTON+TORCH["+getter.getName()+"]");
                    break;
                }
                if (getter.equals(piston)) {
                    pl = getter;
                    log.info("エンダードラゴンエッグ貢献：PISTON["+getter.getName()+"]");
                    break;
                }
                if (getter.equals(torch)) {
                    pl = getter;
                    log.info("エンダードラゴンエッグ貢献：TORCH["+getter.getName()+"]");
                    break;
                }
            }
        }
        for (Player p : pl.getWorld().getPlayers()) {
            p.sendMessage("[" + plg.getName() + "] [" + pl.getName() + "]がエンダードラゴンエッグを取得");
        }
        Item i = event.getItem();
        pl.getInventory().addItem(i.getItemStack());
        i.remove();
        event.setCancelled(true);
    }

    @EventHandler
    public void PlayerDropItem(PlayerDropItemEvent event) {
        Player pl = event.getPlayer();

        // エンドラ戦対象ワールド以外では何もしない
        String prefix = plg.getDefaultConfig().getString("worldprefix");
        if (!pl.getWorld().getName().toLowerCase().startsWith(prefix.toLowerCase())) return;

        dropTable.put(event.getItemDrop(), pl.getName());
    }

}
