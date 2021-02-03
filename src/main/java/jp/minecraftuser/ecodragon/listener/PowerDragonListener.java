
package jp.minecraftuser.ecodragon.listener;

import java.util.List;
import java.util.Random;
import jp.minecraftuser.ecoframework.ListenerFrame;
import jp.minecraftuser.ecoframework.PluginFrame;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.CaveSpider;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * エンドラ戦調整関連イベント処理リスナークラス
 * @author ecolight
 */
public class PowerDragonListener extends ListenerFrame {
    private static RankingListener ranking = null;
    private static double lasthp = 0;
    private static boolean bossdamage = false;

    /**
     * コンストラクタ
     * @param plg_ プラグインインスタンス
     * @param name_ 名前
     */
    public PowerDragonListener(PluginFrame plg_, String name_) {
        super(plg_, name_);
        ranking = (RankingListener) plg.getPluginListerner("ranking");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void EntityDamageByEntity(EntityDamageByEntityEvent event) {

        // GIANTの攻撃ダメージは即死
        if (event.getDamager().getType() == EntityType.GIANT) {
            event.setDamage(1000);
        }
    }

    private boolean isFallingDamageCanceled(EntityDamageEvent event) {
        if (!ranking.isRanking()) return false;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return false;
        if (event.getEntity().getType() == EntityType.PLAYER) return false;
        if (!event.getEntity().getWorld().getName().equals(ranking.getWorld().getName())) return false;
        return true;
    }
    private boolean isEtcDamageCanceled(EntityDamageEvent event) {
        if (!ranking.isRanking()) return false;
        if (!event.getEntity().getWorld().getName().equals(ranking.getWorld().getName())) return false;
        switch (event.getCause()) {
            case LIGHTNING:
                switch (event.getEntityType()) {
                    case ENDER_DRAGON: return true;
                    case CREEPER:
                        event.setDamage(0);
                        break;
                }
                break;
            case FIRE_TICK:
                switch (event.getEntityType()) {
                    case CREEPER: return true;
                }
                break;
        }
        return false;
    }
    private void playerLightningDamageExplosion(EntityDamageEvent event) {
        if (!ranking.isRanking()) return;
        if (!event.getEntity().getWorld().getName().equals(ranking.getWorld().getName())) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.LIGHTNING) return;
        if (event.getEntityType() != EntityType.PLAYER) return;

        Player p = (Player)event.getEntity();
        Location loc = p.getLocation();
        loc.setY(loc.getY() - 1);

        // 一定高さ以上の雷ダメージはHPを半分削る(即死はしないよう+1)
        if (p.getLocation().getY() > 60) {
            event.setCancelled(true);
            p.setHealth(p.getHealth() / 2 + 1);
        }

        // 一定以上の雷ダメージは地形を損傷させる
        if (p.getLocation().getY() <= 55) return;

//        // 足元が空気ならば何もしない
//        if (loc.getBlock().getType() == Material.AIR) return;
//
        // 爆発エフェクト
        p.getWorld().createExplosion(loc, 1);

        // player周りのブロックを消滅☆
        Random rand = new Random();
        int area = 4 + rand.nextInt(7);
        Location ploc = p.getLocation();
        Location changeLoc = p.getLocation();
        Location checkLoc = null;
        for (int x = -area; x < area; x++) {
            changeLoc.setX(loc.getX() + x);
            for (int y = -area; y < area; y++) {
                changeLoc.setY(loc.getY() + y);
                for (int z = -area; z < area; z++) {
                    changeLoc.setZ(loc.getZ() + z);
                    if (ploc.distance(changeLoc) > area-1) continue;
//                    // 上下どちらかが水の黒曜石は破壊しない
//                    checkLoc = changeLoc.clone();
//                    checkLoc.setY(changeLoc.getY()+1);
//                    if ((changeLoc.getBlock().getType() == Material.OBSIDIAN) && ((checkLoc.getBlock().getType() == Material.WATER) || (checkLoc.getBlock().getType() == Material.STATIONARY_WATER))) continue;
//                    checkLoc = changeLoc.clone();
//                    checkLoc.setY(changeLoc.getY()-1);
//                    if ((changeLoc.getBlock().getType() == Material.OBSIDIAN) && ((checkLoc.getBlock().getType() == Material.WATER) || (checkLoc.getBlock().getType() == Material.STATIONARY_WATER))) continue;
                    // 水も破壊しない
                    if (changeLoc.getBlock().getType() == Material.WATER) continue;
                    // あと看板も
                    if (changeLoc.getBlock().getType() == Material.OAK_SIGN) continue;
                    if (changeLoc.getBlock().getType() == Material.SPRUCE_SIGN) continue;
                    if (changeLoc.getBlock().getType() == Material.BIRCH_SIGN) continue;
                    if (changeLoc.getBlock().getType() == Material.JUNGLE_SIGN) continue;
                    if (changeLoc.getBlock().getType() == Material.ACACIA_SIGN) continue;
                    if (changeLoc.getBlock().getType() == Material.DARK_OAK_SIGN) continue;
                    //アプデ用
                    //if (changeLoc.getBlock().getType() == Material.CRIMSON_SIGN) continue;
                    //if (changeLoc.getBlock().getType() == Material.WARPED_SIGN) continue;

                    if (changeLoc.getBlock().getType() == Material.OAK_WALL_SIGN) continue;
                    if (changeLoc.getBlock().getType() == Material.SPRUCE_WALL_SIGN) continue;
                    if (changeLoc.getBlock().getType() == Material.BIRCH_WALL_SIGN) continue;
                    if (changeLoc.getBlock().getType() == Material.JUNGLE_WALL_SIGN) continue;
                    if (changeLoc.getBlock().getType() == Material.ACACIA_WALL_SIGN) continue;
                    if (changeLoc.getBlock().getType() == Material.DARK_OAK_WALL_SIGN) continue;
                    //アプデ用
                    //if (changeLoc.getBlock().getType() == Material.CRIMSON_WALL_SIGN) continue;
                    //if (changeLoc.getBlock().getType() == Material.WARPED_WALL_SIGN) continue;





                    // チェストも壊さない
                    //if (changeLoc.getBlock().getType() == Material.CHEST) continue;
                    //if (changeLoc.getBlock().getType() == Material.ENDER_CHEST) continue;

                    // 黒曜石と岩盤とゲートウェイは破壊しない
                    if (changeLoc.getBlock().getType() == Material.OBSIDIAN) continue;
                    if (changeLoc.getBlock().getType() == Material.BEDROCK) continue;
                    if (changeLoc.getBlock().getType() == Material.END_GATEWAY) continue;

                    changeLoc.getBlock().setType(Material.AIR);
                }
            }
        }

    }
    @EventHandler(priority = EventPriority.LOWEST)
    public void EntityTargetLivingEntity(EntityTargetLivingEntityEvent event) {
        if (!ranking.isRanking()) return;
        if (!event.getEntity().getWorld().equals(ranking.getWorld())) return;
        LivingEntity target = event.getTarget();
        if (!(target instanceof Player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void EntityDamage(EntityDamageEvent event) {
        if (!ranking.isRanking()) return;
        if (!event.getEntity().getWorld().getName().equals(ranking.getWorld().getName())) return;
        // イベント中MOBの落下ダメージはキャンセル
        if (isFallingDamageCanceled(event)) {
            event.setCancelled(true);
        }
        // イベント中のMOBダメージその他(雷など)
        if (isEtcDamageCanceled(event)) {
            event.setCancelled(true);
            return;
        }
        
        // プレイヤーの雷ダメージで地形を粉砕
        playerLightningDamageExplosion(event);

        // エンドラ消滅救済用
//        if (lasthp > 0) {
//            boolean drahit = false;
//            for (Entity ent : event.getEntity().getWorld().getEntities()) {
//                if (ent.getType() == EntityType.ENDER_DRAGON) {
//                    drahit = true;
//                }
//            }
//            if (!drahit) {
//                EnderDragon endra = (EnderDragon)event.getEntity().getWorld().spawnEntity(event.getEntity().getLocation(), EntityType.ENDER_DRAGON);
//                endra.setHealth(lasthp);
//                plg.getServer().broadcastMessage("エンダードラゴンが行方不明なためクローン体が召喚されました");
//            }
//        }

        /* エンダードラゴン強化用ロジック */
        if (event.getEntityType() == EntityType.ENDER_DRAGON &&
           ((event.getCause() == EntityDamageEvent.DamageCause.PROJECTILE) || (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK))){

            Random rand = new Random();
            EnderDragon dra = (EnderDragon)event.getEntity(); 

            lasthp = dra.getHealth();
            // HPの割合算出
            int hp = ((int)dra.getHealth() * 100) / (int)dra.getMaxHealth();
            //m.info("hp:" + hp + " health:" + dra.getHealth() + " max:" + dra.getMaxHealth());
            int mobcount = 0;
            
//            // 残存HP50%メッセージ
//            if ((bossdamage == false) && (hp <= 20)) {
//                for (Player p: dra.getWorld().getPlayers()) {
//                    p.sendMessage("§d[" + plg.getName() + "]§f エンダードラゴンの残存体力が低下、エンダードラゴンの様子が・・・・？");
//                    p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDERDRAGON_DEATH, 0.5f, 0.5f);
//                }
//                PotionEffect potion = new PotionEffect(PotionEffectType.SPEED, 20*60*60*24*7*5, 5);
//                dra.addPotionEffect(potion);
//                dra.setHealth(dra.getMaxHealth());
//                bossdamage = true;
//            }

            // プレイヤーリストと初期ターゲット取得
            List<Player> plist = event.getEntity().getWorld().getPlayers();
            int target = rand.nextInt(plist.size());
            Player tgtp = event.getEntity().getWorld().getPlayers().get(target);

            // 1/20 の確立でドラゴンをランダムプレイヤー下の位置にテレポート
            if (rand.nextInt(20) == 1) {
                Location draloc = tgtp.getLocation();
                draloc.setY(draloc.getY() - 5);
                if (draloc.getBlock().getType() != Material.OBSIDIAN) {
                    if (draloc.getY() >= 10) {
                        dra.teleport(draloc);
                    }
                }
            }
            // 1/10 の確立でランダムプレイヤーの位置にウィザー召還
            if (rand.nextInt(10) == 1) {
                Location witherloc = tgtp.getLocation();
                witherloc.setY(witherloc.getY() + 5);
                if (witherloc.getY() <= 60) witherloc.setY(70);
                event.getEntity().getWorld().spawnEntity(witherloc, EntityType.WITHER);
            }

            // エンドラが特定HP未満の場合、プレイヤーに落雷させる
            int tgtcnt = 0;
            if (hp < 90) {tgtcnt = 1;}
            if (hp < 50) {tgtcnt = 3;}
            if (hp < 20) {tgtcnt = 5;}
            if (hp < 10) {tgtcnt = 7;}
            for (int loop = 0; loop < tgtcnt; loop++) {
                // ランダムプレイヤー算出
                target = rand.nextInt(plist.size());
                tgtp = event.getEntity().getWorld().getPlayers().get(target);

                // creativeユーザーは除外
                if (tgtp.getGameMode() == GameMode.CREATIVE) {
                    boolean exitSurvivalPlayer = false;
                    for(Player player:event.getEntity().getWorld().getPlayers()){
                        if(player.getGameMode() == GameMode.SURVIVAL){
                            exitSurvivalPlayer = true;
                        }
                    }
                    if(!exitSurvivalPlayer){
                        break;
                    }
                    tgtcnt++;
                    continue;

                }
                // プレイヤーの居る位置が高さ60以下だったら対象外
                if (tgtp.getLocation().getY() <= 60) {
                    tgtcnt++;
                    if (tgtcnt >= 20) { // 最大試行20回
                        break;
                    }
                    continue;
                }
                if (hp > 0) {
                    dra.getWorld().strikeLightning(tgtp.getLocation());
                }
                // 再選が規定回数以上になった場合は強制的に終わる
                if (tgtcnt >= 50) {
                    break;
                }
            }

            // 村人投下
            event.getEntity().getWorld().spawnEntity(event.getEntity().getLocation(), EntityType.WITCH);
            
            // ドラゴンに落雷
            if (hp < 20) {
                dra.getWorld().strikeLightning(dra.getLocation());
            }
            
            // ドラゴンはHP50%以下の場合、遠距離武器を3/5の確立で無効化する
            if (event.getCause() == EntityDamageEvent.DamageCause.PROJECTILE) {
                if ((hp < 50) && (rand.nextInt(5) > 1))  {
                    event.setCancelled(true);
                    return;
                } 
            }

            // ドラゴンはHPの残量に応じてMOBを投下する
            if (hp > 80) {mobcount = 2;         // 80%以上の場合のMOB量
            } else if (hp > 60) {mobcount = 2;  // 60%以上の場合のMOB量
            } else if (hp > 40) {mobcount = 2;  // 40%以上の場合のMOB量
            } else if (hp > 20) {mobcount = 1;  // 20%以上の場合のMOB量
            } else {mobcount = 5;}              // 19%以下の場合のMOB量
            for (int cnt = 0; cnt < mobcount; cnt++) {
                // プレイヤーが存在しない場合はMOB投下を終了する
                if (plist.size() <= 0) break;
                // ランダムプレイヤーを算出
                target = rand.nextInt(plist.size());
                tgtp = event.getEntity().getWorld().getPlayers().get(target);

                // MOB召還
                if (hp > 80) { // HPが80%以上の場合のMOB
                    CaveSpider ent = (CaveSpider)event.getEntity().getWorld().spawnEntity(event.getEntity().getLocation(),
                            EntityType.CAVE_SPIDER);
                    ent.setTarget(tgtp);
                }
                else if (hp > 60) { // HPが60%以上の場合のMOB
                    Skeleton ent = (Skeleton)spawnSkeleton(event.getEntity());
                    ent.setTarget(tgtp);
                }
                else if (hp > 40) { // HPが40%以上の場合のMOB
                    Zombie ent = (Zombie)spawnZombie(event.getEntity());
                    ent.setTarget(tgtp);
                }
                else if (hp > 20) { // HPが20%以上の場合のMOB
                    Skeleton ent = (Skeleton)spawnWitherSkeleton(event.getEntity());
                    ent.setTarget(tgtp);
                }
                else {              // HPが19%以下の場合のMOB
                    Creeper ent = (Creeper)event.getEntity().getWorld().spawnEntity(event.getEntity().getLocation(),
                            EntityType.CREEPER);
                    AttributeInstance attr = ent.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
                    attr.setBaseValue(attr.getBaseValue() * 2);
                    ent.setTarget(tgtp);
                }
            }
        }
    }
    private ItemStack addAtkEnchant(ItemStack item) {
        item.addEnchantment(Enchantment.KNOCKBACK, 2);
        item.addEnchantment(Enchantment.DAMAGE_ALL, 5);
        item.addEnchantment(Enchantment.FIRE_ASPECT, 2);
        item.addEnchantment(Enchantment.DURABILITY, 3);
        return item;
    }
    private ItemStack addShotEnchant(ItemStack item) {
        item.addEnchantment(Enchantment.ARROW_DAMAGE, 5);
        item.addEnchantment(Enchantment.ARROW_FIRE, 1);
        item.addEnchantment(Enchantment.ARROW_INFINITE, 1);
        item.addEnchantment(Enchantment.ARROW_KNOCKBACK, 2);
        return item;
    }
    private ItemStack addDefEnchant(ItemStack item) {
        item.addEnchantment(Enchantment.PROTECTION_PROJECTILE, 4);
        item.addEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL, 4);
        item.addEnchantment(Enchantment.THORNS, 3);
        item.addEnchantment(Enchantment.DURABILITY, 3);
        return item;
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void EntityDeath(EntityDeathEvent event) {
        if (!ranking.isRanking()) return;
        if (!event.getEntity().getWorld().getName().equals(ranking.getWorld().getName())) return;

        if (null != event.getEntityType()) 
            switch (event.getEntityType()) {
            /* エンダードラゴン強化用ロジック */
            case ENDER_DRAGON:
                /* 一度全MOBを消滅 */
                for (Entity ent : event.getEntity().getWorld().getEntities()) {
                    if (ent.getType() == EntityType.SNOWMAN) ent.remove();
                    if (ent.getType() == EntityType.WITCH) ent.remove();
                    if (ent.getType() == EntityType.ENDERMAN) ent.remove();
                    if (ent.getType() == EntityType.CREEPER) ent.remove();
                    if (ent.getType() == EntityType.BLAZE) ent.remove();
                    if (ent.getType() == EntityType.CAVE_SPIDER) ent.remove();
                    if (ent.getType() == EntityType.GHAST) ent.remove();
                    if (ent.getType() == EntityType.IRON_GOLEM) ent.remove();
                    if (ent.getType() == EntityType.WITHER) ent.remove();
                    if (ent.getType() == EntityType.ZOMBIE) ent.remove();
                    if (ent.getType() == EntityType.SKELETON) ent.remove();
                }   bossdamage = false;
                Player killer = event.getEntity().getKiller();
                if (killer != null) {
                    plg.getServer().broadcastMessage("§d[" + plg.getName() + "]§f ["+event.getEntity().getKiller().getName()+"]がエンダードラゴンを撃破");
                }   for (int cnt = 0; cnt < 8; cnt++) {
                    event.getEntity().getWorld().spawnEntity(event.getEntity().getLocation(), EntityType.ENDERMAN);
                    event.getEntity().getWorld().spawnEntity(event.getEntity().getLocation(), EntityType.GHAST);
                    event.getEntity().getWorld().spawnEntity(event.getEntity().getLocation(), EntityType.CREEPER);
                }   event.getEntity().getWorld().spawnEntity(event.getEntity().getLocation(), EntityType.GIANT);
                event.getEntity().getWorld().spawnEntity(event.getEntity().getLocation(), EntityType.GIANT);
                event.getEntity().getWorld().spawnEntity(event.getEntity().getLocation(), EntityType.GIANT);
                event.getEntity().getWorld().spawnEntity(event.getEntity().getLocation(), EntityType.GIANT);
                break;
            case WITHER:
                spawnWitherSkeleton(event.getEntity());
                break;
            case ENDERMAN:
                event.setDroppedExp(event.getDroppedExp()*3);
                break;
            default:
                break;
        }
    }

    public LivingEntity spawnWitherSkeleton(Entity entity) {
        Skeleton ent = (Skeleton)entity.getWorld().spawnEntity(entity.getLocation(),
                EntityType.SKELETON);
        ent.getEquipment().setItemInMainHand(addAtkEnchant(new ItemStack(Material.DIAMOND_SWORD)));
        ent.getEquipment().setItemInMainHandDropChance(0.001f);
        ent.getEquipment().setBoots(addDefEnchant(new ItemStack(Material.DIAMOND_BOOTS)));
        ent.getEquipment().setBootsDropChance(0.001f);
        ent.getEquipment().setChestplate(addDefEnchant(new ItemStack(Material.DIAMOND_CHESTPLATE)));
        ent.getEquipment().setChestplateDropChance(0.001f);
        ent.getEquipment().setHelmet(addDefEnchant(new ItemStack(Material.DIAMOND_HELMET)));
        ent.getEquipment().setHelmetDropChance(0.001f);
        ent.getEquipment().setLeggings(addDefEnchant(new ItemStack(Material.DIAMOND_LEGGINGS)));
        ent.getEquipment().setLeggingsDropChance(0.001f);
        // ステータス
        ent.setCanPickupItems(false);
        ent.setCustomName("闇の眷属(骨)");
        ent.setCustomNameVisible(true);
        ent.setRemoveWhenFarAway(false);

        PotionEffect p = new PotionEffect(PotionEffectType.SPEED, 20*60*60*24*7*5, 5);
        ent.addPotionEffect(p);
        p = new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 20*60*60*24*7*5, 5);
        ent.addPotionEffect(p);

        return ent;
    }
    public LivingEntity spawnSkeleton(Entity entity) {
        Skeleton ent = (Skeleton)entity.getWorld().spawnEntity(entity.getLocation(),
                EntityType.SKELETON);
        ent.getEquipment().setItemInMainHand(addShotEnchant(new ItemStack(Material.BOW)));
        ent.getEquipment().setItemInMainHandDropChance(0.001f);
        ent.getEquipment().setBoots(addDefEnchant(new ItemStack(Material.DIAMOND_BOOTS)));
        ent.getEquipment().setBootsDropChance(0.001f);
        ent.getEquipment().setChestplate(addDefEnchant(new ItemStack(Material.DIAMOND_CHESTPLATE)));
        ent.getEquipment().setChestplateDropChance(0.001f);
        ent.getEquipment().setHelmet(addDefEnchant(new ItemStack(Material.DIAMOND_HELMET)));
        ent.getEquipment().setHelmetDropChance(0.001f);
        ent.getEquipment().setLeggings(addDefEnchant(new ItemStack(Material.DIAMOND_LEGGINGS)));
        ent.getEquipment().setLeggingsDropChance(0.001f);
        // ステータス
        ent.setCanPickupItems(false);
        ent.setCustomName("闇の眷属(弓)");
        ent.setCustomNameVisible(true);
        ent.setRemoveWhenFarAway(false);

        PotionEffect p = new PotionEffect(PotionEffectType.SPEED, 20*60*60*24*7*5, 5);
        ent.addPotionEffect(p);
        p = new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 20*60*60*24*7*5, 5);
        ent.addPotionEffect(p);

        return ent;
    }
    public LivingEntity spawnZombie(Entity entity) {
        Zombie ent = (Zombie)entity.getWorld().spawnEntity(entity.getLocation(),
                EntityType.ZOMBIE);
        ent.getEquipment().setItemInMainHand(addAtkEnchant(new ItemStack(Material.DIAMOND_SWORD)));
        ent.getEquipment().setItemInMainHandDropChance(0.001f);
        ent.getEquipment().setBoots(addDefEnchant(new ItemStack(Material.DIAMOND_BOOTS)));
        ent.getEquipment().setBootsDropChance(0.001f);
        ent.getEquipment().setChestplate(addDefEnchant(new ItemStack(Material.DIAMOND_CHESTPLATE)));
        ent.getEquipment().setChestplateDropChance(0.001f);
        ent.getEquipment().setHelmet(addDefEnchant(new ItemStack(Material.DIAMOND_HELMET)));
        ent.getEquipment().setHelmetDropChance(0.001f);
        ent.getEquipment().setLeggings(addDefEnchant(new ItemStack(Material.DIAMOND_LEGGINGS)));
        ent.getEquipment().setLeggingsDropChance(0.001f);
        
        // ステータス
        ent.setCanPickupItems(false);
        ent.setCustomName("闇の眷属(腐)");
        ent.setCustomNameVisible(true);
        ent.setVillager(true);
        ent.setRemoveWhenFarAway(false);
        PotionEffect p = new PotionEffect(PotionEffectType.SPEED, 20*60*60*24*7*5, 5);
        ent.addPotionEffect(p);
        p = new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 20*60*60*24*7*5, 5);
        ent.addPotionEffect(p);
        return ent;
    }

}
