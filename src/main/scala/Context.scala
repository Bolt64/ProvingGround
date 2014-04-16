package provingGround

import provingGround.HoTT._
import scala.reflect.runtime.universe.{Try => UnivTry, Function => FunctionUniv, _}

object Context{
  	
	trait ConstFmlyTmpl extends Term with AtomicObj{
	  val typ : LogicalTyp
	  
	  
	  type ObjTyp <: Term
	  
	  def map(Q: => LogicalTyp) : ConstFmlyTmpl
	  
	  def dmap(Q: Term => LogicalTyp) : Term => ConstFmlyTmpl
	  
	  def ->:(A : LogicalTyp) = ParamConstTmpl(A, this)
	  
	  def pushforward(f: Term => Term)(arg: ObjTyp) : Term
	}
	
	
	case class ConstTmpl(typ: LogicalTyp) extends ConstFmlyTmpl{
//	  val fullTyp = typ
	  
	  type ObjTyp = typ.Obj
	  
	  def map(Q: => LogicalTyp) = ConstTmpl(Q)
	  
	  def dmap(Q: Term => LogicalTyp) : Term => ConstFmlyTmpl = (obj) => ConstTmpl(Q(obj))
	  
	  def pushforward(f: Term => Term)(arg: ObjTyp) = f(arg)
	}
	
	case class ParamConstTmpl(base: LogicalTyp, cod: ConstFmlyTmpl) extends ConstFmlyTmpl{
	  type baseObjTyp = Typ[baseObj]
	  
	  type baseObj = base.Obj
	  
	  val typ = FuncTyp[Term, LogicalTyp, Term](base.asInstanceOf[LogicalTyp], cod.typ)
	  
	  type ObjTyp = FuncObj[Term, LogicalTyp, Term]
	  
	  def push(func: ObjTyp)(arg: base.Obj): cod.ObjTyp = func(arg).asInstanceOf[cod.ObjTyp] 
	  
	  def pushforward(f: Term => Term)(func: ObjTyp) = {
	    val g = cod.pushforward(f) _
	    
	    val s : base.Obj => Term = (arg: base.Obj) => g(push(func)(arg))
	    
	    val ss : Term => Term = (arg) => s(arg.asInstanceOf[base.Obj])
	    
	    FuncDefn[Term, LogicalTyp, Term](ss, base, cod.typ)
	  }
	  
	  def map(Q: => LogicalTyp): ParamConstTmpl = base ->: cod.map(Q)
	  
	  def dmap(Q: Term => LogicalTyp) : Term => ConstFmlyTmpl = {
	    case f: typ.Obj => 
	      val fibre: Term => ConstFmlyTmpl = (obj) => ConstTmpl(Q(f(obj.asInstanceOf[base.Obj])))
	      DepParamConstTmpl(typ, fibre)
	  } 
	}
	
	case class DepParamConstTmpl(base: LogicalTyp, fibre: Term => ConstFmlyTmpl) extends ConstFmlyTmpl{
	  val typ = PiTyp(TypFamilyDefn(base, ((obj) => fibre(obj).typ)))
	  
	  type ObjTyp = DepFuncObj[Term, LogicalTyp, Term]
	  
	  def push(func: ObjTyp)(arg: base.Obj) = {
	    val cod = fibre(arg)
	    func(arg).asInstanceOf[cod.ObjTyp]
	  }
	  
	  def pushforward(f: Term => Term)(func: ObjTyp) = {	    
	    val s : base.Obj => Term = (arg: base.Obj) => {
	      val cod = fibre(arg)
	      val g = cod.pushforward(f) _
	      g(push(func)(arg).asInstanceOf[cod.ObjTyp])
	    }
	    
	    val ss : Term => Term = (arg) => s(arg.asInstanceOf[base.Obj])
	    
	    def fibretyp(arg: Term) = fibre(arg).typ
	    
	    DepFuncDefn[Term, LogicalTyp, Term](ss, base, TypFamilyDefn(base, fibretyp _))
	  }
	  
	  def map(Q: => LogicalTyp): DepParamConstTmpl = DepParamConstTmpl(base, (obj) => fibre(obj).map(Q))
	  
	   def dmap(Q: Term => LogicalTyp) : Term => ConstFmlyTmpl = {
	    case f: typ.Obj => 
	      val fibre: Term => ConstFmlyTmpl = (obj) => ConstTmpl(Q(f(obj)))
	      DepParamConstTmpl(typ, fibre)
	  } 
	}
	
	
	// Inductive types can be constructed from a context.
	
