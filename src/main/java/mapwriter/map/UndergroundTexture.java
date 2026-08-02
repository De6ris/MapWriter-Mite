package mapwriter.map;

import java.awt.Point;
import java.util.Arrays;

import net.minecraft.*;

import org.lwjgl.opengl.GL11;

import mapwriter.Mw;
import mapwriter.Texture;
import mapwriter.region.ChunkRender;
import mapwriter.region.IChunk;

public class UndergroundTexture extends Texture {

    private Mw mw;
    private int px = Integer.MIN_VALUE;
    private int py = Integer.MIN_VALUE;
    private int pz = Integer.MIN_VALUE;
    private int updateX;
    private int updateZ;
    private byte[][] updateFlags;
    private int updateGridW;
    private int updateGridH;
    private int viewCxMin, viewCzMin, viewCxMax, viewCzMax;
    private Point[] loadedChunkArray;
    private int textureSize;
    private int textureChunks;
    private int[] pixels;

    class RenderChunk implements IChunk {
        Chunk chunk;

        public RenderChunk(Chunk chunk) {
            this.chunk = chunk;
        }

        @Override
        public int getMaxY() {
            return this.chunk.getTopFilledSegment() + 15;
        }

        @Override
        public int getBlockAndMetadata(int x, int y, int z) {
            int blockID = this.chunk.getBlockID(x, y, z);
            int meta = this.chunk.getBlockMetadata(x, y, z);
            return ((blockID & 0xfff) << 4) | (meta & 0xf);
        }

        @Override
        public int getBiome(int x, int z) {
            return (int) this.chunk.getBiomeArray()[(z * 16) + x];
        }

        @Override
        public int getLightValue(int x, int y, int z) {
//            return this.chunk.getBlockLightValue(x, y, z, 0);
            return 15;
        }
    }

    public UndergroundTexture(Mw mw, int textureSize, boolean linearScaling) {
        super(textureSize, textureSize, 0x00000000, GL11.GL_NEAREST, GL11.GL_NEAREST, GL11.GL_REPEAT);
        this.setLinearScaling(false);
        this.textureSize = textureSize;
        this.textureChunks = textureSize >> 4;
        this.loadedChunkArray = new Point[this.textureChunks * this.textureChunks];
        this.pixels = new int[textureSize * textureSize];
        Arrays.fill(this.pixels, 0xff000000);
        this.mw = mw;
    }

    public void clearChunkPixels(int cx, int cz) {
        int tx = (cx << 4) & (this.textureSize - 1);
        int tz = (cz << 4) & (this.textureSize - 1);
        for (int j = 0; j < 16; j++) {
            int offset = ((tz + j) * this.textureSize) + tx;
            Arrays.fill(this.pixels, offset, offset + 16, 0xff000000);
        }
        this.updateTextureArea(tx, tz, 16, 16);
    }

    void renderToTexture(int y) {
        this.setPixelBufPosition(0);
	    for (int colour : this.pixels) {
		    int height = (colour >> 24) & 0xff;
		    int alpha = (y >= height) ? 255 - ((y - height) * 8) : 0;
		    if (alpha < 0) {
			    alpha = 0;
		    }
		    this.pixelBufPut(((alpha << 24) & 0xff000000) | (colour & 0xffffff));
	    }
        this.updateTexture();
    }

    public int getLoadedChunkOffset(int cx, int cz) {
        int cxOffset = cx & (this.textureChunks - 1);
        int czOffset = cz & (this.textureChunks - 1);
        return (czOffset * this.textureChunks) + cxOffset;
    }

    public void requestView(MapView view) {
        this.viewCxMin = ((int) view.getMinX()) >> 4;
        this.viewCzMin = ((int) view.getMinZ()) >> 4;
        this.viewCxMax = ((int) view.getMaxX()) >> 4;
        this.viewCzMax = ((int) view.getMaxZ()) >> 4;
        for (int cz = this.viewCzMin; cz <= this.viewCzMax; cz++) {
            for (int cx = this.viewCxMin; cx <= this.viewCxMax; cx++) {
                Point requestedChunk = new Point(cx, cz);
                int offset = this.getLoadedChunkOffset(cx, cz);
                Point currentChunk = this.loadedChunkArray[offset];
                if ((currentChunk == null) || !currentChunk.equals(requestedChunk)) {
                    this.clearChunkPixels(cx, cz);
                    this.loadedChunkArray[offset] = requestedChunk;
                }
            }
        }
    }

    public boolean isChunkInTexture(int cx, int cz) {
        Point requestedChunk = new Point(cx, cz);
        int offset = this.getLoadedChunkOffset(cx, cz);
        Point chunk = this.loadedChunkArray[offset];
        return (chunk != null) && chunk.equals(requestedChunk);
    }

