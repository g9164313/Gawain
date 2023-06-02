package prj.daemon;

import javafx.beans.binding.FloatBinding;
import javafx.beans.property.FloatProperty;
import javafx.beans.property.IntegerProperty;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import narl.itrc.DevModbus;

/**
 * PID controller for FA-TAIE.
 * Refer to: https://www.fa-taie.com.tw/admin/download/file/2014-12-31/54a3b0464a601.pdf
 */
public class DevFYx00 extends DevModbus {

	public final IntegerProperty sv,pv;
	public final String title;

	public DevFYx00(final String name){
		title = name;
		mapAddress(16,
			"h0000",
			"h0007","h0008",
			"h0089",
			"h008A"
		);
		sv = mapInteger(0x0000);
		pv = mapInteger(0x008A);
	}

	public FloatBinding prop_num_SV(){
		return sv.divide(10f);
	}
	public FloatBinding prop_num_PV(){
		return pv.divide(10f);
	}

	public static Pane genInfoPanel(final DevFYx00 dev){

		final Label[] txt = {
			new Label("SV："), new Label(),
			new Label("PV："), new Label()
		};
		for(Label obj:txt){
			obj.getStyleClass().addAll("font-size4");
		}
		GridPane.setHgrow(txt[1], Priority.ALWAYS);
		GridPane.setHgrow(txt[3], Priority.ALWAYS);

		txt[1].textProperty().bind(dev.prop_num_SV().asString("%.1f"));
		txt[3].textProperty().bind(dev.prop_num_PV().asString("%.1f"));

		final GridPane lay = new GridPane();
		lay.getStyleClass().addAll("box-pad");
		lay.add(new Label(dev.title), 0, 0, 2, 1);
		lay.addRow(1, txt[0], txt[1]);
		lay.addRow(2, txt[2], txt[3]);
		return lay;
	}
}
