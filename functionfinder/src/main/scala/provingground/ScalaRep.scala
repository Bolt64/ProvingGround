package provingground
import provingground.HoTT._
import scala.reflect.runtime.universe.{Try => UnivTry, Function => FunctionUniv, _}

import scala.util._

import scala.language.implicitConversions
//import provingground.ScalaUniverses._

object ScalaRep {
	/**
	 * Representation by a scala object of a HoTT term
	 *
	 * @tparam U the HoTT type represented
	 * @tparam V scala type representing the given object.
	 */
  trait ScalaRep[+U <: Term, V]{
    /**
     * HoTT type represented.
     */
    val typ : Typ[U]

    /**
     * scala type of representing object.
     */
    type tpe = V

    /**
     * HoTT object from the scala one.
     */
    def apply(v: V) : U

    /**
     * pattern for matching application of the scala object.
     */
    def unapply(u: Term) : Option[V]

    def subs(x: Term, y: Term): ScalaRep[U, V]

    /**
     * scalarep for function
     */
    def -->:[W <: Term with Subs[W], X, UU >: U <: Term with Subs[UU]](that : ScalaRep[W, X]) =
      FuncRep[W, X, UU, V](that, this)

    def :-->[W <: Term with Subs[W], UU >: U <: Term with Subs[UU]](that: Typ[W]) =
      	SimpleFuncRep[UU, V, W](this, that)

    /**
     *   scalarep for sum type.
     */
    def ++[UU >: U <: Term with Subs[UU], X <: Term with Subs[X], Y](
        codrepfmly: V => ScalaRep[X, Y]) = SigmaRep[UU, V, X, Y](this, codrepfmly)
  }

  object ScalaRep{
    def incl[U <: Term, V, W]: (ScalaRep[U, V], ScalaRep[U, W]) => Option[V => W] = {
      case (x, y) if x ==y => Some((v: V) => v.asInstanceOf[W])
      case (rep: ScalaRep[U, V], IdRep(typ)) if rep.typ == typ => Some((v: V) => rep(v).asInstanceOf[W])
      case (fst: FuncRep[_, a, _, b], scnd: FuncRep[_, c, _, d]) =>
        {
          val dmap = incl(scnd.domrep, fst.domrep)
          val cmap = incl(fst.codomrep, scnd.codomrep)
          val m = for (dm <- dmap; cm <- cmap) yield ((f: a=> b) => (x: c) => cm(f(dm(x))))
          m flatMap ((x: (a => b) => (c => d)) => Try(x.asInstanceOf[V => W]).toOption)
        }

      case _ => None
    }
  }


  /**
   * constant  term.
   */
  	trait ConstTerm[T] extends Term{
	  val value: T

	  type scalaType = T

    def newobj = typ.obj

      def subs(x : Term, y: Term) = if (x==this) y else this
    }

  /**
   * formal extension of a function.
   * XXX must check type.
   */
  def extend[T, U <: Term ](fn: T => U, FuncLike: FuncLike[Term, U], codom: Typ[U]): Term => U = {
	  case c: ConstTerm[_]  => Try(fn(c.value.asInstanceOf[T])).getOrElse(codom.symbObj(ApplnSym(FuncLike, c)))
	  case arg: Term => codom.symbObj(ApplnSym(FuncLike, arg))
	}

  /**
   * scala object wrapped to give a HoTT constant with type declared.
   */
  case class SimpleConst[V](value: V, typ: Typ[Term]) extends ConstTerm[V]{
    override def toString = value.toString
  }

  /**
   * Representation by  wrapping.
   */
  case class SimpleRep[V](typ: Typ[Term]) extends ScalaRep[Term, V]{
    def apply(v: V) = SimpleConst(v, typ)

    def unapply(u : Term) : Option[V] = u match  {
      case smp: SimpleConst[V] if smp.typ == typ => Some(smp.value)
      case _ => None
    }

    def subs(x: Term, y: Term) = SimpleRep(typ.subs(x, y))
  }

  /**
   * A term representing itself.
   */
  case class IdRep[U <: Term with Subs[U]](typ: Typ[U]) extends ScalaRep[U, U]{
    def apply(v : U) = v

    def unapply(u: Term): Option[U] = u match{
      case t: Term => Some(t.asInstanceOf[U])
      case _ => None
    }

    def subs(x: Term, y: Term) = IdRep(typ.subs(x, y))
  }

  implicit def idRep[U <: Term with Subs[U]](typ: Typ[U]) : ScalaRep[U, U] = IdRep(typ)