    public void update() {
        int px = this.mw.playerXInt;
        int py = this.mw.playerYInt;
        int pz = this.mw.playerZInt;

        if ((px == this.px) && (py == this.py) && (pz == this.pz)) {
            return;
        }
        this.px = px;
        this.py = py;
        this.pz = pz;

        this.updateGridW = this.viewCxMax - this.viewCxMin + 1;
        this.updateGridH = this.viewCzMax - this.viewCzMin + 1;
        if (this.updateGridW < 1) { this.updateGridW = 3; this.updateGridH = 3; }
        int totalChunks = this.updateGridW * (this.viewCzMax - this.viewCzMin + 1);
        if (this.updateFlags == null || this.updateFlags.length != totalChunks) {
            this.updateFlags = new byte[totalChunks][256];
        }
        this.clearFlags();

        this.updateX = this.viewCxMin;
        this.updateZ = this.viewCzMin;

        this.processBlock(
                this.px - (this.updateX << 4),
                this.py,
                this.pz - (this.updateZ << 4)
        );

        WorldClient world = this.mw.mc.theWorld;
        int flagOffset = 0;
        for (int cz = this.viewCzMin; cz <= this.viewCzMax; cz++) {
            for (int cx = this.viewCxMin; cx <= this.viewCxMax; cx++) {
                if (this.isChunkInTexture(cx, cz)) {
                    Chunk chunk = world.getChunkFromChunkCoords(cx, cz);
                    int tx = (cx << 4) & (this.textureSize - 1);
                    int tz = (cz << 4) & (this.textureSize - 1);
                    int pixelOffset = (tz * this.textureSize) + tx;
                    byte[] mask = this.updateFlags[flagOffset];
                    ChunkRender.renderUnderground(
                            this.mw.blockColours,
                            new RenderChunk(chunk),
                            this.pixels, pixelOffset, this.textureSize,
                            this.py, mask
                    );
                }
                flagOffset += 1;
            }
        }

        this.renderToTexture(this.py + 1);
    }

    private void clearFlags() {
        for (byte[] chunkFlags : this.updateFlags) {
            Arrays.fill(chunkFlags, ChunkRender.FLAG_UNPROCESSED);
        }
    }

    private void processBlock(int sXi, int y, int startZi) {
        int[] stackX = new int[65536];
        int[] stackZ = new int[65536];
        int stackPos = 0;
        stackX[stackPos] = sXi;
        stackZ[stackPos] = startZi;
        stackPos++;

        WorldClient world = this.mw.mc.theWorld;

        while (stackPos > 0) {
            stackPos--;
            int xi = stackX[stackPos];
            int zi = stackZ[stackPos];

            // skip if outside the update grid
            if (xi < 0 || zi < 0 || (xi >> 4) >= this.updateGridW || (zi >> 4) >= this.updateGridH) {
                continue;
            }

            int x = (this.updateX << 4) + xi;
            int z = (this.updateZ << 4) + zi;

            int xDist = this.px - x;
            int zDist = this.pz - z;

            if (((xDist * xDist) + (zDist * zDist)) > 65536) {
                continue;
            }

            if (!this.isChunkInTexture(x >> 4, z >> 4)) {
                continue;
            }

            int chunkOffset = ((zi >> 4) * this.updateGridW) + (xi >> 4);
            int columnXi = xi & 0xf;
            int columnZi = zi & 0xf;
            int columnOffset = (columnZi << 4) + columnXi;
            byte columnFlag = this.updateFlags[chunkOffset][columnOffset];

            if (columnFlag != ChunkRender.FLAG_UNPROCESSED) {
                // if column not yet processed
                continue;
            }

            int blockID = world.getBlockId(x, y, z);
            Block block = Block.blocksList[blockID];
//					if ((block == null) || !block.isOpaqueCube()) {
            if ((block == null)) {
                // if block is not opaque
                this.updateFlags[chunkOffset][columnOffset] = (byte) ChunkRender.FLAG_NON_OPAQUE;
                if (stackPos + 4 < stackX.length) {
                    stackX[stackPos] = xi + 1; stackZ[stackPos] = zi; stackPos++;
                    stackX[stackPos] = xi - 1; stackZ[stackPos] = zi; stackPos++;
                    stackX[stackPos] = xi; stackZ[stackPos] = zi + 1; stackPos++;
                    stackX[stackPos] = xi; stackZ[stackPos] = zi - 1; stackPos++;
                }
            } else {
                // block is opaque
                this.updateFlags[chunkOffset][columnOffset] = (byte) ChunkRender.FLAG_OPAQUE;
            }
        }
    }

}
