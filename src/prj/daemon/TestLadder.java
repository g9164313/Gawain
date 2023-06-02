package prj.daemon;

import java.util.Optional;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleFloatProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import narl.itrc.Ladder;
import narl.itrc.Misc;
import narl.itrc.PanBase;
import narl.itrc.PropBundle;
import narl.itrc.Stepper;

public class TestLadder extends PanBase {

	public static class stp_count extends Stepper {
		
		final Label label = new Label();
		
		public stp_count(final String title){
			label.setText(title);
			chain(op11);
		}
		void show_mesg(final int idx) {
			tst1.set(idx);
			tst2.set(tst2.get()+(float)Math.random());

			String txt = label.getText();
			int i = txt.indexOf(".");
			if(i>=0) {
				txt = txt.substring(0,i);
			}
			txt = txt + "." + idx;
			Misc.logv(txt);
			label.setText(txt);
		}
		
		int hold;
		final Runnable op11 = ()->{
			show_mesg(11);
			hold = 3;
			trig(this.op12);
		};
		final Runnable op12 = ()->{
			show_mesg(12);
			hold--;
			if(hold>0) {
				trig(this.op12);
			}else {
				trig(this.op13);
			}
		};
		final Runnable op13 = ()->{
			show_mesg(13);
			hold = 3;
			trig(this.op14);
		};
		final Runnable op14 = ()->{
			show_mesg(14);
			trig(this.op15);			
		};
		final Runnable op15 = ()->{
			show_mesg(15);
			hold--;
			if(hold>0) {				
				trig(this.op14);
			}else {				
				trig(null);
			}			
		};
		@Override
		public Node getContent() {
			label.getStyleClass().add("box-border");
			label.setMinWidth(100);
			return label;
		}
		@Override
		public void eventEdit() { }
		@Override
		public String flatten() { return ""; }
		@Override
		public void expand(String txt) { }		
	};
	
	public static class aaa extends stp_count {
		//default constructor must be 'public'.
		//class must be'static'.
		public aaa() { super("aaa"); }
	};
	public static class bbb extends stp_count {
		public bbb() { super("bbb"); }
	};
	public static class ccc extends stp_count {
		public ccc() { super("ccc"); }
	};

	final Ladder ladder = new Ladder();
	final static SimpleIntegerProperty tst1 = new SimpleIntegerProperty();
	final static SimpleFloatProperty   tst2 = new SimpleFloatProperty();
	final static SimpleBooleanProperty tst3 = new SimpleBooleanProperty();

	public TestLadder(Stage stg) {
		super(stg);
	}
	
	@Override
	public Node eventLayout(PanBase self) {
		
		ladder.addStep("aaa", aaa.class);
		ladder.addStep("bbb", bbb.class);
		ladder.addStep("ccc", ccc.class);
		ladder.addStep("Counter", Stepper.Counter.class);
		ladder.addStep("Sticker", Stepper.Sticker.class);
		
		final Label[] inf = {
			new Label(), 
			new Label(), 
			new Label(),
		};
		inf[0].textProperty().bind(tst1.asString());
		inf[1].textProperty().bind(tst2.asString("%.3f"));
		inf[2].textProperty().bind(tst3.asString());

		final GridPane lay1 = new GridPane();		
		lay1.getStyleClass().addAll("box-pad");
		lay1.setPrefWidth(157.);
		lay1.addRow(0, new Label("tst1:"), inf[0]);
		lay1.addRow(1, new Label("tst2:"), inf[1]);
		lay1.addRow(2, new Label("tst3:"), inf[2]);

		final BorderPane lay = new BorderPane();
		lay.setCenter(ladder);
		lay.setRight(lay1);
		return lay;
	}

	private final Runnable event_hook1 = ()->{
		Alert dia = new Alert(AlertType.INFORMATION);
		dia.setTitle("測試點");
		dia.setHeaderText("測試 hook 執行");
		dia.setContentText(null);
		dia.showAndWait();
	};

	@Override
	public Object[] getOpenAPI(){
		Object[] obj = super.getOpenAPI();
		obj[1] = new PropBundle()			
			.pack("tst1"  ,tst1)
			.pack("tst2"  ,tst2)
			.pack("tst3"  ,tst3)
			.pack("ladder",ladder)
			.pack("hook1" ,event_hook1);
		return obj;
	}
}
