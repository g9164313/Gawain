package prj.daemon;

import java.math.BigDecimal;

import javafx.beans.property.IntegerProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import narl.itrc.DevModbus;

/**
 * A Panel controller measures current/voltage/Pt-100/potentionmeter/register.<p>
 * Just a ADC convert with 7-segment panel.<p>
 * Support Modbus communication.<p>
 * Refer: https://cht.nahua.com.tw/meter/48x96/ma-sm2/index.htm
 */
public class DevMASMx extends DevModbus {
	
	public final IntegerProperty DP,STATUS,AZ,HOLD,MAX,DISPLAY;
	public final String title;

	public DevMASMx(final String name){
		title = name;
		mapAddress(16,
			"h0000",
			"h0040-0048"
		);
		DP     = mapInteger(0x0000);
		STATUS = mapInteger(0x0040);
		AZ     = mapInteger(0x0042);
		HOLD   = mapInteger(0x0044);
		MAX    = mapInteger(0x0046);
		DISPLAY= mapInteger(0x0048);
	}

	public static Pane genInfoPanel(final DevMASMx dev){

		final Label txt_display = new Label();
		txt_display.setMinWidth(40f);		
		txt_display.getStyleClass().addAll("font-size4");
		dev.DISPLAY.addListener((obv,oldVal,newVal)->{
			final int dp = dev.DP.get();
			BigDecimal num = BigDecimal.valueOf(dev.DISPLAY.get());
			num = num.movePointLeft(dp);
			txt_display.setText(num.toString());
		});

		final Label[] txt = {
			new Label(), new Label(), new Label(),//STATUS text
		};	
		txt[0].textProperty().bind(dev.MAX.asString());
		txt[1].textProperty().bind(dev.HOLD.asString());
		txt[2].textProperty().bind(dev.AZ.asString());
		//-----------------

		final HBox sts1 = new HBox(new Label("MAX"),txt[0]);
		sts1.visibleProperty().bind(dev.STATUS.isEqualTo(8));//MAX - 顯示最大保持值
		sts1.setAlignment(Pos.CENTER_LEFT);
		
		final HBox sts2 = new HBox(new Label("HOLD"),txt[1]);
		sts2.visibleProperty().bind(dev.STATUS.isEqualTo(4));//HOLD - 顯示保持值
		sts2.setAlignment(Pos.CENTER_LEFT);
		
		final HBox sts3 = new HBox(new Label("AZ："),txt[2]);
		sts3.visibleProperty().bind(dev.STATUS.isEqualTo(2));//AZ - 自動歸零值
		sts3.setAlignment(Pos.CENTER_LEFT);

		final Label sts4 = new Label("Lock");
		sts4.visibleProperty().bind(dev.STATUS.isEqualTo(1));//Lock

		final VBox lay0 = new VBox(
			new Label(dev.title),
			txt_display,
			new StackPane(sts1, sts2, sts3, sts4)
		); 
		lay0.getStyleClass().addAll("box-pad");
		return lay0;
	}
}