	trait ContextElem[+X <: Term]{
	  val constants: List[X]
	  
	  val variables: List[X]
	  
	  val dom: LogicalTyp
	  
	  def exptyp(tp: LogicalTyp) : LogicalTyp 
	  
	  def fulltyp(tp: LogicalTyp) : LogicalTyp 
	  
	  
	  def get(value: Term): Term
	  
	  def subs(x: Term, y: Term): ContextElem[X]
	  

	}
	
	trait Context[+X <: Term] extends ContextElem[X]{
	  /*
	   * The codomain for the multi-function given by the context.
	   */
	  val target: LogicalTyp
	  
	  /*
	   * The type of the object : a multivariate function : that comes from the context.
	   */
	  val typ : LogicalTyp
	  
	  type ObjTyp = typ.Obj
	  
	  
	  def /\:[U <: Term : TypeTag](obj: U) = ContextSeq(LambdaContext(obj), this) 
	  
	  def |:[U <: Term : TypeTag](obj: U) = ContextSeq(KappaContext(obj), this)
	  	  
	  def subs(x: Term, y: Term): Context[X]
	  
	  def recContext(f : Term => Term): Context[X] 
	  
	  def patternMatch(obj: Term) : Option[(Term, List[Term])]
	  
	  object Pattern{
	    def unapply(obj: Term): Option[(Term, List[Term])] = patternMatch(obj)
	  }
	  
	  /*
	   * This can be applied to the ctx being a recursive/inductive one, besides this.
	   */
	  def patternDefn(ctx: Context[Term], fn: Term, obj : Term): PartialFunction[Term, Term] = {
	    case Pattern(`fn`, l) => Context.fold(ctx, l)(obj)
	  }
	  
	}
	
	
	object Context{
	  def instantiate[X <: Term : TypeTag](x: X, y: X): Context[X] => Context[X] = {
	    case ContextSeq(LambdaContext(`x`), tail)  => ContextSeq(KappaContext(y), tail.subs(x, y))
	    case ContextSeq(head, tail) => 
	      val inst = instantiate(x,y)
	      ContextSeq(head.subs(x,y), inst(tail))
	    case ctx => ctx
	  }
	  
	  def instantiateHead[X <: Term : TypeTag](y: Term) : Context[X] => Context[X] = {
	    case ContextSeq(LambdaContext(x), tail) => tail subs (x,y)
	    case ContextSeq(head, tail) => 
	      val inst = instantiateHead(y)
	      ContextSeq(head, inst(tail))
	    case ctx => ctx
	  }
	  
	  def apply(dom: LogicalTyp) = simple(dom)
	  
	  def fold[X <: Term : TypeTag](ctx: Context[X], seq: Seq[Term])(obj : Term) : Term = {
			  if  (seq.isEmpty) ctx.get(obj) 
			  else {val inst = instantiateHead[X](seq.head)
			    fold(inst(ctx), seq.tail)(obj)
			  }
	  }
	  
	  def symbpattern[A, X <: Term](symbs: List[A], ctx: Context[X]) : List[Term] = ctx match {
	    case ContextSeq(head, tail) => head.cnst.typ.symbObj(symbs.head) :: symbpattern(symbs.tail, tail) 
	    case _ => List()
	  }
	  
	  def recsymbpattern(f: Term => Term, Q : LogicalTyp, symbs : List[Term], ctx : Context[ConstFmlyTmpl]) : Context[Term] = ctx match {
	    case ContextSeq(LambdaContext(a), tail) => 
	      val b = a map (Q)
	      ContextSeq(LambdaContext(a.typ.symbObj(symbs.head)), 
	          ContextSeq(KappaContext(f(a.typ.symbObj(symbs.head))),recsymbpattern(f, Q, symbs.tail,tail)))
	    case cntx => cntx
	  }
	  
