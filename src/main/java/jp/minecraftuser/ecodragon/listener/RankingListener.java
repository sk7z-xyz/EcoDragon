
package jp.minecraftuser.ecodragon.listener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.swing.text.html.HTML;
import jp.minecraftuser.ecodragon.EcoDragonUser;
import jp.minecraftuser.ecoframework.PluginFrame;
import jp.minecraftuser.ecodragon.config.CertificateConfig;
import jp.minecraftuser.ecodragon.timer.EndEventTimer;
import jp.minecraftuser.ecodragon.timer.EndPvPTimer;
import jp.minecraftuser.ecodragon.timer.WorldTimer;
import jp.minecraftuser.ecoframework.ListenerFrame;
import jp.minecraftuser.ecoframework.TimerFrame;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Egg;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LingeringPotion;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.LingeringPotionSplashEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * ランキング関連イベント処理リスナークラス
 * - プレイヤーがテレポートした先 or ログイン先が登録プリフィックス名のワールド名
 * 　であればエンドラを検索しエンドラが存在する場合には開始処理を実施する。
 * @author ecolight
 */
public class RankingListener extends ListenerFrame {
    private static final HashMap<String, EcoDragonUser> ranking = new HashMap<>();
    private static final HashMap<Player, Long> intervalList = new HashMap<>();
    private static final ArrayList<Block> existCrystal = new ArrayList<>();
    private static final ArrayList<TimerFrame> evtimer = new ArrayList<>();
    private static final HashMap<Player, TimerFrame> pvptimer = new HashMap<>();
    private static CertificateConfig cerConf = null;
    private static World curWorld = null;
    private static int round = 0;
    private static Scoreboard board = null;
    private static Objective dmgobj = null;
    private static long lastInterval = 0;
    private static WorldTimer timer = null;
    private static boolean first = false;
    
    /**
     * コンストラクタ
     * @param plg_ プラグインインスタンス
     * @param name_ 名前
     */
    public RankingListener(PluginFrame plg_, String name_) {
        super(plg_, name_);
        cerConf = (CertificateConfig)plg.getPluginConfig("certificate");
    }

    
    
    /**
     * プレイヤーログイン後イベント処理
     * @param event イベント情報
     */
    @EventHandler
    public void PlayerJoin(PlayerJoinEvent event)
    {
        Player p = event.getPlayer();

        // エンドラ戦中であればスコアボード設定する
        if (curWorld != null) {
            addScoreboard(p);
            if (curWorld.equals(p.getWorld())) {
                // 透明化が掛かっていたら解除する
                for (PotionEffect pe : p.getActivePotionEffects()) {
                    if (pe.getType().equals(PotionEffectType.INVISIBILITY)) {
                        p.removePotionEffect(PotionEffectType.INVISIBILITY);
                    }
                }
            }
        }

        // エンドラ戦が未実行の場合は開始処理を走らせる（最初の一人がエンドに入らないとエンドラが検出できない）
        startEnderDragonRanking(p.getWorld(), false);
    }

    /**
     * プレイヤーテレポートイベント処理
     * @param event イベント情報
     */
    @EventHandler
    public void PlayerTeleport(PlayerTeleportEvent event)
    {
        startEnderDragonRanking(event.getTo().getWorld(), false);
        
        Player p = event.getPlayer();
        if (curWorld != null) {
            if (curWorld.equals(event.getTo().getWorld())) {
                // 透明化が掛かっていたら解除する
                for (PotionEffect pe : p.getActivePotionEffects()) {
                    if (pe.getType().equals(PotionEffectType.INVISIBILITY)) {
                        p.removePotionEffect(PotionEffectType.INVISIBILITY);
                    }
                }
            }
        }
        
        // エンドラランキング中の場合は、エンドゲートウェイの転送は抑止する。
        // エンドラランキング後はインターバルに応じて開放する

        // テレポートイベントがエンドゲートウェイ由来でなければ何もしない
        if (event.getCause() != TeleportCause.END_GATEWAY) return;

        // エンドラ戦対象ワールドでない場合は何もしない
        String prefix = plg.getDefaultConfig().getString("worldprefix");
        if (!p.getWorld().getName().toLowerCase().startsWith(prefix.toLowerCase())) return;
        
        // インターバルテーブルが空の場合には何もしない
        if (intervalList.isEmpty()) return;
        
        // 最後のインターバル時刻を超えていたら強制解除
        Date now = new Date();
        if (lastInterval < now.getTime()) {
            intervalList.clear();
            return;
        }
        
        // インターバルテーブルがある場合、自分のインターバルを超えてるか確認し超えていない場合は抑止する
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        if (intervalList.containsKey(p)) {
            if (intervalList.get(p) > now.getTime()) {
                // まだ未達
                event.setCancelled(true);
                p.sendMessage("["+plg.getName()+"] "+sdf.format(new Date(intervalList.get(p))));
            } else {
                // 到達
                intervalList.remove(p);
            }
        } else {
            // インターバルテーブルに登録がない場合は空になるまで待つ
            event.setCancelled(true);
            p.sendMessage("["+plg.getName()+"] 現在のサーバー時刻 "+sdf.format(new Date()));
            p.sendMessage("["+plg.getName()+"] エンドゲートウェイ開放目安 "+sdf.format(new Date(lastInterval)));
        }
    }

