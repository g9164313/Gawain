package prj.daemon;

import java.math.BigDecimal;

import javafx.beans.property.FloatProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleFloatProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import narl.itrc.DevModbus;

/**
 * A Panel controller measures current/voltage/Pt-100/potentionmeter/register.<p>
 * Just a ADC convert with 7-segment panel.<p>
 * Support Modbus communication.<p>
 * Refer: https://cht.nahua.com.tw/meter/48x96/ma-sm2/index.htm
 */
public class DevMASMx extends DevModbus {
	
	public final IntegerProperty DP,STATUS,AZ,HOLD,MAX,DISPLAY;
	
	public final FloatProperty  num_display = new SimpleFloatProperty();
	public final StringProperty txt_display = new SimpleStringProperty("？？？？");
	
	@Override
	protected void looper_event(){
		final int dp = DP.get();
		BigDecimal val = BigDecimal.valueOf(DISPLAY.get());
		val = val.movePointLeft(dp);
		num_display.set(val.floatValue());
		txt_display.set(val.toString());
	}

	public DevMASMx(){
		this("MA-SM3");
	}
	public DevMASMx(final String tag){
		TAG = tag;
		mapAddress(16,"h0001","h0006","h002A-0031");
		STATUS = mapShort(0x0001);
		DP     = mapShort(0x0006);		
		AZ     = mapInteger(0x002A);
		HOLD   = mapInteger(0x002C);
		MAX    = mapInteger(0x002E);
		DISPLAY= mapInteger(0x0030);
	}

	public static Pane genInfoPanel(final String title, final DevMASMx dev){

		final Label txt_disp = new Label();
		//txt_disp.setMinWidth(178.);		
		//txt_disp.getStyleClass().addAll("font-size4");
		txt_disp.textProperty().bind(dev.txt_display);

		final Label[] txt = {
			new Label(), new Label(), new Label(),//STATUS text
		};	
		txt[0].textProperty().bind(dev.MAX.asString());
		txt[1].textProperty().bind(dev.HOLD.asString());
		txt[2].textProperty().bind(dev.AZ.asString());
		//-----------------

		/*final HBox sts1 = new HBox(new Label("MAX："),txt[0]);
		sts1.visibleProperty().bind(dev.STATUS.isEqualTo(8));//MAX - 顯示最大保持值
		//sts1.setAlignment(Pos.CENTER_LEFT);
		
		final HBox sts2 = new HBox(new Label("HOLD："),txt[1]);
		sts2.visibleProperty().bind(dev.STATUS.isEqualTo(4));//HOLD - 顯示保持值
		
		final HBox sts3 = new HBox(new Label("AZ："),txt[2]);
		sts3.visibleProperty().bind(dev.STATUS.isEqualTo(2));//AZ - 自動歸零值

		final Label sts4 = new Label("Lock！！");
		sts4.visibleProperty().bind(dev.STATUS.isEqualTo(1));//Lock

		final Label sts5 = new Label("work");
		sts5.visibleProperty().bind(dev.STATUS.isEqualTo(0));//Lock*/


		final Label txt_dp = new Label();
		txt_dp.textProperty().bind(dev.DP.asString("DP:%d"));
		final Label txt_sta = new Label();
		txt_sta.textProperty().bind(dev.STATUS.asString("STA:%d"));
		final Label txt_dsp = new Label();
		txt_dsp.textProperty().bind(dev.DISPLAY.asString("DIS:%d"));

		final GridPane lay = new GridPane();
		lay.getStyleClass().addAll("box-pad");
		lay.add(new Label(title+"數值："), 0, 0, 2, 1);
		lay.add(txt_disp, 0, 1, 2, 1);
		lay.add(new Separator(), 0, 2, 2, 1);
		return lay;
	}
}