  /**
   * Representations for functions given ones for the domain and codomain.
   */
  case class FuncRep[U <: Term with Subs[U], V, X <: Term with Subs[X], Y](
      domrep: ScalaRep[U, V], codomrep: ScalaRep[X, Y]) extends ScalaRep[Func[U, X], V => Y]{
    lazy val typ = domrep.typ ->: codomrep.typ

    def apply(f: V => Y) : Func[U, X] = ExtendedFunction(f, domrep, codomrep)

    def unapply(u: Term) : Option[V => Y] = u match {
      case ext: ExtendedFunction[_, V, _, Y] if ext.domrep == domrep && ext.codomrep == codomrep => Some(ext.dfn)
      case _ => None
    }
    
    def opt(f: V => Option[Y]) = {
      def optfn(u: U) = u match {
        case domrep(v) => f(v) map (codomrep(_))
        case _ => None
      } 
      OptDepFuncDefn(optfn, domrep.typ)
    }

    def subs(x: Term, y: Term) = FuncRep(domrep.subs(x, y), codomrep.subs(x, y))
  }

  /**
   * Formal extension of a function given by a definition and representations for
   * domain and codomain.
   */
  case class ExtendedFunction[U <: Term with Subs[U], V, X <: Term with Subs[X], Y](dfn: V => Y,
      domrep: ScalaRep[U, V], codomrep: ScalaRep[X, Y]) extends Func[U, X]{

	  lazy val dom = domrep.typ

	  lazy val codom = codomrep.typ

	  lazy val typ = dom ->: codom

	  def newobj = typ.obj

	  def act(u : U) = u match {
	    case domrep(v) => codomrep(dfn(v))
	    case _ => codom.symbObj(ApplnSym(this, u))
	  }

	  // val domobjtpe: reflect.runtime.universe.Type = typeOf[U]

	  // val codomobjtpe: reflect.runtime.universe.Type = typeOf[X]


	  def subs(x: provingground.HoTT.Term,y: provingground.HoTT.Term) = (x, y) match {
	    case (u, v: Func[U ,X]) if u == this => v
	    case _ => ExtendedFunction((v: V) => dfn(v), domrep.subs(x, y), codomrep.subs(x,y))
	  }

  }


  /**
   * Function rep with codomain representing itself. Should perhaps use  IdRep instead.
   */
  case class SimpleFuncRep[U <: Term with Subs[U], V, X <: Term with Subs[X]](
      domrep: ScalaRep[U, V], codom: Typ[X]) extends ScalaRep[FuncLike[U, X], V => X]{
    val typ = domrep.typ ->: codom

    def newobj = typ.obj

    def apply(f: V => X) = SimpleExtendedFunction(f, domrep, codom)

    def unapply(u: Term) : Option[V => X] = u match {
      case ext: SimpleExtendedFunction[_, V, X] if ext.domrep == domrep && ext.codom == codom => Some(ext.dfn)
      case _ => None
    }

    def subs(x: Term, y: Term) = SimpleFuncRep(domrep.subs(x, y), codom.subs(x, y))
  }

  /**
   * Extended function with codomain a type. Perhaps use IdRep.
   */
  case class SimpleExtendedFunction[U <: Term with Subs[U], V, X <: Term with Subs[X]](dfn: V => X,
      domrep: ScalaRep[U, V], codom: Typ[X]) extends Func[U, X] with Subs[SimpleExtendedFunction[U, V, X]]{

	  val dom = domrep.typ


	  val typ = dom ->: codom

	  def newobj = this

	  def act(u : U) = u match {
	    case domrep(v) => dfn(v)
	    case _ => codom.symbObj(ApplnSym(this, u))
	  }

	  // val domobjtpe: reflect.runtime.universe.Type = typeOf[U]

	  // val codomobjtpe: reflect.runtime.universe.Type = typeOf[X]


	  def subs(x: provingground.HoTT.Term,y: provingground.HoTT.Term) : SimpleExtendedFunction[U, V, X] = (x, y) match {
	    case (u, v: SimpleExtendedFunction[U , V, X]) if u == this => v
	    case _ => SimpleExtendedFunction((v: V) => dfn(v).subs(x, y), domrep.subs(x, y), codom.subs(x, y))
	  }

  }

