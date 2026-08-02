package mapwriter.event;

import mapwriter.Mw;
import moddedmite.rustedironcore.api.event.Handlers;
import moddedmite.rustedironcore.api.event.listener.IChunkLoadListener;
import moddedmite.rustedironcore.api.event.listener.IConnectionListener;
import moddedmite.rustedironcore.api.event.listener.ITickListener;
import moddedmite.rustedironcore.api.event.listener.IWorldLoadListener;
import net.minecraft.Chunk;
import net.minecraft.Minecraft;
import net.minecraft.NetClientHandler;
import net.minecraft.Packet1Login;
import net.minecraft.Packet255KickDisconnect;
import net.minecraft.WorldClient;
import net.minecraft.server.MinecraftServer;


public class MwEvents extends Handlers {
	
	public static void register() {
		Handlers.Connection.register(new IConnectionListener() {
			@Override
			public void onIntegratedConnection(NetClientHandler clientHandler, MinecraftServer server) {
				Mw.getInstance().onConnectionOpened();
			}

			@Override
			public void onClientConnection(NetClientHandler clientHandler, String server, int port) {
				Mw.getInstance().onConnectionOpened(server, port);
			}
			
			@Override
			public void onClientLoggedIn(NetClientHandler clientHandler, Packet1Login login) {
				Mw.getInstance().onClientLoggedIn(login);
			}
			
			@Override
			public void onClientQuit(NetClientHandler clientHandler, Packet255KickDisconnect disconnect) {
				Mw.getInstance().onConnectionClosed();
			}
			
		});
		Handlers.ChunkLoad.register(new IChunkLoadListener() {
			@Override
			public void onClientChunkLoad(Chunk chunk) {
				Mw.getInstance().onChunkLoad(chunk);
			}
			
			@Override
			public void onClientChunkUnload(Chunk chunk) {
				Mw.getInstance().onChunkUnload(chunk);
			}
		});
		Handlers.Tick.register(new ITickListener() {
			@Override
			public void onRenderTick(float partialTick) {
				if (Minecraft.getMinecraft().gameSettings.gui_mode == 0 && !Minecraft.getMinecraft().gameSettings.showDebugInfo)
					Mw.getInstance().onRenderTick();
			}
		});
		Handlers.WorldLoad.register(new IWorldLoadListener() {
			@Override
			public void onWorldLoad(WorldClient world) {
				if (world != null) {
					Mw.getInstance().onWorldLoad(world);
				}
			}
			
			@Override
			public void onWorldUnload(WorldClient worldBefore) {
//				if (par1WorldClient == null) {
					Mw.getInstance().onWorldUnload(worldBefore);
//				}
			}
		});
	}
}
