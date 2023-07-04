package narl.itrc;

import java.util.Optional;

import com.jfoenix.controls.JFXButton;

import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.ButtonBar.ButtonData;

public abstract class PanDialog<T> extends Dialog<T> {

	protected void init(final Node node) {

		DialogPane pan = new DialogPane() {
			@Override
			protected Node createButton(ButtonType buttonType) {
				final JFXButton btn = new JFXButton(buttonType.getText());				
				btn.setPrefSize(48.*3., 48);				
				if(buttonType==ButtonType.OK) {
					btn.getStyleClass().addAll("btn-raised-3","font-console");
				}else if(buttonType==ButtonType.CANCEL) {
					btn.getStyleClass().addAll("btn-raised-1","font-console");
				}
				final ButtonData buttonData = buttonType.getButtonData();
				ButtonBar.setButtonData(btn, buttonData);
		    btn.setDefaultButton(buttonType != null && buttonData.isDefaultButton());
		    btn.setCancelButton(buttonType != null && buttonData.isCancelButton());
		    btn.addEventHandler(ActionEvent.ACTION, ae -> {
		      if(ae.isConsumed()==true){
						return;
					} 
		      if(set_result_and_close(buttonType)==true) {
		      	close();
		      }
		    });
				return btn;
			}			
		};
		pan.getStylesheets().add(Gawain.sheet);
		pan.getButtonTypes().addAll(
			ButtonType.CANCEL,
			ButtonType.OK			
		);
		pan.setContent(node);
		
		setDialogPane(pan);
		setOnCloseRequest(event->{
			T res = getResult();
			if(res instanceof ButtonType){
				setResult(null);//user only click close button
			}
		});	
	} 
		
	public interface EventOption<T> {
		void callback(T txt);
	}
	
	@SuppressWarnings("unchecked")
	public void popup(final EventOption<T> event) {
		Optional<T> opt = showAndWait();
		if(opt.isPresent()==false) {
			return;
		}
		Object obj = opt.get();
		if(obj instanceof ButtonType) {
			//if user has no result converter, dialog will send ButtonType!!!
			return;
		}
		event.callback((T)obj);
	}
	
	protected abstract boolean set_result_and_close(ButtonType type);
	
	//--------------------------------------------
	
	public static class ShowTextArea extends PanDialog<String>{

		public TextArea box = new TextArea();
		
		public ShowTextArea() {
			init(box);
		}
		public ShowTextArea(final String txt) {
			box.setText(txt);
			init(box);
		}
		
		public ShowTextArea setPrefSize(
			final double width,
			final double height
		) {
			box.setPrefSize(width, height);
			return this;
		}
		
		@Override
		protected boolean set_result_and_close(ButtonType type) {			
			if(type.equals(ButtonType.OK)) {				
				setResult(box.getText());
				return true;
			}else if(type.equals(ButtonType.CANCEL)){
				setResult(null);
				return true;
			}			
			return false;
		}		
	}	
}