    /**
     * エンドラ討伐判定
     * @param event 
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void EntityDeath(EntityDeathEvent event) {
        if (curWorld == null) return;
        if (event.getEntityType() == EntityType.ENDER_DRAGON) {
            // エンダークリスタル設置ボーナスブロック初期化
            existCrystal.clear();
            
            // 討伐時メッセージ表示
            round--;
            roundMessage();
            
            // 終了判定
            if (round == 0) {
                endRankingPresent();
                endEnderDragonRanking();
            }
        }
    }

    /**
     * エンドラ討伐判定
     * @param event 
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void PlayerDeath(PlayerDeathEvent event) {

        // エンドラ戦中でなければ何もしない
        if (curWorld == null) return;

        // エンドラ戦中のワールドでなければ何もしない
        Player pl = event.getEntity();
        if (!pl.getWorld().equals(curWorld)) return;

        // 死亡メッセージ無し
        event.setDeathMessage(null);

        // PvPでの死亡の場合、ランキングポイントを半分譲渡する
        Player killer = pl.getKiller();
        if (killer != null) {
            if (ranking.containsKey(pl.getName())) {
                EcoDragonUser plr = ranking.get(pl.getName());
                if (plr.isPvP()) {
                    EcoDragonUser killerr = null;
                    if (ranking.containsKey(killer.getName())) {
                        killerr = ranking.get(killer.getName());
                    } else {
                        killerr = new EcoDragonUser(killer);
                        ranking.put(killer.getName(), killerr);
                    }
                    if ((plr.isPvP()) &&
                        (killerr.isPvP())) {
                        int harf = plr.getPoint() / 2;
                        StringBuilder sb = new StringBuilder();
                        sb.append("before point ");
                        sb.append(pl.getName());
                        sb.append("(");
                        sb.append(plr.getPoint());
                        sb.append(") -> ");
                        sb.append(killer.getName());
                        sb.append(killerr.getPoint());
                        sb.append(")");
                        sb.append("after point ");

                        plr.addPoint(-harf);
                        killerr.addPoint(harf);

                        pl.sendMessage("[" + plg.getName() + "] エンドラ戦 PvPデスペナルティ: -" + harf + " pt");
                        killer.sendMessage("[" + plg.getName() + "] エンドラ戦 PvP討伐ボーナス: +" + harf + " pt");

                        // リスト更新
                        refreshScoreBoard();
                        sb.append(pl.getName());
                        sb.append("(");
                        sb.append(plr.getPoint());
                        sb.append(") -> ");
                        sb.append(killer.getName());
                        sb.append(killerr.getPoint());
                        sb.append(")");
                        log.info(sb.toString());
                    }
                }
            }
        }
    }
    
    /**
     * エンドラ戦中の一部ブロック設置抑止
     * @param event イベント情報
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void BlockPlaceEvent(BlockPlaceEvent event) {
        // エンドラ戦中だけ
        if (curWorld == null) {
            return;
        }
        
        // 当該ワールドだけ
        Block b = event.getBlock();
        if (!curWorld.equals(b.getWorld())) {
            return;
        }
        
        // 黒曜石の設置を禁止する
        if (b.getType() == Material.OBSIDIAN) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("[" + plg.getName() + "] エンドラ戦中の黒曜石設置破壊は禁止されています");
        } else if (b.getType() == Material.DISPENSER) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("[" + plg.getName() + "] エンドラ戦中のディスペンサー設置は禁止されています");
        } else if (b.getType() == Material.TNT) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("[" + plg.getName() + "] エンドラ戦中のTNT設置は禁止されています");
        }
        
    }
    
    /**
     * プレイヤー接触イベント
     * @param event 
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void PlayerInteract(PlayerInteractEvent event) {
        // エンドラ戦中だけ
        if (curWorld == null) {
            return;
        }
        // 当該ワールドだけ
        Block b = event.getClickedBlock();
        if (b == null) {
            return;
        }
        if (!curWorld.equals(b.getWorld())) {
            return;
        }

        // エンダークリスタル設置
        Player p = event.getPlayer();
        if (Action.RIGHT_CLICK_BLOCK == event.getAction()) {
            if (Material.BEDROCK == event.getClickedBlock().getType()) {
                if (Material.END_CRYSTAL == event.getMaterial()) {
                    // 所定の位置か確認
                    Location x = b.getLocation();
                    Location z = b.getLocation();
                    Location xx = b.getLocation();
                    Location zz = b.getLocation();
                    x.setX(x.getX() - 1);
                    xx.setX(xx.getX() + 1);
                    z.setZ(z.getZ() - 1);
                    zz.setZ(zz.getZ() + 1);
                    if (((x.getBlock().getType() == Material.BEDROCK) && (xx.getBlock().getType() == Material.BEDROCK) &&
                         (z.getBlock().getType() != Material.BEDROCK) && (zz.getBlock().getType() != Material.BEDROCK)) ||
                        ((z.getBlock().getType() == Material.BEDROCK) && (zz.getBlock().getType() == Material.BEDROCK) &&
                         (x.getBlock().getType() != Material.BEDROCK) && (xx.getBlock().getType() != Material.BEDROCK))) {
                        Bukkit.getScheduler().runTask(plg, new Runnable() {
                            @Override
                            public void run() {
                                List<Entity> entities = event.getPlayer().getNearbyEntities(4, 4, 4);
                                for (Entity entity : entities) {
                                    if (EntityType.ENDER_CRYSTAL == entity.getType()) {
                                        EnderCrystal crystal = (EnderCrystal) entity;
                                        Block belowCrystal = crystal.getLocation().getBlock().getRelative(BlockFace.DOWN);
                                        if (event.getClickedBlock().equals(belowCrystal)) {
                                            if (!existCrystal.contains(belowCrystal)) {
                                                existCrystal.add(belowCrystal);
                                                EcoDragonUser u = ranking.get(p.getName());
                                                if (u == null) {
                                                    u = new EcoDragonUser(p);
                                                    ranking.put(p.getName(), u);
                                                }
                                                int bonus = conf.getInt("crystal-place-bonus");
                                                u.addPoint(bonus);
                                                p.sendMessage("[" + plg.getName() + "] エンダークリスタルの設置ボーナス: " + bonus + " pt");
                                                plg.getServer().broadcastMessage("[" + plg.getName() + "] " + p.getName() + " がエンダークリスタルを設置しました(bonus: " + bonus + " pt)");
                                                log.info("[" + plg.getName() + "] " + p.getName() + " エンダークリスタルの設置ボーナス: " + bonus + " pt");
                                                refreshScoreBoard();
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                        });
                    }
                }
            }
        }
        if (b != null) {
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                if (event.getItem() != null) {
                    // ホッパートロッコ禁止
                    if (event.getItem().getType() == Material.TNT_MINECART) {
                        if ((b.getType() == Material.RAIL) ||
                            (b.getType() == Material.POWERED_RAIL) ||
                            (b.getType() == Material.DETECTOR_RAIL) ||
                            (b.getType() == Material.ACTIVATOR_RAIL)){
                            event.getPlayer().sendMessage("[" + plg.getName() + "] エンドラ戦中のTNTマインカートの設置は禁止されています");
                            event.setCancelled(true);
                        }
                    } else if (event.getItem().getType() == Material.END_CRYSTAL) {
                        for (Entity e : b.getWorld().getEntities()) {
                            if (e.getType() == EntityType.ENDER_DRAGON) {
                                p.sendMessage("[" + plg.getName() + "] エンドラ戦中はエンドラがいない間だけエンダークリスタルの設置が許可されています");
                                event.setCancelled(true);
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * エンティティ対エンティティダメージ判定イベントハンドラ
     * @param event 
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void EntityDamageByEntity(EntityDamageByEntityEvent event) {
        // エンドラ戦中だけ
        if (curWorld == null) {
            return;
        }
        
        // 当該ワールドだけ
        Entity ent = event.getEntity();
        if (!curWorld.equals(ent.getWorld())) {
            return;
        }

        Player player = null;
        try {
        // 各Entityタイプごとに攻撃ユーザーを抽出
        if (event.getDamager().getType() == EntityType.PLAYER) {
             player = (Player) event.getDamager();
            //m.info("PlayerDamage:" + player.getName());
        } else if (event.getDamager().getType() == EntityType.ARROW) {
            Arrow arrow = (Arrow) event.getDamager();
//            if (arrow.getShooter().getType() != EntityType.PLAYER) return;
            player = (Player) arrow.getShooter();
            //m.info("ArrowDamage:" + player.getName());
        } else if (event.getDamager().getType() == EntityType.SNOWBALL) {
            Snowball ball = (Snowball) event.getDamager();
//            if (ball.getShooter().getType() != EntityType.PLAYER) return;
            player = (Player) ball.getShooter();
            //m.info("SnowballDamage:" + player.getName());
        } else if (event.getDamager().getType() == EntityType.EGG) {
            Egg egg = (Egg) event.getDamager();
//            if (egg.getShooter().getType() != EntityType.PLAYER) return;
            player = (Player) egg.getShooter();
            //m.info("EggDamage:" + player.getName());
        } else if (event.getDamager().getType() == EntityType.SPLASH_POTION) {
            ThrownPotion potion = (ThrownPotion) event.getDamager();
//            if (potion.getShooter().getType() != EntityType.PLAYER) return;
            player = (Player) potion.getShooter();
            //m.info("PotionDamage:" + player.getName());
        }
        } catch (Exception e) {
            return;
        }
        // 攻撃ユーザーが確定している場合のみ集計
        if (player == null) return;
        // PvP
        if (event.getEntity() instanceof Player) {
            Player p = (Player) event.getEntity();
            Player d = player;
            if ((!isPlayerPvP(p)) ||
                (!isPlayerPvP(d))){
                event.setCancelled(true);
                return;
            }
        }

        if (ent.getType() == EntityType.ENDER_CRYSTAL) {
            // プレイヤーを取得
            Player p = null;
            if (event.getDamager() instanceof Player) {
                p = (Player) event.getDamager();
            } else if (event.getDamager().getType() == EntityType.ARROW) {
                ProjectileSource ps = (ProjectileSource) ((Arrow)event.getDamager()).getShooter();
                if (ps instanceof Player) {
                    p = (Player) ps;
                }
//            } else if (event.getDamager().getType() == EntityType.EGG) {
//                ProjectileSource ps = (ProjectileSource) ((Egg)event.getDamager()).getShooter();
//                if (ps instanceof Player) {
//                    p = (Player) ps;
//                }
//            } else if (event.getDamager().getType() == EntityType.SNOWBALL) {
//                ProjectileSource ps = (ProjectileSource) ((Snowball)event.getDamager()).getShooter();
//                if (ps instanceof Player) {
//                    p = (Player) ps;
//                }
            } else {
                return;
            }
            
            // ボーナス or ペナルティ判定

            // 破壊者のランキング操作
            EcoDragonUser plr = ranking.get(p.getName());
            if (plr == null) {
                plr = new EcoDragonUser(p);
                ranking.put(p.getName(), plr);
            }
            boolean dragonExist = false;
            for (Entity e : curWorld.getEntities()) {
                if (e.getType() == EntityType.ENDER_DRAGON) {
                    dragonExist = true;
                    break;
                }
            }
            if (dragonExist) {
                int bonus = conf.getInt("crystal-break-bonus");
                plr.addPoint(bonus);
                plg.getServer().broadcastMessage("[" + plg.getName() + "] " + p.getName() + " がエンダークリスタルを破壊しました(bonus: " + bonus + " pt)");
                p.sendMessage("[" + plg.getName() + "] エンダークリスタルの破壊ボーナス: " + bonus + " pt");
                log.info("[" + plg.getName() + "] " + p.getName() + " エンダークリスタルの破壊ボーナス: " + bonus + " pt");
            } else {
                int penalty = conf.getInt("crystal-break-penalty");
                plr.addPoint(penalty);
                plg.getServer().broadcastMessage("[" + plg.getName() + "] " + p.getName() + " がエンダークリスタルを破壊しました(penalty: " + penalty + " pt)");
                p.sendMessage("[" + plg.getName() + "] エンダークリスタルの破壊ペナルティ: " + penalty + " pt");
                log.info("[" + plg.getName() + "] " + p.getName() + "エンダークリスタルの破壊ペナルティ: " + penalty + " pt");
            }
            refreshScoreBoard();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void InventoryClick(InventoryClickEvent event) {
        // エンドラ戦中だけ
        if (curWorld == null) {
            return;
        }
        
        // 当該ワールドだけ
        HumanEntity e = event.getWhoClicked();
        if (!curWorld.equals(e.getWorld())) {
            return;
        }

        InventoryType type = event.getInventory().getType();
        switch (type) {
            case CHEST:
            case BREWING:
            case DISPENSER:
            case FURNACE:
            case HOPPER:
                boolean hit = false;
                if (event.getCurrentItem() != null) {
                    if (event.getCurrentItem().getType() == Material.TNT_MINECART) {
                        hit = true;
                    }
                }
                if (event.getCursor() != null) {
                    if (event.getCursor().getType() == Material.TNT_MINECART) {
                        hit = true;
                    }
                }
                if (hit) {
                    plg.getServer().getPlayer(event.getWhoClicked().getName()).sendMessage("[" + plg.getName() + "] エンドラ戦中のTNTマインカートの操作は禁止されています");
                    event.setCancelled(true);
                }
        }
    }

    /**
     * エンドラ戦中の一部ブロック破壊抑止
     * @param event イベント情報
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void BlockBreakEvent(BlockBreakEvent event) {
        // エンドラ戦中だけ
        if (curWorld == null) {
            return;
        }
        
        // 当該ワールドだけ
        Block b = event.getBlock();
        if (!curWorld.equals(b.getWorld())) {
            return;
        }
        
        // 黒曜石の破壊を禁止する
        if (b.getType() == Material.OBSIDIAN) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("[" + plg.getName() + "] エンドラ戦中の黒曜石設置破壊は禁止されています");
        }
    }

    /**
     * エンドラ戦中の一部ブロック設置抑止(バケツ経由)
     * @param event イベント情報
     */
    @EventHandler(priority=EventPriority.LOWEST)
    public void PlayerBucketEmptyEvent(PlayerBucketEmptyEvent event) {
        // エンドラ戦中だけ
        if (curWorld == null) {
            return;
        }
        
        // 当該ワールドだけ
        Player p = event.getPlayer();
        if (!curWorld.equals(p.getWorld())) {
            return;
        }
    
        // 溶岩バケツは抑止
        if (event.getBucket() == Material.LAVA_BUCKET) {
            event.setCancelled(true);
            p.sendMessage("[" + plg.getName() + "] エンドラ戦中の当該ワールドにおける溶岩バケツの使用は禁止されています");
        }
    }
    /**
     * つり
     * @param event 
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void PlayerFish(PlayerFishEvent event) {
        // エンドラ戦中だけ
        if (curWorld == null) {
            return;
        }
        
        // 当該ワールドだけ
        Player p = event.getPlayer();
        if (!curWorld.equals(p.getWorld())) {
            return;
        }
    
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            EcoDragonUser eu = ranking.get(p.getName());
            if (eu == null) {
                eu = new EcoDragonUser(p);
                ranking.put(p.getName(), eu);
            }

            int point = 0;
            if (event.getCaught().getName().equals("item.item.fish.cod.raw")) {
                point = conf.getInt("fishing-bonus");
                eu.addPoint(point);
                p.sendMessage("[" + plg.getName() + "] さかなだー！ (" + point + " pt)");
            } else if (event.getCaught().getName().equals("item.item.fish.salmon.raw")) {
                point = conf.getInt("fishing-salmon-bonus");
                eu.addPoint(point);
                p.sendMessage("[" + plg.getName() + "] しゃけだー！ (" + point + " pt)");
            } else if (event.getCaught().getName().equals("item.item.fish.pufferfish.raw")) {
                point = conf.getInt("fishing-pufferfish-bonus");
                eu.addPoint(point);
                p.sendMessage("[" + plg.getName() + "] ふぐだー！ (" + point + " pt)");
            } else if (event.getCaught().getName().equals("item.item.fish.clownfish.raw")) {
                point = conf.getInt("fishing-clownfish-bonus");
                eu.addPoint(point);
                p.sendMessage("[" + plg.getName() + "] くまのみだー！ (" + point + " pt)");
            } else {
                point = conf.getInt("fishing-trash");
                eu.addPoint(point);
                p.sendMessage("[" + plg.getName() + "] ごみだー！ (" + point + " pt)");
            }
        }
    }

    /**
     * エンドラ戦中透明化ポーション抑止(残留)
     * @param event 
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void LingeringPotionSplashEvent(LingeringPotionSplashEvent event) {
        ThrownPotion p = event.getEntity();
        if (p.getWorld().equals(curWorld)) {
            for (PotionEffect po : p.getEffects()) {
                if (po.getType() == PotionEffectType.INVISIBILITY) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    /**
     * エンドラ戦中透明化ポーション抑止(投合)
     * @param event 
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void PotionSplash(PotionSplashEvent event) {
        ThrownPotion p = event.getEntity();
        if (p.getWorld().equals(curWorld)) {
            for (PotionEffect po : p.getEffects()) {
                if (po.getType() == PotionEffectType.INVISIBILITY) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    /**
     * エンドラ戦中透明化ポーション抑止(飲用)
     * @param event
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPotionDrink(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (curWorld != null) {
            if (curWorld.equals(p.getWorld())) {
                if (((event.getAction() == Action.RIGHT_CLICK_AIR) || (event.getAction() == Action.RIGHT_CLICK_BLOCK)) && (p.getItemInHand().getType() == Material.POTION)) {
                    if ( ((PotionMeta)p.getItemInHand().getItemMeta()).getBasePotionData().getType() == PotionType.INVISIBILITY) {
                        int hs = p.getInventory().getHeldItemSlot();
                        drink(p,hs);
                    }
                }
            }
        }
    }
 
    public void drink(final Player p, int hs) {
        plg.getServer().getScheduler().scheduleSyncDelayedTask(plg, new Runnable() {
            public void run() {
                if (p.getInventory().getItem(hs).getType() == Material.GLASS_BOTTLE) {
                    // 透明化が掛かっていたら解除する
                    for (PotionEffect pe : p.getActivePotionEffects()) {
                        if (pe.getType().equals(PotionEffectType.INVISIBILITY)) {
                            p.removePotionEffect(PotionEffectType.INVISIBILITY);
                        }
                    }
                }
            }
        }, 35);
    }

    /**
     * エンドラ討伐時メッセージ
     */
    private void roundMessage() {
        // ラウンドに応じてメッセージ出力する
        if (round == conf.getInt("roundmax")) {
            plg.getServer().broadcastMessage("§d[" + plg.getName() + "]§f 強化エンダードラゴン戦が開始されました");
            plg.getServer().broadcastMessage("§d[" + plg.getName() + "]§f あと " + round + " 回エンダードラゴンを討伐するとエンドゲートウェイの転送が開放されます。");
            plg.getServer().broadcastMessage("§d[" + plg.getName() + "]§f エンドゲートウェイの開放は個人ごとに (ランキング順位 * 10秒) のインターバルを要します");
        } else if (round != 0) {
            // ラウンド継続中の場合
            plg.getServer().broadcastMessage("§d[" + plg.getName() + "]§f あと " + round + " 回エンダードラゴンを討伐するとエンドゲートウェイの転送が開放されます。");
            plg.getServer().broadcastMessage("§d[" + plg.getName() + "]§f エンダードラゴンの復活は自動的には行われませんのでご注意ください");
            plg.getServer().broadcastMessage("§d[" + plg.getName() + "]§f エンドゲートウェイの開放は個人ごとに (ランキング順位 * 10秒) のインターバルを要します");
        } else {
            // 最終ラウンド後の場合
            plg.getServer().broadcastMessage("§d[" + plg.getName() + "]§f 強化エンダードラゴン戦が終了しました");
            plg.getServer().broadcastMessage("§d[" + plg.getName() + "]§f エンドゲートウェイの転送が順次開放されます。");
            plg.getServer().broadcastMessage("§d[" + plg.getName() + "]§f エンドゲートウェイの開放は個人ごとに (ランキング順位 * 10秒) のインターバルを要します");
        }
    }

