package mapwriter.gui;

import mapwriter.Mw;
import mapwriter.map.MapView;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.GuiScreen;

@Environment(EnvType.CLIENT)
public class MwGuiDimensionDialog extends MwGuiTextDialog {

	final Mw mw;
	final MapView mapView;
	final int dimension;
    
    public MwGuiDimensionDialog(GuiScreen parentScreen, Mw mw, MapView mapView, int dimension) {
        super(parentScreen, "Set dimension to:", "" + dimension, "invalid dimension");
        this.mw = mw;
        this.mapView = mapView;
        this.dimension = dimension;
    }
    	
	@Override
	public boolean submit() {
		boolean done = false;
		int dimension = this.getInputAsInt();
		if (this.inputValid) {
    		this.mapView.setDimensionAndAdjustZoom(dimension);
    		this.mw.miniMap.view.setDimension(dimension);
    		this.mw.addDimension(dimension);
    		done = true;
		}
		return done;
	}
}