	  case class simple[X <: Term](dom: LogicalTyp) extends Context[X]{
	    val target = dom
	    
	    val typ = dom
	    
	    val constants = List()
	    
	    val variables = List()
	    
	    def exptyp(tp: LogicalTyp) : LogicalTyp = dom
	  
	    def fulltyp(tp: LogicalTyp) : LogicalTyp = dom
	  
	    def get(value: Term): Term = value
	  
	    def subs(x: Term, y: Term): Context[X] = simple(dom)
	    
	    def patternMatch(obj: Term) = if (obj.typ == typ) Some((obj, List())) else None
	    
	    //Should be applied to an appropriate induced map
	    def recContext(f : Term => Term): Context[X] = this
	    
	  }
	}
	
	
	
	trait AtomicContext[+X <: Term] extends ContextElem[X]{
	  val cnst: X
	  
	  val dom = cnst.typ.asInstanceOf[LogicalTyp]
	  
	  val constants = List(cnst)
	 
	  
	  def fulltyp(tp: LogicalTyp) = FuncTyp[Term, LogicalTyp, Term](dom , tp)
	  
	  def subs(x: Term, y: Term): AtomicContext[X]
	}
	
	case class ContextSeq[+X <: Term : TypeTag](head: AtomicContext[X], tail: Context[X]) extends Context[X]{
	  val target = tail.target
	  
	  val typ = head.exptyp(tail.typ)
	  
	  lazy val constants = head.cnst :: tail.constants
	  
	  lazy val variables = head.variables ::: tail.variables
	  
	  val dom = head.dom
	  
	  def get(value: Term) = head.get(tail.get(value))
	  
	  def exptyp(tp: LogicalTyp) = head.exptyp(tail.exptyp(tp))
	  
	  def fulltyp(tp: LogicalTyp) = head.exptyp(tail.exptyp(tp))
	  
	  def subs(x: Term, y: Term) = ContextSeq(head.subs(x,y), tail.subs(x, y))
	  
	  /*
	   * The types should be checked
	   */
	  def patternMatch(obj: Term) : Option[(Term, List[Term])] = head match {
	    case l : LambdaContext[_] => 
	      tail.patternMatch(obj) flatMap ((xl) =>  xl._1 match{
	        case applptnterm(func, arg) if (func.dom == dom) => Some((func, arg :: xl._2))
	        case _ => None
	      }	      
	      )
	    case _ => tail.patternMatch(obj)
	  }
	  
	  def recContext(f : Term => Term): Context[X] = head match {
	    case _ : KappaContext[_] => this
	    case l: LambdaContext[_] => ContextSeq(l, ContextSeq(KappaContext(f(l.cnst).asInstanceOf[X]), tail))
	  }
	}
	
	case class LambdaContext[U <: Term  : TypeTag](cnst: U) extends AtomicContext[U]{
	  def export(value: Term) : Term => Term =  (obj) => value.subs(cnst, obj)	  
	  
	  def get(value: Term) = Lambda(cnst, value)
	  
	  def exptyp(tp: LogicalTyp) = FuncTyp[Term, LogicalTyp, Term](dom, tp)
	  
	  val variables = List(cnst)
	  
	  def subs(x: Term, y: Term) = LambdaContext(cnst.subs(x, y).asInstanceOf[U])
	}
	
	case class KappaContext[U <: Term : TypeTag](cnst: U) extends AtomicContext[U]{
	  def export(value: Term) : Term => Term = _ => value
	  
	  def get(value: Term) = value
	  
	  def exptyp(tp: LogicalTyp) = tp
	  
	  val variables = List()
	  
	  def subs(x: Term, y: Term) = LambdaContext(cnst.subs(x, y).asInstanceOf[U])
	}
	
	
	trait DefnPattern{
	  /*
	   * This is the type of the object defined by the pattern
	   */
	  val typ : LogicalTyp
	  
	  
	  val fold : Term
	  
	  val contextPrep : Context[Term] => Context[Term]
	  