    /**
     * エンドラランキングの公開処理とゲート開放時間計測処理を行う
     */
    private void endRankingPresent() {
        int totalPoint = 0;
         SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        // エンドラ戦中でなければ何もしない
        if (curWorld == null) return;

        // ランキング集計
        log.info("ranking集計スタート ");
        ArrayList entries = getRankList();
        try {
            totalPoint = 0;
            Long cur = new Date().getTime();
            // 全体のポイントを集計する、ゲート開放予定時刻を算出
            for (int rank = 1; rank <= entries.size(); rank++) {
                EcoDragonUser rankUser = (EcoDragonUser)((Map.Entry)entries.get(rank - 1)).getValue();
                totalPoint += rankUser.getPoint();
                lastInterval = cur + rank * 10000;
                intervalList.put(rankUser.getPlayer(), lastInterval);
            }
            // 個々人のランキング表彰、アイテム進呈
            for (int rank = 1; rank <= entries.size(); rank++) {
                EcoDragonUser rankUser = (EcoDragonUser)((Map.Entry)entries.get(rank - 1)).getValue();
                log.info("ranking:"+(rank)+"位:"+rankUser.getPlayer().getName());
                int per = (rankUser.getPoint() * 100) / totalPoint;
                if (rank <= 3) {
                    plg.getServer().broadcastMessage("§d[" + plg.getName() + "]§f 討伐ランキング上位 [" + rank + "位:" + rankUser.getPlayer().getName() + "](" + rankUser.getPoint() + "/" + totalPoint + " ポイント(" + per +"%))");
                }
                int lv = 0;
                rankUser.setRanking(true);
                switch (rank) {
                    case 1:
                        lv = 30 * 4;
                        presentItem(rankUser.getPlayer(),Material.DIAMOND_BLOCK, 5);
                        presentItem(rankUser.getPlayer(), Material.EMERALD_BLOCK, 3);
                        presentItem(rankUser.getPlayer(), makeCertificate(rankUser.getPlayer().getName()));
                        break;
                    case 2:
                        lv = 30 * 3;
                        presentItem(rankUser.getPlayer(), Material.DIAMOND_BLOCK, 4);
                        presentItem(rankUser.getPlayer(), Material.EMERALD_BLOCK, 3);
                        break;
                    case 3:
                        lv = 30 * 2;
                        presentItem(rankUser.getPlayer(), Material.DIAMOND_BLOCK, 3);
                        presentItem(rankUser.getPlayer(), Material.EMERALD_BLOCK, 1);
                        break;
                    default:
                        rankUser.setRanking(false);
                }
                rankUser.getPlayer().sendMessage("§d[" + plg.getName() + "]§f EnderDragon討伐ランキング あなたは[" + rank + "位:" + rankUser.getPoint() +" / " + totalPoint + " ポイント(" + per +"%)]でした");
                if (rankUser.getRanking()) {
                    rankUser.getPlayer().setLevel(rankUser.getPlayer().getLevel() + lv);
                    rankUser.getPlayer().sendMessage("§d[" + plg.getName() + "]§f 討伐ボーナス [" + lv + " LV] 獲得しました");
                }
                if (intervalList.containsKey(rankUser.getPlayer())) {
                    rankUser.getPlayer().sendMessage("§d[" + plg.getName() + "]§f あなたのエンドゲートウェイ開放時刻は"+sdf.format(new Date(intervalList.get(rankUser.getPlayer())))+"頃です");
                }
            }
        }
        catch (IndexOutOfBoundsException e) {
            // 人数が足りなくて順位表示できない場合は中断
            log.info("IndexOutOfBoundsException");
        }
        // 
        for(Player p : plg.getServer().getOnlinePlayers()) {
            if (ranking.containsKey(p.getName())) {
                EcoDragonUser user = ranking.get(p.getName());
                // すでに賞品もらってたらキャンセル
                if (user != null) {
                    if (user.getRanking() == true) {
                        continue;
                    }
                }
                // 参加賞
                p.setLevel(p.getLevel() + 30);
                p.sendMessage("§d[" + plg.getName() + "]§f 討伐参加賞 [30 LV] 獲得しました");
                presentItem(p);
            }
        }
        ranking.clear();
    }
    
