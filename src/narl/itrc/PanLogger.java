package narl.itrc;

import java.io.Serializable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import javafx.util.Duration;

public class PanLogger extends PanBase {

	private Statement sta;
	
	public static class Mesg implements Serializable {
		private static final long serialVersionUID = -6645917030155354407L;
		final Timestamp stmp;
		final String tag, msg;
		Mesg(ResultSet rs) throws SQLException {
			stmp= rs.getTimestamp(1);//time stamp			
			tag = rs.getString(2);//tag
			msg = rs.getString(3);//information text
		}
		public String getCol0() { return stmp.toString(); }
		public String getCol1() { return tag; }
		public String getCol2() { return msg; }
	};
	
	private final Timeline timer = new Timeline(new KeyFrame(
		Duration.seconds(1), 
		e->refresh_observe()
	)); 
	
	public PanLogger(Stage stg) {
		super(stg);
		sta = Misc.LoggerSta;
		timer.setCycleCount(Animation.INDEFINITE);		
		stage().setOnShown(e->{
			if(sta==null) { return; }
			timer.play();
		});
		stage().setOnCloseRequest(e->timer.stop());
	}
	
	@Override
	public Node eventLayout(PanBase self) {

		final TextField box = new TextField();
		box.setMaxWidth(Double.MAX_VALUE);
		box.setOnAction(e->{
			Misc.logv(box.getText());
			box.clear();
		});
		HBox.setHgrow(box, Priority.ALWAYS);
		
		final HBox lay1 = new HBox(new Label(">>"),box);
		lay1.setAlignment(Pos.BASELINE_LEFT);		
		lay1.getStyleClass().addAll("box-pad");
			
		final BorderPane lay0 = new BorderPane();
		lay0.setCenter(tbl);
		lay0.setBottom(lay1);
		return lay0;
	}
	
	private final TableView<Mesg> tbl = genViewer();
	
	private String prv_sql_txt = String.format(
		"SELECT * FROM logger "+
		"WHERE stmp>='%s' ",
		Misc.getNowText()
	);

	private void refresh_observe() {		
		try {
			ResultSet rs = sta.executeQuery(prv_sql_txt);
			while(rs.next()) {
				tbl.getItems().add(new Mesg(rs));
			}
			if(rs.getRow()!=0) {
				tbl.scrollTo(tbl.getItems().size()-1);
			}			
		} catch (SQLException e) {
			System.err.println(e.getMessage());
		}
		//update for next turn~~~
		prv_sql_txt = String.format(
			"SELECT * FROM logger "+
			"WHERE stmp>='%s' ",
			Misc.getNowText()
		);
	}

	@SuppressWarnings("unchecked")
	public TableView<Mesg> genViewer() {
				
		final TableColumn<Mesg,String> col0 = new TableColumn<>("時間");
		final TableColumn<Mesg,String> col1 = new TableColumn<>("層級");
		final TableColumn<Mesg,String> col2 = new TableColumn<>("訊息");
		
		col0.setCellValueFactory(new PropertyValueFactory<Mesg,String>("col0"));
		col0.setMinWidth(150);		
		col1.setCellValueFactory(new PropertyValueFactory<Mesg,String>("col1"));
		col1.setMinWidth(90);
		col2.setCellValueFactory(new PropertyValueFactory<Mesg,String>("col2"));		
		col2.setMinWidth(400);
		
		final TableView<Mesg> tbl = new TableView<Mesg>();
		tbl.getStyleClass().addAll("font-console");
		tbl.setEditable(false);
		tbl.getColumns().addAll(col0,col1,col2);
		
		final ListChangeListener<Mesg> event = c->{
			c.next();
			final int size = tbl.getItems().size();
	        if (size > 0) {
	        	tbl.scrollTo(size - 1);
	        }
		};
		tbl.getItems().addListener(event);
		
		final MenuItem itm1 = new MenuItem("清除");
		itm1.setOnAction(e->tbl.getItems().clear());
		
		final MenuItem itm2 = new MenuItem("儲存");
		
		final MenuItem itm3 = new MenuItem("最後...");
		itm3.setOnAction(e->{
			prv_sql_txt = String.format(
				"SELECT * FROM logger "+
				"ORDER BY stmp DESC LIMIT 10",
				Misc.getNowText()
			);
			tbl.getItems().clear();
		});
		
		final MenuItem itm4 = new MenuItem("傾倒");
		itm4.setOnAction(e->{
			prv_sql_txt = String.format(
				"SELECT * FROM logger "+
				"WHERE stmp>='1970-01-01' "
			);
			tbl.getItems().clear();
		});
		
		tbl.setContextMenu(new ContextMenu(itm1,itm2,itm3,itm4));
		return tbl;
	}
}