	  lazy val context = contextPrep(Context.simple(typ))
	  
	   
	  def recContextPrep(f :Term => Option[Term]) : Context[Term] => Context[Term]
	  
	  def recContext(f :Term => Option[Term]) = recContextPrep(f)(Context.simple(typ))
	  
	  
	}
	
	object DefnPattern{
	  
	}
	
	case class Const(head: Term) extends DefnPattern{
	  val typ = head.typ.asInstanceOf[LogicalTyp]
	  
	  val fold = head
	  
	  lazy val contextPrep : Context[Term] => Context[Term] = (ctx) => ContextSeq(LambdaContext(head), ctx) 
	  
	  def recContextPrep(f :Term => Option[Term]) : Context[Term] => Context[Term] = (ctx) =>
	    f(head) match {
	      case Some(fx) => ContextSeq(LambdaContext(head), ContextSeq(KappaContext(fx), ctx))
	      case None => ContextSeq(LambdaContext(head), ctx)
	    }
	  
	}
	
	/*
	 * This also includes the case of dependent functions.
	 */
	case class FuncPattern(head: DefnPattern, tail: DefnPattern) extends DefnPattern{
	  
	  val typ = fold.typ.asInstanceOf[LogicalTyp]
	  
	  val fold  = head.fold match {
	    case f : FuncObj[d, _,_] if f.dom == tail.typ => f(tail.fold.asInstanceOf[d])
	    case f : FuncTerm[d, _] if f.dom == tail.typ => f(tail.fold.asInstanceOf[d])
	  } 
	  
	  
	  lazy val contextPrep : Context[Term] => Context[Term] = (ctx) => head.contextPrep(tail.contextPrep(ctx)) 
	  
	  def recContextPrep(f :Term => Option[Term]) : Context[Term] => Context[Term] = (ctx) => 
	    head.recContextPrep(f)(tail.recContextPrep(f)(ctx))
	 
	}
	
	case class CasesSymb[U <: Term](cases: List[Term], typ : Typ[U]) extends Term{
	  def subs(x: Term, y: Term) = CasesSymb(cases map (_.subs(x,y)), typ.subs(x,y))
	}
	
	case class UnionPattern(ps: List[DefnPattern]) extends DefnPattern{
	  val typ = ps.head.typ
	  
	  val fold = typ.symbObj(CasesSymb(ps map ((pat) => (pat.fold)), typ))
	  
	  val contextPrep : Context[Term] => Context[Term] = (ctx) => ctx
	  
	  def recContextPrep(f :Term => Option[Term]) : Context[Term] => Context[Term] = (ctx) => ctx
	}
	
	
	// Avoid using type patterns, instead use only defn patterns with blanks where no object is needed.
	
	trait TypPattern{
	  /*
	   * The pattern does not determine the type in the case of dependent types
	   */
//	  val typ : LogicalTyp
	
	}
	
	object TypPattern{
	  val fromConstFmlyTmpl: ConstFmlyTmpl => TypPattern = {
	    case ConstTmpl(tp : LogicalTyp) => ConstTyp(tp)
	    case ParamConstTmpl(head, tail) => FuncTypPattern(ConstTyp(head), fromConstFmlyTmpl(tail))
	    case DepParamConstTmpl(head, tail) => DepFuncTypPattern(ConstTyp(head), (obj) =>  fromConstFmlyTmpl(tail(obj)))
	    
	  }
	}
	
	case class ConstTyp(head : LogicalTyp) extends TypPattern{
	//  val typ = head

	 
	}
	
	case class FuncTypPattern(head: TypPattern, tail: TypPattern) extends TypPattern{
	  
	//  val typ = head.typ match {
	//    case f : FuncTyp[_, _, _] if f.dom == tail.typ => f.codom.asInstanceOf[LogicalTyp]  
	//  } 
	  
	}
	
	case class DepFuncTypPattern(head: TypPattern, tail: Term => TypPattern) extends TypPattern

	
	
	trait InductSeq{
	  val typ: LogicalTyp
	  
	  val pattern: TypPattern
	  
	  def map(Q : => LogicalTyp): InductSeq
	}
	