    /**
     * 賞状作成処理
     * @param name 表彰者名
     * @return 賞状インスタンス
     */
    private ItemStack makeCertificate(String name) {
        SimpleDateFormat sdf1 = new SimpleDateFormat("[yyyy/MM/dd]");
        SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy年MM月dd日 HH時mm分ss秒");
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta)item.getItemMeta();
        meta.setAuthor(cerConf.getString("author"));
        meta.setDisplayName(cerConf.getString(sdf1+"name"));
        meta.setTitle(cerConf.getString("sdf1+title"));
        for (String page: cerConf.getArrayList("pages")) {
            meta.addPage(page + "\n\n§c§l成績 1 位 プレイヤー[" + name + "]§r\n" + sdf2.format(new Date()));
        }
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 参加賞進呈処理
     * @param p 進呈プレイヤー
     */
    private void presentItem(Player p) {
        presentItem(p, Material.AIR, 1);
    }

    /**
     * アイテム進呈処理
     * @param p 進呈プレイヤー
     * @param item アイテム指定
     * @param amount 個数指定
     */
    private void presentItem(Player p, Material item, int amount){
        ItemStack i = null;
        if (item == Material.AIR) {
            ArrayList<ItemStack> list = new ArrayList<>();
            list.add(new ItemStack(Material.GRASS, 64));
            list.add(new ItemStack(Material.LAPIS_BLOCK));
            list.add(new ItemStack(Material.COBWEB, 32));
            list.add(new ItemStack(Material.POPPY, 10));
            list.add(new ItemStack(Material.BROWN_MUSHROOM, 10));
            list.add(new ItemStack(Material.RED_MUSHROOM, 10));
            list.add(new ItemStack(Material.MOSSY_COBBLESTONE, 64));
            list.add(new ItemStack(Material.DIAMOND_BLOCK, 3));
            list.add(new ItemStack(Material.CRAFTING_TABLE));
            //list.add(new ItemStack(Material.FARMLAND, 64));//耕地ブロックは logblockで使用する為コメントアウト
            list.add(new ItemStack(Material.FLINT, 64));
            list.add(new ItemStack(Material.EMERALD_BLOCK, 3));
            list.add(new ItemStack(Material.GOLDEN_APPLE, 3));
            list.add(new ItemStack(Material.BONE_BLOCK, 16));
            i = list.get(new Random().nextInt(list.size()));
            p.sendMessage("§d[" + plg.getName() + "]§f 討伐参加賞アイテム ["+i.getType().name()+"] x " + i.getAmount() + " 獲得しました");
        } else {
            i = new ItemStack(item, amount);
            p.sendMessage("§d[" + plg.getName() + "]§f 討伐ボーナスアイテム ["+i.getType().name()+"] x " + i.getAmount() + "獲得しました");
        }
        p.getInventory().addItem(i);
    }
    
