package provingground
import HoTT._


  trait TermRec[U] {
	val specialTerms: PartialFunction[Term, U]
  
    def fromString(str: String): U

    def appln(func: U, arg: U): U

    def arrow(dom: U, codom: U): U

    def lambda(variable: U, value: U): U

    def pi(fibre: U): U

    def sigma(fibre: U): U

    def plus(first: U, second: U): U
    
    def pair(first: U, second: U): U

    def symbobj(term: SymbObj[Term]): U

    def symbtyp(term: SymbTyp): U

    def apply(term: Term): U = term match {
 //     case applptnterm(func, arg) => appln(apply(func), apply(arg))
      case LambdaFixed(x : Term, y: Term) => lambda(apply(x), apply(y))
      case Lambda(x: Term, y: Term) => lambda(apply(x), apply(y))
      case PiTyp(fibre) => pi(apply(fibre))
      case SigmaTyp(fibre) => sigma(apply(fibre))
      case PlusTyp(first, scnd) => plus(apply(first), apply(scnd))
      case p: AbsPair[_, _] => pair(this(p.first), this(p.second)) 
      case fn: FuncTyp[_, _] => arrow(apply(fn.dom), apply(fn.codom))
      case sym: SymbObj[_] => symbobj(sym)
      case sym: SymbTyp => symbtyp(sym)
      case _ => fromString(term.toString)
    }
  }


