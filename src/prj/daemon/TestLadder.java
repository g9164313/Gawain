package prj.daemon;

import java.util.Optional;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import narl.itrc.Ladder;
import narl.itrc.Misc;
import narl.itrc.PanBase;
import narl.itrc.Stepper;

public class TestLadder extends PanBase {

	Ladder ladder = new Ladder();
	
	public static class stp_count extends Stepper {
		
		final Label label = new Label();
		
		public stp_count(final String title){
			label.setText(title);
			chain(op11);
		}
		void show_mesg(final int idx) {
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
			hold = 1;
			trig(this.op14);
		};
		final Runnable op14 = ()->{
			show_mesg(14);
			trig(this.op15);
		};
		final Runnable op15 = ()->{
			show_mesg(15);
			hold--;
			if(hold>=0) {				
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

	public TestLadder(final Stage stg) {
	}
	
	@Override
	public Node eventLayout(PanBase self) {
		
		ladder.addStep("aaa", aaa.class);
		ladder.addStep("bbb", bbb.class);
		ladder.addStep("ccc", ccc.class);
		ladder.addStep("Counter", Stepper.Counter.class);
		ladder.addStep("Sticker", Stepper.Sticker.class);
		
		final BorderPane lay = new BorderPane();
		lay.setCenter(ladder);
		return lay;
	}
}