    /**
     * 賞状進呈処理
     * @param p 表彰プレイヤー
     * @param i 賞状インスタンス
     */
    private void presentItem(Player p, ItemStack i) {
        p.getInventory().addItem(i);
        p.sendMessage("§d[" + plg.getName() + "]§f 討伐ボーナスアイテム [エンドラ討伐ランキング賞状] 獲得しました");
    }

    public void setFirst() {
        first = false;
    }
    /**
     * エンドラランキング開始処理
     * @param w 開始ワールド名
     * @return 開始結果
     */
    public boolean startEnderDragonRanking (World w, boolean force) {
        
        // 開始済みであれば何もしない
        if (curWorld != null) {
            if (force) {
                endEnderDragonRanking();
            } else {
                return false;
            }
        }

        // 暫定終了処理
        // 一回やったらもうやらない
        if ((!force) && (first)) {
            log.info("一回終了済み");
            return false;
        }
        
        // 開始条件チェック
        if ((force) || checkEnderDragon(w)) {
            curWorld = w;
            round = conf.getInt("roundmax");
            ranking.clear();
            intervalList.clear();
            w.setDifficulty(Difficulty.HARD);
            w.setPVP(true);
            w.setGameRuleValue("keepInventory", "true");
            
            // タイマ起動
            for (TimerFrame tm : pvptimer.values()) {
                tm.cancel();
            }
            pvptimer.clear();
            for (TimerFrame tm : evtimer) {
                tm.cancel();
            }
            evtimer.clear();
            timer = new WorldTimer(plg, this);
            timer.runTaskTimer(plg, 0, 200);
            evtimer.add(timer);
            
            // スコアボード初期化
            resetScoreboard();
            
            roundMessage();
            return true;
        }
        
        return false;
    }

