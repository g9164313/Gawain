package narl.itrc;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.glass.ui.Application;

import javafx.concurrent.Task;

/**
 * progressUpdate-
 * messageUpdate - 顯示通知或錯誤訊息
 * titleUpdate   - 紀錄狀態(Runnable)
 * valueUpdate   - 
 * 
 */
public abstract class DevTask extends Task<String> {
	
	protected String TAG = "DevBase";
	protected Thread thd;

	private static class BlkRun{
		final String   name;
		final Runnable work;
		BlkRun(String _name, Runnable _work){
			name = _name;
			work = _work;
		}
		void exec_work(){
			if(name.startsWith("__")==true){
				Application.invokeLater(work);
			}else if(name.startsWith("##")==true){
				Application.invokeAndWait(work);
			}else{
				work.run();
			}
		}
	};

	private final ConcurrentLinkedDeque<BlkRun> queue = new ConcurrentLinkedDeque<BlkRun>();

	/**
	 * 第一個被執行的抽象函數，要自行呼叫 nextState()，不然就是 IDLE 狀態
	 */
	abstract public void open();
	/**
	 * 最後被執行的抽象函數，shutdown()後執行～
	 */
	abstract public void close();

	/**
	 * 裝置執行核心，分配每個 Runnable 何時執行
	 */
	@Override
	protected String call() throws Exception {
		open();
		do{
			BlkRun ss = queue.poll();
			if(ss==null){
				try {
					updateTitle("IDLE");
					thd.wait();
				} catch (InterruptedException e) {
					updateMessage("[call] interrupted!");				
				}
			}else{
				try {
					updateTitle(ss.name);
					ss.exec_work();
				} catch (Exception e) {
					updateMessage("[call] next BlkRun~");				
				}				
			}			
		}while(flagDown.get()==false);
		close();
		return thd.getState().toString();
	}
	//-------------------------------

	/**
	 * 執行下一個狀態執行區塊（runnable），EventThread()呼叫會沒作用。
	 * @param name - 狀態名字
	 * @param work - 執行區塊（runnable）
	 */
	public void nextState(final String name, final Runnable work){
		if(Application.isEventThread()==true){
			return;
		}
		queue.addLast(new BlkRun(name,work));		
		throw new RuntimeException();
	}
	public void nextState(final Runnable work){
		if(Application.isEventThread()==true){
			return;
		}
		nextState("anonymous",work);
	}

	/**
	 * 插入狀態執行區塊（runnable），狀態名字有意義。
	 * @param name - 以 "__" 開頭會以 invokeLater() 執行，以 "##" 開頭會以 invokeAndWait() 執行，
	 * @param work - 執行區塊（runnable）
	 */
	public void asyncBreakIn(String name,Runnable work){
		asyncBreakIn(new BlkRun(name,work));
	}
	/**
	 * 方便用的 asyncBreakIn()，名字預設是"breakIn"
	 * @param work - 執行區塊（runnable）
	 */
	public void asyncBreakIn(Runnable work){
		asyncBreakIn("breakIn",work);
	}
	private void asyncBreakIn(BlkRun blk){
		if(Application.isEventThread()==false){
			return;
		}
		queue.addFirst(blk);
		Thread.State ss = thd.getState();
		if(ss==Thread.State.WAITING||ss==Thread.State.TIMED_WAITING) {
			synchronized(thd) { thd.notify(); }
		}
	}

	private final ArrayList<BlkRun> chain = new ArrayList<BlkRun>();
	/**
	 * 連續插入狀態執行區塊（runnable）<p>
	 * 狀態名字有意義，以 "__" 開頭會以 invokeLater() 執行
	 * @param args - 必須以 String,Runnable 形式連續出現～～
	 */
	public void asyncBreakInChain(Object... args){
		chain.clear();//remove old chain!!!!
		for(int i=0; i<(args.length-args.length%2); i+=2){
			if(args[i+0]==null || args[i+1]==null){
				break;
			}
			String   name = (String  )args[i+0];
			Runnable work = (Runnable)args[i+1];
			chain.add(new BlkRun(name,work));
		}
		asyncBreakIn(chain.get(0));
	}
	/**
	 * 當 asyncBreakInChain（）執行時，底下的執行區塊<p>
	 * 必須以 nextChain() 跳到下一個執行區塊<p>
	 * @param idx - chain裡的索引值
	 */
	public void nextChain(final int idx){
		if(idx>=chain.size() || idx<=0){
			System.err.printf("[%s] chain is too short!!!\n",TAG);			
		}else{
			queue.addFirst(chain.get(idx));
		}		
		throw new RuntimeException();
	}
	/**
	 * 當 asyncBreakInChain（）執行時，用名子搜尋 chain 底下的執行區塊<p>
	 * @param name - 根據名字搜尋
	 */
	public void nextChain(final String name){
		BlkRun dst = null;
		for(BlkRun blk:chain){
			if(blk.name.equals(name)==true){
				dst = blk;
				break;
			}
		}
		if(dst==null){
			System.err.printf("[%s] miss BlkRun-[%s]!!!\n",TAG,name);
		}else{
			queue.addFirst(dst);
		}
		throw new RuntimeException();
	}
	//-------------------------------

	private final AtomicBoolean flagDown = new AtomicBoolean(false);	

	public void power_up(){
		flagDown.set(false);
		thd = new Thread(this,TAG);
		thd.setDaemon(true);
		thd.start();
	}
	public void shutdown(){
		try {
			flagDown.set(true);
			thd.join(1000);
		} catch (InterruptedException e) {
			updateMessage("[shutdown] interrupted!!!");
		}
	}
	@Override
	public String toString() { return String.format("[%s]%s", TAG,getMessage()); }
	//-------------------------------
	//TODO: implement Device Manager!!!!
}