	/*
	 * This is for constant sequences not ending in the type W being defined
	 */
	case class CnstInductSeq(fm : ConstTmpl) extends InductSeq{
	  val typ = fm.typ
	  
	  val pattern = TypPattern.fromConstFmlyTmpl(fm)
	  
	  def map(Q : => LogicalTyp) = this
	}
	
	/*
	 * These are families ending in the type being defined
	 */
	case class TargetInductSeq(w: ConstFmlyTmpl) extends InductSeq{
	  val typ = w.typ
	  
	  val pattern = TypPattern.fromConstFmlyTmpl(w)
	  
	  def map(Q : => LogicalTyp) = TargetInductSeq(w map Q)
	}
	
	case class InductSeqCons(head: ConstFmlyTmpl, tail: InductSeq) extends InductSeq{
	  val typ = FuncTyp[Term, LogicalTyp, Term](head.typ, tail.typ) 
	  
	  val pattern = FuncTypPattern(TypPattern.fromConstFmlyTmpl(head), tail.pattern)
	  
	  def map(Q : => LogicalTyp) = InductSeqCons(head map Q, tail map Q)
	}
	
	case class InductSeqDepCons(head: ConstFmlyTmpl, tail : Term => InductSeq) extends InductSeq{
	  val typ = PiTyp[Term, LogicalTyp, Term](TypFamilyDefn(head.typ, (obj) => tail(obj).typ))
	  
	  val pattern = DepFuncTypPattern(TypPattern.fromConstFmlyTmpl(head), (obj) => tail(obj).pattern)
	  
	  def map(Q : => LogicalTyp) = InductSeqDepCons(head map Q, (obj) => tail(obj) map Q)
	}
	
	case class InductCons(name: Term, constyp : InductSeq){
	  def map(Q: => LogicalTyp) = InductCons(name, constyp map Q)
	  
	  val obj = constyp.typ.symbObj(name)
	}
	
	class InductiveTyp(consPatterns : List[InductCons]) extends LogicalSTyp{
	  lazy val cons = consPatterns map (_.map(this).obj)
	}
	
	
	
	
	object AlsoOld{
	
	trait ConstructorPattern{
	  val typ : LogicalTyp
	  
	  /*
	   * closed means no free variables
	   */
	  val isClosed : Boolean
	  
	  val dom: LogicalTyp
	  
	}
	
	object ConstructorPattern{
	  case class Overfull(typ : LogicalTyp) extends ConstructorPattern{
	    val isClosed = false
	    
	    val dom = typ
	  }
	}
	
	case class SimpleConstructor(typ: LogicalTyp) extends ConstructorPattern{
	  val isClosed = true
	  
	  val dom = Unit
	}
	
	case class FuncConstructor(dom : LogicalTyp, tail: ConstructorPattern) extends ConstructorPattern{
	  val typ = FuncTyp[Term, LogicalTyp, Term](dom, tail.typ)
	  
	  val isClosed = false	  	  
	}
	
	class Constructor(pattern: ConstructorPattern) extends RecDefnPattern{
	  val typ = pattern.typ
	  
	  val isClosed = pattern.isClosed
	  
	  val isValid = true
	  
	  val tail   = pattern match {
	    case f : FuncConstructor => f.tail
	    case _ => ConstructorPattern.Overfull(typ)
	  }
	  
	  val argsTotal = true
	  
	  val head = this
	}
	
	/*
	 * A typed pattern for defining objects, given recursively except for its head. Can also have constants in place of the variables
	 */
	trait DefnPattern{
	  val typ : LogicalTyp
	  
	  /*
	   * totality of a definition based on this pattern
	   */
	  val isTotal : Boolean
	  
	  /*
	   * Checks if the types are correct and we have not applied where there is no free vairable
	   */
	  val isValid : Boolean
	}
	
	trait RecDefnPattern extends DefnPattern{
	  val isClosed : Boolean
	  
	  val tail: ConstructorPattern
	  
	  val isTotal = false
	  
	  val argsTotal : Boolean
	  