    /**
     * エンドラ戦強制終了処理
     * @return 終了結果
     */
    public boolean abortEnderDragonRanking() {
        return endEnderDragonRanking();
    }
    
    /**
     * エンドラランキング終了処理
     * @return 終了結果
     */
    private boolean endEnderDragonRanking() {
        // エンドラ戦中でなければ何もしない
        if (curWorld == null) {
            log.info("終了済み");
            return false;
        }

        //エンドラ戦終わったワールドを記録
        ArrayList<String> list = (ArrayList<String>) conf.getArrayList("stopworld");
        if (!list.contains(curWorld.getName())) {
            list.add(curWorld.getName());
            conf.getConf().set("stopworld", list);
            conf.saveConfig();
            conf.reload();
        }

        // 設定戻し
        WorldBorder bd = curWorld.getWorldBorder();
        bd.setDamageAmount(0);
        bd.setDamageBuffer(0);
        bd.setWarningDistance(0);
        bd.setWarningTime(0);
        bd.setSize(4000, 100);
        curWorld.setPVP(false);
        for (TimerFrame tm : pvptimer.values()) {
            tm.cancel();
        }
        pvptimer.clear();
        EndEventTimer e = null;
        e = new EndEventTimer(plg, "1200 tick後に World:" + curWorld.getName() + " のエンドラ戦後処理を開始します"); e.runTaskLater(plg, 1); evtimer.add(e);
        e = new EndEventTimer(plg, "1000 tick後に World:" + curWorld.getName() + " のエンドラ戦後処理を開始します"); e.runTaskLater(plg, 200); evtimer.add(e);
        e = new EndEventTimer(plg, "800 tick後に World:" + curWorld.getName() + " のエンドラ戦後処理を開始します"); e.runTaskLater(plg, 400); evtimer.add(e);
        e = new EndEventTimer(plg, "600 tick後に World:" + curWorld.getName() + " のエンドラ戦後処理を開始します"); e.runTaskLater(plg, 600); evtimer.add(e);
        e = new EndEventTimer(plg, "400 tick後に World:" + curWorld.getName() + " のエンドラ戦後処理を開始します"); e.runTaskLater(plg, 800); evtimer.add(e);
        e = new EndEventTimer(plg, "200 tick後に World:" + curWorld.getName() + " のエンドラ戦後処理を開始します"); e.runTaskLater(plg, 1000); evtimer.add(e);
        e = new EndEventTimer(plg, "World:" + curWorld.getName() + " のKeepInventoryを解除しました。", curWorld); e.runTaskLater(plg, 1200); evtimer.add(e);
        
        // スコアボード破棄
        e = new EndEventTimer(plg, "ランキングスコアボードを破棄しました。", board, dmgobj); e.runTaskLater(plg, 1200); evtimer.add(e);

        curWorld = null;
        
        return false;
    }