    case class SigmaRep[U <: Term with Subs[U] , V, X <: Term with Subs[X], Y](domrep: ScalaRep[U, V],
      codrepfmly: V => ScalaRep[X, Y]) extends ScalaRep[Term, (V, Y)]{


//    val rep = SimpleFuncRep(domrep, __)

    val rep = FuncRep(domrep, __)

    val fibers = rep((v: V) => codrepfmly(v).typ)

    lazy val typ = SigmaTyp(fibers)

    def apply(vy: (V, Y))  = DepPair(domrep(vy._1), codrepfmly(vy._1)(vy._2), fibers)

    def unapply(u: Term) = u match {
      case DepPair(domrep(v), vy, _) =>
        val codrep = codrepfmly(v)
        vy match {
          case codrep(y) => Some((v, y))
          case _ => None
        }
      case _ => None
    }

    def subs(x: Term, y: Term) = SigmaRep(domrep.subs(x, y), (v: V) => codrepfmly(v).subs(x, y))
  }


  /**
   * Formal extendsion of a dependent function given scalareps for the domain and codomains.
   */
  case class DepFuncRep[U <: Term with Subs[U], V, X <: Term with Subs[X], Y](
      domrep: ScalaRep[U, V], codomreps: V => ScalaRep[X, Y],
      fibers: TypFamily[U, X]) extends ScalaRep[FuncLike[U, X], V => Y]{
    val typ = PiTyp(fibers)

    def apply(f: V => Y) : FuncLike[U, X] = ExtendedDepFunction(f, domrep, codomreps, fibers)

    def unapply(u: Term) : Option[V => Y] = u match {
      case ext: ExtendedDepFunction[_, V, _, Y] if ext.domrep == domrep && ext.codomreps == codomreps => Some(ext.dfn)
      case _ => None
    }

    def subs(x: Term, y: Term) = DepFuncRep(domrep.subs(x, y), (v: V) => codomreps(v).subs(x, y), fibers.subs(x, y))
  }

  /**
   * implicit class associated to a type family to create dependent functions scalareps.
   */
  implicit class FmlyReps[U <: Term with Subs[U], X <: Term with Subs[X]](fibers: TypFamily[U, X]){

    def ~~>:[V](domrep : ScalaRep[U, V]) = {
      DepFuncRep(domrep, (v: V) => IdRep(fibers(domrep(v))), fibers)
    }
  }

    /**
   * implicit class associated to a family of scalareps to create dependent functions scalareps.
   * Not much use since U ends up having strange bounds such as Term with Long.
   */
  implicit class RepSection[V, X <: Term with Subs[X], Y](section: V => ScalaRep[X, Y]){

    def ~~>:[U <: Term with Subs[U]](domrep : ScalaRep[U, V])
    /*(implicit
        sux : ScalaUniv[X], suu: ScalaUniv[U])*/ = {
      val univrep = domrep -->: __
      val fmly = univrep((v: V) => section(v).typ)
      val x = "x" :: domrep.typ

      val fibers = lmbda(x)(fmly(x))
        //typFamily(domrep.typ, fmly)

      DepFuncRep(domrep, (v: V) => section(v), fibers)
    }
  }

  /**
   * formal extension of a dependent function.
   */
    case class ExtendedDepFunction[U <: Term with Subs[U], V, X <: Term , Y](dfn: V => Y,
      domrep: ScalaRep[U, V], codomreps: V => ScalaRep[X, Y], fibers: TypFamily[U, X]) extends FuncLike[U, X]{

	  val dom = domrep.typ

	  val depcodom : U => Typ[X] = (arg : U) => fibers(arg)

	  val typ = PiTyp(fibers)

    def newobj = typ.obj

	  def act(u : U) = u match {
	    case domrep(v) => codomreps(v)(dfn(v))
	    case arg => fibers(arg).symbObj(ApplnSym(this, arg))
	  }

	  // val domobjtpe: reflect.runtime.universe.Type = typeOf[Term]

	  // val codomobjtpe: reflect.runtime.universe.Type = typeOf[U]



	  def subs(x: provingground.HoTT.Term,y: provingground.HoTT.Term) = (x, y) match {
	    case (u, v: FuncLike[U ,X]) if u == this => v
	    case _ => this
	  }

  }

    /*
    case class InclRep[U <: Term , V <: U ](typ: Typ[U]) extends ScalaRep[U, V]{
      def apply(v: V) = v

      def unapply(u: Term) = u match {
        case v : V => Some(v)
        case _ => None
      }
    }
    */

    object dsl{
      def i[V](typ: Typ[Term]) = SimpleRep[V](typ)

      def s[U <: Term with Subs[U] , V, X <: Term with Subs[X], Y](domrep: ScalaRep[U, V])(
      codrepfmly: V => ScalaRep[X, Y]) = SigmaRep(domrep, codrepfmly)
    }


}
