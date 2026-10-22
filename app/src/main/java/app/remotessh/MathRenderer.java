package app.remotessh;

import android.os.Handler;
import android.os.Looper;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.regex.*;


/** Single-worker, bounded local TeX layout. No remote URL, macro definition or file command is accepted. */
final class MathRenderer {
  static final class Formula {
    final MathDrawable drawable;
    final int width,height,baseline;
    Formula(MathDrawable drawable) {
      this.drawable=drawable;width=drawable.getIntrinsicWidth();height=drawable.getIntrinsicHeight();
      baseline=Math.round(drawable.icon().getBaseLine()*height);
    }
  }
  private static final ThreadPoolExecutor WORKER=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,
      new ArrayBlockingQueue<>(64), r->{Thread t=new Thread(r,"formula-layout");t.setDaemon(true);return t;});
  private static final Handler MAIN=new Handler(Looper.getMainLooper());
  private static final Map<String,Formula> CACHE=new LinkedHashMap<String,Formula>(64,.75f,true) {
    @Override protected boolean removeEldestEntry(Map.Entry<String,Formula> entry){return size()>64;}
  };
  private static final Set<String> COMMANDS=new HashSet<>(Arrays.asList((
      "frac dfrac tfrac sqrt bar overline underline vec hat widehat tilde widetilde dot ddot "
      + "alpha beta gamma delta epsilon varepsilon zeta eta theta vartheta iota kappa lambda mu nu xi pi varpi rho varrho sigma varsigma tau upsilon phi varphi chi psi omega "
      + "Gamma Delta Theta Lambda Xi Pi Sigma Upsilon Phi Psi Omega "
      + "approx sim simeq cong equiv ne neq le leq ge geq ll gg in notin ni subset supset subseteq supseteq "
      + "pm mp times div cdot cdots dots ldots vdots ddots ast star circ bullet "
      + "sum prod coprod int iint iiint oint lim limsup liminf min max inf sup sin cos tan cot sec csc arcsin arccos arctan arccot sinh cosh tanh log ln exp det gcd Pr "
      + "infty partial nabla ell hbar imath jmath Re Im forall exists nexists emptyset varnothing land lor neg to rightarrow leftarrow leftrightarrow Rightarrow Leftarrow Leftrightarrow mapsto "
      + "left right middle big Big bigg Bigg bigl bigr Bigl Bigr biggl biggr Biggl Biggr langle rangle lvert rvert lVert rVert vert Vert lfloor rfloor lceil rceil "
      + "text textrm textbf textit operatorname mathrm mathit mathbf mathsf mathtt mathcal mathbb mathfrak boldsymbol "
      + "boxed fbox overbrace underbrace overset underset stackrel binom dbinom tbinom "
      + "begin end hline cr quad qquad enspace thinspace medspace thickspace displaystyle textstyle scriptstyle scriptscriptstyle limits nolimits "
      + "color textcolor phantom hphantom vphantom not braces brace") .split(" +")));
  private static final Set<String> ENVIRONMENTS=new HashSet<>(Arrays.asList(
      "matrix","pmatrix","bmatrix","Bmatrix","vmatrix","Vmatrix","smallmatrix","aligned","alignedat","gathered","cases","array"));

  static boolean supported(String tex) {
    if(tex.length()>4096)return false;
    int depth=0,commands=0;
    for(int i=0;i<tex.length();i++) {
      char c=tex.charAt(i);
      if(c=='\\') {
        if(++commands>256)return false;
        int start=++i;while(i<tex.length() && Character.isLetter(tex.charAt(i)))i++;
        if(i>start && !COMMANDS.contains(tex.substring(start,i)))return false;
        if(i>start)i--;
      } else if(c=='{' && ++depth>32)return false;
      else if(c=='}' && --depth<0)return false;
    }
    if(depth!=0)return false;
    Matcher binary=Pattern.compile("\\\\(?:frac|dfrac|tfrac|binom|dbinom|tbinom|overset|underset|stackrel)(?![A-Za-z])").matcher(tex);
    while(binary.find()) {
      int first=argumentEnd(tex,binary.end());
      if(first<0 || argumentEnd(tex,first)<0)return false;
    }
    Matcher unary=Pattern.compile("\\\\(?:sqrt|bar|overline|underline|vec|hat|widehat|tilde|widetilde|dot|ddot|boxed|fbox|text|textrm|textbf|textit|operatorname|mathrm|mathit|mathbf|mathsf|mathtt|mathcal|mathbb|mathfrak|boldsymbol|phantom|hphantom|vphantom)(?![A-Za-z])").matcher(tex);
    while(unary.find()) {
      int at=unary.end();while(at<tex.length() && Character.isWhitespace(tex.charAt(at)))at++;
      if(unary.group().equals("\\sqrt") && at<tex.length() && tex.charAt(at)=='[') {
        at=tex.indexOf(']',at+1);if(at<0)return false;at++;
      }
      if(argumentEnd(tex,at)<0)return false;
    }
    Matcher env=Pattern.compile("\\\\(?:begin|end)\\s*\\{([^}]+)\\}").matcher(tex);
    while(env.find())if(!ENVIRONMENTS.contains(env.group(1)))return false;
    return true;
  }

  private static int argumentEnd(String tex,int at) {
    while(at<tex.length() && Character.isWhitespace(tex.charAt(at)))at++;
    if(at==tex.length())return -1;
    if(tex.charAt(at)=='{') {
      int depth=1;
      while(++at<tex.length()) {
        char c=tex.charAt(at);
        if(c=='\\'){at++;continue;}
        if(c=='{')depth++;
        else if(c=='}' && --depth==0)return at+1;
      }
      return -1;
    }
    if(tex.charAt(at)=='\\') {
      at++;while(at<tex.length() && Character.isLetter(tex.charAt(at)))at++;
      return at;
    }
    return at+1;
  }

  static synchronized Formula render(String tex,boolean display,float size,int color) {
    if(!supported(tex))return null;
    String key=display+"|"+size+"|"+color+"|"+tex;
    if(CACHE.containsKey(key))return CACHE.get(key);
    Formula result=null;
    try {
      MathDrawable drawable=new MathDrawable(tex,display,size,color);
      if(drawable.getIntrinsicWidth()>0 && drawable.getIntrinsicWidth()<=8192
          && drawable.getIntrinsicHeight()>0 && drawable.getIntrinsicHeight()<=2048)result=new Formula(drawable);
    } catch(RuntimeException | StackOverflowError error) {
      // Keep the canonical TeX visible when the local engine cannot typeset it.
    }
    CACHE.put(key,result);return result;
  }

  static Future<?> submit(List<MathMarkup.Part> parts,float size,int color,Consumer<List<Formula>> callback) {
    try {
      return WORKER.submit(()->{
        List<Formula> results=new ArrayList<>();
        for(MathMarkup.Part part:parts) {
          if(Thread.currentThread().isInterrupted())return;
          results.add(part.kind==MathMarkup.Kind.TEXT?null:render(part.tex,part.kind==MathMarkup.Kind.DISPLAY,size,color));
        }
        MAIN.post(()->callback.accept(results));
      });
    } catch(RejectedExecutionException error){return null;}
  }
  static void cancel(Future<?> future) {
    if(future!=null){future.cancel(false);if(future instanceof Runnable)WORKER.remove((Runnable)future);}
  }
}