    /**
     * スコアボード初期化処理
     */
    private void resetScoreboard() {
        board = plg.getServer().getScoreboardManager().getNewScoreboard();
        dmgobj = board.getObjective("damage");
        if (dmgobj != null) {
            dmgobj.unregister();
            dmgobj = null;
        }
        dmgobj = board.registerNewObjective("damage", "dummy");
        dmgobj.setDisplayName("エンドラダメージランキング");
        dmgobj.setDisplaySlot(DisplaySlot.PLAYER_LIST);
        
        for (Player p: plg.getServer().getOnlinePlayers()) {
            addScoreboard(p);
        }
    }

    /**
     * スコアボード設定処理
     */
    private void addScoreboard(Player p) {
        if (p.hasPermission("ecodragon.board")) {
            p.setScoreboard(board);
        }
    }

    /**
     * エンドラランキング開始条件チェック処理
     * @param w ワールド名
     * @return 開始可否
     */
    private boolean checkEnderDragon(World w) {
        // ランキング開始済みであれば本メソッドはコールしないこと
        
        // 指定ワールドがエンドラランキング対象かチェック
        if (!w.getName().toLowerCase().startsWith(conf.getString("worldprefix").toLowerCase())) {
            return false;
        }

        // 未実施か？
        ArrayList<String> list = (ArrayList<String>) conf.getArrayList("stopworld");
        if (list.contains(w.getName())) {
            return false;
        }
        
        // エンドラチェック
        log.info("EnderDragonCheck["+w.getName()+"]");
        for (Entity ent : w.getEntities()) {
            log.info("Check["+ent.getName()+"]");
            if (ent.getType() == EntityType.ENDER_DRAGON) {
                log.info("hit:EnderDragon");
                return true;
            }
        }
        for (LivingEntity ent : w.getLivingEntities()) {
            log.info("LivingCheck["+ent.getName()+"]");
            if (ent.getType() == EntityType.ENDER_DRAGON) {
                log.info("hit:EnderDragon");
                return true;
            }
        }
        log.info("unhit:EnderDragon");
        
        return false;
    }