	  val head: Constructor
	}
	
	
	case class Var(obj: Term) extends DefnPattern{
	  val typ = obj.typ.asInstanceOf[LogicalTyp]
	  
	  val isTotal = true
	  
	  val isValid = true
	}
	
	case class VarFamily(name: Term, tmpl: ConstFmlyTmpl) extends DefnPattern{
	  
	  val typ = tmpl.typ
	  
 
	  
	  val isTotal = true
	  
	  val isValid = true
	}
	
	case class ApplyPattern(func: RecDefnPattern, arg: DefnPattern) extends RecDefnPattern{
	  val typ = func.tail.typ
	  
	  val argsTotal = arg.isTotal && func.argsTotal
	  
	  val tail = func.tail match {
	    case f : FuncConstructor => f.tail
	    case _ => ConstructorPattern.Overfull(typ)
	  }
	  
	  val isClosed = func.tail.isClosed
	  
	  val isValid = arg.isValid && func.isValid && (func match {
	    case f : FuncConstructor => arg.typ == f.dom 
	    case _ => false
	  }
	  )
	  
	  val head = func.head
	}
	
	trait InductiveTypLike extends LogicalTyp{self =>
	  val constrs: Set[Constructor]
	  
	  case class AggregatePattern(ps : Set[RecDefnPattern]) extends DefnPattern{
	    val typ =self
	    
	    val mtch = ps.map(_.head) == constrs
	    
	    val isValid = (mtch /: ps.map(_.isValid))(_ && _)
	    
	    val isTotal = (mtch /: ps.map(_.argsTotal))(_ && _)
	  }
	}
	
	
	

//	def totalPatterns(defs : List[DefnPattern]) : Boolean
	
	
	
	
	class ConstructorDefn(defn : LogicalTyp => Context[ConstFmlyTmpl], target: => LogicalTyp){
	  lazy val context = defn(target)
	}
	
	class InductiveTyp(constructors : List[ConstructorDefn]){
	  lazy val constructorContexts = constructors map (_.context)
	  
	  // Pattern matching functions are defined in terms of maps from Constructors.
	  class Constructor(ctx: Context[ConstFmlyTmpl]){
	    // have simple/recursive/inductive contexts and definitions here
	  }
	  
	  type SimpleDefn = Constructor => Term
	  
	  // Deprecate
	  def namedConstructors[A](syms: List[A]) = for ((n, t) <- syms zip constructorContexts) yield (t.typ.symbObj(n))
	}
	}
	object Old{
	
	class InductiveTyp(cnstrFns: List[LogicalTyp => Context[ConstFmlyTmpl]]) extends LogicalSTyp{self =>
	  lazy val cnstrCtxs = cnstrFns map (_(this))
	  
	  class Constructor(val ctx: Context[ConstFmlyTmpl]){
	    def apply(pattern: List[Term]) = ObjPattern(this, pattern)
	  }
	  
	  
	  val constructors = cnstrCtxs map (new Constructor(_))
	  
	  /*
	   * The patterns in contexts should replace these
	   */
	  case class ObjPattern(cnst: Constructor, pattern: List[Term]) extends Term{
	    val typ = self
	    
	    def subs(x: Term, y: Term) = ObjPattern(cnst, pattern map (_.subs(x,y)))
	  }
	  
	  /* 
	   * There must be a match between the lambda types of the base and ctx. One should be using the patterns in contexts.
	   */
	  def patternMatch(base: Constructor, ctx: Context[ConstFmlyTmpl], value: Term): PartialFunction[Term, Term] = {
	    case ObjPattern(`base`, pattern) => Context.fold(ctx, pattern)(value) 
	  }
	}
	
	
	// May not be needed
	trait InductiveConstructor[+A]{
	  val sym: A
	}
	
	object InductiveConstructor{
	  case class const[A](sym: A)  extends InductiveConstructor[A]
	}
	
	case class ToW[A, B](sym: A, head: LogicalTyp => ConstFmlyTmpl, tail: InductiveConstructor[B]) extends InductiveConstructor[A]
	
	case class IndctParam[A, B](sym: A, head: LogicalTyp, tail: InductiveConstructor[B]) extends InductiveConstructor[A]
	
	// Should also add dependent function
	}
}