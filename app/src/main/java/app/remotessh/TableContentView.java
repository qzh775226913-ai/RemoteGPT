package app.remotessh;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.Consumer;

/** One native grid, scrolled as a unit. Existing cells survive streaming updates. */
@SuppressLint("ViewConstructor")
final class TableContentView extends LinearLayout {
  private static final int TEXT_SIZE=14;
  private final int fg,muted,panel;
  private final Consumer<String> open,copy;
  private final GridScroll scroll;
  private final TableLayout grid;
  private final List<List<Cell>> cells=new ArrayList<>();
  private TableMarkup.Table data;
  private int[] widths;

  TableContentView(Context context,int fg,int muted,int panel,Consumer<String> open,Consumer<String> copy) {
    super(context);setOrientation(VERTICAL);this.fg=fg;this.muted=muted;this.panel=panel;this.open=open;this.copy=copy;
    LinearLayout toolbar=new LinearLayout(context);toolbar.setGravity(Gravity.CENTER_VERTICAL);
    TextView label=new TextView(context);label.setText(UiText.get(R.string.ui_table));label.setTextSize(12);label.setTextColor(muted);
    toolbar.addView(label,new LayoutParams(0,-2,1));
    TextView button=new TextView(context);button.setText(UiText.get(R.string.ui_copy));button.setTextSize(13);button.setTextColor(fg);
    button.setGravity(Gravity.CENTER);button.setMinHeight(dp(48));button.setPadding(dp(12),0,dp(12),0);
    button.setContentDescription(UiText.get(R.string.ui_copy_table));PressFeedback.apply(button,(fg&0xffffff)>0x808080);
    button.setOnClickListener(v->{if(copy!=null && data!=null)copy.accept(data.raw);});toolbar.addView(button);addView(toolbar);
    scroll=new GridScroll(context);scroll.setFillViewport(true);scroll.setHorizontalScrollBarEnabled(false);
    grid=new TableLayout(context);grid.setStretchAllColumns(true);grid.setShrinkAllColumns(false);
    scroll.addView(grid,new HorizontalScrollView.LayoutParams(-2,-2));addView(scroll,new LayoutParams(-1,-2));
  }
  private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
  String key(){return data==null?"":data.key;}
  String raw(){return data==null?"":data.raw;}
  int offset(){return scroll.getScrollX();}
  void restoreOffset(int value){scroll.restoreX=value;requestLayout();}
  void setTable(TableMarkup.Table next) {
    if(data!=null && data.raw.equals(next.raw))return;
    int previous=scroll.getScrollX();boolean append=data!=null && next.raw.startsWith(data.raw);
    if(widths==null || widths.length!=next.columns() || !append)widths=new int[next.columns()];
    data=next;
    TextPaint paint=new TextPaint();paint.setTextSize(TEXT_SIZE*getResources().getDisplayMetrics().scaledDensity);
    for(List<String> row:next.rows)for(int c=0;c<next.columns();c++) {
      CharSequence display=Markdown.render(row.get(c),muted,panel);
      float longest=0;for(String line:display.toString().split("\n",-1))longest=Math.max(longest,paint.measureText(line));
      widths[c]=Math.max(widths[c],Math.max(dp(48),Math.min(dp(240),(int)Math.ceil(longest)+dp(12))));
    }
    while(cells.size()>next.rows.size()){cells.remove(cells.size()-1);grid.removeViewAt(grid.getChildCount()-1);}
    for(int r=0;r<next.rows.size();r++) {
      if(r==cells.size()) {
        TableRow row=new Row();List<Cell> rowCells=new ArrayList<>();
        for(int c=0;c<next.columns();c++) {
          Cell cell=new Cell(r==0,next.align.get(c));rowCells.add(cell);row.addView(cell,new TableRow.LayoutParams(widths[c],-1));
        }
        cells.add(rowCells);grid.addView(row,new TableLayout.LayoutParams(-1,-2));
      }
      for(int c=0;c<next.columns();c++) {
        Cell cell=cells.get(r).get(c);cell.body.setSource(next.rows.get(r).get(c));
        TableRow.LayoutParams params=(TableRow.LayoutParams)cell.getLayoutParams();params.width=widths[c];cell.setLayoutParams(params);
      }
    }
    scroll.restoreX=previous;requestLayout();
  }
  private final class Row extends TableRow {
    private final Paint divider=new Paint();
    Row(){super(TableContentView.this.getContext());divider.setColor((fg&0xffffff)|0x14000000);}
    @Override protected void dispatchDraw(Canvas canvas) {
      super.dispatchDraw(canvas);
      // Each edge is painted once, including intersections, so translucency stays uniform.
      int top=this==grid.getChildAt(0)?1:0,bottom=getHeight()-1;
      if(top==1)canvas.drawRect(0,0,getWidth(),1,divider);
      canvas.drawRect(0,top,1,bottom,divider);
      for(int c=1;c<getChildCount();c++) {
        int x=getChildAt(c).getLeft();canvas.drawRect(x,top,x+1,bottom,divider);
      }
      canvas.drawRect(getWidth()-1,top,getWidth(),bottom,divider);
      canvas.drawRect(0,getHeight()-1,getWidth(),getHeight(),divider);
    }
  }
  private final class Cell extends LinearLayout {
    final MessageBodyView body;
    private final boolean heading;private final int alignment;
    Cell(boolean heading,int alignment) {
      super(TableContentView.this.getContext());this.heading=heading;this.alignment=alignment;setOrientation(VERTICAL);
      setBackgroundColor(heading?panel:android.graphics.Color.TRANSPARENT);
      setPadding(dp(4),dp(2),dp(4),dp(2));
      body=new MessageBodyView(getContext(),fg,muted,panel,open,copy,TEXT_SIZE,1);addView(body,new LayoutParams(-1,-2));
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec) {style(body);super.onMeasure(widthSpec,heightSpec);}
    private void style(View view) {
      if(view instanceof TextView) {
        TextView text=(TextView)view;text.setGravity(alignment==1?Gravity.CENTER_HORIZONTAL:alignment==2?Gravity.RIGHT:Gravity.LEFT);
        if(heading && (text.getTypeface()==null || !text.getTypeface().isBold()))text.setTypeface(text.getTypeface(),Typeface.BOLD);
      }
      if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)style(((ViewGroup)view).getChildAt(i));
    }
  }
  private static final class GridScroll extends HorizontalScrollView {
    int restoreX=-1;
    private float downX,downY;
    private int direction; // 0: undecided, 1: horizontal, 2: vertical
    private final int touchSlop;
    private ConversationScrollView gestureParent;
    GridScroll(Context context){super(context);touchSlop=ViewConfiguration.get(context).getScaledTouchSlop();}
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
      int action=event.getActionMasked();
      if(action==MotionEvent.ACTION_DOWN) {
        releaseGesture();
        downX=event.getX();downY=event.getY();direction=0;
        // Keep the parent from intercepting the first diagonal MOVE before we can
        // choose its dominant direction. A vertical gesture is released below.
        if(getParent()!=null && (canScrollHorizontally(-1)||canScrollHorizontally(1)))
          getParent().requestDisallowInterceptTouchEvent(true);
      } else if(action==MotionEvent.ACTION_MOVE && direction==0) {
        float dx=Math.abs(event.getX()-downX),dy=Math.abs(event.getY()-downY);
        if(Math.max(dx,dy)>touchSlop) {
          direction=dx>dy?1:2;
          if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(direction==1);
          if(direction==1)for(ViewParent parent=getParent();parent!=null;parent=parent.getParent()) {
            if(parent instanceof ConversationScrollView) {
              gestureParent=(ConversationScrollView)parent;gestureParent.holdHorizontalGesture(this);break;
            }
          }
        }
      }
      try {return super.dispatchTouchEvent(event);}
      finally {
        if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL) {
          direction=0;
          releaseGesture();
          if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);
        }
      }
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
      return direction!=2 && super.onInterceptTouchEvent(event);
    }
    private void releaseGesture() {
      if(gestureParent!=null){gestureParent.releaseHorizontalGesture(this);gestureParent=null;}
    }
    @Override protected void onDetachedFromWindow(){releaseGesture();super.onDetachedFromWindow();}
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b) {
      super.onLayout(changed,l,t,r,b);if(restoreX>=0){scrollTo(restoreX,0);restoreX=-1;}
    }
  }
}
