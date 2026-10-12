package com.bnnn.polyfield.client;

import com.bnnn.polyfield.entity.TankEntity;
import com.bnnn.polyfield.net.PfNetwork;
import com.bnnn.polyfield.weapon.GunItem;
import com.bnnn.polyfield.weapon.GunLogic;
import com.bnnn.polyfield.weapon.Weapon;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/** Ammo counter, reload bar, hit marker and tank status. */
public final class PfHud {
    private PfHud() {
    }

    public static void render(DrawContext ctx, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity p = client.player;
        if (p == null || client.options.hudHidden) {
            return;
        }
        TextRenderer tr = client.textRenderer;
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();

        if (ClientGunState.hitMarker > 0) {
            int cx = w / 2;
            int cy = h / 2;
            boolean kill = (ClientGunState.hitFlags & PfNetwork.HIT_KILL) != 0;
            boolean head = (ClientGunState.hitFlags & PfNetwork.HIT_HEADSHOT) != 0;
            int color = kill ? 0xFFFF3030 : head ? 0xFFFFC030 : 0xFFFFFFFF;
            for (int i = 3; i <= 6; i++) {
                ctx.fill(cx - i, cy - i, cx - i + 1, cy - i + 1, color);
                ctx.fill(cx + i, cy - i, cx + i + 1, cy - i + 1, color);
                ctx.fill(cx - i, cy + i, cx - i + 1, cy + i + 1, color);
                ctx.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, color);
            }
        }

        if (p.getVehicle() instanceof TankEntity tank) {
            String name = Text.translatable("entity.polyfield." + tank.type.id).getString();
            int hp = Math.round(tank.getHealth());
            float rl = tank.reloadProgress();
            Text line = Text.translatable("hud.polyfield.tank", name, hp, Math.round(tank.type.maxHealth),
                    rl >= 1f ? Text.translatable("hud.polyfield.loaded") : Text.literal(String.format("%.1fs",
                            (1f - rl) * tank.type.reloadTicks / 20f)));
            int x = w - tr.getWidth(line) - 8;
            ctx.drawTextWithShadow(tr, line, x, h - 22, rl >= 1f ? 0xA0FFA0 : 0xFFD060);
            return;
        }

        ItemStack stack = p.getMainHandStack();
        if (!(stack.getItem() instanceof GunItem gun)) {
            return;
        }
        Weapon wpn = gun.weapon;
        int ammo = GunItem.getAmmo(stack);
        String reserve = GunLogic.freeAmmo(p) ? "∞" : Integer.toString(GunLogic.countAmmo(p, wpn.ammo));
        Text name = Text.translatable("item.polyfield." + wpn.id);
        String count = ammo + " / " + reserve;
        int x = w - 8;
        int y = h - 34;
        ctx.drawTextWithShadow(tr, name, x - tr.getWidth(name), y, 0xDDDDDD);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y + 10, 0);
        ctx.getMatrices().scale(1.5f, 1.5f, 1f);
        int cw = tr.getWidth(count);
        ctx.drawTextWithShadow(tr, count, -cw, 0, ammo == 0 ? 0xFF5050 : ammo <= wpn.magazine / 4 ? 0xFFC040 : 0xFFFFFF);
        ctx.getMatrices().pop();
        float rel = ClientGunState.reloadProgress(p, tickDelta);
        if (rel >= 0f) {
            Text t = Text.translatable("hud.polyfield.reloading");
            int bw = 60;
            int bx = w / 2 - bw / 2;
            int by = h / 2 + 14;
            ctx.drawTextWithShadow(tr, t, w / 2 - tr.getWidth(t) / 2, by - 11, 0xFFFFFF);
            ctx.fill(bx, by, bx + bw, by + 3, 0x80000000);
            ctx.fill(bx, by, bx + Math.round(bw * rel), by + 3, 0xFFFFD040);
        }
    }
}