    /**
     * ランキングデータ取得処理
     * @return 
     */
    public ArrayList getRankList() {
        ArrayList entries = new ArrayList(ranking.entrySet());
        Collections.sort(entries, new Comparator(){
            public int compare(Object obj1, Object obj2){
                Map.Entry ent1 =(Map.Entry)obj1;
                Map.Entry ent2 =(Map.Entry)obj2;
                EcoDragonUser val1 = (EcoDragonUser) ent1.getValue();
                EcoDragonUser val2 = (EcoDragonUser) ent2.getValue();
                return (val2.getPoint()) - (val1.getPoint());
            }
        });
        return entries;
    }
    public void addPoint(Player p, int i) {
        if (ranking.containsKey(p.getName())) {
            ranking.get(p.getName()).addPoint(i);
        } else {
            EcoDragonUser user = new EcoDragonUser(p);
            user.addPoint(i);
            ranking.put(p.getName(), user);
        }
    }
    
    public boolean isRanking() {
        return (curWorld != null);
    }
    public World getWorld() {
        return curWorld;
    }
    @EventHandler
    private void EndUpdateDamage(EntityDamageByEntityEvent event) {
        if (curWorld == null) {
//            log.info("worldなし");
            return;
        }
        if (!event.getEntity().getWorld().getName().equals(curWorld.getName())) {
//            log.info("world違い");
            return;
        }
        // ダメージリストに加算
        if (event.getDamager() == null) return;
        Player player = null;
        try {
        // 各Entityタイプごとに攻撃ユーザーを抽出
        if (event.getDamager().getType() == EntityType.PLAYER) {
             player = (Player) event.getDamager();
            //m.info("PlayerDamage:" + player.getName());
        } else if (event.getDamager().getType() == EntityType.ARROW) {
            Arrow arrow = (Arrow) event.getDamager();
//            if (arrow.getShooter().getType() != EntityType.PLAYER) return;
            player = (Player) arrow.getShooter();
            //m.info("ArrowDamage:" + player.getName());
        } else if (event.getDamager().getType() == EntityType.SNOWBALL) {
            Snowball ball = (Snowball) event.getDamager();
//            if (ball.getShooter().getType() != EntityType.PLAYER) return;
            player = (Player) ball.getShooter();
            //m.info("SnowballDamage:" + player.getName());
        } else if (event.getDamager().getType() == EntityType.EGG) {
            Egg egg = (Egg) event.getDamager();
//            if (egg.getShooter().getType() != EntityType.PLAYER) return;
            player = (Player) egg.getShooter();
            //m.info("EggDamage:" + player.getName());
        } else if (event.getDamager().getType() == EntityType.SPLASH_POTION) {
            ThrownPotion potion = (ThrownPotion) event.getDamager();
//            if (potion.getShooter().getType() != EntityType.PLAYER) return;
            player = (Player) potion.getShooter();
            //m.info("PotionDamage:" + player.getName());
        }
        } catch (Exception e) {
            return;
        }
        // 攻撃ユーザーが確定している場合のみ集計
        if (player == null) return;
        // 相手もPlayerの場合PvPフラグをチェックする
        if (event.getEntity().getType() == EntityType.PLAYER) {
            Player p = (Player) event.getEntity();
            Player d = player;
            if ((!isPlayerPvP(p)) ||
                (!isPlayerPvP(d))){
                event.setCancelled(true);
                log.info("PvP無効化1");
                return;
            }
        }
        EcoDragonUser damage = ranking.get(player.getName());
        if (damage == null) {
            damage = new EcoDragonUser(player);
            ranking.put(player.getName(), damage);
            //m.info("AddDamageList:" + player.getName());
        }
        if (event.getEntityType() == EntityType.ENDER_DRAGON) {
            damage.addDamage((int)event.getDamage());
            //lasthp = ((EnderDragon)event.getEntity()).getHealth();
        } else {
            damage.addDamageEtc((int)event.getDamage());
        }
        refreshScoreBoard();
    }
    
    public void refreshScoreBoard() {
        for (EcoDragonUser rankUser : ranking.values()) {
            dmgobj.getScore(rankUser.getPlayer()).setScore(rankUser.getPoint());
        }
    }

    public void setPlayerPvP(Player p, boolean pvp_) {
        if (ranking.containsKey(p.getName())) {
            ranking.get(p.getName()).setPvP(pvp_);
        } else {
            EcoDragonUser u = new EcoDragonUser(p);
            u.setPvP(pvp_);
            ranking.put(p.getName(), u);
        }
        if (pvp_) {
            if (pvptimer.containsKey(p)) {
                pvptimer.get(p).cancel();
                pvptimer.remove(p);
            }

            EndPvPTimer t = new EndPvPTimer(plg, p);
            t.runTaskTimer(plg, 0, 20 * 5);
            pvptimer.put(p, t);
        } else {
            if (pvptimer.containsKey(p)) {
                pvptimer.get(p).cancel();
                pvptimer.remove(p);
            }
        }
        return;
    }
    public boolean isExistPlayer(Player p) {
        return ranking.containsKey(p.getName());
    }
    public boolean isPlayerPvP(Player p) {
        boolean result = false;
        if (ranking.containsKey(p.getName())) {
            result = ranking.get(p.getName()).isPvP();
        }
        return result;
    }
}
