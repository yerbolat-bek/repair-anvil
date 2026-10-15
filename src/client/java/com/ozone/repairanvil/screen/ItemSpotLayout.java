package com.ozone.repairanvil.screen;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.ItemStack;

/**
 * Picks where the red "damage spots" lie on the enlarged item.
 * Everything is in the 16x16 item pixel grid and is deterministic per (item, spot count).
 * Purely visual and client side - the server only knows spot indices.
 */
public final class ItemSpotLayout {

    /** One spot: its centre pixel and the item pixels it covers (all opaque, so it never leaves the item). */
    public record Spot(int px, int py, List<int[]> pixels) {
    }

    private static final Map<ResourceLocation, boolean[][]> GRIDS = new HashMap<>();
    private static final Map<String, List<Spot>> LAYOUTS = new HashMap<>();

    private ItemSpotLayout() {
    }

    public static List<Spot> get(ItemStack stack, int count) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return LAYOUTS.computeIfAbsent(id + "#" + count, k -> compute(id, count));
    }

    // ------------------------------------------------------------------ texture -> opaque grid

    private static boolean[][] grid(ResourceLocation id) {
        return GRIDS.computeIfAbsent(id, ItemSpotLayout::loadGrid);
    }

    private static boolean[][] loadGrid(ResourceLocation id) {
        boolean[][] grid = new boolean[16][16];
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(
                id.getNamespace(), "textures/item/" + id.getPath() + ".png");

        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(texture);
        boolean any = false;
        if (resource.isPresent()) {
            try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
                int size = image.getWidth();                 // animated textures are taller: use the first frame
                for (int gy = 0; gy < 16; gy++) {
                    for (int gx = 0; gx < 16; gx++) {
                        int sx = Math.min(size - 1, gx * size / 16 + size / 32);
                        int sy = Math.min(image.getHeight() - 1, gy * size / 16 + size / 32);
                        int alpha = (image.getPixel(sx, sy) >>> 24) & 0xFF;
                        if (alpha > 128) {
                            grid[gx][gy] = true;
                            any = true;
                        }
                    }
                }
            } catch (Exception ignored) {
                any = false;
            }
        }

        return any ? grid : discGrid();
    }

    /** Stand-in silhouette for 3D / special items: a small disc, safely inside the icon. */
    private static boolean[][] discGrid() {
        boolean[][] grid = new boolean[16][16];
        for (int gy = 0; gy < 16; gy++) {
            for (int gx = 0; gx < 16; gx++) {
                double dx = gx - 7.5, dy = gy - 7.5;
                grid[gx][gy] = dx * dx + dy * dy <= 25;
            }
        }
        return grid;
    }

    private static int countSolid(boolean[][] grid) {
        int n = 0;
        for (boolean[] column : grid) {
            for (boolean solid : column) {
                if (solid) {
                    n++;
                }
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ spot selection

    private static List<Spot> compute(ResourceLocation id, int count) {
        boolean[][] grid = grid(id);
        if (countSolid(grid) < count) {
            grid = discGrid();   // too few pixels to place every spot
        }

        // depth = how many solid pixels in a row until the edge (0..3), the minimum over 4 directions
        List<int[]> all = new ArrayList<>();
        List<int[]> inner = new ArrayList<>();
        List<int[]> deep = new ArrayList<>();
        for (int gy = 0; gy < 16; gy++) {
            for (int gx = 0; gx < 16; gx++) {
                if (!grid[gx][gy]) {
                    continue;
                }
                int depth = Math.min(
                        Math.min(run(grid, gx, gy, 1, 0), run(grid, gx, gy, -1, 0)),
                        Math.min(run(grid, gx, gy, 0, 1), run(grid, gx, gy, 0, -1)));
                int[] pixel = {gx, gy};
                all.add(pixel);
                if (depth >= 1) {
                    inner.add(pixel);
                }
                if (depth >= 2) {
                    deep.add(pixel);
                }
            }
        }

        List<int[]> pool = deep.size() >= count ? deep : (inner.size() >= count ? inner : all);

        // farthest-point sampling keeps the spots spread over the whole item
        Random random = new Random(id.toString().hashCode() * 31L + count);
        List<int[]> chosen = new ArrayList<>();
        chosen.add(pool.get(random.nextInt(pool.size())));
        while (chosen.size() < Math.min(count, pool.size())) {
            int[] best = null;
            double bestDist = -1;
            for (int[] p : pool) {
                double nearest = Double.MAX_VALUE;
                for (int[] c : chosen) {
                    double dx = p[0] - c[0], dy = p[1] - c[1];
                    nearest = Math.min(nearest, dx * dx + dy * dy);
                }
                if (nearest > bestDist) {
                    bestDist = nearest;
                    best = p;
                }
            }
            chosen.add(best);
        }

        // each spot = centre pixel + its solid 4-neighbours (a small cross painted on the item)
        int[][] neighbours = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        List<Spot> spots = new ArrayList<>();
        for (int[] c : chosen) {
            List<int[]> pixels = new ArrayList<>();
            pixels.add(new int[]{c[0], c[1]});
            for (int[] d : neighbours) {
                int nx = c[0] + d[0];
                int ny = c[1] + d[1];
                if (isSolid(grid, nx, ny)) {
                    pixels.add(new int[]{nx, ny});
                }
            }
            spots.add(new Spot(c[0], c[1], pixels));
        }
        return spots;
    }

    private static int run(boolean[][] grid, int x, int y, int dx, int dy) {
        int n = 0;
        int cx = x + dx;
        int cy = y + dy;
        while (n < 3 && isSolid(grid, cx, cy)) {
            n++;
            cx += dx;
            cy += dy;
        }
        return n;
    }

    private static boolean isSolid(boolean[][] grid, int x, int y) {
        return x >= 0 && y >= 0 && x < 16 && y < 16 && grid[x][y];
    }
}