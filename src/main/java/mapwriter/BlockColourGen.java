package mapwriter;

import mapwriter.region.BlockColours;
import mapwriter.region.BlockColours.BlockType;
import net.minecraft.*;

// Static class to generate BlockColours.
// This is separate from BlockColours because it needs to run in the GL rendering thread
// whereas the generated BlockColours object is used only in the background thread.
// So basically split to make it clear that BlockColourGen and the generated BlockColours
// must not have any interaction after it is generated.

public class BlockColourGen {

    private static int getIconMapColour(Icon icon, Texture terrainTexture) {
        // flipped icons have the U and V coords reversed (minU > maxU, minV > maxV).
        // thanks go to taelnia for fixing this.
        int iconX = (int) Math.round(((float) terrainTexture.w) * Math.min(icon.getMinU(), icon.getMaxU()));
        int iconY = (int) Math.round(((float) terrainTexture.h) * Math.min(icon.getMinV(), icon.getMaxV()));
        int iconWidth = (int) Math.round(((float) terrainTexture.w) * Math.abs(icon.getMaxU() - icon.getMinU()));
        int iconHeight = (int) Math.round(((float) terrainTexture.h) * Math.abs(icon.getMaxV() - icon.getMinV()));

        int[] pixels = new int[iconWidth * iconHeight];

        //MwUtil.log("(%d, %d) %dx%d", iconX, iconY, iconWidth, iconHeight);

        terrainTexture.getRGB(iconX, iconY, iconWidth, iconHeight, pixels, 0, iconWidth);

        // need to use custom averaging routine rather than scaling down to one pixel to
        // stop transparent pixel colours being included in the average.
        return Render.getAverageColourOfArray(pixels);
    }

    private static int adjustBlockColourFromType(BlockColours bc, int blockAndMeta, int blockColour) {
        // for normal blocks multiply the block colour by the render colour.
        // for other blocks the block colour will be multiplied by the biome colour.
        Block block = Block.blocksList[blockAndMeta >> 4];
        BlockType blockType = bc.getBlockType(blockAndMeta);
        switch (blockType) {

            case OPAQUE:
                blockColour |= 0xff000000;
            case NORMAL:
                // fix crash when mods don't implement getRenderColor for all
                // block meta values.
                try {
                    int renderColour = block.getRenderColor(blockAndMeta & 0xf);
                    if (renderColour != 0xffffff) {
                        blockColour = Render.multiplyColours(blockColour, 0xff000000 | renderColour);
                    }
                } catch (RuntimeException e) {
                    // do nothing
                }
                break;
            case LEAVES:
                // leaves look weird on the map if they are not opaque.
                // they also look too dark if the render colour is applied.
                blockColour |= 0xff000000;
                break;
            default:
                break;
        }
        return blockColour;
    }

    private static void genBiomeColours(BlockColours bc) {
        // generate array of foliage, grass, and water colour multipliers
        // for each biome.

        for (int i = 0; i < BiomeGenBase.biomeList.length; i++) {
            if (BiomeGenBase.biomeList[i] != null) {
//				bc.setBiomeWaterShading(i, BiomeGenBase.biomeList[i].getWaterColorMultiplier() & 0xffffff);
                bc.setBiomeWaterShading(i, BiomeGenBase.biomeList[i].waterColorMultiplier & 0xffffff);
                bc.setBiomeGrassShading(i, BiomeGenBase.biomeList[i].getBiomeGrassColor() & 0xffffff);
                bc.setBiomeFoliageShading(i, BiomeGenBase.biomeList[i].getBiomeFoliageColor() & 0xffffff);
            } else {
                bc.setBiomeWaterShading(i, 0xffffff);
                bc.setBiomeGrassShading(i, 0xffffff);
                bc.setBiomeFoliageShading(i, 0xffffff);
            }
        }
    }

    public static void genBlockColours(BlockColours bc) {

        MwUtil.log("generating block map colours from textures");

        // copy terrain texture to MwRender pixel bytebuffer

        // bind the terrain texture
        //Minecraft.getMinecraft().func_110434_K().func_110577_a(TextureMap.field_110575_b);
        // get the bound texture id
        //int terrainTextureId = Render.getBoundTextureId();

        int terrainTextureId = Minecraft.getMinecraft().getTextureManager().getTexture(TextureMap.locationBlocksTexture).getGlTextureId();

        // create texture object from the currently bound GL texture
        if (terrainTextureId == 0) {
            MwUtil.log("error: could get terrain texture ID");
            return;
        }
        Texture terrainTexture = new Texture(terrainTextureId);

        double u1Last = 0;
        double u2Last = 0;
        double v1Last = 0;
        double v2Last = 0;
        int blockColourLast = 0;
        int e_count = 0;
        int b_count = 0;
        int s_count = 0;

        for (int blockID = 0; blockID < Block.blocksList.length; blockID++) {

            Block block = Block.blocksList[blockID];
            if (block == null) continue;

            for (int dv = 0; dv < 16; dv++) {
                if (!block.isValidMetadata(dv)) continue;

                int blockAndMeta = ((blockID & 0xfff) << 4) | (dv & 0xf);

                int blockColour = 0;

                Icon icon = null;
                try {
                    icon = block.getIcon(1, dv);
                } catch (Exception e) {
                    //MwUtil.log("genFromTextures: exception caught when requesting block texture for %03x:%x", blockID, dv);
                    //e.printStackTrace();
                    e_count++;
                }

                if (icon != null) {
                    double u1 = icon.getMinU();
                    double u2 = icon.getMaxU();
                    double v1 = icon.getMinV();
                    double v2 = icon.getMaxV();

                    if ((u1 == u1Last) && (u2 == u2Last) && (v1 == v1Last) && (v2 == v2Last)) {
                        blockColour = blockColourLast;
                        s_count++;
                    } else {
                        blockColour = getIconMapColour(icon, terrainTexture);
                        u1Last = u1;
                        u2Last = u2;
                        v1Last = v1;
                        v2Last = v2;
                        blockColourLast = blockColour;
                        b_count++;
                    }
                    //if (dv == 0)
                    //	MwUtil.log("block %03x:%x colour = %08x", blockID, dv, blockColour);
                }

                // doesn't work as some leaves blocks aren't rendered using the biome
                // foliage colour
                //try {
                //	if (block.isLeaves(null, 0, 0, 0)) {
                //		bc.setBlockType(blockAndMeta, BlockType.LEAVES);
                //	}
                //} catch (NullPointerException e) {
                //}

                blockColour = adjustBlockColourFromType(bc, blockAndMeta, blockColour);
                bc.setColour(blockAndMeta, blockColour);
            }
        }

        MwUtil.log("processed %d block textures, %d skipped, %d exceptions", b_count, s_count, e_count);

        genBiomeColours(bc);
    }
}
